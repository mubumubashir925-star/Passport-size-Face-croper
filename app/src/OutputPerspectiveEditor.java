import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Window;
import java.awt.Dialog;
import java.awt.Color;
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
            JDialog dialog = new JDialog(owner, "Four-corner perspective adjustment - " + output.getName(),
                Dialog.ModalityType.APPLICATION_MODAL);
            double cropHeight = Math.min(source.getHeight() * .85, source.getWidth() * .85 * height / width);
            double cropWidth = cropHeight * width / height;
            double x0 = (source.getWidth()-cropWidth)/2, y0=(source.getHeight()-cropHeight)/2;
            double[][] corners = {{x0,y0},{x0+cropWidth,y0},
                {x0+cropWidth,y0+cropHeight},{x0,y0+cropHeight}};
            JCheckBox perspective = new JCheckBox("Perspective: independent 4 corners");
            JCheckBox grid = new JCheckBox("Show 3 x 3 grid");
            JPanel canvas = new JPanel() {
                double scale, ox, oy;
                int moving = -1;
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
                            if(nearest>35/scale) moving=-1;
                        }
                        public void mouseDragged(MouseEvent e) {
                            if(moving<0) return;
                            double nx=Math.max(0,Math.min(source.getWidth()-1,(e.getX()-ox)/scale));
                            double ny=Math.max(0,Math.min(source.getHeight()-1,(e.getY()-oy)/scale));
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
                        public void mouseReleased(MouseEvent e) {moving=-1;}
                    };
                    addMouseListener(mouse);addMouseMotionListener(mouse);
                }
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D a=(Graphics2D)g.create();
                    scale=Math.max(.01,Math.min((getWidth()-40.0)/source.getWidth(),
                        (getHeight()-40.0)/source.getHeight()));
                    ox=(getWidth()-source.getWidth()*scale)/2;
                    oy=(getHeight()-source.getHeight()*scale)/2;
                    a.drawImage(source,(int)ox,(int)oy,(int)(source.getWidth()*scale),
                        (int)(source.getHeight()*scale),null);
                    a.setColor(Color.CYAN);a.setStroke(new BasicStroke(2));
                    for(int i=0;i<4;i++) {
                        int j=(i+1)%4;
                        int x=(int)(ox+corners[i][0]*scale),y=(int)(oy+corners[i][1]*scale);
                        a.drawLine(x,y,(int)(ox+corners[j][0]*scale),(int)(oy+corners[j][1]*scale));
                        a.fillOval(x-7,y-7,14,14);
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
            options.add(perspective);options.add(grid);
            grid.addActionListener(e->canvas.repaint());
            bottom.add(options,BorderLayout.NORTH);
            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton reset=new JButton("Reset Corners");
            reset.addActionListener(e->{
                corners[0]=new double[]{x0,y0};corners[1]=new double[]{x0+cropWidth,y0};
                corners[2]=new double[]{x0+cropWidth,y0+cropHeight};
                corners[3]=new double[]{x0,y0+cropHeight};canvas.repaint();
            });
            JButton cancel=new JButton("Cancel");cancel.addActionListener(e->dialog.dispose());
            JButton save=new JButton("Save Adjusted Output");
            save.addActionListener(e->{
                Mat input=Imgcodecs.imread(original.getAbsolutePath()), transformed=new Mat();
                MatOfPoint2f from=new MatOfPoint2f(),to=new MatOfPoint2f();
                Mat matrix=null;
                try {
                    if(input.empty()) throw new IllegalStateException("Unable to read output image");
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
