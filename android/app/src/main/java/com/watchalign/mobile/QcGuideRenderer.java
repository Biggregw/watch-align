package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Visual QC guide: dial boundary, roll-corrected ideal marker positions and measured marker positions. */
final class QcGuideRenderer {
    static Bitmap render(Bitmap watch) {
        Mat src=new Mat();
        try {
            Utils.bitmapToMat(watch,src); Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=privateMethod("detectDial",Mat.class);
            Object dial=detect.invoke(null,src);
            if(dial==null) return watch.copy(Bitmap.Config.ARGB_8888,false);
            Method measure=privateMethod("measureMarkerSet",Mat.class,dial.getClass());
            Method perspective=privateMethod("perspectiveEquivalent",Mat.class,dial.getClass());
            Object set=measure.invoke(null,src,dial);
            double tilt=((Number)perspective.invoke(null,src,dial)).doubleValue();
            boolean reliable=QcGuideMath.alignmentGuideReliable(tilt);

            double cx=num(dial,"x"), cy=num(dial,"y"), dr=num(dial,"r");
            double global=num(set,"globalRotation"), median=num(set,"medianRadius");

            Mat out=src.clone();
            Scalar cyan=new Scalar(242,213,50);
            Scalar yellow=new Scalar(60,220,250);
            Scalar green=new Scalar(120,220,120);
            Scalar amber=new Scalar(40,180,255);
            Scalar red=new Scalar(70,70,255);
            Scalar white=new Scalar(245,245,245);
            Scalar neutral=new Scalar(175,175,175);

            Imgproc.circle(out,new Point(cx,cy),(int)Math.round(dr),cyan,2,Imgproc.LINE_AA,0);
            if(Double.isFinite(median)) Imgproc.circle(out,new Point(cx,cy),(int)Math.round(median),yellow,2,Imgproc.LINE_AA,0);
            Imgproc.drawMarker(out,new Point(cx,cy),white,Imgproc.MARKER_CROSS,18,2,Imgproc.LINE_AA);

            Object markers=field(set,"markers");
            java.util.List<?> list=(java.util.List<?>)markers;
            for(int h=1;h<=12;h++) {
                Object marker=findHour(list,h);
                double idealDeg=QcGuideMath.idealAngleDeg(h,global);
                QcGuideMath.P p0=QcGuideMath.polar(cx,cy,dr*0.56,idealDeg);
                QcGuideMath.P p1=QcGuideMath.polar(cx,cy,dr*0.93,idealDeg);
                Imgproc.line(out,new Point(p0.x,p0.y),new Point(p1.x,p1.y),neutral,1,Imgproc.LINE_AA,0);

                QcGuideMath.P ideal=QcGuideMath.polar(cx,cy,median,idealDeg);
                Imgproc.drawMarker(out,new Point(ideal.x,ideal.y),white,Imgproc.MARKER_TILTED_CROSS,10,1,Imgproc.LINE_AA);

                Scalar sev=neutral;
                if(marker!=null) {
                    double angular=num(marker,"angular"), radial=num(marker,"radial"), actualR=num(marker,"radius");
                    if(reliable){int level=QcGuideMath.severity(angular,radial);sev=level==0?green:level==1?amber:red;}
                    QcGuideMath.P actual=QcGuideMath.polar(cx,cy,actualR,idealDeg+angular);
                    Imgproc.circle(out,new Point(actual.x,actual.y),6,sev,2,Imgproc.LINE_AA,0);
                    if(reliable) Imgproc.line(out,new Point(ideal.x,ideal.y),new Point(actual.x,actual.y),sev,2,Imgproc.LINE_AA,0);
                }

                QcGuideMath.P label=QcGuideMath.polar(cx,cy,dr*1.06,idealDeg);
                String txt=h==12?"12":Integer.toString(h);
                Imgproc.putText(out,txt,new Point(label.x-8,label.y+6),Imgproc.FONT_HERSHEY_SIMPLEX,0.42,sev,1,Imgproc.LINE_AA,false);
            }

            String legend=reliable ? "cyan=dial  yellow=marker ring  x=ideal  o=measured" : "PERSPECTIVE HIGH: alignment guide is advisory only";
            Imgproc.putText(out,legend,new Point(18,30),Imgproc.FONT_HERSHEY_SIMPLEX,0.48,reliable?white:amber,1,Imgproc.LINE_AA,false);
            Bitmap result=Bitmap.createBitmap(out.cols(),out.rows(),Bitmap.Config.ARGB_8888);
            Mat rgba=new Mat(); Imgproc.cvtColor(out,rgba,Imgproc.COLOR_BGR2RGBA); Utils.matToBitmap(rgba,result); rgba.release(); out.release();
            return result;
        } catch(Throwable e) {
            return watch.copy(Bitmap.Config.ARGB_8888,false);
        } finally { src.release(); }
    }

    private static Object findHour(java.util.List<?> list,int hour) throws Exception {
        for(Object m:list) if((int)Math.round(num(m,"hour"))==hour) return m;
        return null;
    }
    private static Method privateMethod(String name,Class<?>...types)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String n)throws Exception{Object v=field(o,n);return ((Number)v).doubleValue();}
    private QcGuideRenderer(){}
}
