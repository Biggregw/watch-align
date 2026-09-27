package android.graphics;
public class Color{public static final int WHITE=0xffffffff,BLACK=0xff000000,RED=0xffff0000,GREEN=0xff00ff00,YELLOW=0xffffff00,CYAN=0xff00ffff,TRANSPARENT=0;
 public static int rgb(int r,int g,int b){return 0xff000000|(r<<16)|(g<<8)|b;}
 public static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}
 public static int red(int c){return (c>>16)&255;} public static int green(int c){return (c>>8)&255;} public static int blue(int c){return c&255;} public static int alpha(int c){return (c>>>24);}}
