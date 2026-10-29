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
    private final JLabel status = new JLabel("Ready — processing engine not installed yet");
    private final JLabel count = new JLabel("0 images selected");
    private final JButton process = new JButton("Start Cropping");
    private final JCheckBox multipleFaces = new JCheckBox("Extract all faces");
    private final JComboBox<String> preset = new JComboBox<String>(new String[]{"413 x 531", "466 x 600", "600 x 600", "Custom"});
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(413, 100, 6000, 1));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(531, 100, 6000, 1));

    private FaceCropPro() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(850, 540));
        frame.setSize(1050, 680);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel heading = new JLabel("Face Crop Pro");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
        root.add(heading, BorderLayout.NORTH);

        JPanel sidebar = new JPanel(new GridLayout(0, 1, 0, 8));
        sidebar.setPreferredSize(new Dimension(190, 0));
        for (String section : new String[]{"Batch Crop", "Multiple Faces", "Robot Mode", "Presets"}) {
            JButton button = new JButton(section);
            button.addActionListener((ActionEvent e) ->
                status.setText(section + " — coming in a later development phase"));
            sidebar.add(button);
        }
        JPanel left = new JPanel(new BorderLayout());
        left.add(sidebar, BorderLayout.NORTH);
        root.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Photos");
        JButton clear = new JButton("Clear");
        process.addActionListener((ActionEvent e) -> startCropping());
        add.addActionListener((ActionEvent e) -> chooseFiles());
        clear.addActionListener((ActionEvent e) -> {
            files.clear();
            updateCount();
        });
        toolbar.add(add);
        toolbar.add(clear);
        toolbar.add(process);
        toolbar.add(multipleFaces);
        toolbar.add(new JLabel("Size:"));
        toolbar.add(preset);
        toolbar.add(width);
        toolbar.add(new JLabel("x"));
        toolbar.add(height);
        preset.addActionListener((ActionEvent e) -> {
            int i = preset.getSelectedIndex();
            if (i == 0) { width.setValue(413); height.setValue(531); }
            if (i == 1) { width.setValue(466); height.setValue(600); }
            if (i == 2) { width.setValue(600); height.setValue(600); }
        });
        center.add(toolbar, BorderLayout.NORTH);
        JList<String> fileList = new JList<String>(files);
        center.add(new JScrollPane(fileList), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.add(count, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        frame.setContentPane(root);
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

    private void startCropping() {
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Add photos first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose output folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        final File output = chooser.getSelectedFile();
        final boolean extractAll = multipleFaces.isSelected();
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
        final java.util.List<String> selected = new java.util.ArrayList<String>();
        for (int i = 0; i < files.size(); i++) selected.add(files.get(i));
        new SwingWorker<int[], String>() {
            protected int[] doInBackground() {
                int success = 0, failed = 0;
                try {
                    System.load(nativeDll.getAbsolutePath());
                    FaceCropEngine engine = new FaceCropEngine(model);
                    if (!output.isDirectory() && !output.mkdirs())
                        throw new IllegalStateException("Cannot create output folder");
                    for (String name : selected) {
                        Mat image = new Mat();
                        Mat cropped = null;
                        try {
                            image = Imgcodecs.imread(name);
                            if (image.empty()) throw new IllegalArgumentException("Cannot read image");
                            List<Rect> faces = engine.detect(image);
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
                                cropped = engine.cropAligned(image, faces.get(index), outputWidth, outputHeight);
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
                            publish("Skipped " + new File(name).getName() + ": " + ex.getMessage());
                        } finally {
                            image.release();
                            if (cropped != null) cropped.release();
                        }
                    }
                } catch (Throwable ex) {
                    publish("Processing stopped: " + ex.getMessage());
                    failed += selected.size() - success - failed;
                }
                return new int[]{success, failed};
            }
            protected void process(java.util.List<String> messages) {
                if (!messages.isEmpty()) status.setText(messages.get(messages.size()-1));
            }
            protected void done() {
                process.setEnabled(true);
                try {
                    int[] result = get();
                    JOptionPane.showMessageDialog(frame,
                        "Saved: " + result[0] + "\\nFailed: " + result[1]);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, ex.getMessage());
                }
            }
        }.execute();
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
