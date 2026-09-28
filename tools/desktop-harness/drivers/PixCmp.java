package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.File;import java.awt.image.BufferedImage;
/** PixCmp <device_working.png> <original photo>: mean/max |diff| of the harness load (phone-like and ImageIO) vs the device's working image. */
public class PixCmp{
 static String cmp(BufferedImage a,Bitmap b){
  if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return "size "+a.getWidth()+"x"+a.getHeight()+" vs "+b.getWidth()+"x"+b.getHeight();
  long sum=0;int max=0;long n=0;int big=0;
  for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int p=a.getRGB(x,y),q=b.img.getRGB(x,y);
   for(int s=0;s<24;s+=8){int d=Math.abs(((p>>s)&255)-((q>>s)&255));sum+=d;max=Math.max(max,d);n++;if(d>8)big++;}}
  return String.format(java.util.Locale.US,"mean %.3f max %d  >8: %.4f%%",sum/(double)n,max,100.0*big/n);}
 public static void main(String[] a)throws Exception{nu.pattern.OpenCV.loadLocally();
  BufferedImage dev=ImageIO.read(new File(a[0]));
  System.out.println("  phone-like : "+cmp(dev,Load.photo(a[1])));
  System.out.println("  imageio    : "+cmp(dev,Load.imageio(a[1])));}}
