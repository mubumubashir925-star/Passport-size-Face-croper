import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline face-guided portrait cropper. All coordinates use source image pixels.
 * This is a baseline algorithm, not a guarantee of official passport compliance.
 */
public final class FaceCropEngine {
    private final CascadeClassifier detector;
    private final CascadeClassifier eyes;
    public FaceCropEngine(File modelFile) {
        if (!modelFile.isFile()) throw new IllegalArgumentException("Missing face model: " + modelFile);
        detector = new CascadeClassifier(modelFile.getAbsolutePath());
        if (detector.empty()) throw new IllegalStateException("Unable to load face model");
        File eyeModel = new File(modelFile.getParentFile(), "haarcascade_eye.xml");
        eyes = eyeModel.isFile() ? new CascadeClassifier(eyeModel.getAbsolutePath()) : null;
    }
    public List<Rect> detect(Mat source) {
        if (source == null || source.empty()) throw new IllegalArgumentException("Empty image");
        Mat gray = new Mat();
        MatOfRect found = new MatOfRect();
        try {
            if (source.channels() == 1) source.copyTo(gray);
            else if (source.channels() == 3) Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
            else if (source.channels() == 4) Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGRA2GRAY);
            else throw new IllegalArgumentException("Unsupported channel count");
            Imgproc.equalizeHist(gray, gray);
            detector.detectMultiScale(gray, found, 1.08, 4, 0, new Size(35, 35), new Size());
            List<Rect> result = new ArrayList<Rect>();
            for (Rect face : found.toArray()) result.add(face);
            result.sort((a,b) -> Double.compare(b.area(), a.area()));
            return result;
        } finally { gray.release(); found.release(); }
    }
    /** Estimate eye-line angle; returns 0 when landmarks are uncertain. */
    private double eyeAngle(Mat source, Rect face) {
        if (eyes == null || eyes.empty()) return 0;
        Rect upper = new Rect(face.x, face.y, face.width, Math.max(1, face.height * 2 / 3));
        Mat region = new Mat(source, upper);
        Mat gray = new Mat();
        MatOfRect found = new MatOfRect();
        try {
            if (region.channels() == 1) region.copyTo(gray);
            else Imgproc.cvtColor(region, gray, region.channels() == 4 ?
                Imgproc.COLOR_BGRA2GRAY : Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);
            eyes.detectMultiScale(gray, found, 1.08, 3, 0, new Size(12, 12), new Size());
            Rect[] candidates = found.toArray();
            double best = -1;
            Rect left = null, right = null;
            for (int i = 0; i < candidates.length; i++) {
                for (int j = i + 1; j < candidates.length; j++) {
                    Rect a = candidates[i], b = candidates[j];
                    double ax = a.x + a.width / 2.0, bx = b.x + b.width / 2.0;
                    double ay = a.y + a.height / 2.0, by = b.y + b.height / 2.0;
                    double dx = Math.abs(ax - bx);
                    double dy = Math.abs(ay - by);
                    if (dx < face.width * .22 || dx > face.width * .8 || dy > face.height * .2) continue;
                    double score = dx - 2 * dy;
                    if (score > best) {
                        best = score;
                        left = ax < bx ? a : b;
                        right = ax < bx ? b : a;
                    }
                }
            }
            if (left == null) return 0;
            double dy = (right.y + right.height / 2.0) - (left.y + left.height / 2.0);
            double dx = (right.x + right.width / 2.0) - (left.x + left.width / 2.0);
            double degrees = Math.toDegrees(Math.atan2(dy, dx));
            return Math.abs(degrees) <= 20 ? degrees : 0;
        } finally { region.release(); gray.release(); found.release(); }
    }

    /** Deskew around face center, then crop with conservative headroom. */
    public Mat cropAligned(Mat source, Rect face, int outW, int outH) {
        double angle = eyeAngle(source, face);
        if (Math.abs(angle) < 1.0) return crop(source, face, outW, outH);
        Point center = new Point(face.x + face.width / 2.0, face.y + face.height / 2.0);
        Mat transform = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotated = new Mat();
        try {
            Imgproc.warpAffine(source, rotated, transform, source.size(),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, new Scalar(255, 255, 255));
            return crop(rotated, face, outW, outH);
        } finally { transform.release(); rotated.release(); }
    }

    /** Calculates a bounded crop with target ratio, headroom and shoulder room. */
    public static Rect cropRect(Rect face, int imageW, int imageH, double ratio) {
        if (face == null || imageW <= 0 || imageH <= 0 || ratio <= 0)
            throw new IllegalArgumentException("Invalid crop inputs");
        // Keep head near the upper third while avoiding oversized margins.
        double centerX = face.x + face.width * 0.5;
        double desiredTop = face.y - face.height * 0.62;
        double desiredHeight = face.height * 2.70;
        double desiredWidth = desiredHeight * ratio;
        if (desiredWidth < face.width * 1.55) {
            desiredWidth = face.width * 1.40;
            desiredHeight = desiredWidth / ratio;
        }
        // Fit a ratio-preserving rectangle inside the source without stretching faces.
        double fit = Math.min(1.0, Math.min(imageW / desiredWidth, imageH / desiredHeight));
        int iw = Math.max(1, Math.min(imageW, (int)Math.floor(desiredWidth * fit)));
        int ih = Math.max(1, Math.min(imageH, (int)Math.floor(iw / ratio)));
        // Reconcile integer rounding with image height, retaining target aspect ratio.
        if (ih > imageH) {
            ih = imageH;
            iw = Math.max(1, Math.min(imageW, (int)Math.floor(ih * ratio)));
        }
        int ix = (int)Math.round(Math.max(0, Math.min(imageW - iw, centerX - iw / 2.0)));
        int iy = (int)Math.round(Math.max(0, Math.min(imageH - ih, desiredTop)));
        return new Rect(ix, iy, iw, ih);
    }
    public static Mat crop(Mat image, Rect face, int outW, int outH) {
        if (outW < 1 || outH < 1) throw new IllegalArgumentException("Invalid output size");
        Rect bounds = cropRect(face, image.cols(), image.rows(), (double)outW/outH);
        Mat region = new Mat(image, bounds);
        Mat output = new Mat();
        try { Imgproc.resize(region, output, new Size(outW,outH), 0, 0,
                bounds.width > outW ? Imgproc.INTER_AREA : Imgproc.INTER_CUBIC); }
        finally { region.release(); }
        return output;
    }
}
