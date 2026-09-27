package android.graphics;
public class Path{final java.awt.geom.Path2D.Float p=new java.awt.geom.Path2D.Float();boolean started;
 public void moveTo(float x,float y){p.moveTo(x,y);started=true;} public void lineTo(float x,float y){if(!started)moveTo(x,y);else p.lineTo(x,y);} public void close(){p.closePath();} public void reset(){p.reset();started=false;}}
