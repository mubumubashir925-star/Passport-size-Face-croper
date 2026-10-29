import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BatchBackgroundRemover extends JFrame {
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton selectInputButton;
    private JButton selectOutputButton;
    private JButton processButton;
    private File inputFolder;
    private File outputFolder;
    private static final String MODEL_PATH = "C:\\FaceCropper\\models\\u2net.onnx";
    private static final boolean TEST_BGR_SAVE = false; // Set to true to save original BGR image for testing

    static {
        try {
            System.load("C:\\FaceCropper\\lib\\opencv_java455.dll");
            System.out.println("OpenCV DLL loaded successfully");
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV DLL: " + e.getMessage());
        }
    }

    public BatchBackgroundRemover() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            System.out.println("FlatLaf set successfully");
        } catch (Exception e) {
            System.err.println("Failed to set FlatLaf: " + e.getMessage());
        }

        setTitle("Batch Background Remover");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        statusLabel = new JLabel("Select input and output folders to start", SwingConstants.CENTER);
        add(statusLabel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        selectInputButton = new JButton("Select Input Folder");
        selectOutputButton = new JButton("Select Output Folder");
        processButton = new JButton("Process Images");
        processButton.setEnabled(false);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);

        controlPanel.add(selectInputButton);
        controlPanel.add(selectOutputButton);
        controlPanel.add(processButton);
        controlPanel.add(progressBar);
        add(controlPanel, BorderLayout.SOUTH);

        selectInputButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                inputFolder = chooser.getSelectedFile();
                statusLabel.setText("Input folder: " + inputFolder.getAbsolutePath());
                System.out.println("Selected input folder: " + inputFolder.getAbsolutePath());
                checkReadyToProcess();
            }
        });

        selectOutputButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputFolder = chooser.getSelectedFile();
                if (!outputFolder.exists()) {
                    outputFolder.mkdirs();
                    System.out.println("Created output folder: " + outputFolder.getAbsolutePath());
                }
                if (!outputFolder.canWrite()) {
                    JOptionPane.showMessageDialog(this, "Output folder is not writable", "Error", JOptionPane.ERROR_MESSAGE);
                    System.err.println("Output folder is not writable: " + outputFolder.getAbsolutePath());
                    outputFolder = null;
                    return;
                }
                statusLabel.setText("Output folder: " + outputFolder.getAbsolutePath());
                System.out.println("Selected output folder: " + outputFolder.getAbsolutePath());
                checkReadyToProcess();
            }
        });

        processButton.addActionListener(e -> {
            if (inputFolder != null && outputFolder != null) {
                processButton.setEnabled(false);
                selectInputButton.setEnabled(false);
                selectOutputButton.setEnabled(false);
                new Thread(() -> {
                    try {
                        processImages();
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Error processing images: " + ex.getMessage(), 
                                "Error", JOptionPane.ERROR_MESSAGE);
                            statusLabel.setText("Error: " + ex.getMessage());
                        });
                        System.err.println("Processing error: " + ex.getMessage());
                    } finally {
                        SwingUtilities.invokeLater(() -> {
                            processButton.setEnabled(true);
                            selectInputButton.setEnabled(true);
                            selectOutputButton.setEnabled(true);
                            progressBar.setValue(0);
                            statusLabel.setText("Processing complete");
                        });
                        System.out.println("Processing complete");
                    }
                }).start();
            }
        });
    }

    private void checkReadyToProcess() {
        if (inputFolder != null && outputFolder != null) {
            processButton.setEnabled(true);
        }
    }

    private void processImages() {
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Failed to create output folder", 
                    "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Error: Failed to create output folder");
            });
            System.err.println("Failed to create output folder: " + outputFolder.getAbsolutePath());
            return;
        }
        if (!outputFolder.canWrite()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Output folder is not writable", 
                    "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Error: Output folder is not writable");
            });
            System.err.println("Output folder is not writable: " + outputFolder.getAbsolutePath());
            return;
        }

        File[] files = inputFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });
        if (files == null || files.length == 0) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "No images found in input folder", 
                    "Warning", JOptionPane.WARNING_MESSAGE);
                statusLabel.setText("No images found in input folder");
            });
            System.err.println("No images found in input folder");
            return;
        }
        List<File> imageFiles = Arrays.asList(files);
        System.out.println("Found " + imageFiles.size() + " images to process");

        Net net;
        try {
            net = Dnn.readNetFromONNX(MODEL_PATH);
            SwingUtilities.invokeLater(() -> statusLabel.setText("U2Net model loaded successfully"));
            System.out.println("U2Net model loaded successfully");
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Failed to load U2Net model: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Error: Failed to load U2Net model - " + e.getMessage());
            });
            System.err.println("Failed to load U2Net model: " + e.getMessage());
            return;
        }

        final int totalFiles = imageFiles.size();
        for (int i = 0; i < totalFiles; i++) {
            File file = imageFiles.get(i);
            final int progress = (i + 1) * 100 / totalFiles;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(progress);
                statusLabel.setText("Processing: " + file.getName());
            });
            System.out.println("Processing: " + file.getName() + " (" + (i + 1) + "/" + totalFiles + ")");

            long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
            System.out.println("Free memory before processing: " + freeMemory + " MB");

            Mat image;
            try {
                image = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_COLOR);
                if (image.empty() || image.cols() <= 0 || image.rows() <= 0) {
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Skipping " + file.getName() + ": Invalid or empty image"));
                    System.err.println("Skipping " + file.getName() + ": Invalid or empty image");
                    continue;
                }
                SwingUtilities.invokeLater(() -> statusLabel.setText("Loaded image: " + file.getName()));
                System.out.println("Loaded image: " + file.getName() + " (" + image.cols() + "x" + image.rows() + ")");
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Image load failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Image load failed - " + e.getMessage());
                continue;
            }

            // Validate image size
            Size imageSize = image.size();
            if (imageSize.width <= 0 || imageSize.height <= 0) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Invalid image size"));
                System.err.println("Skipping " + file.getName() + ": Invalid image size");
                image.release();
                continue;
            }
            System.out.println("Image size validated: " + imageSize.width + "x" + imageSize.height);

            Mat blob;
            try {
                blob = Dnn.blobFromImage(image, 1.0 / 255, new Size(320, 320), new Scalar(0, 0, 0), true, false);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Created blob for: " + file.getName()));
                System.out.println("Created blob for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Blob creation failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Blob creation failed - " + e.getMessage());
                image.release();
                continue;
            }

            Mat mask;
            try {
                net.setInput(blob);
                System.out.println("Set input for U2Net: " + file.getName());
                mask = net.forward();
                System.out.println("Mask dimensions: " + mask.cols() + "x" + mask.rows() + ", channels: " + mask.channels());
                if (mask.empty() || mask.cols() <= 0 || mask.rows() <= 0) {
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Skipping " + file.getName() + ": Invalid U2Net mask"));
                    System.err.println("Skipping " + file.getName() + ": Invalid U2Net mask");
                    blob.release();
                    image.release();
                    mask.release();
                    continue;
                }
                SwingUtilities.invokeLater(() -> statusLabel.setText("Generated U2Net mask for: " + file.getName()));
                System.out.println("Generated U2Net mask for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": U2Net processing failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": U2Net processing failed - " + e.getMessage());
                blob.release();
                image.release();
                continue;
            }

            try {
                Core.normalize(mask, mask, 0, 255, Core.NORM_MINMAX);
                System.out.println("Normalized mask for: " + file.getName());
                // Explicitly create new Size object to avoid potential issues
                Size targetSize = new Size(image.cols(), image.rows());
                if (targetSize.width <= 0 || targetSize.height <= 0) {
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Skipping " + file.getName() + ": Invalid target size for resize"));
                    System.err.println("Skipping " + file.getName() + ": Invalid target size for resize");
                    blob.release();
                    mask.release();
                    image.release();
                    continue;
                }
                Imgproc.resize(mask, mask, targetSize);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Processed mask for: " + file.getName()));
                System.out.println("Processed mask for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Resize failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Resize failed - " + e.getMessage());
                blob.release();
                mask.release();
                image.release();
                continue;
            }

            Mat maskSingleChannel = new Mat();
            try {
                Core.extractChannel(mask, maskSingleChannel, 0);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Created mask channel for: " + file.getName()));
                System.out.println("Created mask channel for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Mask channel creation failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Mask channel creation failed - " + e.getMessage());
                blob.release();
                mask.release();
                image.release();
                maskSingleChannel.release();
                continue;
            }

            Mat mask8U = new Mat();
            try {
                maskSingleChannel.convertTo(mask8U, CvType.CV_8U);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Converted mask to 8-bit for: " + file.getName()));
                System.out.println("Converted mask to 8-bit for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Mask conversion failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Mask conversion failed - " + e.getMessage());
                blob.release();
                mask.release();
                maskSingleChannel.release();
                image.release();
                continue;
            }

            Mat alpha = new Mat(image.size(), CvType.CV_8U);
            try {
                Imgproc.threshold(mask8U, alpha, 128, 255, Imgproc.THRESH_BINARY);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Created alpha channel for: " + file.getName()));
                System.out.println("Created alpha channel for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Alpha channel creation failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Alpha channel creation failed - " + e.getMessage());
                blob.release();
                mask.release();
                maskSingleChannel.release();
                mask8U.release();
                image.release();
                continue;
            }

            Mat rgba = new Mat();
            List<Mat> channels = new ArrayList<>();
            try {
                Imgproc.cvtColor(image, rgba, Imgproc.COLOR_BGR2BGRA);
                Core.split(rgba, channels);
                channels.set(3, alpha);
                Core.merge(channels, rgba);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Created RGBA image for: " + file.getName()));
                System.out.println("Created RGBA image for: " + file.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": RGBA creation failed - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": RGBA creation failed - " + e.getMessage());
                blob.release();
                mask.release();
                maskSingleChannel.release();
                mask8U.release();
                alpha.release();
                image.release();
                for (Mat channel : channels) {
                    channel.release();
                }
                continue;
            }

            if (rgba.empty() || rgba.cols() <= 0 || rgba.rows() <= 0) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Invalid RGBA image"));
                System.err.println("Skipping " + file.getName() + ": Invalid RGBA image");
                blob.release();
                mask.release();
                maskSingleChannel.release();
                mask8U.release();
                alpha.release();
                rgba.release();
                for (Mat channel : channels) {
                    channel.release();
                }
                image.release();
                continue;
            }

            String outputPath = outputFolder.getAbsolutePath() + File.separator + 
                file.getName().replaceFirst("[.][^.]+$", "") + "_nobg.png";
            SwingUtilities.invokeLater(() -> 
                statusLabel.setText("Attempting to save: " + file.getName() + " to " + outputPath));
            System.out.println("Attempting to save: " + file.getName() + " to " + outputPath);
            try {
                Mat outputImage = TEST_BGR_SAVE ? image : rgba;
                if (!Imgcodecs.imwrite(outputPath, outputImage)) {
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Skipping " + file.getName() + ": Failed to save output to " + outputPath));
                    System.err.println("Skipping " + file.getName() + ": Failed to save output to " + outputPath);
                } else {
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Saved: " + file.getName() + " to " + outputPath));
                    System.out.println("Saved: " + file.getName() + " to " + outputPath);
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText("Skipping " + file.getName() + ": Save error - " + e.getMessage()));
                System.err.println("Skipping " + file.getName() + ": Save error - " + e.getMessage());
            }

            blob.release();
            mask.release();
            maskSingleChannel.release();
            mask8U.release();
            alpha.release();
            rgba.release();
            for (Mat channel : channels) {
                channel.release();
            }
            image.release();
            System.out.println("Cleaned up resources for: " + file.getName());
        }
        SwingUtilities.invokeLater(() -> statusLabel.setText("Processing complete"));
        System.out.println("Processing complete");
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("OpenCV library loaded");
        SwingUtilities.invokeLater(() -> {
            new BatchBackgroundRemover().setVisible(true);
        });
    }
}