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
    public FaceCropEngine(File modelFile) {
        if (!modelFile.isFile()) throw new IllegalArgumentException("Missing face model: " + modelFile);
        detector = new CascadeClassifier(modelFile.getAbsolutePath());
        if (detector.empty()) throw new IllegalStateException("Unable to load face model");
    }
    public List<Rect> detect(Mat source) {
        if (source == null || source.empty()) throw new IllegalArgumentException("Empty image");
        Mat gray = new Mat();
        MatOfRect found = new MatOfRect();
        try {
            Imgproc.cvtColor(source, gray, source.channels() == 4 ? Imgproc.COLOR_BGRA2GRAY :
                    source.channels() == 3 ? Imgproc.COLOR_BGR2GRAY : Imgproc.COLOR_GRAY2BGR);
            if (source.channels() == 1) source.copyTo(gray);
            Imgproc.equalizeHist(gray, gray);
            detector.detectMultiScale(gray, found, 1.08, 4, 0, new Size(35, 35), new Size());
            List<Rect> result = new ArrayList<Rect>();
            for (Rect face : found.toArray()) result.add(face);
            result.sort((a,b) -> Double.compare(b.area(), a.area()));
            return result;
        } finally { gray.release(); found.release(); }
    }
    /** Calculates a bounded crop with target ratio, headroom and shoulder room. */
    public static Rect cropRect(Rect face, int imageW, int imageH, double ratio) {
        if (face == null || imageW <= 0 || imageH <= 0 || ratio <= 0)
            throw new IllegalArgumentException("Invalid crop inputs");
        double centerX = face.x + face.width / 2.0;
        double top = face.y - 0.55 * face.height;
        double bottom = face.y + 2.75 * face.height;
        double height = bottom - top;
        double width = height * ratio;
        if (width < face.width * 1.75) {
            width = face.width * 1.75;
            height = width / ratio;
        }
        double scale = Math.min(1.0, Math.min(imageW / width, imageH / height));
        width *= scale; height *= scale;
        double x = Math.max(0, Math.min(imageW - width, centerX - width / 2));
        double y = Math.max(0, Math.min(imageH - height, top));
        int ix = (int)Math.round(x), iy = (int)Math.round(y);
        int iw = Math.min(imageW - ix, Math.max(1, (int)Math.floor(width)));
        int ih = Math.min(imageH - iy, Math.max(1, (int)Math.floor(height)));
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
