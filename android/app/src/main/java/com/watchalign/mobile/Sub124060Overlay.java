package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GMT-style whole-dial and close-up presentation for the 124060.
 *
 * This keeps the alpha71 GMT-parity product contract and changes only the verdict source: reliable
 * measurements are now judged against the frozen 124060 family calibration. The same four visual
 * states as GMT are used: green tick, amber !, red !! and grey dash. No GMT tolerance is copied.
 *
 * While Sub124060Calibration.VERDICTS_ENABLED is false, reliable measurements are drawn solid in a
 * neutral MEASURED blue with a dot badge instead of a verdict colour; unmeasured items keep the
 * grey dashed / dash-badge state.
 */
final class Sub124060Overlay {
    static final int CLEAR=Color.rgb(40,210,120), CHECK=Color.rgb(255,176,0),
            STRONG=Color.rgb(255,45,45), WITHHELD=Color.rgb(165,175,190),
            TICK=Color.rgb(90,220,255), WHITE=Color.WHITE, MEASURED=Color.rgb(110,160,255);
    static final String BANNER=Sub124060Calibration.VERDICTS_ENABLED
            ?"124060 experimental · provisional family calibration"
            :"124060 experimental · measured, not yet judged";

    static final class Baton {
        String label;
        double[][] poly;
        double[] tickBefore,tickCentre,tickAfter;
        double expectedX=Double.NaN,expectedY=Double.NaN;
        boolean measured;
        GmtHumanQcMath.Attention attention=GmtHumanQcMath.Attention.UNASSESSABLE;
        String note;
        /** Authoritative reason a measured baton has no verdict (its 3-9 or 12-6 decision); not shown yet. */
        MeasurementDecisions.Reason reason;
        double centreX(){
            if(poly==null||poly.length==0)return expectedX;
            double x=0;for(double[] p:poly)x+=p[0];return x/poly.length;
        }
        double centreY(){
            if(poly==null||poly.length==0)return expectedY;
            double y=0;for(double[] p:poly)y+=p[1];return y/poly.length;
        }
        double width(){
            if(poly==null||poly.length<3)return Double.NaN;
            return Math.hypot(poly[1][0]-poly[2][0],poly[1][1]-poly[2][1]);
        }
    }

    static final class Round {
        int hour;
        boolean found,measured;
        GmtHumanQcMath.Attention attention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double x=Double.NaN,y=Double.NaN,radius=Double.NaN;
        double expectedX=Double.NaN,expectedY=Double.NaN,expectedRadius=Double.NaN;
        double[] tickBefore,tickCentre,tickAfter;
        String note;
    }

    static final class Drawing {
        double dialCx=Double.NaN,dialCy=Double.NaN,dialA=Double.NaN,dialB=Double.NaN,dialAngleDeg=0;
        boolean dialEdgeFitted;

        double[] triL,triR,triT,tick59,tick60,tick01;
        boolean triMeasured;
        GmtHumanQcMath.Attention triAttention=GmtHumanQcMath.Attention.UNASSESSABLE;
        String triNote;
        double rotationDeg=Double.NaN,gapR=Double.NaN,centringW=Double.NaN;
        String rotationNote,gapNote,centringNote;

        Sub124060Calibration.Assessment calibration=new Sub124060Calibration.Assessment();
        /** The decisions the calibration assessment above was projected from. */
        MeasurementDecisions.Photo decisions;
        final List<Baton> batons=new ArrayList<>();
        final List<Round> rounds=new ArrayList<>();

        boolean hasTriangle(){return triL!=null&&triR!=null&&triT!=null;}
        boolean hasAnything(){return Double.isFinite(dialCx)||hasTriangle()||!batons.isEmpty()||!rounds.isEmpty();}

        static Drawing of(Sub124060QcAnalyzer.Result res){
            Drawing d=new Drawing();
            d.decisions=Sub124060Calibration.decide(res);
            d.calibration=Sub124060Calibration.project(d.decisions,Sub124060Calibration.VERDICTS_ENABLED);
            if(!res.dialAssessable()||res.frame==null)return d;
            if(res.edge!=null){
                d.dialCx=res.edge.cx;d.dialCy=res.edge.cy;d.dialA=res.edge.axisA;d.dialB=res.edge.axisB;
                d.dialAngleDeg=res.edge.angleDeg;d.dialEdgeFitted=true;
            }else{
                d.dialCx=res.frame.cx;d.dialCy=res.frame.cy;d.dialA=res.frame.a;d.dialB=res.frame.b;
            }

            SubTwelveTriangle.Cand c=res.triangle;
            if(c!=null){
                d.triL=c.L.clone();d.triR=c.R.clone();d.triT=c.T.clone();
                d.tick59=cloneP(res.tick59);d.tick60=cloneP(res.tick60);d.tick01=cloneP(res.tick01);
                d.rotationDeg=res.rotationDeg;d.gapR=res.gapR;d.centringW=res.centringW;
                d.rotationNote=res.rotationWithheld;d.gapNote=res.gapWithheld;d.centringNote=res.centringWithheld;
                d.triMeasured=res.rotationWithheld==null||res.gapWithheld==null||res.centringWithheld==null;
                d.triAttention=d.triMeasured?d.calibration.twelve():GmtHumanQcMath.Attention.UNASSESSABLE;
                d.triNote=res.twelveWithheld!=null?shortReason(res.twelveWithheld)
                        :!d.triMeasured?"not measured"
                        :d.triAttention==GmtHumanQcMath.Attention.UNASSESSABLE&&Sub124060Calibration.VERDICTS_ENABLED?"measured, not judged":null;
            }

            double phi12=res.tick60!=null?res.frame.phiOf(res.tick60[0],res.tick60[1]):-Math.PI/2.0;
            for(Sub124060QcAnalyzer.Baton b:res.batons){
                Baton x=new Baton();x.label=b.position.label;
                x.measured=b.status==Sub124060QcAnalyzer.Status.FOUND;
                x.attention=x.measured?d.calibration.baton(x.label):GmtHumanQcMath.Attention.UNASSESSABLE;
                x.note=statusReason(b.status,b.note);
                if(x.measured&&x.attention==GmtHumanQcMath.Attention.UNASSESSABLE){
                    MeasurementDecisions.Metric why=d.decisions.metric(Sub124060Calibration.batonMetric(x.label));
                    x.reason=why.reason;
                    x.note=Sub124060Calibration.VERDICTS_ENABLED?unjudgedBatonNote(why,x.label):null;
                }
                double[] expected=res.frame.at(phi12+Math.toRadians(b.position.angleFromTwelveDeg),0.817);
                x.expectedX=expected[0];x.expectedY=expected[1];
                if(b.result!=null&&b.result.geometry!=null){
                    GmtSixLandmarkAnalyzer.Geometry g=b.result.geometry;
                    double[][] p=g.polygon();x.poly=new double[p.length][];
                    for(int i=0;i<p.length;i++)x.poly[i]=p[i].clone();
                    x.tickBefore=cloneP(g.tickBefore);x.tickCentre=cloneP(g.tickCentre);x.tickAfter=cloneP(g.tickAfter);
                }
                d.batons.add(x);
            }

            GmtHumanQcMath.Attention roundAttention=d.calibration.rounds();
            for(Sub124060QcAnalyzer.Round r:res.rounds){
                GmtRoundMarkerAnalyzer.Marker m=r.marker;Round x=new Round();x.hour=m.hour;
                x.found=m.found;x.measured=r.status==Sub124060QcAnalyzer.Status.FOUND;
                x.attention=x.measured?roundAttention:GmtHumanQcMath.Attention.UNASSESSABLE;
                x.note=statusReason(r.status,r.note);
                if(x.measured&&x.attention==GmtHumanQcMath.Attention.UNASSESSABLE)x.note=Sub124060Calibration.VERDICTS_ENABLED?"measured, not judged":null;
                x.x=m.x;x.y=m.y;x.radius=m.radiusPx;
                x.expectedX=m.seedX;x.expectedY=m.seedY;x.expectedRadius=m.expectedRadiusPx;
                if(!Double.isFinite(x.expectedX)||!Double.isFinite(x.expectedY)){
                    double ph=phi12+Math.toRadians(30.0*m.hour);
                    double[] q=res.frame.at(ph,0.817);x.expectedX=q[0];x.expectedY=q[1];
                }
                if(!(x.expectedRadius>0))x.expectedRadius=0.086*res.frame.r;
                x.tickBefore=cloneP(m.tickBefore);x.tickCentre=cloneP(m.tickCentre);x.tickAfter=cloneP(m.tickAfter);
                d.rounds.add(x);
            }
            return d;
        }

        void mapTo(GmtDialCrop.Crop c){
            if(Double.isFinite(dialCx)){
                dialCx=c.toPreviewX(dialCx);dialCy=c.toPreviewY(dialCy);
                dialA=c.toPreviewLength(dialA);dialB=c.toPreviewLength(dialB);
            }
            for(double[] p:new double[][]{triL,triR,triT,tick59,tick60,tick01})map(p,c);
            for(Baton b:batons){
                if(b.poly!=null)for(double[] p:b.poly)map(p,c);
                for(double[] p:new double[][]{b.tickBefore,b.tickCentre,b.tickAfter})map(p,c);
                if(Double.isFinite(b.expectedX)){b.expectedX=c.toPreviewX(b.expectedX);b.expectedY=c.toPreviewY(b.expectedY);}
            }
            for(Round r:rounds){
                if(Double.isFinite(r.x)){r.x=c.toPreviewX(r.x);r.y=c.toPreviewY(r.y);r.radius=c.toPreviewLength(r.radius);}
                if(Double.isFinite(r.expectedX)){r.expectedX=c.toPreviewX(r.expectedX);r.expectedY=c.toPreviewY(r.expectedY);r.expectedRadius=c.toPreviewLength(r.expectedRadius);}
                for(double[] p:new double[][]{r.tickBefore,r.tickCentre,r.tickAfter})map(p,c);
            }
        }
        private static void map(double[] p,GmtDialCrop.Crop c){if(p!=null){p[0]=c.toPreviewX(p[0]);p[1]=c.toPreviewY(p[1]);}}
    }

    private Sub124060Overlay(){}

    /**
     * Visible words for a measured baton with no verdict, from its authoritative 3-9 or 12-6 decision.
     * The wording is unchanged: "needs the other baton" when the pair decision cites the partner
     * baton's own detection status, otherwise "reading not steady enough" whatever the cited reason
     * (production follow-up: show the authoritative reason after a separately approved change).
     */
    static String unjudgedBatonNote(MeasurementDecisions.Metric pair,String label){
        if("3".equals(label)||"9".equals(label)){
            String other="3".equals(label)?"9":"3",subject=Sub12Reasons.batonSubject(other);
            for(String code:Sub12Reasons.BATON_STATUS_CODES)if(pair.cites(code,subject))return "needs the "+other+" baton";
            return "reading not steady enough";
        }
        return "reading not steady enough";
    }

    static int colour(GmtHumanQcMath.Attention a){
        switch(a){case CLEAR:return CLEAR;case CHECK:return CHECK;case STRONG:return STRONG;default:return WITHHELD;}
    }
    static boolean judged(GmtHumanQcMath.Attention a){return a!=null&&a!=GmtHumanQcMath.Attention.UNASSESSABLE;}
    /** Reliable value shown without a verdict (measured-only mode). */
    static boolean neutral(GmtHumanQcMath.Attention a,boolean measured){return !judged(a)&&measured&&!Sub124060Calibration.VERDICTS_ENABLED;}
    static boolean solid(GmtHumanQcMath.Attention a,boolean measured){return judged(a)||neutral(a,measured);}
    static int ink(GmtHumanQcMath.Attention a,boolean measured){return judged(a)?colour(a):neutral(a,measured)?MEASURED:WITHHELD;}
    static boolean flagged(GmtHumanQcMath.Attention a){return a==GmtHumanQcMath.Attention.CHECK||a==GmtHumanQcMath.Attention.STRONG;}

    static Bitmap render(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        double R=Double.isFinite(d.dialA)&&Double.isFinite(d.dialB)?Math.max(d.dialA,d.dialB):Math.min(out.getWidth(),out.getHeight())/3.0;
        float base=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        float badge=(float)Math.max(7.0,0.045*R),text=badge*1.05f;

        drawDial(c,d,base);

        for(Round r:d.rounds){
            double x=r.found&&Double.isFinite(r.x)?r.x:r.expectedX;
            double y=r.found&&Double.isFinite(r.y)?r.y:r.expectedY;
            double rad=r.found&&r.radius>0?r.radius:r.expectedRadius;
            if(!Double.isFinite(x)||!Double.isFinite(y)||!(rad>0))continue;
            float lw=(float)Math.max(1.0,2*rad/22.0);
            if(solid(r.attention,r.measured))c.drawCircle((float)x,(float)y,(float)rad,stroke(ink(r.attention,r.measured),lw,235));
            else circleDashed(c,x,y,rad,stroke(WITHHELD,lw,205));
            badge(c,x,y,badge,r.attention,r.measured);
            String w=flagged(r.attention)?"position":r.note;
            if(w!=null)word(c,d,x,y,rad,w,ink(r.attention,r.measured),text);
        }

        for(Baton b:d.batons){
            double x=b.centreX(),y=b.centreY();
            if(b.poly!=null){
                double w=b.width();float lw=(float)Math.max(1.0,(Double.isFinite(w)?w:badge)/16.0);
                if(solid(b.attention,b.measured))poly(c,b.poly,true,stroke(ink(b.attention,b.measured),lw,235));
                else for(int i=0;i<b.poly.length;i++)dashed(c,b.poly[i],b.poly[(i+1)%b.poly.length],Math.max(3,w/4.0),stroke(WITHHELD,lw,215));
                badge(c,x,y,badge,b.attention,b.measured);
                String why=flagged(b.attention)?"axis":b.note;
                if(why!=null)word(c,d,x,y,Math.max(badge*2,w),why,ink(b.attention,b.measured),text);
            }else if(Double.isFinite(x)&&Double.isFinite(y)){
                badge(c,x,y,badge,GmtHumanQcMath.Attention.UNASSESSABLE);
                word(c,d,x,y,badge*2.5,b.note!=null?b.note:"not found",WITHHELD,text);
            }
        }

        if(d.hasTriangle()){
            double width=Math.hypot(d.triR[0]-d.triL[0],d.triR[1]-d.triL[1]);
            float lw=(float)Math.max(1.0,width/40.0);double[][] tri={d.triL,d.triR,d.triT};
            double x=(d.triL[0]+d.triR[0]+d.triT[0])/3.0,y=(d.triL[1]+d.triR[1]+d.triT[1])/3.0;
            if(solid(d.triAttention,d.triMeasured))poly(c,tri,true,stroke(ink(d.triAttention,d.triMeasured),lw,235));
            else for(int i=0;i<3;i++)dashed(c,tri[i],tri[(i+1)%3],width/8.0,stroke(WITHHELD,lw,220));
            badge(c,x,y,badge,d.triAttention,d.triMeasured);
            String why=flagged(d.triAttention)?"alignment":d.triNote;
            if(why!=null)word(c,d,x,y,width*0.5,why,ink(d.triAttention,d.triMeasured),text);
        }else if(Double.isFinite(d.dialCx)){
            double[] p=ellipsePoint(d,-90,0.78);badge(c,p[0],p[1],badge,GmtHumanQcMath.Attention.UNASSESSABLE);word(c,d,p[0],p[1],badge*2.5,"12 not found",WITHHELD,text);
        }

        banner(c,BANNER,d.dialCx,d.dialCy-R,2*R,out.getWidth(),badge);
        legend(c,out.getWidth(),out.getHeight(),d.dialCx,d.dialCy+R,2*R,badge);
        return out;
    }

    /** Detail overlay used by the close-up. Geometry is unchanged; verdict colour comes from calibration. */
    static Bitmap renderDetail(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);float base=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        drawDial(c,d,base);

        for(Round r:d.rounds)drawRoundDetail(c,r);
        for(Baton b:d.batons)drawBatonDetail(c,b);

        if(!d.hasTriangle())return out;
        double width=Math.hypot(d.triR[0]-d.triL[0],d.triR[1]-d.triL[1]);
        float lw=(float)Math.max(1.0,width/40.0);double[][] tri={d.triL,d.triR,d.triT};
        if(solid(d.triAttention,d.triMeasured))poly(c,tri,true,stroke(ink(d.triAttention,d.triMeasured),lw,240));
        else for(int i=0;i<3;i++)dashed(c,tri[i],tri[(i+1)%3],width/8.0,stroke(WITHHELD,lw,220));

        if(d.tick59!=null&&d.tick60!=null&&d.tick01!=null){
            Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
            c.drawLine((float)d.tick59[0],(float)d.tick59[1],(float)d.tick01[0],(float)d.tick01[1],tick);
            Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,width/30.0);
            for(double[] q:new double[][]{d.tick59,d.tick60,d.tick01})c.drawCircle((float)q[0],(float)q[1],dr,dot);

            // Gap line: drawn in the tick colour; its verdict is part of the 12 overall badge.
            double mx=(d.triL[0]+d.triR[0])/2,my=(d.triL[1]+d.triR[1])/2;
            double[] foot=footOnLine(mx,my,d.tick59,d.tick01);
            Paint gp=stroke(d.gapNote==null?TICK:WITHHELD,Math.max(1f,lw*1.4f),235);
            c.drawLine((float)mx,(float)my,(float)foot[0],(float)foot[1],gp);
            double tx=d.tick01[0]-d.tick59[0],ty=d.tick01[1]-d.tick59[1],tl=Math.hypot(tx,ty);
            if(tl>1e-9){tx=tx/tl*width*0.08;ty=ty/tl*width*0.08;
                c.drawLine((float)(mx-tx),(float)(my-ty),(float)(mx+tx),(float)(my+ty),gp);
                c.drawLine((float)(foot[0]-tx),(float)(foot[1]-ty),(float)(foot[0]+tx),(float)(foot[1]+ty),gp);}

            Paint sp=stroke(ink(d.triAttention,d.triMeasured),Math.max(1f,lw*0.75f),210);
            c.drawLine((float)d.triL[0],(float)d.triL[1],(float)d.tick59[0],(float)d.tick59[1],sp);
            c.drawLine((float)d.triR[0],(float)d.triR[1],(float)d.tick01[0],(float)d.tick01[1],sp);
        }
        return out;
    }

    /** Enlarged 12 region with the same status hierarchy as the mature GMT close-up. */
    static Bitmap closeUp(Bitmap watch,Bitmap ignored,Drawing d,int size){
        if(watch==null||d==null||!d.hasTriangle())return null;
        Bitmap detail=renderDetail(watch,d);
        double width=Math.hypot(d.triR[0]-d.triL[0],d.triR[1]-d.triL[1]);
        double cx=(d.triL[0]+d.triR[0])/2,cy=(d.triL[1]+d.triR[1])/2;
        cx=0.62*cx+0.38*d.triT[0];cy=0.62*cy+0.38*d.triT[1];
        int half=(int)Math.round(Math.max(24,width*1.55));
        int x0=(int)Math.round(cx-half),y0=(int)Math.round(cy-half),side=2*half;
        x0=Math.max(0,Math.min(x0,watch.getWidth()-1));y0=Math.max(0,Math.min(y0,watch.getHeight()-1));
        side=Math.min(side,Math.min(watch.getWidth()-x0,watch.getHeight()-y0));if(side<8)return null;

        Bitmap crop=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);Canvas cc=new Canvas(crop);
        cc.drawBitmap(Bitmap.createBitmap(watch,x0,y0,side,side),0,0,null);
        if(detail!=null)cc.drawBitmap(Bitmap.createBitmap(detail,x0,y0,side,side),0,0,null);
        Bitmap square=Bitmap.createScaledBitmap(crop,size,size,true);

        int strip=Math.max(56,size/7);Bitmap out=Bitmap.createBitmap(size,size+strip,Bitmap.Config.ARGB_8888);
        Canvas oc=new Canvas(out);oc.drawBitmap(square,0,0,null);oc.drawRect(0,size,size,size+strip,fill(Color.rgb(12,16,22),245));
        float ts=Math.max(14,size/28f);Paint t=fill(ink(d.triAttention,d.triMeasured),255);t.setTextSize(ts);t.setFakeBoldText(true);
        String l1=neutral(d.triAttention,d.triMeasured)?"12  •  measured, not judged":"12  "+symbol(d.triAttention)+"  "+label(d.triAttention);
        oc.drawText(l1,ts*0.7f,size+ts*1.25f,t);
        t.setFakeBoldText(false);t.setColor(Color.rgb(205,215,228));t.setTextSize(ts*0.90f);
        String l2=triangleValues(d);
        while(t.measureText(l2)>size-ts*1.4f&&t.getTextSize()>9)t.setTextSize(t.getTextSize()-1);
        oc.drawText(l2,ts*0.7f,size+strip-ts*0.55f,t);
        return out;
    }

    private static void drawDial(Canvas c,Drawing d,float base){
        if(!Double.isFinite(d.dialCx)||!Double.isFinite(d.dialA)||!Double.isFinite(d.dialB))return;
        Paint ring=stroke(WHITE,1.0f*base,d.dialEdgeFitted?90:150);Path p=new Path();
        double t=Math.toRadians(d.dialAngleDeg),ct=Math.cos(t),st=Math.sin(t);
        for(int i=0;i<=180;i++){
            double a=2*Math.PI*i/180.0,x=d.dialA*Math.cos(a),y=d.dialB*Math.sin(a);
            float px=(float)(d.dialCx+x*ct-y*st),py=(float)(d.dialCy+x*st+y*ct);
            if(i==0)p.moveTo(px,py);else p.lineTo(px,py);
        }
        if(d.dialEdgeFitted)c.drawPath(p,ring);else{
            double[][] pts=new double[73][];for(int i=0;i<73;i++)pts[i]=ellipsePoint(d,-180+5*i,1.0);
            for(int i=0;i<72;i+=2)c.drawLine((float)pts[i][0],(float)pts[i][1],(float)pts[i+1][0],(float)pts[i+1][1],ring);
        }
    }

    private static void drawBatonDetail(Canvas c,Baton b){
        if(b.poly==null)return;double w=b.width();float lw=(float)Math.max(1.0,w/16.0);
        if(solid(b.attention,b.measured))poly(c,b.poly,true,stroke(ink(b.attention,b.measured),lw,235));
        else for(int i=0;i<b.poly.length;i++)dashed(c,b.poly[i],b.poly[(i+1)%b.poly.length],Math.max(3,w/4),stroke(WITHHELD,lw,210));
        if(b.tickBefore!=null&&b.tickAfter!=null){
            Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
            c.drawLine((float)b.tickBefore[0],(float)b.tickBefore[1],(float)b.tickAfter[0],(float)b.tickAfter[1],tick);
            Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,w/12.0);
            c.drawCircle((float)b.tickBefore[0],(float)b.tickBefore[1],dr,dot);c.drawCircle((float)b.tickAfter[0],(float)b.tickAfter[1],dr,dot);
        }
    }

    private static void drawRoundDetail(Canvas c,Round r){
        if(!r.found||!Double.isFinite(r.x)||!(r.radius>0))return;
        float lw=(float)Math.max(1.0,2*r.radius/22.0);
        if(solid(r.attention,r.measured))c.drawCircle((float)r.x,(float)r.y,(float)r.radius,stroke(ink(r.attention,r.measured),lw,235));
        else circleDashed(c,r.x,r.y,r.radius,stroke(WITHHELD,lw,210));
        if(r.tickBefore!=null&&r.tickAfter!=null){
            Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
            c.drawLine((float)r.tickBefore[0],(float)r.tickBefore[1],(float)r.tickAfter[0],(float)r.tickAfter[1],tick);
            Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,2*r.radius/16.0);
            c.drawCircle((float)r.tickBefore[0],(float)r.tickBefore[1],dr,dot);c.drawCircle((float)r.tickAfter[0],(float)r.tickAfter[1],dr,dot);
        }
    }

    private static String triangleValues(Drawing d){
        List<String> p=new ArrayList<>();
        p.add(d.rotationNote==null&&Double.isFinite(d.rotationDeg)?String.format(Locale.US,"rot %+.2f°",d.rotationDeg):"rot –");
        p.add(d.gapNote==null&&Double.isFinite(d.gapR)?String.format(Locale.US,"gap %.3fR",d.gapR):"gap –");
        p.add(d.centringNote==null&&Double.isFinite(d.centringW)?String.format(Locale.US,"centre %+.3fw",d.centringW):"centre –");
        return String.join(" · ",p);
    }

    private static String statusReason(Sub124060QcAnalyzer.Status s,String note){
        switch(s){
            case FOUND:return null;
            case HAND:return "hand in the way";
            case LOW_CONFIDENCE:return "low confidence";
            case WRONG_PLACE:return "not found where expected";
            default:return note!=null&&!note.isEmpty()?shortReason(note):"not found";
        }
    }
    private static String shortReason(String s){
        if(s==null)return null;String x=s.toLowerCase(Locale.US);
        if(x.contains("hand"))return "hand beside 12";
        if(x.contains("small"))return "too small";
        if(x.contains("resize")||x.contains("reduced")||x.contains("reprodu"))return "measurement unstable";
        if(x.contains("dial"))return "dial fit uncertain";
        if(x.contains("not found"))return "not found";
        return s.length()>28?s.substring(0,28)+"…":s;
    }

    private static String symbol(GmtHumanQcMath.Attention a){
        switch(a){case CLEAR:return "✓";case CHECK:return "!";case STRONG:return "!!";default:return "–";}
    }
    private static String label(GmtHumanQcMath.Attention a){
        switch(a){case CLEAR:return "nothing flagged";case CHECK:return "worth a look";case STRONG:return "check closely";default:return "not judged";}
    }

    private static double[] cloneP(double[] p){return p==null?null:p.clone();}
    private static double[] ellipsePoint(Drawing d,double clockDeg,double rho){
        double phi=Math.toRadians(clockDeg-90.0),u=d.dialA*Math.cos(phi)*rho,v=d.dialB*Math.sin(phi)*rho;
        double t=Math.toRadians(d.dialAngleDeg),ct=Math.cos(t),st=Math.sin(t);
        return new double[]{d.dialCx+ct*u-st*v,d.dialCy+st*u+ct*v};
    }
    private static double[] footOnLine(double x,double y,double[] a,double[] b){
        double dx=b[0]-a[0],dy=b[1]-a[1],q=dx*dx+dy*dy;if(q<1e-12)return new double[]{a[0],a[1]};
        double t=((x-a[0])*dx+(y-a[1])*dy)/q;return new double[]{a[0]+t*dx,a[1]+t*dy};
    }

    /** Measured-only badge: neutral blue disc with a centre dot; otherwise the GMT verdict badge. */
    private static void badge(Canvas c,double x,double y,float r,GmtHumanQcMath.Attention a,boolean measured){
        if(!neutral(a,measured)){badge(c,x,y,r,a);return;}
        c.drawCircle((float)x,(float)y,r*1.12f,fill(Color.rgb(12,16,22),210));
        c.drawCircle((float)x,(float)y,r,fill(MEASURED,245));
        c.drawCircle((float)x,(float)y,Math.max(1.5f,r*0.30f),fill(Color.WHITE,255));
    }

    /** Same symbol contract as the mature GMT overlay. */
    private static void badge(Canvas c,double x,double y,float r,GmtHumanQcMath.Attention a){
        int col=colour(a);
        c.drawCircle((float)x,(float)y,r*1.12f,fill(Color.rgb(12,16,22),210));
        c.drawCircle((float)x,(float)y,r,fill(col,245));
        int ink=a==GmtHumanQcMath.Attention.CHECK?Color.rgb(20,20,20):Color.WHITE;
        float sw=Math.max(1.5f,r*0.26f);Paint pen=stroke(ink,sw,255);
        switch(a){
            case CLEAR:{Path t=new Path();t.moveTo((float)(x-r*0.48),(float)(y+r*0.02));t.lineTo((float)(x-r*0.12),(float)(y+r*0.38));t.lineTo((float)(x+r*0.50),(float)(y-r*0.36));c.drawPath(t,pen);break;}
            case CHECK:bang(c,x,y,r,pen,sw,ink);break;
            case STRONG:bang(c,x-r*0.30,y,r,pen,sw,ink);bang(c,x+r*0.30,y,r,pen,sw,ink);break;
            default:c.drawLine((float)(x-r*0.45),(float)y,(float)(x+r*0.45),(float)y,pen);
        }
    }
    private static void bang(Canvas c,double x,double y,float r,Paint pen,float sw,int ink){
        c.drawLine((float)x,(float)(y-r*0.55),(float)x,(float)(y+r*0.12),pen);c.drawCircle((float)x,(float)(y+r*0.45),sw*0.62f,fill(ink,255));
    }

    private static void word(Canvas c,Drawing d,double x,double y,double clearance,String w,int col,float size){
        if(w==null||w.isEmpty())return;double dx=d.dialCx-x,dy=d.dialCy-y,dl=Math.hypot(dx,dy);
        if(!Double.isFinite(dl)||dl<1e-6){dx=0;dy=1;dl=1;}dx/=dl;dy/=dl;
        Paint t=fill(col,255);t.setTextSize(size);t.setFakeBoldText(true);float tw=t.measureText(w),th=size;
        double off=clearance+size*0.9,px=x+dx*(off+Math.abs(dx)*tw*0.5),py=y+dy*(off+Math.abs(dy)*th*0.5);
        c.drawRect((float)(px-tw/2-size*0.35),(float)(py-th*0.72),(float)(px+tw/2+size*0.35),(float)(py+th*0.42),fill(Color.rgb(12,16,22),205));
        c.drawText(w,(float)(px-tw/2),(float)(py+th*0.3),t);
    }

    private static void legend(Canvas c,int W,int H,double centreX,double dialBottom,double maxWidth,float badge){
        float r=Math.max(5f,badge*0.80f),size=r*1.9f,gap=size*0.8f;
        boolean v=Sub124060Calibration.VERDICTS_ENABLED;
        String[] words=v?new String[]{"nothing flagged","worth a look","check closely","not judged"}:new String[]{"measured (not judged)","not measured"};
        GmtHumanQcMath.Attention[] as=v?new GmtHumanQcMath.Attention[]{GmtHumanQcMath.Attention.CLEAR,GmtHumanQcMath.Attention.CHECK,GmtHumanQcMath.Attention.STRONG,GmtHumanQcMath.Attention.UNASSESSABLE}
                :new GmtHumanQcMath.Attention[]{GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE};
        boolean[] ms=v?new boolean[]{false,false,false,false}:new boolean[]{true,false};
        Paint t=fill(Color.WHITE,255);t.setTextSize(size);float total=0;
        for(String w:words)total+=2*r+size*0.35f+t.measureText(w)+gap;total-=gap;
        float lim=(float)Math.min(W*0.96,maxWidth);if(total>lim){float k=lim/total;r*=k;size*=k;gap*=k;t.setTextSize(size);total*=k;}
        float y=(float)Math.min(H-r*1.6f,Double.isFinite(dialBottom)&&dialBottom+r*2.4f<H?dialBottom+r*1.8f:H-r*1.6f);
        float x=(float)Math.max(r,Math.min(W-total-r,centreX-total/2));
        c.drawRect(x-r,y-r*1.45f,x+total+r,y+r*1.45f,fill(Color.rgb(12,16,22),185));
        for(int i=0;i<words.length;i++){badge(c,x+r,y,r,as[i],ms[i]);x+=2*r+size*0.35f;c.drawText(words[i],x,y+size*0.35f,t);x+=t.measureText(words[i])+gap;}
    }

    private static void banner(Canvas c,String text,double cx,double top,double maxWidth,int W,float badge){
        if(!Double.isFinite(cx))cx=W/2.0;Paint t=fill(Color.WHITE,255);t.setTextSize(badge*1.40f);t.setFakeBoldText(true);
        float lim=(float)Math.min(W*0.96,Double.isFinite(maxWidth)?maxWidth*0.90:W*0.90);
        while(t.measureText(text)>lim&&t.getTextSize()>8)t.setTextSize(t.getTextSize()-1);
        float tw=t.measureText(text),ts=t.getTextSize(),y=(float)Math.max(ts*1.4,Double.isFinite(top)?top+ts*1.6:ts*1.4);
        float x=(float)Math.max(ts*0.5,Math.min(W-tw-ts*0.5,cx-tw/2));
        c.drawRect(x-ts*0.5f,y-ts*1.05f,x+tw+ts*0.5f,y+ts*0.45f,fill(Color.rgb(12,16,22),215));c.drawText(text,x,y,t);
    }

    private static void poly(Canvas c,double[][] pts,boolean close,Paint p){
        if(pts==null||pts.length==0)return;Path path=new Path();path.moveTo((float)pts[0][0],(float)pts[0][1]);
        for(int i=1;i<pts.length;i++)path.lineTo((float)pts[i][0],(float)pts[i][1]);if(close)path.close();c.drawPath(path,p);
    }
    private static void dashed(Canvas c,double[] a,double[] b,double dash,Paint p){
        if(a==null||b==null)return;double len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<1e-6)return;dash=Math.max(2,dash);
        int n=(int)Math.max(1,Math.floor(len/dash));for(int i=0;i<n;i+=2){double t0=i/(double)n,t1=Math.min(1.0,(i+1)/(double)n);
            c.drawLine((float)(a[0]+(b[0]-a[0])*t0),(float)(a[1]+(b[1]-a[1])*t0),(float)(a[0]+(b[0]-a[0])*t1),(float)(a[1]+(b[1]-a[1])*t1),p);}
    }
    private static void circleDashed(Canvas c,double x,double y,double R,Paint p){
        for(int i=0;i<24;i+=2){double a0=2*Math.PI*i/24,a1=2*Math.PI*(i+1)/24;
            c.drawLine((float)(x+R*Math.cos(a0)),(float)(y+R*Math.sin(a0)),(float)(x+R*Math.cos(a1)),(float)(y+R*Math.sin(a1)),p);}
    }
    private static Paint stroke(int col,float w,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(col);p.setAlpha(alpha);return p;}
    private static Paint fill(int col,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.FILL);p.setColor(col);p.setAlpha(alpha);return p;}
}
