package android.graphics;
import java.awt.*;import java.awt.geom.*;
public class Canvas{final Graphics2D g;
 public Canvas(Bitmap b){g=b.img.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);}
 void set(Paint p){g.setColor(p.awt());g.setStroke(new BasicStroke(p.width));}
 public void drawPath(Path path,Paint p){set(p);if(p.style==Paint.Style.FILL)g.fill(path.p);else g.draw(path.p);}
 public void drawLine(float a,float b,float c,float d,Paint p){set(p);g.draw(new Line2D.Float(a,b,c,d));}
 public void drawCircle(float x,float y,float r,Paint p){set(p);Shape s=new Ellipse2D.Float(x-r,y-r,2*r,2*r);if(p.style==Paint.Style.FILL)g.fill(s);else g.draw(s);}
 public void drawRect(float l,float t,float r,float b,Paint p){set(p);Shape s=new Rectangle2D.Float(l,t,r-l,b-t);if(p.style==Paint.Style.FILL)g.fill(s);else g.draw(s);}
 public void drawRect(RectF rc,Paint p){drawRect(rc.left,rc.top,rc.right,rc.bottom,p);}
 public void drawText(String s,float x,float y,Paint p){set(p);g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,p.bold?java.awt.Font.BOLD:java.awt.Font.PLAIN,1).deriveFont(p.textSize));g.drawString(s,x,y);}
 public void drawBitmap(Bitmap b,float x,float y,Paint p){g.drawImage(b.img,(int)x,(int)y,null);}
 public void drawColor(int c){g.setColor(new java.awt.Color(c,true));g.fillRect(0,0,100000,100000);}
 public void drawOval(RectF r,Paint p){set(p);Shape s=new Ellipse2D.Float(r.left,r.top,r.right-r.left,r.bottom-r.top);if(p.style==Paint.Style.FILL)g.fill(s);else g.draw(s);}
}
