import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Window;
import java.awt.Dialog;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/** Edit an existing cropped output using four independently draggable corners. */
public final class OutputPerspectiveEditor {
    private OutputPerspectiveEditor() {}
    public static void open(Window owner, File original, File output, int width, int height, Runnable onSaved) {
        try {
            BufferedImage source = ImageIO.read(original);
            if (source == null) throw new IllegalArgumentException("Cannot read output image");
            final BufferedImage[] view = {source};
            final int[] quarterTurns = {0};
            JDialog dialog = new JDialog(owner, "Four-corner perspective adjustment - " + output.getName(),
                Dialog.ModalityType.APPLICATION_MODAL);
            double cropHeight = Math.min(source.getHeight() * .85, source.getWidth() * .85 * height / width);
            double cropWidth = cropHeight * width / height;
            double x0 = (source.getWidth()-cropWidth)/2, y0=(source.getHeight()-cropHeight)/2;
            try {
                FaceCropEngine detector=new FaceCropEngine(new File("models/haarcascade_frontalface_alt2.xml"));
                Mat full=Imgcodecs.imread(original.getAbsolutePath());
                try {
                    java.util.List<Rect> faces=detector.detect(full);
                    if(!faces.isEmpty()) {
                        Rect face=faces.get(0);
                        cropWidth=Math.min(source.getWidth(),face.width*2.15);
                        cropHeight=Math.min(source.getHeight(),cropWidth*height/width);
                        x0=Math.max(0,Math.min(source.getWidth()-cropWidth,face.x+face.width/2.0-cropWidth/2));
                        y0=Math.max(0,Math.min(source.getHeight()-cropHeight,face.y-face.height*.55));
                    }
                } finally {full.release();}
            } catch(Exception ignored) {}
            final double initialX=x0, initialY=y0, initialW=cropWidth, initialH=cropHeight;
            double[][] corners = {{x0,y0},{x0+cropWidth,y0},
                {x0+cropWidth,y0+cropHeight},{x0,y0+cropHeight}};
            JCheckBox perspective = new JCheckBox("Perspective: independent 4 corners");
            JCheckBox grid = new JCheckBox("Show 3 x 3 grid");
            JCheckBox fullGrid = new JCheckBox("Grid across full image");
            JPanel canvas = new JPanel() {
                double scale, ox, oy;
                int moving = -1;
                boolean rotating = false;
                double previousAngle = 0;
                {
                    setBackground(new Color(30,33,38));
                    MouseAdapter mouse = new MouseAdapter() {
                        public void mousePressed(MouseEvent e) {
                            double x=(e.getX()-ox)/scale, y=(e.getY()-oy)/scale;
                            double nearest=Double.MAX_VALUE;
                            for(int i=0;i<4;i++) {
                                double d=Math.hypot(x-corners[i][0],y-corners[i][1]);
                                if(d<nearest) {nearest=d;moving=i;}
                            }
                            double distancePx = nearest * scale;
                            rotating = distancePx > 13 && distancePx <= 42;
                            if (rotating) {
                                moving = -1;
                                double cx=0,cy=0;
                                for(int j=0;j<4;j++){cx+=corners[j][0];cy+=corners[j][1];}
                                previousAngle=Math.atan2(y-cy/4,x-cx/4);
                            } else if(distancePx>13) moving=-1;
                        }
                        public void mouseDragged(MouseEvent e) {
                            if (rotating) {
                                double x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
                                double cx=0,cy=0;
                                for(int j=0;j<4;j++){cx+=corners[j][0];cy+=corners[j][1];}
                                cx/=4;cy/=4;
                                double angle=Math.atan2(y-cy,x-cx);
                                double delta=angle-previousAngle;
                                double co=Math.cos(delta),si=Math.sin(delta);
                                for(int j=0;j<4;j++){
                                    double dx=corners[j][0]-cx,dy=corners[j][1]-cy;
                                    corners[j][0]=cx+dx*co-dy*si;
                                    corners[j][1]=cy+dx*si+dy*co;
                                }
                                previousAngle=angle;
                                repaint();
                                return;
                            }
                            if(moving<0) return;
                            double nx=Math.max(0,Math.min(view[0].getWidth()-1,(e.getX()-ox)/scale));
                            double ny=Math.max(0,Math.min(view[0].getHeight()-1,(e.getY()-oy)/scale));
                            if(perspective.isSelected()) {
                                corners[moving][0]=nx;corners[moving][1]=ny;
                            } else {
                                int opposite=(moving+2)%4;
                                double fixedX=corners[opposite][0], fixedY=corners[opposite][1];
                                double left=Math.min(nx,fixedX),right=Math.max(nx,fixedX);
                                double top=Math.min(ny,fixedY),bottom=Math.max(ny,fixedY);
                                corners[0][0]=left;corners[0][1]=top;
                                corners[1][0]=right;corners[1][1]=top;
                                corners[2][0]=right;corners[2][1]=bottom;
                                corners[3][0]=left;corners[3][1]=bottom;
                            }
                            repaint();
                        }
                        public void mouseReleased(MouseEvent e) {moving=-1;rotating=false;}
                        public void mouseMoved(MouseEvent e) {
                            double x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
                            double nearest=Double.MAX_VALUE;
                            for(int j=0;j<4;j++) nearest=Math.min(nearest,Math.hypot(x-corners[j][0],y-corners[j][1]));
                            double px=nearest*scale;
                            setCursor(Cursor.getPredefinedCursor(px>13 && px<=42 ? Cursor.HAND_CURSOR :
                                px<=13 ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
                        }
                    };
                    addMouseListener(mouse);addMouseMotionListener(mouse);
                }
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D a=(Graphics2D)g.create();
                    scale=Math.max(.01,Math.min((getWidth()-40.0)/view[0].getWidth(),
                        (getHeight()-40.0)/view[0].getHeight()));
                    ox=(getWidth()-view[0].getWidth()*scale)/2;
                    oy=(getHeight()-view[0].getHeight()*scale)/2;
                    a.drawImage(view[0],(int)ox,(int)oy,(int)(view[0].getWidth()*scale),
                        (int)(view[0].getHeight()*scale),null);
                    a.setColor(Color.CYAN);a.setStroke(new BasicStroke(2));
                    for(int i=0;i<4;i++) {
                        int j=(i+1)%4;
                        int x=(int)(ox+corners[i][0]*scale),y=(int)(oy+corners[i][1]*scale);
                        a.drawLine(x,y,(int)(ox+corners[j][0]*scale),(int)(oy+corners[j][1]*scale));
                        a.fillOval(x-7,y-7,14,14);
                    }
                    if(fullGrid.isSelected()) {
                        a.setColor(new Color(255, 193, 7, 160));
                        a.setStroke(new BasicStroke(1));
                        for(int k=1;k<3;k++) {
                            int xx=(int)(ox+view[0].getWidth()*scale*k/3.0);
                            int yy=(int)(oy+view[0].getHeight()*scale*k/3.0);
                            a.drawLine(xx,(int)oy,xx,(int)(oy+view[0].getHeight()*scale));
                            a.drawLine((int)ox,yy,(int)(ox+view[0].getWidth()*scale),yy);
                        }
                    }
                    if(grid.isSelected()) {
                        a.setColor(new Color(0,255,255,140));
                        a.setStroke(new BasicStroke(1));
                        for(int k=1;k<3;k++) {
                            double t=k/3.0;
                            double xa=corners[0][0]*(1-t)+corners[3][0]*t;
                            double ya=corners[0][1]*(1-t)+corners[3][1]*t;
                            double xb=corners[1][0]*(1-t)+corners[2][0]*t;
                            double yb=corners[1][1]*(1-t)+corners[2][1]*t;
                            a.drawLine((int)(ox+xa*scale),(int)(oy+ya*scale),
                                (int)(ox+xb*scale),(int)(oy+yb*scale));
                            xa=corners[0][0]*(1-t)+corners[1][0]*t;
                            ya=corners[0][1]*(1-t)+corners[1][1]*t;
                            xb=corners[3][0]*(1-t)+corners[2][0]*t;
                            yb=corners[3][1]*(1-t)+corners[2][1]*t;
                            a.drawLine((int)(ox+xa*scale),(int)(oy+ya*scale),
                                (int)(ox+xb*scale),(int)(oy+yb*scale));
                        }
                    }
                    a.dispose();
                }
            };
            dialog.setLayout(new BorderLayout(8,8));
            dialog.add(canvas,BorderLayout.CENTER);
            JPanel bottom=new JPanel(new BorderLayout());
            JPanel options=new JPanel(new FlowLayout(FlowLayout.LEFT));
            options.add(perspective);options.add(grid);options.add(fullGrid);
            JLabel rotationHint = new JLabel("Rotate crop: drag just outside a corner");
            rotationHint.setToolTipText("Hover 13-42 pixels outside a corner until the hand cursor appears, then drag to rotate the crop box.");
            options.add(rotationHint);
            JButton rotateLeft = new JButton("Rotate Left 90°");
            JButton rotateRight = new JButton("Rotate Right 90°");
            java.util.function.IntConsumer rotate = direction -> {
                BufferedImage before = view[0];
                BufferedImage after = new BufferedImage(before.getHeight(), before.getWidth(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = after.createGraphics();
                if (direction > 0) { g.translate(after.getWidth(), 0); g.rotate(Math.PI / 2); }
                else { g.translate(0, after.getHeight()); g.rotate(-Math.PI / 2); }
                g.drawImage(before, 0, 0, null); g.dispose();
                view[0] = after;
                quarterTurns[0] = (quarterTurns[0] + (direction > 0 ? 1 : 3)) % 4;
                double boxH = Math.min(after.getHeight() * .85, after.getWidth() * .85 * height / width);
                double boxW = boxH * width / height;
                double xx = (after.getWidth() - boxW) / 2, yy = (after.getHeight() - boxH) / 2;
                corners[0] = new double[]{xx,yy}; corners[1] = new double[]{xx+boxW,yy};
                corners[2] = new double[]{xx+boxW,yy+boxH}; corners[3] = new double[]{xx,yy+boxH};
                canvas.repaint();
            };
            rotateLeft.addActionListener(e -> rotate.accept(-1));
            rotateRight.addActionListener(e -> rotate.accept(1));
            options.add(rotateLeft); options.add(rotateRight);
            grid.addActionListener(e->canvas.repaint());
            fullGrid.addActionListener(e->canvas.repaint());
            bottom.add(options,BorderLayout.NORTH);
            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton reset=new JButton("Reset Corners");
            reset.addActionListener(e->{
                corners[0]=new double[]{initialX,initialY};corners[1]=new double[]{initialX+initialW,initialY};
                corners[2]=new double[]{initialX+initialW,initialY+initialH};
                corners[3]=new double[]{initialX,initialY+initialH};canvas.repaint();
            });
            JButton cancel=new JButton("Cancel");cancel.addActionListener(e->dialog.dispose());
            JButton save=new JButton("Save Adjusted Output");
            save.addActionListener(e->{
                Mat input=Imgcodecs.imread(original.getAbsolutePath()), transformed=new Mat();
                MatOfPoint2f from=new MatOfPoint2f(),to=new MatOfPoint2f();
                Mat matrix=null;
                try {
                    if(input.empty()) throw new IllegalStateException("Unable to read output image");
                    if (quarterTurns[0] != 0) {
                        Mat rotated = new Mat();
                        int rotation = quarterTurns[0] == 1 ? Core.ROTATE_90_CLOCKWISE :
                            quarterTurns[0] == 2 ? Core.ROTATE_180 : Core.ROTATE_90_COUNTERCLOCKWISE;
                        Core.rotate(input, rotated, rotation);
                        input.release(); input = rotated;
                    }
                    from.fromArray(new Point(corners[0][0],corners[0][1]),
                        new Point(corners[1][0],corners[1][1]),
                        new Point(corners[2][0],corners[2][1]),
                        new Point(corners[3][0],corners[3][1]));
                    to.fromArray(new Point(0,0),new Point(width-1,0),
                        new Point(width-1,height-1),new Point(0,height-1));
                    matrix=Imgproc.getPerspectiveTransform(from,to);
                    Imgproc.warpPerspective(input,transformed,matrix,new Size(width,height));
                    if(!Imgcodecs.imwrite(output.getAbsolutePath(),transformed))
                        throw new IllegalStateException("Could not save output");
                    dialog.dispose();onSaved.run();
                } catch(Exception ex) {
                    JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Save failed",JOptionPane.ERROR_MESSAGE);
                } finally {
                    input.release();transformed.release();from.release();to.release();
                    if(matrix!=null) matrix.release();
                }
            });
            buttons.add(reset);buttons.add(cancel);buttons.add(save);
            bottom.add(buttons,BorderLayout.SOUTH);dialog.add(bottom,BorderLayout.SOUTH);
            dialog.setSize(850,700);dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
        } catch(Exception ex) {
            JOptionPane.showMessageDialog(owner,ex.getMessage(),"Perspective editor",JOptionPane.ERROR_MESSAGE);
        }
    }
}
