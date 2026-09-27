package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Locale;

/**
 * Draws what the analysis actually measured, not a predicted template.
 *
 * The overlay is built from measured elements only, so it cannot disagree with the
 * results: a faint ring for the fitted dial edge, and for the 12 marker the detected
 * triangle, the 59/60/01 tick ends, the gap bracket and the 59/01 spacing brackets, each
 * coloured by its verdict. Further checks add their own elements here.
 */
final class MeasuredOverlayRenderer {
    static final int CLEAR=Color.rgb(40,210,120), CHECK=Color.rgb(255,176,0),
            STRONG=Color.rgb(255,45,45), UNKNOWN=Color.rgb(165,175,190), TICK=Color.rgb(90,220,255);

    /** Everything needed to draw; image coordinates of the analysed bitmap. */
    static final class Drawing {
        double dialCx=Double.NaN,dialCy=Double.NaN,dialA=Double.NaN,dialB=Double.NaN,dialAngleDeg=0;
        GmtTwelveLandmarkAnalyzer.Geometry twelve;
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention alignment=GmtHumanQcMath.Attention.UNASSESSABLE;
        double gapValue=Double.NaN, spacing59=Double.NaN, spacing01=Double.NaN;

        boolean hasAnything(){return Double.isFinite(dialCx)||twelve!=null;}
    }

    private MeasuredOverlayRenderer(){}

    static int colour(GmtHumanQcMath.Attention a){
        switch(a){case CLEAR:return CLEAR;case CHECK:return CHECK;case STRONG:return STRONG;default:return UNKNOWN;}
    }

    static Bitmap render(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        float base=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);

        // Measured dial edge: faint, context only.
        if(Double.isFinite(d.dialCx)&&Double.isFinite(d.dialA)&&Double.isFinite(d.dialB)){
            Paint ring=stroke(Color.WHITE,1.0f*base,90);
            Path p=new Path();
            double t=Math.toRadians(d.dialAngleDeg),ct=Math.cos(t),st=Math.sin(t);
            for(int i=0;i<=180;i++){
                double a=2*Math.PI*i/180.0,x=d.dialA*Math.cos(a),y=d.dialB*Math.sin(a);
                float px=(float)(d.dialCx+x*ct-y*st),py=(float)(d.dialCy+x*st+y*ct);
                if(i==0)p.moveTo(px,py);else p.lineTo(px,py);
            }
            c.drawPath(p,ring);
        }

        GmtTwelveLandmarkAnalyzer.Geometry g=d.twelve;
        if(g==null)return out;
        double width=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);
        float lw=(float)Math.max(1.0,width/40.0);
        int alignCol=colour(d.alignment), gapCol=colour(d.gap);

        // Detected triangle, coloured by the alignment verdict.
        Paint tri=stroke(alignCol,lw,235);
        Path tp=new Path();
        tp.moveTo((float)g.triLeft[0],(float)g.triLeft[1]);
        tp.lineTo((float)g.triRight[0],(float)g.triRight[1]);
        tp.lineTo((float)g.triTip[0],(float)g.triTip[1]);
        tp.close();
        c.drawPath(tp,tri);

        // 59/60/01 tick ends and the local-12 reference line through 59 and 01.
        Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
        c.drawLine((float)g.tick59[0],(float)g.tick59[1],(float)g.tick01[0],(float)g.tick01[1],tick);
        Paint dot=fill(TICK,230);
        float dr=(float)Math.max(1.5,width/30.0);
        for(double[] q:new double[][]{g.tick59,g.tick60,g.tick01})c.drawCircle((float)q[0],(float)q[1],dr,dot);

        // Gap bracket: base midpoint to the 59-01 line, coloured by the gap verdict.
        double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2;
        double[] foot=footOnLine(mx,my,g.tick59,g.tick01);
        Paint gapP=stroke(gapCol,lw,245);
        c.drawLine((float)mx,(float)my,(float)foot[0],(float)foot[1],gapP);
        double capx=(g.tick01[0]-g.tick59[0]),capy=(g.tick01[1]-g.tick59[1]),cl=Math.hypot(capx,capy);
        if(cl>1e-9){capx=capx/cl*width*0.08;capy=capy/cl*width*0.08;
            c.drawLine((float)(mx-capx),(float)(my-capy),(float)(mx+capx),(float)(my+capy),gapP);
            c.drawLine((float)(foot[0]-capx),(float)(foot[1]-capy),(float)(foot[0]+capx),(float)(foot[1]+capy),gapP);}

        // 59/01 spacing: triangle base corners to their ticks, coloured by alignment.
        Paint sp=stroke(alignCol,Math.max(1f,lw*0.7f),200);
        c.drawLine((float)g.triLeft[0],(float)g.triLeft[1],(float)g.tick59[0],(float)g.tick59[1],sp);
        c.drawLine((float)g.triRight[0],(float)g.triRight[1],(float)g.tick01[0],(float)g.tick01[1],sp);

        // Labels, small and outlined so they read on any dial.
        float ts=(float)Math.max(9.0,width*0.22);
        if(Double.isFinite(d.gapValue))
            label(c,String.format(Locale.US,"gap %.2f",d.gapValue),(float)(g.triRight[0]+width*0.25),(float)(my-width*0.05),ts,gapCol);
        if(Double.isFinite(d.spacing59))
            label(c,String.format(Locale.US,"%.2f",d.spacing59),(float)(g.triLeft[0]-width*0.95),(float)(g.triLeft[1]+width*0.35),ts,alignCol);
        if(Double.isFinite(d.spacing01))
            label(c,String.format(Locale.US,"%.2f",d.spacing01),(float)(g.triRight[0]+width*0.25),(float)(g.triRight[1]+width*0.35),ts,alignCol);
        return out;
    }

    static double[] footOnLine(double px,double py,double[] a,double[] b){
        double ux=b[0]-a[0],uy=b[1]-a[1],l2=ux*ux+uy*uy;
        if(l2<1e-12)return new double[]{a[0],a[1]};
        double t=((px-a[0])*ux+(py-a[1])*uy)/l2;
        return new double[]{a[0]+ux*t,a[1]+uy*t};
    }

    private static void label(Canvas c,String s,float x,float y,float size,int colour){
        Paint o=new Paint(Paint.ANTI_ALIAS_FLAG);o.setTextSize(size);o.setColor(Color.BLACK);o.setAlpha(200);
        o.setStyle(Paint.Style.STROKE);o.setStrokeWidth(Math.max(2f,size/5f));
        c.drawText(s,x,y,o);
        Paint f=new Paint(Paint.ANTI_ALIAS_FLAG);f.setTextSize(size);f.setColor(colour);
        c.drawText(s,x,y,f);
    }
    private static Paint stroke(int col,float w,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(col);p.setAlpha(alpha);return p;}
    private static Paint fill(int col,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.FILL);p.setColor(col);p.setAlpha(alpha);return p;}
}
