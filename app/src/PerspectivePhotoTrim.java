import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** Find a tilted rectangular printed-photo boundary and rectify it. */
public final class PerspectivePhotoTrim {
    private PerspectivePhotoTrim() {}
    public static Mat trim(Mat source) {
        if (source.empty() || source.cols() < 160 || source.rows() < 160) return source.clone();
        Mat gray = new Mat(), blur = new Mat(), edges = new Mat(), hierarchy = new Mat();
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blur, new Size(5, 5), 0);
            Imgproc.Canny(blur, edges, 45, 135);
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE);
            double area = source.cols() * (double)source.rows();
            Point[] best = null;
            double bestArea = 0;
            for (MatOfPoint contour : contours) {
                double perimeter;
                MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f polygon = new MatOfPoint2f();
                try {
                    perimeter = Imgproc.arcLength(curve, true);
                    Imgproc.approxPolyDP(curve, polygon, 0.025 * perimeter, true);
                    if (polygon.total() != 4) continue;
                    Point[] points = polygon.toArray();
                    MatOfPoint polyInt = new MatOfPoint(points);
                    try {
                        if (!Imgproc.isContourConvex(polyInt)) continue;
                    } finally { polyInt.release(); }
                    double candidateArea = Math.abs(Imgproc.contourArea(polygon));
                    if (candidateArea < area * 0.32 || candidateArea > area * 0.96 ||
                        candidateArea < bestArea) continue;
                    Point center = new Point(source.cols() * .5, source.rows() * .5);
                    if (Imgproc.pointPolygonTest(polygon, center, false) < 0) continue;
                    Rect box = Imgproc.boundingRect(new MatOfPoint(points));
                    if (box.width < source.cols() * .48 || box.height < source.rows() * .48)
                        continue;
                    bestArea = candidateArea;
                    best = points;
                } finally { curve.release(); polygon.release(); }
            }
            if (best == null) return source.clone();
            Point[] ordered = order(best);
            double width = Math.max(distance(ordered[0], ordered[1]),
                distance(ordered[2], ordered[3]));
            double height = Math.max(distance(ordered[0], ordered[3]),
                distance(ordered[1], ordered[2]));
            if (width < 80 || height < 80) return source.clone();
            // Inset inside the detected printed border, not into the portrait.
            double inset = Math.min(width, height) * .018;
            MatOfPoint2f from = new MatOfPoint2f(ordered);
            MatOfPoint2f to = new MatOfPoint2f(
                new Point(-inset, -inset), new Point(width + inset, -inset),
                new Point(width + inset, height + inset), new Point(-inset, height + inset));
            Mat matrix = new Mat(), output = new Mat();
            try {
                matrix = Imgproc.getPerspectiveTransform(from, to);
                Imgproc.warpPerspective(source, output, matrix,
                    new Size(Math.round(width - 2 * inset), Math.round(height - 2 * inset)));
                return output;
            } finally { from.release(); to.release(); matrix.release(); }
        } finally {
            gray.release(); blur.release(); edges.release(); hierarchy.release();
            for (MatOfPoint contour : contours) contour.release();
        }
    }
    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }
    private static Point[] order(Point[] points) {
        Point[] ordered = new Point[4];
        Point[] byY = points.clone();
        Arrays.sort(byY, (a,b) -> Double.compare(a.y, b.y));
        if (byY[0].x < byY[1].x) { ordered[0] = byY[0]; ordered[1] = byY[1]; }
        else { ordered[0] = byY[1]; ordered[1] = byY[0]; }
        if (byY[2].x < byY[3].x) { ordered[3] = byY[2]; ordered[2] = byY[3]; }
        else { ordered[3] = byY[3]; ordered[2] = byY[2]; }
        return ordered;
    }
}
