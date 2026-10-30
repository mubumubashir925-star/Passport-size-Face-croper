import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Interactive source-image crop editor, independent of OpenCV. */
public final class CropPreviewDialog extends JDialog {
    private final BufferedImage image;
    private final int outputWidth, outputHeight;
    private final PreviewPanel panel = new PreviewPanel();
    private final JLabel hint = new JLabel("Drag inside the crop box to move; drag corners to resize. Reset restores suggested crop.");
    private Rectangle2D.Double crop;
    private Rectangle result;
    private int navigation;
    private Point start;
    private Rectangle2D.Double original;
    private int dragMode; // 1 move, 2 resize
    private double initialX, initialY, initialW, initialH;

    private CropPreviewDialog(Window owner, BufferedImage image, Rectangle suggested, int w, int h) {
        super(owner, "Crop Preview & Manual Adjustment", ModalityType.APPLICATION_MODAL);
        this.image = image;
        outputWidth = w;
        outputHeight = h;
        double ratio = (double) w / h;
        Rectangle initial = suggested == null ? defaultCrop(image, ratio) : suggested;
        crop = normalize(initial.x, initial.y, initial.width, ratio);
        original = (Rectangle2D.Double) crop.clone();
        setLayout(new BorderLayout(8, 8));
        add(panel, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(hint, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton previous = new JButton("◀ Previous");
        JButton next = new JButton("Next ▶");
        previous.addActionListener(e -> { navigation = -1; dispose(); });
        next.addActionListener(e -> { navigation = 1; dispose(); });
        buttons.add(previous);
        buttons.add(next);
        JButton reset = new JButton("Reset");
        JButton cancel = new JButton("Cancel");
        JButton apply = new JButton("Apply Crop");
        reset.addActionListener(e -> { crop = (Rectangle2D.Double) original.clone(); panel.repaint(); });
        cancel.addActionListener(e -> dispose());
        apply.addActionListener(e -> {
            result = new Rectangle((int)Math.round(crop.x), (int)Math.round(crop.y),
                (int)Math.round(crop.width), (int)Math.round(crop.height));
            dispose();
        });
        buttons.add(reset); buttons.add(cancel); buttons.add(apply);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        setSize(950, 740);
        setMinimumSize(new Dimension(550, 450));
        setLocationRelativeTo(owner);
    }

    public static final class PreviewResult {
        public final Rectangle crop;
        public final int direction;
        PreviewResult(Rectangle crop, int direction) { this.crop = crop; this.direction = direction; }
    }

    public static PreviewResult editNavigable(Window owner, File file, Rectangle suggested,
            int w, int h) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("Unsupported image: " + file);
        CropPreviewDialog dialog = new CropPreviewDialog(owner, image, suggested, w, h);
        dialog.setTitle("Crop Preview - " + file.getName());
        dialog.setVisible(true);
        return new PreviewResult(dialog.result, dialog.navigation);
    }

    public static Rectangle edit(Window owner, File file, Rectangle suggested, int w, int h) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("Unsupported image: " + file);
        CropPreviewDialog dialog = new CropPreviewDialog(owner, image, suggested, w, h);
        dialog.setVisible(true);
        return dialog.result;
    }

    public static Rectangle defaultCrop(BufferedImage image, double ratio) {
        double h = Math.min(image.getHeight() * .85, image.getWidth() * .85 / ratio);
        int width = Math.max(1, (int)Math.round(h * ratio));
        int height = Math.max(1, (int)Math.round(h));
        return new Rectangle((image.getWidth()-width)/2, (image.getHeight()-height)/2, width, height);
    }

    private Rectangle2D.Double normalize(double x, double y, double width, double ratio) {
        width = Math.max(20, Math.min(width, Math.min(image.getWidth(), image.getHeight()*ratio)));
        double height = width / ratio;
        return new Rectangle2D.Double(Math.max(0, Math.min(x, image.getWidth()-width)),
            Math.max(0, Math.min(y, image.getHeight()-height)), width, height);
    }

    private final class PreviewPanel extends JPanel {
        double scale, offsetX, offsetY;
        PreviewPanel() {
            setBackground(new Color(35, 39, 45));
            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    Point p = toImage(e.getPoint());
                    double tolerance = 14 / scale;
                    double right = crop.x + crop.width, bottom = crop.y + crop.height;
                    dragMode = Math.abs(p.x-right) < tolerance && Math.abs(p.y-bottom) < tolerance ? 2 :
                        crop.contains(p) ? 1 : 0;
                    start = p;
                    initialX = crop.x; initialY = crop.y; initialW = crop.width; initialH = crop.height;
                }
                public void mouseDragged(MouseEvent e) {
                    if (dragMode == 0 || start == null) return;
                    Point p = toImage(e.getPoint());
                    double dx = p.x-start.x, dy = p.y-start.y;
                    if (dragMode == 1) crop = normalize(initialX+dx, initialY+dy, initialW,
                        (double)outputWidth/outputHeight);
                    else crop = normalize(initialX, initialY, initialW+dx,
                        (double)outputWidth/outputHeight);
                    repaint();
                }
                public void mouseReleased(MouseEvent e) { dragMode = 0; start = null; }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }
        private Point toImage(Point p) {
            return new Point((int)Math.round((p.x-offsetX)/scale), (int)Math.round((p.y-offsetY)/scale));
        }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g.create();
            try {
                scale = Math.min((getWidth()-30.0)/image.getWidth(), (getHeight()-30.0)/image.getHeight());
                scale = Math.max(.01, scale);
                offsetX = (getWidth()-image.getWidth()*scale)/2;
                offsetY = (getHeight()-image.getHeight()*scale)/2;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(image, (int)offsetX, (int)offsetY,
                    (int)Math.round(image.getWidth()*scale), (int)Math.round(image.getHeight()*scale), null);
                int x=(int)Math.round(offsetX+crop.x*scale), y=(int)Math.round(offsetY+crop.y*scale);
                int w=(int)Math.round(crop.width*scale), h=(int)Math.round(crop.height*scale);
                g2.setColor(new Color(0,0,0,115));
                g2.fillRect((int)offsetX,(int)offsetY,(int)Math.round(image.getWidth()*scale),Math.max(0,y-(int)offsetY));
                g2.fillRect((int)offsetX,y,Math.max(0,x-(int)offsetX),h);
                g2.fillRect(x+w,y,Math.max(0,(int)(offsetX+image.getWidth()*scale)-x-w),h);
                g2.fillRect((int)offsetX,y+h,(int)Math.round(image.getWidth()*scale),
                    Math.max(0,(int)(offsetY+image.getHeight()*scale)-y-h));
                g2.setStroke(new BasicStroke(2));
                g2.setColor(new Color(53,205,255));
                g2.drawRect(x,y,w,h);
                g2.fillRect(x+w-6,y+h-6,12,12);
                g2.setColor(new Color(255,255,255,130));
                g2.drawLine(x+w/3,y,x+w/3,y+h);
                g2.drawLine(x+2*w/3,y,x+2*w/3,y+h);
                g2.drawLine(x,y+h/3,x+w,y+h/3);
                g2.drawLine(x,y+2*h/3,x+w,y+2*h/3);
            } finally { g2.dispose(); }
        }
    }
}
