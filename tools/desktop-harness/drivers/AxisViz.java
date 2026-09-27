package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.File;import java.awt.*;import java.awt.geom.*;import java.awt.image.BufferedImage;
public class AxisViz{public static void main(String[] a)throws Exception{
 nu.pattern.OpenCV.loadLocally();
 Bitmap b=Load.photo(a[0]);
 GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");
 var d=h.drawing;var g=d.twelve;double cx=d.dialCx,cy=d.dialCy;
 double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2,w=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);
 System.out.printf("rot %.2f  tri %.0fpx%n",h.summary.rotationDeg,w);
 int S=6;int half=(int)(w*1.3);int ox=(int)(mx-half),oy=(int)(my-half*0.55);int W=2*half,H=(int)(2.1*half);
 BufferedImage crop=b.img.getSubimage(Math.max(0,ox),Math.max(0,oy),Math.min(W,b.getWidth()-ox),Math.min(H,b.getHeight()-oy));
 BufferedImage big=new BufferedImage(crop.getWidth()*S,crop.getHeight()*S,BufferedImage.TYPE_INT_RGB);
 Graphics2D gg=big.createGraphics();gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);gg.drawImage(crop,0,0,big.getWidth(),big.getHeight(),null);
 BufferedImage plain=new BufferedImage(big.getWidth(),big.getHeight(),BufferedImage.TYPE_INT_RGB);plain.getGraphics().drawImage(big,0,0,null);
 gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);gg.setStroke(new BasicStroke(3f));
 java.util.function.BiFunction<Double,Double,Point2D> P=(x,y)->new Point2D.Double((x-ox)*S,(y-oy)*S);
 // reference: 60 tick -> centre, extended
 double ux=cx-g.tick60[0],uy=cy-g.tick60[1],un=Math.hypot(ux,uy);ux/=un;uy/=un;double L=w*1.6;
 gg.setColor(new Color(90,220,255));gg.draw(new Line2D.Double(P.apply(g.tick60[0]-ux*w*0.2,g.tick60[1]-uy*w*0.2),P.apply(g.tick60[0]+ux*L,g.tick60[1]+uy*L)));
 // triangle axis: base mid -> tip, extended both ways
 double ax=g.triTip[0]-mx,ay=g.triTip[1]-my,an=Math.hypot(ax,ay);ax/=an;ay/=an;
 gg.setColor(new Color(255,210,0));gg.draw(new Line2D.Double(P.apply(mx-ax*w*0.25,my-ay*w*0.25),P.apply(mx+ax*L*1.0,my+ay*L*1.0)));
 gg.setColor(new Color(40,210,120));gg.setStroke(new BasicStroke(2f));
 Path2D t=new Path2D.Double();Point2D p0=P.apply(g.triLeft[0],g.triLeft[1]),p1=P.apply(g.triRight[0],g.triRight[1]),p2=P.apply(g.triTip[0],g.triTip[1]);t.moveTo(p0.getX(),p0.getY());t.lineTo(p1.getX(),p1.getY());t.lineTo(p2.getX(),p2.getY());t.closePath();gg.draw(t);
 gg.dispose();
 BufferedImage out=new BufferedImage(big.getWidth()*2+10,big.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D go=out.createGraphics();go.setColor(Color.WHITE);go.fillRect(0,0,out.getWidth(),out.getHeight());go.drawImage(plain,0,0,null);go.drawImage(big,big.getWidth()+10,0,null);go.dispose();
 ImageIO.write(out,"png",new File(a[1]));
}}
