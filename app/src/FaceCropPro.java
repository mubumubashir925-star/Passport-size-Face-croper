import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;

/** Phase 1: Java 8-compatible unified desktop shell. */
public final class FaceCropPro {
    private final JFrame frame = new JFrame("Face Crop Pro");
    private final DefaultListModel<String> files = new DefaultListModel<String>();
    private final JLabel status = new JLabel("Ready — add photos to begin");
    private final JLabel count = new JLabel("0 images selected");
    private final JLabel engineStatus = new JLabel("Engine: checking...");
    private final JButton checkEngine = new JButton("Check Engine");
    private final JButton process = new JButton("Start Cropping");
    private final JTextField inputPath = new JTextField();
    private final JTextField outputPath = new JTextField();
    private final JButton cancel = new JButton("Cancel");
    private final JButton retry = new JButton("Retry Failed");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private volatile boolean stopRequested;
    private FolderRobot robot;
    private Thread robotThread;
    private final JButton robotToggle = new JButton("Start Robot");
    private boolean robotActive;
    private File robotOutput;
    private final java.util.Set<String> robotSeen = new java.util.HashSet<String>();
    private final java.util.List<String> failedFiles = new java.util.ArrayList<String>();
    private final JList<String> fileList = new JList<String>(files);
    private final JLabel outputPreview = new JLabel("Output preview", SwingConstants.CENTER);
    private final JButton editOutput = new JButton("Adjust 4 Corners & Save");
    private final java.util.Map<String, ImageIcon> thumbnails = new java.util.concurrent.ConcurrentHashMap<String, ImageIcon>();
    private final java.util.Set<String> thumbnailJobs = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private final java.util.concurrent.ExecutorService thumbnailPool = java.util.concurrent.Executors.newFixedThreadPool(2, task -> {
        Thread worker = new Thread(task, "FaceCropThumbnail");
        worker.setDaemon(true);
        return worker;
    });

    private final java.util.Map<String, org.opencv.core.Rect> manualCrops = new java.util.HashMap<String, org.opencv.core.Rect>();
    private final JCheckBox trimBorders = new JCheckBox("Remove scan borders", true);
    private final JCheckBox multipleFaces = new JCheckBox("Extract all faces");
    private final JComboBox<String> preset = new JComboBox<String>(new String[]{"413 x 531", "466 x 600", "600 x 600", "Custom"});
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(413, 100, 6000, 1));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(531, 100, 6000, 1));

    private FaceCropPro() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 580));
        frame.setSize(1050, 680);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel heading = new JLabel("Face Crop Pro  v" + AppUpdates.VERSION);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
        JPanel header = new JPanel(new BorderLayout());
        header.add(heading, BorderLayout.WEST);
        JButton updates = new JButton("Check for Updates");
        updates.setToolTipText("Requires internet only while checking; cropping works offline");
        updates.addActionListener(e -> AppUpdates.check(frame));
        header.add(updates, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel sidebar = new JPanel(new GridLayout(0, 1, 0, 8));
        sidebar.setPreferredSize(new Dimension(190, 0));
        for (String section : new String[]{"Batch Crop", "Multiple Faces", "Presets"}) {
            JButton button = new JButton(section);
            button.addActionListener((ActionEvent e) ->
                status.setText(section + " — coming in a later development phase"));
            sidebar.add(button);
        }
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(190, 0));
        left.add(sidebar, BorderLayout.NORTH);
        root.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBorder(BorderFactory.createTitledBorder("Photo processing"));
        JButton add = new JButton("Add Photos");
        JButton addFolder = new JButton("Add Folder");
        addFolder.addActionListener(e -> chooseInputFolder());
        JButton clear = new JButton("Clear");
        JButton preview = new JButton("Preview / Adjust");
        preview.addActionListener(e -> previewSelected());
        process.addActionListener((ActionEvent e) -> startCropping());
        add.addActionListener((ActionEvent e) -> chooseFiles());
        clear.addActionListener((ActionEvent e) -> {
            files.clear();
            manualCrops.clear();
            updateCount();
        });
        checkEngine.addActionListener(e -> checkEngine());
        toolbar.add(checkEngine);
        toolbar.add(add);
        toolbar.add(addFolder);
        toolbar.add(clear);
        toolbar.add(preview);
        toolbar.add(process);
        cancel.setEnabled(false);
        retry.setEnabled(false);
        cancel.addActionListener(e -> { stopRequested = true; status.setText("Stopping after current photo..."); });
        retry.addActionListener(e -> {
            java.util.List<String> copy = new java.util.ArrayList<String>(failedFiles);
            files.clear();
            for (String item : copy) files.addElement(item);
            updateCount();
            startCropping();
        });
        toolbar.add(cancel);
        toolbar.add(retry);
        robotToggle.addActionListener(e -> toggleRobot());
        toolbar.add(robotToggle);
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        options.add(multipleFaces);
        options.add(trimBorders);
        options.add(new JLabel("Size:"));
        options.add(preset);
        options.add(width);
        options.add(new JLabel("x"));
        options.add(height);
        preset.addActionListener((ActionEvent e) -> {
            int i = preset.getSelectedIndex();
            if (i == 0) { width.setValue(413); height.setValue(531); }
            if (i == 1) { width.setValue(466); height.setValue(600); }
            if (i == 2) { width.setValue(600); height.setValue(600); }
        });
        JPanel controls = new JPanel(new BorderLayout());
        controls.add(toolbar, BorderLayout.NORTH);
        controls.add(options, BorderLayout.SOUTH);
        JPanel folders = new JPanel(new GridLayout(2, 1, 4, 4));
        folders.setBorder(BorderFactory.createTitledBorder("Input and output folders"));
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(new JLabel("Input:   "), BorderLayout.WEST);
        inputPath.setToolTipText("Paste an input folder path or use Browse");
        inputRow.add(inputPath, BorderLayout.CENTER);
        JButton inputBrowse = new JButton("Browse Input");
        inputBrowse.addActionListener(e -> chooseInputFolder());
        JPanel inputActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JButton loadInput = new JButton("Load Path");
        loadInput.addActionListener(e -> loadPastedInput());
        inputPath.addActionListener(e -> loadPastedInput());
        inputActions.add(loadInput);
        inputActions.add(inputBrowse);
        inputRow.add(inputActions, BorderLayout.EAST);
        JPanel outputRow = new JPanel(new BorderLayout(6, 0));
        outputRow.add(new JLabel("Output:"), BorderLayout.WEST);
        outputPath.setToolTipText("Paste an output folder path or use Browse");
        outputRow.add(outputPath, BorderLayout.CENTER);
        JButton outputBrowse = new JButton("Browse Output");
        outputBrowse.addActionListener(e -> chooseOutputFolder());
        outputRow.add(outputBrowse, BorderLayout.EAST);
        folders.add(inputRow);
        folders.add(outputRow);
        JPanel topControls = new JPanel(new BorderLayout(4, 4));
        topControls.add(controls, BorderLayout.NORTH);
        topControls.add(folders, BorderLayout.SOUTH);
        center.add(topControls, BorderLayout.NORTH);
        fileList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        fileList.setVisibleRowCount(-1);
        fileList.setFixedCellWidth(150);
        fileList.setFixedCellHeight(175);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = fileList.locationToIndex(e.getPoint());
                    if (index >= 0 && fileList.getCellBounds(index, index).contains(e.getPoint())) {
                        fileList.setSelectedIndex(index);
                        previewSelected();
                    }
                }
            }
        });
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                String path = String.valueOf(value);
                label.setIcon(thumbnails.get(path));
                label.setText(new File(path).getName());
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setHorizontalTextPosition(SwingConstants.CENTER);
                label.setVerticalTextPosition(SwingConstants.BOTTOM);
                label.setToolTipText(path);
                if (!thumbnailJobs.contains(path)) loadThumbnail(path);
                return label;
            }
        });
        JPanel gallery = new JPanel(new BorderLayout());
        JPanel galleryBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(e -> {
            if (!files.isEmpty()) fileList.setSelectionInterval(0, files.size() - 1);
        });
        JButton removeSelected = new JButton("Remove Selected");
        removeSelected.addActionListener(e -> {
            for (String path : fileList.getSelectedValuesList()) {
                files.removeElement(path);
                manualCrops.remove(path);
            }
            updateCount();
        });
        galleryBar.add(new JLabel("Photo Gallery"));
        galleryBar.add(selectAll);
        galleryBar.add(removeSelected);
        gallery.add(galleryBar, BorderLayout.NORTH);
        gallery.add(new JScrollPane(fileList), BorderLayout.CENTER);
        center.add(gallery, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        JPanel footerTop = new JPanel(new BorderLayout());
        footerTop.add(count, BorderLayout.WEST);
        footerTop.add(engineStatus, BorderLayout.EAST);
        footer.add(footerTop, BorderLayout.NORTH);
        progress.setStringPainted(true);
        footer.add(progress, BorderLayout.CENTER);
        footer.add(status, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        frame.setContentPane(root);
        SwingUtilities.invokeLater(() -> checkEngine());
    }

    private void checkEngine() {
        checkEngine.setEnabled(false);
        engineStatus.setText("Engine: checking...");
        new SwingWorker<String, Void>() {
            protected String doInBackground() {
                File dll = new File("lib/opencv_java455.dll");
                File face = new File("models/haarcascade_frontalface_alt2.xml");
                File eyes = new File("models/haarcascade_eye.xml");
                if (!dll.isFile()) return "Missing native library: " + dll.getAbsolutePath();
                if (!face.isFile()) return "Missing face model: " + face.getAbsolutePath();
                try {
                    ensureOpenCvLoaded(dll);
                    FaceCropEngine detector = new FaceCropEngine(face);
                    Mat probe = Mat.zeros(160, 160, CvType.CV_8UC3);
                    try { detector.detect(probe); } finally { probe.release(); }
                    return eyes.isFile() ? "READY: OpenCV face detection operational"
                        : "READY: face detection operational (eye model missing)";
                } catch (Throwable error) {
                    return "FAILED: " + error.getClass().getSimpleName() + ": " + error.getMessage();
                }
            }
            protected void done() {
                checkEngine.setEnabled(true);
                try {
                    String result = get();
                    engineStatus.setText(result.startsWith("READY") ? "Engine: READY" : "Engine: ERROR");
                    engineStatus.setToolTipText(result);
                    status.setText(result);
                    if (!result.startsWith("READY"))
                        JOptionPane.showMessageDialog(frame, result, "Engine diagnostics", JOptionPane.ERROR_MESSAGE);
                } catch (Exception error) {
                    engineStatus.setText("Engine: ERROR");
                    status.setText(error.toString());
                }
            }
        }.execute();
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Images (JPG, JPEG, PNG, BMP)", "jpg", "jpeg", "png", "bmp"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                if (!files.contains(f.getAbsolutePath())) files.addElement(f.getAbsolutePath());
            }
            updateCount();
        }
    }

    private static boolean isPhoto(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".bmp");
    }

    private void chooseInputFolder() {
        status.setText("Opening folder picker...");
        final javax.swing.SwingWorker<File, Void> picker = new javax.swing.SwingWorker<File, Void>() {
            protected File doInBackground() {
                return FolderPathDialog.choose(frame, "Choose input photo folder", null);
            }
            protected void done() {
                try {
                    final File folder = get();
                    if (folder == null) { status.setText("Folder selection cancelled"); return; }
                    if (!folder.isDirectory()) {
                        JOptionPane.showMessageDialog(frame, "Not a valid folder: " + folder);
                        return;
                    }
                    inputPath.setText(folder.getAbsolutePath());
                    status.setText("Scanning images in " + folder.getAbsolutePath() + "...");
                    new javax.swing.SwingWorker<java.util.List<String>, Void>() {
                        protected java.util.List<String> doInBackground() throws Exception {
                            java.util.List<String> found = new java.util.ArrayList<String>();
                            java.nio.file.Files.walkFileTree(folder.toPath(),
                                new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path path,
                                            java.nio.file.attribute.BasicFileAttributes attrs) {
                                        if (attrs.isRegularFile() && isPhoto(path.getFileName().toString()))
                                            found.add(path.toAbsolutePath().toString());
                                        return java.nio.file.FileVisitResult.CONTINUE;
                                    }
                                    public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path path,
                                            java.io.IOException ex) {
                                        return java.nio.file.FileVisitResult.CONTINUE;
                                    }
                                });
                            java.util.Collections.sort(found, String.CASE_INSENSITIVE_ORDER);
                            return found;
                        }
                        protected void done() {
                            try {
                                java.util.List<String> found = get();
                                java.util.Set<String> existing = new java.util.HashSet<String>();
                                for (int i = 0; i < files.size(); i++) existing.add(files.get(i));
                                int added = 0;
                                for (String path : found)
                                    if (existing.add(path)) { files.addElement(path); added++; }
                                updateCount();
                                status.setText("Added " + added + " images from " + folder.getAbsolutePath());
                                if (found.isEmpty()) JOptionPane.showMessageDialog(frame,
                                    "No JPG, JPEG, PNG or BMP images found in this folder or subfolders.");
                            } catch (Exception ex) {
                                status.setText("Folder scan failed: " + ex.getMessage());
                                JOptionPane.showMessageDialog(frame, ex.toString(), "Folder scan error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
                } catch (Exception ex) {
                    status.setText("Folder picker failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame, ex.toString(), "Folder picker error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        picker.execute();
    }

    private void loadPastedInput() {
        File folder = new File(inputPath.getText().trim());
        if (!folder.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "Input folder does not exist.");
            return;
        }
        status.setText("Scanning " + folder.getAbsolutePath() + "...");
        new SwingWorker<java.util.List<String>, Void>() {
            protected java.util.List<String> doInBackground() throws Exception {
                final java.util.List<String> found = new java.util.ArrayList<String>();
                java.nio.file.Files.walkFileTree(folder.toPath(),
                    new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                        public java.nio.file.FileVisitResult visitFile(java.nio.file.Path path,
                                java.nio.file.attribute.BasicFileAttributes attrs) {
                            if (attrs.isRegularFile() && isPhoto(path.getFileName().toString()))
                                found.add(path.toAbsolutePath().toString());
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path path,
                                java.io.IOException error) { return java.nio.file.FileVisitResult.CONTINUE; }
                    });
                java.util.Collections.sort(found, String.CASE_INSENSITIVE_ORDER);
                return found;
            }
            protected void done() {
                try {
                    java.util.Set<String> existing = new java.util.HashSet<String>();
                    for (int i = 0; i < files.size(); i++) existing.add(files.get(i));
                    int added = 0;
                    for (String path : get())
                        if (existing.add(path)) { files.addElement(path); added++; }
                    updateCount();
                    status.setText("Loaded " + added + " photos from " + folder.getAbsolutePath());
                } catch (Exception ex) {
                    status.setText("Input folder scan failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame, ex.toString());
                }
            }
        }.execute();
    }

    private static boolean openCvLoaded;
    private static synchronized void ensureOpenCvLoaded(File dll) {
        if (!openCvLoaded) {
            System.load(dll.getAbsolutePath());
            openCvLoaded = true;
        }
    }

    private void previewSelected() {
        int currentIndex = fileList.getSelectedIndex();
        String path = fileList.getSelectedValue();
        if (path == null) {
            JOptionPane.showMessageDialog(frame, "Select a photo first.");
            return;
        }
        while (currentIndex >= 0 && currentIndex < files.size()) {
          path = files.get(currentIndex);
          try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new File(path));
            if (image == null) throw new java.io.IOException("Unsupported image");
            int w = ((Number) width.getValue()).intValue();
            int h = ((Number) height.getValue()).intValue();
            java.awt.Rectangle suggested = CropPreviewDialog.defaultCrop(image, (double) w / h);
            // Show the actual automatic crop suggestion when OpenCV is available.
            try {
                File dll = new File("lib/opencv_java455.dll");
                File modelFile = new File("models/haarcascade_frontalface_alt2.xml");
                if (dll.isFile() && modelFile.isFile()) {
                    ensureOpenCvLoaded(dll);
                    Mat source = Imgcodecs.imread(path);
                    try {
                        if (!source.empty()) {
                            FaceCropEngine detector = new FaceCropEngine(modelFile);
                            java.util.List<org.opencv.core.Rect> found = detector.detect(source);
                            if (!found.isEmpty()) {
                                org.opencv.core.Rect r = FaceCropEngine.cropRect(found.get(0),
                                    source.cols(), source.rows(), (double) w / h);
                                suggested = new java.awt.Rectangle(r.x, r.y, r.width, r.height);
                            }
                        }
                    } finally { source.release(); }
                }
            } catch (Exception ignored) {
                // Preview remains available even without OpenCV dependencies.
            }
            org.opencv.core.Rect previous = manualCrops.get(path);
            if (previous != null) suggested = new java.awt.Rectangle(previous.x, previous.y, previous.width, previous.height);
            CropPreviewDialog.PreviewResult preview = CropPreviewDialog.editNavigable(frame, new File(path), suggested, w, h);
            java.awt.Rectangle selected = preview.crop;
            if (selected != null) {
                manualCrops.put(path, new org.opencv.core.Rect(selected.x, selected.y, selected.width, selected.height));
                status.setText("Manual crop saved for " + new File(path).getName());
            }
        if (preview.direction == 0) break;
        currentIndex = Math.max(0, Math.min(files.size() - 1, currentIndex + preview.direction));
        fileList.setSelectedIndex(currentIndex);
        fileList.ensureIndexIsVisible(currentIndex);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Preview error", JOptionPane.ERROR_MESSAGE);
            break;
        }
        }
    }

    private void toggleRobot() {
        if (robotActive) {
            robot.stop();
            robotActive = false;
            robotToggle.setText("Start Robot");
            status.setText("Robot stopped");
            return;
        }
        File input = FolderPathDialog.choose(frame, "Robot input folder", null);
        if (input == null) return;
        robotOutput = FolderPathDialog.choose(frame, "Robot output folder", null);
        if (robotOutput == null) return;
        try {
            if (input.getCanonicalFile().equals(robotOutput.getCanonicalFile()))
                throw new IllegalArgumentException("Input and output folders must differ");
            robotSeen.clear();
            robot = new FolderRobot(input, photo -> SwingUtilities.invokeLater(() -> {
                if (!robotActive) return;
                if (!robotSeen.add(photo.getAbsolutePath())) return;
                if (!files.contains(photo.getAbsolutePath())) files.addElement(photo.getAbsolutePath());
                updateCount();
                if (process.isEnabled()) runRobotBatch();
            }));
            robotActive = true;
            robotThread = new Thread(robot, "FaceCropRobot");
            robotThread.setDaemon(true);
            robotThread.start();
            robotToggle.setText("Stop Robot");
            status.setText("Robot watching: " + input.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Robot error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runRobotBatch() {
        if (robotOutput != null && !files.isEmpty()) startCroppingTo(robotOutput);
    }

    private void chooseOutputFolder() {
        status.setText("Opening output folder picker...");
        new SwingWorker<File, Void>() {
            protected File doInBackground() {
                return FolderPathDialog.choose(frame, "Choose output photo folder", null);
            }
            protected void done() {
                try {
                    File folder = get();
                    if (folder != null && folder.isDirectory()) {
                        outputPath.setText(folder.getAbsolutePath());
                        status.setText("Output folder selected: " + folder.getAbsolutePath());
                    } else status.setText("Output folder selection cancelled");
                } catch (Exception ex) {
                    status.setText("Output folder picker error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame, ex.toString(), "Folder picker error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void startCropping() {
        String sourceText = inputPath.getText().trim();
        if (!sourceText.isEmpty()) {
            File source = new File(sourceText);
            if (!source.isDirectory()) {
                JOptionPane.showMessageDialog(frame, "Input folder does not exist: " + sourceText);
                return;
            }
        }
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Add photos using Browse Input or Add Photos first.");
            return;
        }
        String destination = outputPath.getText().trim();
        if (destination.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Select or paste an Output folder before Start Cropping.");
            return;
        }
        File folder = new File(destination);
        if (folder.exists() && !folder.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "Output path is not a folder.");
            return;
        }
        startCroppingTo(folder);
    }

    private void startCroppingTo(final File output) {

        final boolean extractAll = multipleFaces.isSelected();
        final boolean trim = trimBorders.isSelected();
        final java.util.Map<String, org.opencv.core.Rect> overrides = new java.util.HashMap<String, org.opencv.core.Rect>(manualCrops);
        final int outputWidth = ((Number)width.getValue()).intValue();
        final int outputHeight = ((Number)height.getValue()).intValue();
        final File model = new File("models/haarcascade_frontalface_alt2.xml");
        final File nativeDll = new File("lib/opencv_java455.dll");
        if (!model.isFile() || !nativeDll.isFile()) {
            JOptionPane.showMessageDialog(frame,
                "Required OpenCV files are missing. Expected:\\n" +
                nativeDll.getPath() + "\\n" + model.getPath() +
                "\\nThe Windows installer will bundle these dependencies.",
                "Missing dependencies", JOptionPane.ERROR_MESSAGE);
            return;
        }
        process.setEnabled(false);
        cancel.setEnabled(true);
        retry.setEnabled(false);
        stopRequested = false;
        failedFiles.clear();
        progress.setValue(0);
        final java.util.List<String> selected = new java.util.ArrayList<String>();
        for (int i = 0; i < files.size(); i++) selected.add(files.get(i));
        SwingWorker<int[], String> worker = new SwingWorker<int[], String>() {
            protected int[] doInBackground() {
                int success = 0, failed = 0, completed = 0;
                try {
                    ensureOpenCvLoaded(nativeDll);
                    FaceCropEngine engine = new FaceCropEngine(model);
                    publish("OpenCV engine loaded — detecting faces...");
                    if (!output.isDirectory() && !output.mkdirs())
                        throw new IllegalStateException("Cannot create output folder");
                    for (String name : selected) {
                        if (stopRequested) break;
                        Mat image = new Mat();
                        Mat cropped = null;
                        try {
                            image = Imgcodecs.imread(name);
                            if (image.empty()) throw new IllegalArgumentException("Cannot read image");
                            if (trim && !overrides.containsKey(name)) {
                                Mat rectified = PerspectivePhotoTrim.trim(image);
                                try {
                                    Mat clean = PhotoBorderTrim.trim(rectified);
                                    image.release();
                                    image = clean;
                                } finally { rectified.release(); }
                            }
                            List<Rect> faces = (!extractAll && overrides.containsKey(name))
                                ? java.util.Collections.singletonList(overrides.get(name)) : engine.detect(image);
                            if (faces.isEmpty()) throw new IllegalArgumentException("No face detected");
                            File original = new File(name);
                            String originalName = original.getName();
                            String base = originalName.replaceFirst("(?i)\\.[^.]+$", "");
                            int faceCount = extractAll ? faces.size() : 1;
                            for (int index = 0; index < faceCount; index++) {
                                // Preserve the exact original filename for single-face output.
                                // Multi-face images require numbered names to avoid collisions.
                                String destinationName = extractAll
                                    ? base + "_face_" + (index + 1) + ".jpg"
                                    : originalName;
                                File destination = new File(output, destinationName);
                                if (destination.getCanonicalPath().equals(original.getCanonicalPath()))
                                    throw new IllegalStateException("Output would overwrite the original image");
                                // Existing output may be replaced; never overwrite the source photo.
                                if (!extractAll && overrides.containsKey(name)) {
                                    org.opencv.core.Rect r = overrides.get(name);
                                    if (r.x < 0 || r.y < 0 || r.width <= 0 || r.height <= 0 ||
                                        r.x + r.width > image.cols() || r.y + r.height > image.rows())
                                        throw new IllegalArgumentException("Manual crop is outside image bounds");
                                    Mat roi = new Mat(image, r);
                                    cropped = new Mat();
                                    try {
                                        org.opencv.imgproc.Imgproc.resize(roi, cropped, new Size(outputWidth, outputHeight));
                                    } finally { roi.release(); }
                                } else {
                                    cropped = engine.cropAligned(image, faces.get(index), outputWidth, outputHeight);
                                }
                                try {
                                    if (!Imgcodecs.imwrite(destination.getAbsolutePath(), cropped))
                                        throw new IllegalStateException("Save failed");
                                } finally {
                                    cropped.release();
                                    cropped = null;
                                }
                                publish("Saved " + destinationName);
                            }
                            success++;
                        } catch (Exception ex) {
                            failed++;
                            synchronized (failedFiles) { failedFiles.add(name); }
                            publish("Skipped " + new File(name).getName() + ": " + ex.getMessage());
                        } finally {
                            image.release();
                            if (cropped != null) cropped.release();
                            completed++;
                            setProgress((int)(100.0 * completed / selected.size()));
                        }
                    }
                } catch (Throwable ex) {
                    publish("Processing stopped: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    failed += selected.size() - success - failed;
                }
                return new int[]{success, failed};
            }
            protected void process(java.util.List<String> messages) {
                if (!messages.isEmpty()) status.setText(messages.get(messages.size()-1));
            }
            protected void done() {
                process.setEnabled(true);
                cancel.setEnabled(false);
                if (robotActive && output.equals(robotOutput)) {
                    for (String donePath : selected) files.removeElement(donePath);
                    updateCount();
                    if (!files.isEmpty()) SwingUtilities.invokeLater(() -> runRobotBatch());
                }
                retry.setEnabled(!failedFiles.isEmpty());
                try {
                    int[] result = get();
                    JOptionPane.showMessageDialog(frame,
                        "Saved: " + result[0] + "\\nFailed: " + result[1] +
                        "\\nEngine status: " + status.getText());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, ex.getMessage());
                }
            }
        };
        worker.addPropertyChangeListener(e -> {
            if ("progress".equals(e.getPropertyName())) progress.setValue((Integer)e.getNewValue());
        });
        worker.execute();
    }

    private void loadThumbnail(final String path) {
        if (!thumbnailJobs.add(path)) return;
        thumbnailPool.execute(() -> {
            try {
                javax.imageio.ImageReader reader = null;
                try (javax.imageio.stream.ImageInputStream stream =
                        javax.imageio.ImageIO.createImageInputStream(new File(path))) {
                    if (stream != null) {
                        java.util.Iterator<javax.imageio.ImageReader> available =
                            javax.imageio.ImageIO.getImageReaders(stream);
                        if (available.hasNext()) {
                            reader = available.next();
                            reader.setInput(stream, true, true);
                            int w = reader.getWidth(0);
                            int h = reader.getHeight(0);
                            int sample = Math.max(1, Math.max(w / 120, h / 130));
                            javax.imageio.ImageReadParam params = reader.getDefaultReadParam();
                            params.setSourceSubsampling(sample, sample, 0, 0);
                            java.awt.image.BufferedImage image = reader.read(0, params);
                            if (image != null) {
                                double scale = Math.min(120.0 / image.getWidth(), 125.0 / image.getHeight());
                                int tw = Math.max(1, (int)(image.getWidth() * scale));
                                int th = Math.max(1, (int)(image.getHeight() * scale));
                                thumbnails.put(path, new ImageIcon(image.getScaledInstance(tw, th, Image.SCALE_FAST)));
                                image.flush();
                            }
                        }
                    }
                } finally {
                    if (reader != null) reader.dispose();
                }
            } catch (Exception ignored) {
                thumbnails.put(path, new ImageIcon(new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB)));
            }
            SwingUtilities.invokeLater(() -> fileList.repaint());
        });
    }

    private void updateCount() {
        count.setText(files.size() + " images selected");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception ignored) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignoredAgain) { /* Swing default remains available */ }
        }
        SwingUtilities.invokeLater(() -> new FaceCropPro().frame.setVisible(true));
    }
}
