package android.graphics;
public class Paint{public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2,DITHER_FLAG=4;public enum Style{STROKE,FILL,FILL_AND_STROKE}public enum Cap{BUTT,ROUND,SQUARE}public enum Align{LEFT,CENTER,RIGHT}
 int color=0xff000000;float width=1,textSize=12;Style style=Style.FILL;
 public Paint(){} public Paint(int f){}
 public void setStyle(Style s){style=s;} public void setStrokeWidth(float w){width=w;} public void setColor(int c){color=c;}
 public void setAlpha(int a){color=(color&0x00ffffff)|(a<<24);} public void setAntiAlias(boolean b){} public void setTextSize(float s){textSize=s;}
 public void setStrokeCap(Cap c){} public void setTextAlign(Align a){} public int getColor(){return color;}
 java.awt.Color awt(){return new java.awt.Color(color,true);}}
