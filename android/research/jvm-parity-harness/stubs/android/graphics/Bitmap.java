package android.graphics;
import org.opencv.core.Mat;
public class Bitmap { public Mat rgba; private final int w,h;
  public Bitmap(int w,int h){this.w=w;this.h=h;}
  public Bitmap(Mat rgba){this.rgba=rgba;this.w=rgba.cols();this.h=rgba.rows();}
  public int getWidth(){return w;} public int getHeight(){return h;} }
