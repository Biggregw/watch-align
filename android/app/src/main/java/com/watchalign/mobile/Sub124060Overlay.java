package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Neutral, measured-only overlay for the experimental 124060 path. It draws only what was found on
 * the photo: the fitted dial boundary, the 12 triangle with its 59/60/01 minute-track reference, the
 * 3/6/9 batons and the round markers. No expected/template geometry, no pass ticks, no warning
 * colours, no scores. Found and measured = cyan; found but measurement withheld = grey dashed.
 * GMT drawing (MeasuredOverlayRenderer) is untouched.
 */
final class Sub124060Overlay {
    static final int MEASURED=Color.rgb(50,213,242), WITHHELD=Color.rgb(170,170,170), REF=Color.rgb(255,255,255);
    static final String BANNER="124060 experimental · measured, not judged";

    static final class Baton {
        String label;double[][] poly;boolean withheld;String note;
    }
    static final class Round {
        int hour;double x,y,radius;boolean withheld;String note;
    }

    static final class Drawing {
        double dialCx=Double.NaN,dialCy=Double.NaN,dialA=Double.NaN,dialB=Double.NaN,dialAngleDeg=0;
        boolean dialEdgeFitted;
        double[] triL,triR,triT;
        /** Why the 12 measurements are withheld, or null when they are reported. */
        String triWithheld;
        boolean triLume;
        double[] tick59,tick60,tick01;
        /** Minute-track circle used by the 12 frame (centre, radius) and the 60-tick clock angle / pitch. */
        double trackCx=Double.NaN,trackCy=Double.NaN,trackR=Double.NaN,tick60ClockDeg=Double.NaN,tickPitchDeg=Double.NaN;
        final List<Baton> batons=new ArrayList<>();
        final List<Round> rounds=new ArrayList<>();

        boolean hasTriangle(){return triL!=null&&triR!=null&&triT!=null;}
        boolean hasAnything(){return Double.isFinite(dialCx)||hasTriangle()||!batons.isEmpty()||!rounds.isEmpty();}

        static Drawing of(Sub124060QcAnalyzer.Result res){
            Drawing d=new Drawing();
            if(!res.dialAssessable()||res.frame==null)return d;
            if(res.edge!=null){d.dialCx=res.edge.cx;d.dialCy=res.edge.cy;d.dialA=res.edge.axisA;d.dialB=res.edge.axisB;d.dialAngleDeg=res.edge.angleDeg;d.dialEdgeFitted=true;}
            else{d.dialCx=res.frame.cx;d.dialCy=res.frame.cy;d.dialA=res.frame.r;d.dialB=res.frame.r;}
            SubTwelveTriangle.Cand c=res.triangle;
            if(c!=null){
                d.triL=c.L.clone();d.triR=c.R.clone();d.triT=c.T.clone();
                d.triWithheld=res.twelveWithheld;d.triLume=res.lumeOutline;
                d.tick59=res.tick59!=null?res.tick59.clone():null;d.tick60=res.tick60!=null?res.tick60.clone():null;d.tick01=res.tick01!=null?res.tick01.clone():null;
                if(Double.isFinite(c.trackR)&&Double.isFinite(c.tickAngle)){
                    d.trackCx=res.frame.cx;d.trackCy=res.frame.cy;d.trackR=c.trackR;d.tick60ClockDeg=c.tickAngle;d.tickPitchDeg=c.tickPitch;
                }
            }
            for(Sub124060QcAnalyzer.Baton b:res.batons){
                if(b.status==Sub124060QcAnalyzer.Status.NOT_FOUND||b.status==Sub124060QcAnalyzer.Status.WRONG_PLACE)continue;
                if(b.result==null||b.result.geometry==null)continue;
                Baton x=new Baton();x.label=b.position.label;
                double[][] p=b.result.geometry.polygon();x.poly=new double[p.length][];for(int i=0;i<p.length;i++)x.poly[i]=p[i].clone();
                x.withheld=b.status!=Sub124060QcAnalyzer.Status.FOUND;
                x.note=b.status==Sub124060QcAnalyzer.Status.HAND?"hand":b.status==Sub124060QcAnalyzer.Status.LOW_CONFIDENCE?"low confidence":null;
                d.batons.add(x);
            }
            for(Sub124060QcAnalyzer.Round r:res.rounds){
                if(r.status==Sub124060QcAnalyzer.Status.NOT_FOUND)continue;
                Round x=new Round();x.hour=r.marker.hour;x.x=r.marker.x;x.y=r.marker.y;x.radius=r.marker.radiusPx;
                x.withheld=r.status!=Sub124060QcAnalyzer.Status.FOUND;
                x.note=r.status==Sub124060QcAnalyzer.Status.HAND?"hand":r.status==Sub124060QcAnalyzer.Status.LOW_CONFIDENCE?"low confidence":null;
                d.rounds.add(x);
            }
            return d;
        }

        /** Maps everything from full-resolution crop coordinates onto the preview. */
        void mapTo(GmtDialCrop.Crop c){
            if(Double.isFinite(dialCx)){dialCx=c.toPreviewX(dialCx);dialCy=c.toPreviewY(dialCy);dialA=c.toPreviewLength(dialA);dialB=c.toPreviewLength(dialB);}
            for(double[] p:new double[][]{triL,triR,triT,tick59,tick60,tick01})map(p,c);
            if(Double.isFinite(trackR)){trackCx=c.toPreviewX(trackCx);trackCy=c.toPreviewY(trackCy);trackR=c.toPreviewLength(trackR);}
            for(Baton b:batons)for(double[] p:b.poly)map(p,c);
            for(Round r:rounds){r.x=c.toPreviewX(r.x);r.y=c.toPreviewY(r.y);r.radius=c.toPreviewLength(r.radius);}
        }
        private static void map(double[] p,GmtDialCrop.Crop c){if(p==null)return;p[0]=c.toPreviewX(p[0]);p[1]=c.toPreviewY(p[1]);}
    }

    private Sub124060Overlay(){}

    static Bitmap render(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        double R=Double.isFinite(d.dialA)&&Double.isFinite(d.dialB)?Math.max(d.dialA,d.dialB):Math.min(out.getWidth(),out.getHeight())/3.0;
        float lw=(float)Math.max(1.5,R/140.0);
        float text=(float)Math.max(12.0,0.055*R);

        // Dial boundary: solid when the dial edge was fitted, dashed for a hand-aligned circle.
        if(Double.isFinite(d.dialCx)){
            double t=Math.toRadians(d.dialAngleDeg),ct=Math.cos(t),st=Math.sin(t);
            double[][] pts=new double[181][];
            for(int i=0;i<=180;i++){double a=2*Math.PI*i/180.0,x=d.dialA*Math.cos(a),y=d.dialB*Math.sin(a);pts[i]=new double[]{d.dialCx+x*ct-y*st,d.dialCy+x*st+y*ct};}
            Paint ring=stroke(d.dialEdgeFitted?REF:WITHHELD,lw,d.dialEdgeFitted?130:200);
            if(d.dialEdgeFitted)poly(c,pts,false,ring);else for(int i=0;i<180;i+=2)line(c,pts[i],pts[i+1],ring);
        }

        // Round markers and batons: found = cyan, withheld = grey dashed with a short reason.
        for(Round r:d.rounds){
            if(!Double.isFinite(r.x)||!(r.radius>0))continue;
            float w=(float)Math.max(1.5,r.radius/9.0);
            if(r.withheld)dashedCircle(c,r.x,r.y,r.radius,stroke(WITHHELD,w,220));
            else c.drawCircle((float)r.x,(float)r.y,(float)r.radius,stroke(MEASURED,w,235));
            if(r.note!=null)label(c,r.note,r.x,r.y-r.radius-text*0.4,text*0.8f,WITHHELD);
        }
        for(Baton b:d.batons){
            if(b.poly==null)continue;
            double width=Math.hypot(b.poly[1][0]-b.poly[2][0],b.poly[1][1]-b.poly[2][1]);
            float w=(float)Math.max(1.5,width/9.0);
            if(b.withheld)for(int i=0;i<b.poly.length;i++)dashed(c,b.poly[i],b.poly[(i+1)%b.poly.length],width/4.0,stroke(WITHHELD,w,220));
            else poly(c,b.poly,true,stroke(MEASURED,w,235));
            double mx=0,my=0;for(double[] p:b.poly){mx+=p[0];my+=p[1];}mx/=b.poly.length;my/=b.poly.length;
            label(c,b.label+(b.note!=null?" · "+b.note:""),mx,my,text*0.85f,b.withheld?WITHHELD:MEASURED);
        }

        // 12: the minute-track arc it was measured against (+-4 minutes), the 59/60/01 points on it, the
        // radial through the 60 tick (the rotation reference), and the triangle outline.
        if(Double.isFinite(d.trackR)&&Double.isFinite(d.tick60ClockDeg)&&Double.isFinite(d.tickPitchDeg)){
            Paint ref=stroke(REF,Math.max(1f,lw*0.8f),150);
            int n=16;double[][] arc=new double[n+1][];
            for(int i=0;i<=n;i++)arc[i]=SubTwelveTriangle.polar(d.trackCx,d.trackCy,d.trackR,d.tick60ClockDeg+(-4+8.0*i/n)*d.tickPitchDeg);
            poly(c,arc,false,ref);
            double[] a=SubTwelveTriangle.polar(d.trackCx,d.trackCy,d.trackR*1.03,d.tick60ClockDeg),b=SubTwelveTriangle.polar(d.trackCx,d.trackCy,d.trackR*0.55,d.tick60ClockDeg);
            dashed(c,a,b,R*0.02,ref);
        }
        Paint dot=fill(REF,220);
        for(double[] p:new double[][]{d.tick59,d.tick60,d.tick01})if(p!=null)c.drawCircle((float)p[0],(float)p[1],(float)Math.max(1.5,lw*1.6),dot);
        if(d.hasTriangle()){
            double width=Math.hypot(d.triR[0]-d.triL[0],d.triR[1]-d.triL[1]);
            float w=(float)Math.max(1.5,width/28.0);
            double[][] tri={d.triL,d.triR,d.triT};
            if(d.triWithheld!=null)for(int i=0;i<3;i++)dashed(c,tri[i],tri[(i+1)%3],width/8.0,stroke(WITHHELD,w,230));
            else poly(c,tri,true,stroke(MEASURED,w,240));
            double bx=(d.triL[0]+d.triR[0]+d.triT[0])/3,by=(d.triL[1]+d.triR[1]+d.triT[1])/3;
            String l=d.triWithheld!=null?"12 · not measured":d.triLume?"12 · lume outline":"12";
            label(c,l,bx,by+width*0.75+text,text*0.85f,d.triWithheld!=null?WITHHELD:MEASURED);
        }

        // Banner at the top of the photo and a one-line key at the bottom, clear of the dial.
        float bt=(float)Math.max(14.0,Math.min(out.getWidth(),out.getHeight())/40.0);
        banner(c,BANNER,out.getWidth()/2.0,bt*1.4,bt,out.getWidth());
        banner(c,"cyan = found and measured · grey dashed = found, not measured",out.getWidth()/2.0,out.getHeight()-bt*0.6,bt*0.8f,out.getWidth());
        return out;
    }

    /** Enlarged 12 region (photo with the overlay), or null when no triangle was found. */
    static Bitmap closeUp(Bitmap watch,Bitmap overlay,Drawing d,int size){
        if(watch==null||d==null||!d.hasTriangle())return null;
        double width=Math.hypot(d.triR[0]-d.triL[0],d.triR[1]-d.triL[1]);
        double cx=(d.triL[0]+d.triR[0])/2,cy=(d.triL[1]+d.triR[1])/2;
        // Centre between the base and the tip, a little towards the minute track.
        cx=0.65*cx+0.35*d.triT[0];cy=0.65*cy+0.35*d.triT[1];
        int half=(int)Math.round(Math.max(20,width*1.3));
        int x0=(int)Math.round(cx-half),y0=(int)Math.round(cy-half),side=2*half;
        x0=Math.max(0,Math.min(x0,watch.getWidth()-1));y0=Math.max(0,Math.min(y0,watch.getHeight()-1));
        side=Math.min(side,Math.min(watch.getWidth()-x0,watch.getHeight()-y0));
        if(side<8)return null;
        Bitmap crop=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        Canvas cc=new Canvas(crop);
        cc.drawBitmap(Bitmap.createBitmap(watch,x0,y0,side,side),0,0,null);
        if(overlay!=null)cc.drawBitmap(Bitmap.createBitmap(overlay,x0,y0,side,side),0,0,null);
        return Bitmap.createScaledBitmap(crop,size,size,true);
    }

    // ------------------------------------------------------------------------------------------ drawing helpers
    private static void poly(Canvas c,double[][] pts,boolean close,Paint p){
        Path path=new Path();
        path.moveTo((float)pts[0][0],(float)pts[0][1]);
        for(int i=1;i<pts.length;i++)path.lineTo((float)pts[i][0],(float)pts[i][1]);
        if(close)path.close();
        c.drawPath(path,p);
    }
    private static void line(Canvas c,double[] a,double[] b,Paint p){c.drawLine((float)a[0],(float)a[1],(float)b[0],(float)b[1],p);}
    private static void dashed(Canvas c,double[] a,double[] b,double dash,Paint p){
        double len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<1e-6)return;
        dash=Math.max(2.0,dash);int n=(int)Math.max(1,Math.floor(len/dash));
        for(int i=0;i<n;i+=2){double t0=i/(double)n,t1=Math.min(1.0,(i+1)/(double)n);
            c.drawLine((float)(a[0]+(b[0]-a[0])*t0),(float)(a[1]+(b[1]-a[1])*t0),(float)(a[0]+(b[0]-a[0])*t1),(float)(a[1]+(b[1]-a[1])*t1),p);}
    }
    private static void dashedCircle(Canvas c,double x,double y,double R,Paint p){
        int n=24;for(int i=0;i<n;i+=2){double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n;
            c.drawLine((float)(x+R*Math.cos(a0)),(float)(y+R*Math.sin(a0)),(float)(x+R*Math.cos(a1)),(float)(y+R*Math.sin(a1)),p);}
    }
    private static void label(Canvas c,String s,double x,double y,float size,int colour){
        Paint t=fill(colour,255);t.setTextSize(size);t.setFakeBoldText(true);
        float w=t.measureText(s);
        Paint bg=fill(Color.BLACK,150);
        c.drawRect((float)(x-w/2-size*0.25),(float)(y-size*0.95),(float)(x+w/2+size*0.25),(float)(y+size*0.3),bg);
        c.drawText(s,(float)(x-w/2),(float)y,t);
    }
    private static void banner(Canvas c,String s,double cx,double y,float size,int W){
        Paint t=fill(Color.WHITE,255);t.setTextSize(size);t.setFakeBoldText(true);
        float w=t.measureText(s);
        if(w>W*0.96f){size*=W*0.96f/w;t.setTextSize(size);w=t.measureText(s);}
        double x=Math.max(2,Math.min(W-w-2,cx-w/2));
        c.drawRect((float)(x-size*0.3),(float)(y-size),(float)(x+w+size*0.3),(float)(y+size*0.35),fill(Color.BLACK,170));
        c.drawText(s,(float)x,(float)y,t);
    }
    private static Paint stroke(int col,float w,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(col);p.setAlpha(alpha);return p;}
    private static Paint fill(int col,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.FILL);p.setColor(col);p.setAlpha(alpha);return p;}
}
