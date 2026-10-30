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
        JLabel heading = new JLabel("Face Crop Pro");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
        root.add(heading, BorderLayout.NORTH);

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
        center.add(controls, BorderLayout.NORTH);
        center.add(new JScrollPane(fileList), BorderLayout.CENTER);
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

    private static boolean openCvLoaded;
    private static synchronized void ensureOpenCvLoaded(File dll) {
        if (!openCvLoaded) {
            System.load(dll.getAbsolutePath());
            openCvLoaded = true;
        }
    }

    private void previewSelected() {
        String path = fileList.getSelectedValue();
        if (path == null) {
            JOptionPane.showMessageDialog(frame, "Select a photo first.");
            return;
        }
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
            java.awt.Rectangle selected = CropPreviewDialog.edit(frame, new File(path), suggested, w, h);
            if (selected != null) {
                manualCrops.put(path, new org.opencv.core.Rect(selected.x, selected.y, selected.width, selected.height));
                status.setText("Manual crop saved for " + new File(path).getName());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Preview error", JOptionPane.ERROR_MESSAGE);
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

    private void startCropping() {
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Add photos first.");
            return;
        }
        File folder = FolderPathDialog.choose(frame, "Choose output folder", null);
        if (folder == null) return;
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
                                Mat clean = PhotoBorderTrim.trim(image);
                                image.release();
                                image = clean;
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
                                if (destination.exists())
                                    throw new IllegalStateException("Output already exists: " + destinationName);
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
