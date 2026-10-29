import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.formdev.flatlaf.FlatLightLaf;

public class TransparencyCropperGUI extends JFrame {
    private JTextField inputField;
    private JTextField outputField;
    private JProgressBar progressBar;
    private JButton runButton;
    private JLabel statusLabel;

    public TransparencyCropperGUI() {
        // Set FlatLaf look-and-feel
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("Failed to set FlatLaf: " + e.getMessage());
        }

        // Load OpenCV library
        System.load("C:\\FaceCropper\\lib\\opencv_java455.dll");

        // Window setup
        setTitle("PNG Transparency Cropper");
        setSize(500, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Input folder
        JLabel inputLabel = new JLabel("Input Folder:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(inputLabel, gbc);

        inputField = new JTextField(20);
        gbc.gridx = 1;
        add(inputField, gbc);

        JButton inputButton = new JButton("Browse");
        gbc.gridx = 2;
        add(inputButton, gbc);

        // Output folder
        JLabel outputLabel = new JLabel("Output Folder:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(outputLabel, gbc);

        outputField = new JTextField(20);
        gbc.gridx = 1;
        add(outputField, gbc);

        JButton outputButton = new JButton("Browse");
        gbc.gridx = 2;
        add(outputButton, gbc);

        // Status and progress
        statusLabel = new JLabel("Ready");
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(statusLabel, gbc);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setString("0%");
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        add(progressBar, gbc);

        // Run button
        runButton = new JButton("Start Cropping");
        runButton.setToolTipText("Start cropping transparent borders from PNGs");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        add(runButton, gbc);

        // Add listeners
        inputButton.addActionListener(new FolderChooserListener(this, inputField));
        outputButton.addActionListener(new FolderChooserListener(this, outputField));
        runButton.addActionListener(new RunListener());

        setVisible(true);
    }

    // Folder chooser listener
    private class FolderChooserListener implements ActionListener {
        private JFrame frame;
        private JTextField field;

        public FolderChooserListener(JFrame frame, JTextField field) {
            this.frame = frame;
            this.field = field;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(new File("C:\\"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }
    }

    // Run button listener
    private class RunListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String inputFolder = inputField.getText();
            String outputFolder = outputField.getText();

            if (inputFolder.isEmpty() || outputFolder.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Please select both input and output folders.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            new File(outputFolder).mkdirs();
            runButton.setEnabled(false);
            statusLabel.setText("Processing...");
            progressBar.setValue(0);
            progressBar.setString("0%");

            // Background processing
            new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() {
                    File folder = new File(inputFolder);
                    List<File> pngFiles = new ArrayList<>();
                    for (File file : folder.listFiles()) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                            pngFiles.add(file);
                        }
                    }

                    int totalFiles = pngFiles.size();
                    if (totalFiles == 0) {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("No PNGs found");
                            progressBar.setString("0%");
                            JOptionPane.showMessageDialog(null, "No PNG files found in input folder.", "Warning", JOptionPane.WARNING_MESSAGE);
                        });
                        return null;
                    }

                    progressBar.setMaximum(totalFiles);
                    int processed = 0;

                    for (File file : pngFiles) {
                        try {
                            Mat image = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_UNCHANGED);
                            if (image.empty() || image.channels() < 4) {
                                System.out.println("Skipping " + file.getName() + ": No transparency or invalid image");
                                continue;
                            }

                            Mat alpha = new Mat();
                            Core.extractChannel(image, alpha, 3);

                            Rect nonTransparentRect = findNonTransparentBounds(alpha);
                            if (nonTransparentRect == null) {
                                System.out.println("Skipping " + file.getName() + ": No non-transparent content");
                                continue;
                            }

                            Mat croppedImage = new Mat(image, nonTransparentRect);

                            String outputName = file.getName();
                            String outputPath = outputFolder + "\\" + outputName;
                            int[] params = {Imgcodecs.IMWRITE_PNG_COMPRESSION, 9};
                            Imgcodecs.imwrite(outputPath, croppedImage, new MatOfInt(params));
                            System.out.println("Processed " + file.getName());

                            image.release();
                            alpha.release();
                            croppedImage.release();
                        } catch (Exception ex) {
                            System.out.println("Error processing " + file.getName() + ": " + ex.getMessage());
                        } finally {
                            processed++;
                            publish((processed * 100) / totalFiles); // Update progress
                        }
                    }
                    return null;
                }

                @Override
                protected void process(List<Integer> chunks) {
                    for (Integer progress : chunks) {
                        progressBar.setValue(progress);
                        progressBar.setString(progress + "%");
                    }
                }

                @Override
                protected void done() {
                    runButton.setEnabled(true);
                    statusLabel.setText("Done!");
                    JOptionPane.showMessageDialog(null, "Processing complete! Cropped PNGs saved to output folder.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    // Find non-transparent bounds
    private Rect findNonTransparentBounds(Mat alpha) {
        int minX = alpha.cols(), minY = alpha.rows(), maxX = 0, maxY = 0;
        boolean found = false;

        for (int y = 0; y < alpha.rows(); y++) {
            for (int x = 0; x < alpha.cols(); x++) {
                double alphaValue = alpha.get(y, x)[0];
                if (alphaValue > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    found = true;
                }
            }
        }

        if (!found) return null;

        return new Rect(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TransparencyCropperGUI::new);
    }
}