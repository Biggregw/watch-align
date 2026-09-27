package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap{
 public enum Config{ARGB_8888,RGB_565}
 public final BufferedImage img;
 public Bitmap(BufferedImage i){img=i;}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
 public static Bitmap createBitmap(Bitmap src){return src.copy(Config.ARGB_8888,true);}
 public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean f){BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=o.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(b.img,0,0,w,h,null);g.dispose();return new Bitmap(o);}
 public int getWidth(){return img.getWidth();} public int getHeight(){return img.getHeight();}
 public Bitmap copy(Config c,boolean m){BufferedImage o=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_ARGB);o.getGraphics().drawImage(img,0,0,null);return new Bitmap(o);}
 public void getPixels(int[] px,int off,int stride,int x,int y,int w,int h){img.getRGB(x,y,w,h,px,off,stride);}
 public void setPixels(int[] px,int off,int stride,int x,int y,int w,int h){img.setRGB(x,y,w,h,px,off,stride);}
 public int getPixel(int x,int y){return img.getRGB(x,y);}
 public void setPixel(int x,int y,int c){img.setRGB(x,y,c);}
 public boolean isRecycled(){return false;} public void recycle(){}
 public Config getConfig(){return Config.ARGB_8888;}
}
