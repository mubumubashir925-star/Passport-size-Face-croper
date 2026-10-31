import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import org.opencv.imgproc.Imgproc;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;

/** Phase 1: Java 8-compatible unified desktop shell. */
public final class FaceCropPro {
    private final JFrame frame = new JFrame("Face Crop Pro");
    private static final java.util.prefs.Preferences SETTINGS = java.util.prefs.Preferences.userNodeForPackage(FaceCropPro.class);
    private boolean darkTheme = SETTINGS.getBoolean("darkTheme", false);
    private final java.util.List<JPanel> styledPanels = new java.util.ArrayList<JPanel>();
    private final DefaultListModel<String> files = new DefaultListModel<String>();
    private final JLabel status = new JLabel("Ready — add photos to begin");
    private final JLabel count = new JLabel("0 images selected");
    private final JLabel engineStatus = new JLabel("Engine: checking...");
    private final JButton checkEngine = new JButton("Check Engine");
    private final JButton process = new JButton("Start Cropping");
    private final JTextField inputPath = new JTextField();
    private final JTextField outputPath = new JTextField();
    private final JLabel outputFolderFeedback = new JLabel("  Enter a folder path and press Enter");
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
    private final JLabel failedCount = new JLabel("Failed: 0");
    private final JList<String> fileList = new JList<String>(files);
    private final JLabel outputPreview = new JLabel("Select an output preview thumbnail", SwingConstants.CENTER);
    private final DefaultListModel<String> previewModel = new DefaultListModel<String>();
    private final JList<String> previewList = new JList<String>(previewModel);
    private final java.util.Map<String, File> previewFiles = new java.util.concurrent.ConcurrentHashMap<String, File>();
    private final java.util.Map<String, ImageIcon> previewIcons = new java.util.concurrent.ConcurrentHashMap<String, ImageIcon>();
    private volatile int previewGeneration = 0;
    private File previewDirectory;
    private final JButton editOutput = new JButton("Adjust Selected Crop");
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
    private final JComboBox<String> preset = new JComboBox<String>(new String[]{"Passport portrait · 413 x 531", "ID portrait · 466 x 600", "Square headshot · 600 x 600", "Custom size"});
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(466, 100, 6000, 1));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(600, 100, 6000, 1));

    private FaceCropPro() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setIconImage(makeAppIcon(64));
        frame.setMinimumSize(new Dimension(900, 580));
        frame.setSize(1050, 680);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JLabel heading = new JLabel("  Face Crop Pro  v" + AppUpdates.VERSION);
        heading.setIcon(new ImageIcon(makeAppIcon(40)));
        heading.setIconTextGap(12);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
        JPanel header = new JPanel(new BorderLayout());
        header.add(heading, BorderLayout.WEST);
        JButton updates = new JButton("Check for Updates");
        updates.setToolTipText("Requires internet only while checking; cropping works offline");
        updates.addActionListener(e -> AppUpdates.check(frame));
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JComboBox<String> themePicker = new JComboBox<String>(new String[]{"Light theme", "Dark theme"});
        themePicker.setSelectedIndex(darkTheme ? 1 : 0);
        themePicker.setToolTipText("Switch between light and dark appearance (saved automatically)");
        themePicker.addActionListener(e -> switchTheme(themePicker.getSelectedIndex() == 1));
        headerActions.add(themePicker);
        headerActions.add(updates);
        header.add(headerActions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBorder(BorderFactory.createTitledBorder("PHOTO PROCESSING"));
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
        toolbar.add(clear);
        process.setFont(process.getFont().deriveFont(Font.BOLD, 17f));
        process.setBackground(new Color(24, 126, 79));
        process.setForeground(Color.WHITE);
        process.setOpaque(true);
        process.setFocusPainted(false);
        process.setPreferredSize(new Dimension(195, 43));
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
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        options.add(multipleFaces);
        options.add(trimBorders);
        options.add(new JLabel("Size:"));
        options.add(preset);
        JButton comparePresets = new JButton("See Input / Output Examples");
        comparePresets.setToolTipText("Compare each preset using a photo from your gallery");
        comparePresets.addActionListener(e -> showPresetExamples());
        options.add(comparePresets);
        options.add(width);
        options.add(new JLabel("x"));
        options.add(height);
        preset.addActionListener((ActionEvent e) -> {
            int i = preset.getSelectedIndex();
            if (i == 0) { width.setValue(413); height.setValue(531); }
            if (i == 1) { width.setValue(466); height.setValue(600); }
            if (i == 2) { width.setValue(600); height.setValue(600); }
        });
        preset.setSelectedIndex(1);
        JPanel controls = new JPanel(new BorderLayout());
        controls.add(toolbar, BorderLayout.NORTH);
        controls.add(options, BorderLayout.SOUTH);
        JPanel folders = new JPanel(new GridLayout(2, 1, 4, 4));
        folders.setBorder(BorderFactory.createTitledBorder("FOLDERS  ·  paste a path and press Enter"));
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(new JLabel("Input:   "), BorderLayout.WEST);
        inputPath.setToolTipText("Paste an input folder path or use Browse");
        inputRow.add(inputPath, BorderLayout.CENTER);
        JButton inputBrowse = new JButton("Browse Input");
        inputBrowse.addActionListener(e -> chooseInputFolder());
        inputPath.addActionListener(e -> loadPastedInput());
        inputRow.add(new JLabel("Paste path and press Enter to load images",SwingConstants.RIGHT), BorderLayout.EAST);
        JPanel outputRow = new JPanel(new BorderLayout(6, 0));
        outputRow.add(new JLabel("Output:"), BorderLayout.WEST);
        outputPath.setToolTipText("Paste an output folder path and press Enter");
        outputPath.addActionListener(e -> {
            String typed = outputPath.getText().trim().replace(String.valueOf((char)34),"");
            File folder = new File(typed);
            if (typed.isEmpty()) {
                outputFolderFeedback.setText("  ! Enter an output folder");
                outputFolderFeedback.setForeground(new Color(190, 80, 30));
                status.setText("Please enter an output folder path.");
            } else if (folder.isDirectory()) {
                outputFolderFeedback.setText("  \\u2713 Output folder ready");
                outputFolderFeedback.setForeground(new Color(20, 145, 100));
                status.setText("Output folder confirmed: " + folder.getAbsolutePath());
            } else if (!folder.exists()) {
                outputFolderFeedback.setText("  \\u2713 New folder - will be created when cropping");
                outputFolderFeedback.setForeground(new Color(20, 145, 100));
                status.setText("Output path accepted. Folder will be created when cropping starts: " + folder.getAbsolutePath());
            } else {
                outputFolderFeedback.setText("  ! Invalid output folder");
                outputFolderFeedback.setForeground(new Color(190, 65, 45));
                status.setText("Output path is not a folder: " + folder.getAbsolutePath());
            }
        });
        outputRow.add(outputPath, BorderLayout.CENTER);
        outputFolderFeedback.setFont(outputFolderFeedback.getFont().deriveFont(Font.PLAIN, 11f));
        outputRow.add(outputFolderFeedback, BorderLayout.EAST);
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
                boolean needsManual = !previewFiles.containsKey(path);
                label.setText(new File(path).getName() + (needsManual ? "  [MANUAL CROP]" : ""));
                if (needsManual && !selected) {
                    label.setOpaque(true);
                    label.setBackground(new Color(255, 235, 209));
                    label.setForeground(new Color(120, 55, 0));
                }
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
        galleryBar.add(new JLabel("Photo Gallery  ·  Ctrl+A: select all  ·  Delete: remove from list"));
        fileList.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "removeSelectedImages");
        fileList.getActionMap().put("removeSelectedImages", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                java.util.List<String> selected = new java.util.ArrayList<String>(fileList.getSelectedValuesList());
                for (String path : selected) {
                    files.removeElement(path);
                    manualCrops.remove(path);
                    previewFiles.remove(path);
                    previewIcons.remove(path);
                    previewModel.removeElement(path);
                }
                // Cancel the old background generation and restart only with remaining inputs.
                ++previewGeneration;
                if (!files.isEmpty()) generateCropPreviews();
                updateCount();
                status.setText("Removed " + selected.size() + " image(s) from list; original files are safe.");
            }
        });
        gallery.add(galleryBar, BorderLayout.NORTH);
        gallery.add(new JScrollPane(fileList), BorderLayout.CENTER);
        JPanel outputPanel = new JPanel(new BorderLayout(6, 6));
        outputPanel.setBorder(BorderFactory.createTitledBorder("CROP PREVIEW"));
        previewList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        previewList.setVisibleRowCount(-1);
        previewList.setFixedCellWidth(145);
        previewList.setFixedCellHeight(175);
        previewList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel)super.getListCellRendererComponent(list,value,index,selected,focused);
                String path = String.valueOf(value);
                label.setIcon(previewIcons.get(path));
                label.setText(new File(path).getName());
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setHorizontalTextPosition(SwingConstants.CENTER);
                label.setVerticalTextPosition(SwingConstants.BOTTOM);
                return label;
            }
        });
        previewList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = previewList.locationToIndex(e.getPoint());
                    if (index >= 0 && previewList.getCellBounds(index,index).contains(e.getPoint())) {
                        previewList.setSelectedIndex(index);
                        editSelectedOutput();
                    }
                }
            }
        });
        previewList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = previewList.getSelectedValue();
                if (selected != null) {
                    fileList.setSelectedValue(selected,true);
                    editOutput.setEnabled(true);
                }
            }
        });
        outputPanel.add(new JScrollPane(previewList), BorderLayout.CENTER);
        editOutput.setEnabled(false);
        JPanel outputButtons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton refreshOutput = new JButton("Refresh Output");
        refreshOutput.addActionListener(e -> refreshSelectedOutput());
        editOutput.addActionListener(e -> editSelectedOutput());
        outputButtons.add(refreshOutput);
        outputButtons.add(editOutput);
        JLabel editHint = new JLabel("Double-click a crop to adjust it");
        outputButtons.add(editHint);
        outputPanel.add(outputButtons, BorderLayout.SOUTH);
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshSelectedOutput();
        });
        gallery.setBorder(BorderFactory.createTitledBorder("INPUT IMAGES"));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gallery, outputPanel);
        split.setResizeWeight(0.52);
        center.add(split, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        JPanel footerTop = new JPanel(new BorderLayout());
        footerTop.add(count, BorderLayout.WEST);
        footerTop.add(failedCount, BorderLayout.CENTER);
        footerTop.add(engineStatus, BorderLayout.EAST);
        footer.add(footerTop, BorderLayout.NORTH);
        progress.setStringPainted(true);
        footer.add(progress, BorderLayout.CENTER);
        JPanel footerBottom = new JPanel(new BorderLayout(8, 0));
        footerBottom.add(status, BorderLayout.CENTER);
        JLabel credit = new JLabel("Developed by Mubashir  |  For Printo Cards and Technologies");
        credit.setFont(credit.getFont().deriveFont(Font.PLAIN, 10f));
        credit.setForeground(new Color(125, 135, 145));
        credit.setToolTipText("Face Crop Pro - developed by Mubashir for Printo Cards and Technologies");
        footerBottom.add(credit, BorderLayout.EAST);
        footer.add(footerBottom, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        frame.setContentPane(root);
        applyAccent();
        SwingUtilities.invokeLater(() -> checkEngine());
    }


    /** Drawn in Java 8 so the title-bar icon works offline on Windows 7/10/11. */
    private static java.awt.image.BufferedImage makeAppIcon(int size) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(18, 40, 64));
        g.fillRoundRect(0, 0, size - 1, size - 1, size / 4, size / 4);
        g.setColor(new Color(41, 208, 190));
        g.setStroke(new BasicStroke(Math.max(2f, size / 15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int m = size / 5, l = size / 5;
        g.drawLine(m, m + l, m, m); g.drawLine(m, m, m + l, m);
        g.drawLine(size-m-l, m, size-m, m); g.drawLine(size-m, m, size-m, m+l);
        g.drawLine(m, size-m-l, m, size-m); g.drawLine(m, size-m, m+l, size-m);
        g.drawLine(size-m-l, size-m, size-m, size-m); g.drawLine(size-m, size-m-l, size-m, size-m);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1.5f, size / 28f)));
        g.drawOval(size * 36 / 100, size * 29 / 100, size * 28 / 100, size * 28 / 100);
        g.drawArc(size * 27 / 100, size * 47 / 100, size * 46 / 100, size * 34 / 100, 15, 150);
        g.dispose();
        return image;
    }

    private void applyAccent() {
        process.setBackground(new Color(18, 137, 105));
        process.setForeground(Color.WHITE);
        process.setFont(process.getFont().deriveFont(Font.BOLD, 17f));
        process.setFocusPainted(false);
        progress.setForeground(new Color(18, 137, 105));
    }

    private void switchTheme(boolean dark) {
        if (darkTheme == dark) return;
        darkTheme = dark;
        SETTINGS.putBoolean("darkTheme", dark);
        try {
            UIManager.setLookAndFeel(dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf");
            SwingUtilities.updateComponentTreeUI(frame);
            applyAccent();
            frame.revalidate();
            frame.repaint();
        } catch (Exception ex) {
            status.setText("Unable to change theme: " + ex.getMessage());
        }
    }

    /** Show real input and output samples for every preset, not generic illustrations. */
    private void showPresetExamples() {
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Load photos first to see real crop examples.");
            return;
        }
        String path = fileList.getSelectedValue();
        if (path == null) path = files.get(0);
        final String sample = path;
        final JDialog dialog = new JDialog(frame, "Crop Presets · Input and Output Examples", true);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel info = new JLabel("Generating crop examples from " + new File(sample).getName() + "...");
        content.add(info, BorderLayout.NORTH);
        JPanel gallery = new JPanel(new GridLayout(1, 4, 12, 12));
        content.add(new JScrollPane(gallery), BorderLayout.CENTER);
        dialog.setContentPane(content);
        dialog.setSize(950, 400);
        dialog.setLocationRelativeTo(frame);
        final String[] labels = {"Input photo", "Passport 413 x 531",
            "ID 466 x 600", "Square 600 x 600"};
        new SwingWorker<java.awt.image.BufferedImage[], Void>() {
            protected java.awt.image.BufferedImage[] doInBackground() throws Exception {
                java.awt.image.BufferedImage[] examples = new java.awt.image.BufferedImage[4];
                examples[0] = javax.imageio.ImageIO.read(new File(sample));
                ensureOpenCvLoaded(new File("lib/opencv_java455.dll"));
                FaceCropEngine engine = new FaceCropEngine(new File("models/haarcascade_frontalface_alt2.xml"));
                Mat source = Imgcodecs.imread(sample);
                try {
                    int[][] sizes = {{413,531},{466,600},{600,600}};
                    for (int i=0;i<sizes.length;i++) {
                        Mat cropped = engine.cropAutoOriented(source,sizes[i][0],sizes[i][1]);
                        try {
                            if (cropped != null && !cropped.empty()) {
                                org.opencv.core.MatOfByte encoded = new org.opencv.core.MatOfByte();
                                try {
                                    Imgcodecs.imencode(".png", cropped, encoded);
                                    examples[i+1] = javax.imageio.ImageIO.read(
                                        new java.io.ByteArrayInputStream(encoded.toArray()));
                                } finally { encoded.release(); }
                            }
                        } finally { if(cropped != null) cropped.release(); }
                    }
                } finally { source.release(); }
                return examples;
            }
            protected void done() {
                try {
                    java.awt.image.BufferedImage[] images = get();
                    gallery.removeAll();
                    for (int i=0;i<4;i++) {
                        JPanel panel = new JPanel(new BorderLayout(4,4));
                        JLabel title = new JLabel(labels[i],SwingConstants.CENTER);
                        panel.add(title,BorderLayout.NORTH);
                        if(images[i] != null) {
                            java.awt.Image scaled = images[i].getScaledInstance(
                                Math.max(1,(int)(images[i].getWidth() *
                                    Math.min(190.0/images[i].getWidth(), 255.0/images[i].getHeight()))),
                                Math.max(1,(int)(images[i].getHeight() *
                                    Math.min(190.0/images[i].getWidth(), 255.0/images[i].getHeight()))),
                                java.awt.Image.SCALE_SMOOTH);
                            panel.add(new JLabel(new ImageIcon(scaled),SwingConstants.CENTER),BorderLayout.CENTER);
                        } else panel.add(new JLabel("No face found",SwingConstants.CENTER),BorderLayout.CENTER);
                        if(i>0) {
                            final int index=i-1;
                            JButton choose=new JButton("Use this preset");
                            choose.addActionListener(e -> {
                                preset.setSelectedIndex(index);
                                dialog.dispose();
                                generateCropPreviews();
                            });
                            panel.add(choose,BorderLayout.SOUTH);
                        }
                        gallery.add(panel);
                    }
                    info.setText("Original and actual crop examples · " + new File(sample).getName());
                    gallery.revalidate(); gallery.repaint();
                } catch(Exception ex) { info.setText("Cannot generate examples: "+ex.getMessage()); }
            }
        }.execute();
        dialog.setVisible(true);
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
                                generateCropPreviews();
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
                    generateCropPreviews();
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
                        refreshSelectedOutput();
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
        final java.util.Map<String, File> prepared = new java.util.HashMap<String, File>(previewFiles);
        if (previewModel.size() < files.size()) {
            int answer = JOptionPane.showConfirmDialog(frame,
                "Some crop previews are still being prepared. Continue anyway?",
                "Preview check", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
        }
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
        failedCount.setText("Failed: 0");
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
                        if (!files.contains(name)) continue;
                        Mat image = new Mat();
                        Mat cropped = null;
                        try {
                            File preparedPreview = !extractAll ? prepared.get(name) : null;
                            image = Imgcodecs.imread(name);
                            if (image.empty()) throw new IllegalArgumentException("Cannot read image");
                            if (preparedPreview == null && !extractAll && !overrides.containsKey(name)) {
                                cropped = trim
                                    ? engine.cropAutoOrientedWithScanTrim(image, outputWidth, outputHeight)
                                    : engine.cropAutoOriented(image, outputWidth, outputHeight);
                            }
                            if (preparedPreview == null && cropped == null && trim &&
                                !overrides.containsKey(name)) {
                                Mat rectified = PerspectivePhotoTrim.trim(image);
                                try {
                                    Mat clean = PhotoBorderTrim.trim(rectified);
                                    image.release();
                                    image = clean;
                                } finally { rectified.release(); }
                                if (!extractAll) cropped = engine.cropAutoOriented(image, outputWidth, outputHeight);
                            }
                            List<Rect> faces = preparedPreview != null
                                ? java.util.Collections.singletonList(new Rect(0,0,1,1))
                                : ((!extractAll && overrides.containsKey(name))
                                    ? java.util.Collections.singletonList(overrides.get(name)) : engine.detect(image));
                            if (faces.isEmpty() && cropped == null && !extractAll && !overrides.containsKey(name)) {
                                // A missing preview should still get the same rotation fallback at export.
                                cropped = engine.cropAutoOriented(image, outputWidth, outputHeight);
                                if (cropped != null && !cropped.empty())
                                    faces = java.util.Collections.singletonList(new Rect(0,0,1,1));
                            }
                            if (faces.isEmpty()) throw new IllegalArgumentException("No face detected at 0/90/180/270 degrees");
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
                                if (preparedPreview != null && preparedPreview.isFile()) {
                                    cropped = Imgcodecs.imread(preparedPreview.getAbsolutePath());
                                    if (cropped.empty()) throw new IllegalStateException("Cannot read prepared crop");
                                } else if (!extractAll && overrides.containsKey(name)) {
                                    org.opencv.core.Rect r = overrides.get(name);
                                    if (r.x < 0 || r.y < 0 || r.width <= 0 || r.height <= 0 ||
                                        r.x + r.width > image.cols() || r.y + r.height > image.rows())
                                        throw new IllegalArgumentException("Manual crop is outside image bounds");
                                    Mat roi = new Mat(image, r);
                                    cropped = new Mat();
                                    try {
                                        org.opencv.imgproc.Imgproc.resize(roi, cropped, new Size(outputWidth, outputHeight));
                                    } finally { roi.release(); }
                                } else if (cropped == null) {
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
                refreshSelectedOutput();
                failedCount.setText("Failed: " + failedFiles.size());
                try {
                    int[] result = get();
                    StringBuilder summary = new StringBuilder();
                    summary.append("CROPPING COMPLETED\\n\\n");
                    summary.append("Successfully saved: ").append(result[0]).append(" photo(s)\\n");
                    summary.append("Could not crop: ").append(result[1]).append(" photo(s)\\n");
                    summary.append("Total selected: ").append(selected.size()).append(" photo(s)\\n");
                    summary.append("Output folder: ").append(output.getAbsolutePath());
                    if (!failedFiles.isEmpty()) {
                        summary.append("\\n\\nPHOTOS THAT NEED ATTENTION:");
                        for (int i = 0; i < Math.min(20, failedFiles.size()); i++)
                            summary.append("\\n  ").append(i+1).append(". ").append(new File(failedFiles.get(i)).getName());
                        summary.append("\\n\\nThese photos were not saved. Select their");
                        summary.append("\\n[MANUAL CROP] preview, double-click to adjust,");
                        summary.append("\\nthen use Retry Failed.");
                    } else summary.append("\\n\\nAll selected photos were saved successfully.");
                    JOptionPane.showMessageDialog(frame, summary.toString(),
                        result[1] > 0 ? "Cropping finished - some photos need attention" : "Cropping completed successfully",
                        result[1] > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
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

    private volatile boolean manualEditorOpen = false;
    private File savedManualState(String path, int w, int h) {
        File preview=savedManualPreview(path,w,h);
        return new File(preview.getParentFile(),preview.getName()+".properties");
    }
    private File savedManualPreview(String path, int w, int h) {
        File source = new File(path);
        String key = source.getAbsolutePath()+"|"+w+"x"+h;
        File folder = new File(System.getProperty("user.home"), ".face-crop-pro/manual-crops");
        return new File(folder, java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()+".png");
    }

    private void generateCropPreviews() {
        if (files.isEmpty()) return;
        final int generation = ++previewGeneration;
        final java.util.List<String> paths = new java.util.ArrayList<String>();
        for (int i=0;i<files.size();i++) paths.add(files.get(i));
        final int outW=((Number)width.getValue()).intValue();
        final int outH=((Number)height.getValue()).intValue();
        final boolean trim=trimBorders.isSelected();
        previewModel.clear();
        previewFiles.clear();
        previewIcons.clear();
        status.setText("Preparing " + paths.size() + " crop previews...");
        new SwingWorker<Void,String>() {
            protected Void doInBackground() throws Exception {
                ensureOpenCvLoaded(new File("lib/opencv_java455.dll"));
                FaceCropEngine engine=new FaceCropEngine(new File("models/haarcascade_frontalface_alt2.xml"));
                previewDirectory=java.nio.file.Files.createTempDirectory("facecrop-previews-").toFile();
                int i=0;
                for (String path:paths) {
                    if (generation!=previewGeneration) break;
                    while(manualEditorOpen && generation==previewGeneration) {
                        try {Thread.sleep(120);} catch(InterruptedException interrupted) {
                            Thread.currentThread().interrupt(); return null;
                        }
                    }
                    if (!files.contains(path)) continue;
                    File saved = savedManualPreview(path,outW,outH);
                    if(saved.isFile()) { previewFiles.put(path,saved); publish(path); i++; continue; }
                    Mat image=Imgcodecs.imread(path), crop=null;
                    try {
                        if(image.empty()) { publish(path); continue; }
                        // Use the same scan-aware crop in preview and final export.
                        crop = trim ? engine.cropAutoOrientedWithScanTrim(image,outW,outH)
                                    : engine.cropAutoOriented(image,outW,outH);
                        if(crop==null || crop.empty()) { publish(path); continue; }
                        File target=new File(previewDirectory,String.format("%06d.jpg",i));
                        if(Imgcodecs.imwrite(target.getAbsolutePath(),crop)) {
                            previewFiles.put(path,target);
                            publish(path);
                        }
                    } catch(Exception ignored) {
                        publish(path);
                    } finally {
                        image.release();
                        if(crop!=null) crop.release();
                    }
                    i++;
                }
                return null;
            }
            protected void process(java.util.List<String> items) {
                if(generation!=previewGeneration) return;
                for(String path:items) {
                    if (!files.contains(path)) continue;
                    File target=previewFiles.get(path);
                    try {
                        java.awt.image.BufferedImage img=target == null ? null : javax.imageio.ImageIO.read(target);
                        if(img!=null) {
                            Image scaled=img.getScaledInstance(112,144,Image.SCALE_FAST);
                            previewIcons.put(path,new ImageIcon(scaled));
                        }
                        if (target == null) {
                            ImageIcon original = thumbnails.get(path);
                            if (original != null) previewIcons.put(path,original);
                        }
                        if (!previewModel.contains(path)) previewModel.addElement(path);
                        editOutput.setEnabled(true);
                    } catch(Exception ignored) {}
                }
                status.setText("Generated "+previewModel.size()+" / "+paths.size()+" crop previews");
            }
            protected void done() {
                if(generation==previewGeneration)
                    status.setText("Crop previews ready: "+previewModel.size()+" / "+paths.size());
            }
        }.execute();
    }

    private File selectedOutputFile() {
        String input = fileList.getSelectedValue();
        String folder = outputPath.getText().trim();
        if (input == null || folder.isEmpty()) return null;
        return new File(folder, new File(input).getName());
    }

    private void refreshSelectedOutput() {
        final File target = selectedOutputFile();
        if (target == null || !target.isFile()) {
            outputPreview.setIcon(null);
            outputPreview.setText("Output not saved yet. Run Start Cropping first.");
            editOutput.setEnabled(previewList.getSelectedValue() != null &&
                previewFiles.containsKey(previewList.getSelectedValue()));
            return;
        }
        editOutput.setEnabled(!previewFiles.isEmpty());
        outputPreview.setText("Loading output...");
        new SwingWorker<ImageIcon, Void>() {
            protected ImageIcon doInBackground() throws Exception {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(target);
                if (image == null) return null;
                double scale = Math.min(360.0 / image.getWidth(), 440.0 / image.getHeight());
                return new ImageIcon(image.getScaledInstance(
                    Math.max(1,(int)(image.getWidth()*scale)),
                    Math.max(1,(int)(image.getHeight()*scale)), Image.SCALE_SMOOTH));
            }
            protected void done() {
                if (!target.equals(selectedOutputFile())) return;
                try {
                    outputPreview.setIcon(get());
                    outputPreview.setText(outputPreview.getIcon() == null ? "Unsupported output" : "");
                } catch (Exception ex) {
                    outputPreview.setText("Could not read output");
                }
            }
        }.execute();
    }

    private void editSelectedOutput() {
        String selectedPath = previewList.getSelectedValue();
        if (selectedPath == null) selectedPath = fileList.getSelectedValue();
        final String previewKey = selectedPath;
        File target = previewKey == null ? null : previewFiles.get(previewKey);
        if (previewKey == null) {
            JOptionPane.showMessageDialog(frame, "Select an image from Crop Preview first.");
            return;
        }
        if (target == null || !target.isFile()) {
            try {
                ensureOpenCvLoaded(new File("lib/opencv_java455.dll"));
                if (previewDirectory == null) previewDirectory = java.nio.file.Files.createTempDirectory("facecrop-manual-").toFile();
                target = new File(previewDirectory, "manual-" + Integer.toHexString(previewKey.hashCode()) + ".jpg");
                Mat original = Imgcodecs.imread(previewKey);
                try {
                    if (original.empty()) throw new IOException("Cannot read source photo");
                    Mat initial = new Mat();
                    try {
                        Imgproc.resize(original, initial, new org.opencv.core.Size(((Number)width.getValue()).intValue(), ((Number)height.getValue()).intValue()));
                        if (!Imgcodecs.imwrite(target.getAbsolutePath(), initial)) throw new IOException("Cannot create manual crop preview");
                    } finally { initial.release(); }
                } finally { original.release(); }
                previewFiles.put(previewKey,target);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Cannot open manual crop: " + ex.getMessage());
                return;
            }
        }
        final File editableTarget = target;
        try {
            ensureOpenCvLoaded(new File("lib/opencv_java455.dll"));
            manualEditorOpen = true;
            OutputPerspectiveEditor.open(frame, new File(previewKey), editableTarget,
                ((Number)width.getValue()).intValue(),
                ((Number)height.getValue()).intValue(), () -> {
                    try {
                        File persistent = savedManualPreview(previewKey, ((Number)width.getValue()).intValue(), ((Number)height.getValue()).intValue());
                        persistent.getParentFile().mkdirs();
                        java.nio.file.Files.copy(editableTarget.toPath(), persistent.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        previewFiles.put(previewKey,persistent);
                        refreshPreviewIcon(previewKey,persistent);
                        status.setText("Manual crop saved permanently: "+new File(previewKey).getName());
                    } catch(Exception ex) {
                        refreshPreviewIcon(previewKey,editableTarget);
                        status.setText("Crop saved for session only: "+ex.getMessage());
                    }
                }, () -> {
                    int index=previewList.getSelectedIndex();
                    if(index<0) index=previewModel.indexOf(previewKey);
                    if(index>=0 && index+1<previewModel.size()) {
                        previewList.setSelectedIndex(index+1);
                        previewList.ensureIndexIsVisible(index+1);
                        editSelectedOutput();
                    } else JOptionPane.showMessageDialog(frame,"Last photo reached.");
                }, savedManualState(previewKey,((Number)width.getValue()).intValue(),((Number)height.getValue()).intValue()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.toString(), "Output editor error", JOptionPane.ERROR_MESSAGE);
        } finally {
            manualEditorOpen = false;
        }
    }

    private void refreshPreviewIcon(String path, File target) {
        try {
            java.awt.image.BufferedImage img=javax.imageio.ImageIO.read(target);
            if(img!=null) previewIcons.put(path,
                new ImageIcon(img.getScaledInstance(112,144,Image.SCALE_FAST)));
            previewList.repaint();
        } catch(Exception ignored) {}
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
            UIManager.setLookAndFeel(SETTINGS.getBoolean("darkTheme", false) ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception ignored) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignoredAgain) { /* Swing default remains available */ }
        }
        SwingUtilities.invokeLater(() -> new FaceCropPro().frame.setVisible(true));
    }
}
