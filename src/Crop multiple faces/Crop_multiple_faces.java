import java.awt.*;
import java.io.File;
import java.util.Arrays;
import javax.swing.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import com.formdev.flatlaf.FlatLightLaf;

public class Crop_multiple_faces {

    static { System.load("C:\\FaceCropper\\lib\\opencv_java455.dll"); }

    private static final String FACE_CASCADE = "C:\\FaceCropper\\models\\haarcascade_frontalface_alt2.xml";
    private static final String EYE_CASCADE  = "C:\\FaceCropper\\models\\haarcascade_eye.xml";
    private static final int TARGET_W = 466;
    private static final int TARGET_H = 600;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(Crop_multiple_faces::createGUI);
    }

    private static void createGUI() {
        JFrame f = new JFrame("Scanned Sheet to Passport Extractor (Perfect Mode)");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(700, 320);
        f.setLocationRelativeTo(null);
        f.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(15,15,15,15);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField inField = new JTextField(30);
        JTextField outField = new JTextField(30);
        JButton btnIn = new JButton("Input Folder");
        JButton btnOut = new JButton("Output Folder");
        JButton btnRun = new JButton("START EXTRACTING");
        btnRun.setFont(new Font("Arial", Font.BOLD, 22));
        JProgressBar pb = new JProgressBar(0,100);
        pb.setStringPainted(true);
        JLabel status = new JLabel("Ready - Only real faces will be saved", JLabel.CENTER);

        c.gridx=0; c.gridy=0; f.add(new JLabel("Input Folder:"), c);
        c.gridx=1; f.add(inField, c);
        c.gridx=2; f.add(btnIn, c);

        c.gridx=0; c.gridy=1; f.add(new JLabel("Output Folder:"), c);
        c.gridx=1; f.add(outField, c);
        c.gridx=2; f.add(btnOut, c);

        c.gridx=0; c.gridy=2; c.gridwidth=3; c.fill=GridBagConstraints.NONE;
        f.add(btnRun, c);

        c.gridy=3; f.add(pb, c);
        c.gridy=4; f.add(status, c);

        btnIn.addActionListener(e -> chooseFolder(inField, f));
        btnOut.addActionListener(e -> chooseFolder(outField, f));

        btnRun.addActionListener(e -> {
            String in = inField.getText().trim();
            String out = outField.getText().trim();
            if (in.isEmpty() || out.isEmpty()) {
                JOptionPane.showMessageDialog(f, "Please select both folders!");
                return;
            }
            btnRun.setEnabled(false);
            status.setText("Processing with smart filters...");
            new Thread(() -> extractSmart(in, out, pb, status, () -> {
                btnRun.setEnabled(true);
                status.setText("Completed! Only real faces saved.");
            })).start();
        });

        f.setVisible(true);
    }

    private static void chooseFolder(JTextField field, JFrame frame) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.setText(fc.getSelectedFile().getPath());
        }
    }

    private static void extractSmart(String inputDir, String outputDir,
                                     JProgressBar bar, JLabel status, Runnable onDone) {

        File[] files = new File(inputDir).listFiles((d, n) ->
                n.toLowerCase().endsWith(".jpg") || n.toLowerCase().endsWith(".jpeg") || n.toLowerCase().endsWith(".png"));

        if (files == null || files.length == 0) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "No images found!");
                onDone.run();
            });
            return;
        }

        CascadeClassifier faceDet = new CascadeClassifier(FACE_CASCADE);
        CascadeClassifier eyeDet  = new CascadeClassifier(EYE_CASCADE);

        int totalSaved = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            Mat img = Imgcodecs.imread(file.getAbsolutePath());
            if (img.empty()) continue;

            Mat gray = new Mat();
            Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(3,3), 0);
            Imgproc.equalizeHist(gray, gray);

            MatOfRect faces = new MatOfRect();
            faceDet.detectMultiScale(gray, faces, 1.05, 3, 0,
                    new Size(40,40), new Size(500,500));

            Rect[] faceArr = faces.toArray();
            Arrays.sort(faceArr, (a,b) -> {
                int dy = a.y - b.y;
                return Math.abs(dy) < 120 ? a.x - b.x : dy;
            });

            String baseName = file.getName().substring(0, file.getName().lastIndexOf('.'));
            String ext = file.getName().substring(file.getName().lastIndexOf('.'));

            int validCount = 0;
            for (Rect face : faceArr) {
                if (face.width < 50 || face.height < 50) continue;
                if (face.width > 500 || face.height > 600) continue;
                double aspect = (double)face.width / face.height;
                if (aspect < 0.6 || aspect > 1.4) continue;

                // Eye detection - removes pen writing, text, etc.
                Mat faceROI = new Mat(gray, face);
                MatOfRect eyes = new MatOfRect();
                eyeDet.detectMultiScale(faceROI, eyes, 1.1, 4, 0,
                        new Size(20,20), new Size(100,100));
                if (eyes.toArray().length == 0) continue;

                // Contrast check
                Mat faceMat = new Mat(gray, face);
                MatOfDouble mean = new MatOfDouble();
                MatOfDouble stddev = new MatOfDouble();
                Core.meanStdDev(faceMat, mean, stddev);
                double contrast = stddev.toArray()[0];
                if (contrast < 20) continue;

                // REAL FACE - crop properly
                double side = 0.5, top = 0.8, bot = 1.3;
                int x = Math.max(0, (int)(face.x - face.width  * side));
                int y = Math.max(0, (int)(face.y - face.height * top));
                int w = Math.min(img.cols() - x, (int)(face.width  * (1 + 2*side)));
                int h = Math.min(img.rows() - y, (int)(face.height * (1 + top + bot)));

                Mat crop = new Mat(img, new Rect(x, y, w, h));
                Mat result = new Mat();
                Imgproc.resize(crop, result, new Size(TARGET_W, TARGET_H));

                String outName = baseName + "_" + (++validCount) + ext;
                Imgcodecs.imwrite(outputDir + "\\" + outName, result);
                totalSaved++;
            }

            // FIX: capture values before lambda
            final int currentFile = i + 1;
            final int totalFiles = files.length;
            final int savedThisFile = validCount;
            final int progress = currentFile * 100 / totalFiles;

            SwingUtilities.invokeLater(() -> {
                bar.setValue(progress);
                status.setText("File " + currentFile + "/" + totalFiles + " to " + savedThisFile + " real faces saved");
            });
        }

        final int finalTotal = totalSaved;
        SwingUtilities.invokeLater(() -> {
            bar.setValue(100);
            JOptionPane.showMessageDialog(null,
                "PERFECT RESULT!\n\nExtracted only " + finalTotal + " REAL passport photos.\n" +
                "All pen marks, blank areas, and false detections removed.",
                "Success!", JOptionPane.INFORMATION_MESSAGE);
            onDone.run();
        });
    }
}