package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.Map;
import java.util.List;
import java.util.Collections;

/** Display only. Paths come from the stored scoring polygons, never a raster mask or a new fit. */
final class MarkerMismatchOverlay {
    static final int YELLOW=0xffffd60a,CYAN=0xff32d5f2,EXTRA=0xe6ff801f,MISSING=0xe6e64bdc,INFERRED=0xffff3b30;
    static final String LEGEND="Orange: extra detected area. Magenta: missing expected area. Yellow: scoring reference. Cyan: directly sampled edge. Red: corner or end inferred from the fitted edges.";
    private MarkerMismatchOverlay(){}

    static Bitmap fullWatch(int width,int height,double[] H,Map<Integer,MarkerMismatchResearch.Result> results){
        boolean available=false;
        for(MarkerMismatchResearch.Result q:results.values())if(q.expected!=null){available=true;break;}
        if(!available)return null; // No ring-adjusted reference: never substitute the fixed master.
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);
        for(MarkerMismatchResearch.Result q:results.values()){
            draw(canvas,q.expected,H,YELLOW,false,1.5f);
            if(q.trusted())actual(canvas,q,H,1f);
        }
        return image;
    }

    static Bitmap closeup(Bitmap base,double cx,double cy,double half,MarkerMismatchResearch.Result q){
        Canvas canvas=new Canvas(base);
        double[] T=closeupTransform(base.getWidth(),cx,cy,half);
        draw(canvas,q.expected,T,YELLOW,false,1f);
        if(q.trusted()){
            actual(canvas,q,T,1f);
            // Fill last: thin genuine residual regions remain visible beneath no thick outline.
            drawRegions(canvas,q.score.extraRegions(),T,EXTRA,true,0);
            drawRegions(canvas,q.score.missingRegions(),T,MISSING,true,0);
        }
        return base;
    }

    /** The exact scored polygon. With known sampled spans: thin red for the whole (inferred) shape, cyan over the
     *  stretches the detector actually sampled, so an inferred corner is never shown as an observed boundary. */
    private static void actual(Canvas canvas,MarkerMismatchResearch.Result q,double[] T,float width){
        if(q.observedSpans==null){draw(canvas,q.score.actualPolygon,T,CYAN,false,width);return;}
        draw(canvas,q.score.actualPolygon,T,INFERRED,false,width);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*width);
        Path path=new Path();
        for(double[][] span:q.observedSpans){double[][] p=project(new double[][]{span[0],span[1],span[1]},T);if(p==null)continue;
            path.moveTo((float)p[0][0],(float)p[0][1]);path.lineTo((float)p[1][0],(float)p[1][1]);}
        canvas.drawPath(path,paint);
    }

    /** Same half-pixel origin used by Alpha98Closeups.render and its canonical crop. */
    static double[] closeupTransform(int size,double cx,double cy,double half){
        double scale=size/(2*half);
        return new double[]{scale,0,-(cx-half)*scale-.5,0,scale,-(cy-half)*scale-.5,0,0,1};
    }

    static double[][] project(double[][] polygon,double[] H){
        if(polygon==null||polygon.length<3||H==null||H.length!=9)return null;
        double[][] out=new double[polygon.length][2];
        for(int i=0;i<polygon.length;i++){
            double x=polygon[i][0],y=polygon[i][1],z=H[6]*x+H[7]*y+H[8];
            if(!Double.isFinite(z)||Math.abs(z)<1e-10)return null;
            out[i][0]=(H[0]*x+H[1]*y+H[2])/z;out[i][1]=(H[3]*x+H[4]*y+H[5])/z;
            if(!Double.isFinite(out[i][0])||!Double.isFinite(out[i][1]))return null;
        }
        return out;
    }

    private static void draw(Canvas canvas,double[][] polygon,double[] transform,int colour,boolean filled,float width){
        drawRegions(canvas,Collections.singletonList(polygon),transform,colour,filled,width);
    }

    private static void drawRegions(Canvas canvas,List<double[][]> regions,double[] transform,int colour,boolean filled,float width){
        // One fill path per colour avoids alpha/antialias seams along partition boundaries.
        Path path=new Path();
        for(double[][] polygon:regions){
            double[][] p=project(polygon,transform);if(p==null)continue;
            path.moveTo((float)p[0][0],(float)p[0][1]);
            for(int i=1;i<p.length;i++)path.lineTo((float)p[i][0],(float)p[i][1]);path.close();
        }
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(colour);
        paint.setStyle(filled?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(width);
        canvas.drawPath(path,paint);
    }
}
