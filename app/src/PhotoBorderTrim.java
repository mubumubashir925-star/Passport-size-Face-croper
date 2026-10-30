import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Conservative scan-border trimming. Returns a new Mat; caller releases it. */
public final class PhotoBorderTrim {
    private PhotoBorderTrim() {}
    public static Mat trim(Mat image) {
        if (image.empty() || image.cols() < 100 || image.rows() < 100) return image.clone();
        Mat gray = new Mat();
        try {
            if (image.channels() == 1) image.copyTo(gray);
            else Imgproc.cvtColor(image, gray, image.channels() == 4 ?
                Imgproc.COLOR_BGRA2GRAY : Imgproc.COLOR_BGR2GRAY);
            int w = gray.cols(), h = gray.rows();
            int maxX = Math.min(60, w / 12), maxY = Math.min(60, h / 12);
            int left = scan(gray, true, false, maxX);
            int right = scan(gray, true, true, maxX);
            int top = scan(gray, false, false, maxY);
            int bottom = scan(gray, false, true, maxY);
            // Do not crop unless a real edge strip was identified.
            if (left + right + top + bottom < 2) return image.clone();
            int x = left, y = top, nw = w - left - right, nh = h - top - bottom;
            if (nw < w * .8 || nh < h * .8) return image.clone();
            Mat region = new Mat(image, new Rect(x, y, nw, nh));
            try { return region.clone(); } finally { region.release(); }
        } finally { gray.release(); }
    }
    private static int scan(Mat gray, boolean vertical, boolean reverse, int limit) {
        int length = vertical ? gray.rows() : gray.cols();
        int dimension = vertical ? gray.cols() : gray.rows();
        int edge = reverse ? dimension - 1 : 0;
        int edgeValue = sample(gray, vertical, edge, length / 2);
        // Restrict automatic trimming to near-black or near-white scan borders.
        if (edgeValue > 55 && edgeValue < 245) return 0;
        int found = 0;
        for (int offset = 0; offset < limit; offset++) {
            int coordinate = reverse ? dimension - 1 - offset : offset;
            int matching = 0, samples = 0;
            for (int p = length / 10; p < length * 9 / 10; p += Math.max(1, length / 80)) {
                int value = sample(gray, vertical, coordinate, p);
                if (Math.abs(value - edgeValue) <= 22) matching++;
                samples++;
            }
            if (samples == 0 || matching * 100 < samples * 88) break;
            found = offset + 1;
        }
        // Keep a small safety margin to avoid clipping real photo content.
        return found >= 3 ? Math.max(0, found - 1) : 0;
    }
    private static int sample(Mat gray, boolean vertical, int edge, int along) {
        return (int) gray.get(vertical ? along : edge, vertical ? edge : along)[0];
    }
}
