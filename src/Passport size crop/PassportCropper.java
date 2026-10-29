import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import com.formdev.flatlaf.FlatLightLaf;

public class PassportCropper {
    static {
        System.load("C:\\FaceCropper\\lib\\opencv_java455.dll");
    }

    private static final String FACE_CASCADE_PATH = "C:\\FaceCropper\\models\\haarcascade_frontalface_alt2.xml";
    private static final String EYE_CASCADE_PATH = "C:\\FaceCropper\\models\\haarcascade_eye.xml";
    private static final int TARGET_WIDTH = 466;  // Approx 35mm at ~300dpi
    private static final int TARGET_HEIGHT = 600; // Approx 45mm at ~300dpi
    private static final double TARGET_ASPECT = (double) TARGET_WIDTH / TARGET_HEIGHT;
    private static final double EXTRA_TOP_MM = 5.0; // Extra 5mm space above head
    private static final double MM_PER_PIXEL = 0.0847; // Approx pixels per mm at 300dpi

    public static void main(String[] args) {
        FlatLightLaf.setup(); // Modern UI look and feel

        JFrame frame = new JFrame("Passport Size Cropper");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel inputLabel = new JLabel("Input Folder:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        frame.add(inputLabel, gbc);

        JTextField inputField = new JTextField(20);
        gbc.gridx = 1;
        frame.add(inputField, gbc);

        JButton inputBtn = new JButton("Browse");
        gbc.gridx = 2;
        frame.add(inputBtn, gbc);

        JLabel outputLabel = new JLabel("Output Folder:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        frame.add(outputLabel, gbc);

        JTextField outputField = new JTextField(20);
        gbc.gridx = 1;
        frame.add(outputField, gbc);

        JButton outputBtn = new JButton("Browse");
        gbc.gridx = 2;
        frame.add(outputBtn, gbc);

        JButton cropBtn = new JButton("Run Crop");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        frame.add(cropBtn, gbc);

        JProgressBar progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        gbc.gridy = 3;
        frame.add(progress, gbc);

        inputBtn.addActionListener(new FolderChooserListener(frame, inputField));
        outputBtn.addActionListener(new FolderChooserListener(frame, outputField));

        cropBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inputDir = inputField.getText();
                String outputDir = outputField.getText();
                if (inputDir.isEmpty() || outputDir.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Please select both input and output folders.");
                    return;
                }
                new Thread(() -> processImages(inputDir, outputDir, progress, frame)).start();
            }
        });

        frame.setVisible(true);
    }

    private static class FolderChooserListener implements ActionListener {
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
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }
    }

    private static void processImages(String inputDir, String outputDir, JProgressBar progress, JFrame frame) {
        File[] files = new File(inputDir).listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });
        if (files == null || files.length == 0) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "No images found in input folder."));
            return;
        }

        CascadeClassifier faceClassifier = new CascadeClassifier(FACE_CASCADE_PATH);
        CascadeClassifier eyeClassifier = new CascadeClassifier(EYE_CASCADE_PATH);

        int total = files.length;
        for (int i = 0; i < total; i++) {
            File file = files[i];
            Mat image = Imgcodecs.imread(file.getAbsolutePath());
            if (image.empty()) continue;

            MatOfRect faces = new MatOfRect();
            faceClassifier.detectMultiScale(image, faces);

            Rect bestFace = null;
            double maxArea = 0;

            for (Rect face : faces.toArray()) {
                Mat faceROI = new Mat(image, face);
                MatOfRect eyes = new MatOfRect();
                eyeClassifier.detectMultiScale(faceROI, eyes);
                if (!eyes.empty()) { // At least one eye detected
                    double area = face.width * face.height;
                    if (area > maxArea) {
                        maxArea = area;
                        bestFace = face;
                    }
                }
            }

            if (bestFace == null && !faces.empty()) {
                // Fallback to largest face if no eyes detected in any
                for (Rect face : faces.toArray()) {
                    double area = face.width * face.height;
                    if (area > maxArea) {
                        maxArea = area;
                        bestFace = face;
                    }
                }
            }

            if (bestFace != null) {
                // Desired paddings
                double extraTopPixels = EXTRA_TOP_MM / MM_PER_PIXEL; // 5mm converted to pixels at 300dpi
                double desiredPaddingTop = 0.30 * bestFace.height + extraTopPixels; // Minimal space plus 5mm
                double desiredPaddingBottom = 1.20 * bestFace.height; // For chest if available
                double paddingSide = 0.50 * bestFace.width; // per side for shoulders

                // Available spaces
                double availableTop = bestFace.y;
                double availableBottom = image.rows() - (bestFace.y + bestFace.height);
                double availableLeft = bestFace.x;
                double availableRight = image.cols() - (bestFace.x + bestFace.width);

                // Actual paddings, limited by available
                double paddingTop = Math.min(desiredPaddingTop, availableTop);
                double paddingBottom = Math.min(desiredPaddingBottom, availableBottom);
                double paddingLeft = Math.min(paddingSide, availableLeft);
                double paddingRight = Math.min(paddingSide, availableRight);

                // Desired crop
                double cropX = bestFace.x - paddingLeft;
                double cropY = bestFace.y - paddingTop;
                double cropW = bestFace.width + paddingLeft + paddingRight;
                double cropH = bestFace.height + paddingTop + paddingBottom;

                // Check if bottom is limited
                boolean bottomLimited = paddingBottom < desiredPaddingBottom;

                // Adjust to target aspect by trimming excess (no stretch)
                double currentAspect = cropW / cropH;
                if (currentAspect > TARGET_ASPECT) {
                    // Too wide: trim sides symmetrically
                    double targetW = cropH * TARGET_ASPECT;
                    double trim = (cropW - targetW) / 2;
                    cropX += trim;
                    cropW = targetW;
                } else if (currentAspect < TARGET_ASPECT) {
                    // Too tall: trim height
                    double targetH = cropW / TARGET_ASPECT;
                    double trim = cropH - targetH;
                    if (bottomLimited) {
                        // Trim from top to reduce extra head space when bottom is limited
                        cropY += trim;
                    } // Else trim from bottom by default
                    cropH = targetH;
                }

                // Final clamp (in case of floating point issues)
                cropX = Math.max(0, cropX);
                cropY = Math.max(0, cropY);
                cropW = Math.min(cropW, image.cols() - cropX);
                cropH = Math.min(cropH, image.rows() - cropY);

                // Crop
                Rect cropRect = new Rect((int) cropX, (int) cropY, (int) cropW, (int) cropH);
                Mat cropped = new Mat(image, cropRect);

                // Resize to target size (exact aspect, no stretch)
                Mat resized = new Mat();
                Imgproc.resize(cropped, resized, new Size(TARGET_WIDTH, TARGET_HEIGHT));

                // Save
                String outputPath = outputDir + File.separator + file.getName();
                Imgcodecs.imwrite(outputPath, resized);
            }
            // Else skip if no face

            int perc = (i + 1) * 100 / total;
            SwingUtilities.invokeLater(() -> progress.setValue(perc));
        }

        SwingUtilities.invokeLater(() -> {
            progress.setValue(100);
            JOptionPane.showMessageDialog(frame, "Cropping completed.");
        });
    }
}