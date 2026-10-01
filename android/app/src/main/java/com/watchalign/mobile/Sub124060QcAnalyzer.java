package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Experimental Rolex Submariner 124060 analysis (checkpoint build). It finds and measures the dial
 * landmarks and reports MEASURED / NOT YET JUDGED values. It issues no QC verdict of any kind: there
 * are no 124060 tolerances yet (see docs/124060_CHECKPOINT.md).
 *
 * Reused unchanged from the GMT path: the dark-dial seed (GmtDialSeedAnalyzer), the dial-edge fit
 * (DialEdgeFitter), the baton detector (GmtSixLandmarkAnalyzer, with Position.THREE), the round-marker
 * detector (GmtRoundMarkerAnalyzer; its seed priors, 0.816 R ring and 0.088 R surround, match the
 * 124060 measurements of the research study: ring 0.817 R, fitted radius about 0.083-0.090 R) and the
 * hand checks (HandIntrusion). Not used: GmtHumanQcAnalyzerV2, the GMT pose policy, GMT thresholds,
 * GmtDialLayout (date side) and the GMT 12-triangle detector with its 44.3 deg apex gate. The 12 comes
 * from SubTwelveTriangle (frozen research v2, single photo).
 *
 * Fail-closed rules (each only withholds, none can produce a verdict):
 *  - no dial geometry unless the dial edge was fitted (automatically or from a hand alignment);
 *    a hand alignment whose edge could not be re-fitted is used for finding landmarks only;
 *  - 12 measurements need an automatic or hand-seeded edge fit that is reproduced on the photo reduced
 *    to 94% and 88% (centre within 0.01 R, radius within 2%: the research dial-consistency rule);
 *  - and a 12 triangle at least MIN_TRIANGLE_PX wide, with no hand at 12, found as the same outline at
 *    94% and 88%; gap and centring also need the outer (surround) outline and a minute track.
 */
final class Sub124060QcAnalyzer {
    static final double[] RESIZE_SCALES = GmtTwelveLandmarkAnalyzer.STABILITY_SCALES;   // 0.94, 0.88
    /** Research dial-consistency rule (subresearch/triangle.py dial_usable). */
    static final double DIAL_SAME_CENTRE_R = 0.01, DIAL_SAME_RADIUS_REL = 0.02;
    /** Same physical outline across the resize check (research consensus grouping). */
    static final double SAME_OUTLINE_CENTRE_R = 0.02, SAME_OUTLINE_WIDTH_REL = 0.12;
    static final double MIN_TRIANGLE_PX = GmtHumanQcAnalyzerV2.MIN_TRIANGLE_PX;
    /** A baton must sit within this angle of where the layout puts it relative to the 12 (as for GMT). */
    static final double BATON_MAX_ANGLE_DEG = 8.0;

    enum DialSource {
        AUTO_EDGE_FIT("automatic edge fit"),
        MANUAL_EDGE_FIT("hand-aligned, dial edge re-fitted"),
        MANUAL_CIRCLE("hand-aligned circle (dial edge could not be re-fitted)"),
        UNAVAILABLE("not assessable");
        final String words;
        DialSource(String w){words=w;}
    }

    enum Status { FOUND, LOW_CONFIDENCE, HAND, WRONG_PLACE, NOT_FOUND }

    static final class Baton {
        final GmtSixLandmarkAnalyzer.Position position;
        Status status=Status.NOT_FOUND;String note="";
        GmtSixLandmarkAnalyzer.Result result;
        Baton(GmtSixLandmarkAnalyzer.Position p){position=p;}
    }

    static final class Round {
        final GmtRoundMarkerAnalyzer.Marker marker;
        Status status;String note;
        Round(GmtRoundMarkerAnalyzer.Marker m,Status s,String n){marker=m;status=s;note=n;}
    }

    static final class Result {
        DialSource dialSource=DialSource.UNAVAILABLE;
        String dialReason="";
        double seedX=Double.NaN,seedY=Double.NaN,seedR=Double.NaN,seedQuality=Double.NaN;
        DialEdgeEllipseFit.Fit edge;
        GmtRoundMarkerAnalyzer.DialFrame frame;
        /** Edge fit reproduced at 94% and 88% (null: not checked). */
        Boolean dialReproducible;
        String dialReproNote="";

        SubTwelveTriangle.Result triangles;
        SubTwelveTriangle.Cand triangle;
        String triangleReason="";
        /** Why the 12 measurements are withheld, or null when they are reported. */
        String twelveWithheld;
        boolean handAtTwelve,tooSmall,lumeOutline;
        Boolean triangleResizeStable;String triangleResizeNote="";
        double[] tick59,tick60,tick01;

        double rotationDeg=Double.NaN,gapR=Double.NaN,centringW=Double.NaN;
        String rotationWithheld,gapWithheld,centringWithheld;

        final List<Baton> batons=new ArrayList<>();
        final List<Round> rounds=new ArrayList<>();
        Sub124060Overlay.Drawing drawing=new Sub124060Overlay.Drawing();

        boolean dialAssessable(){return dialSource!=DialSource.UNAVAILABLE;}
        boolean needsManual(){return dialSource==DialSource.UNAVAILABLE||triangle==null;}
        int batonsFound(){int n=0;for(Baton b:batons)if(b.status!=Status.NOT_FOUND&&b.status!=Status.WRONG_PLACE)n++;return n;}
        int roundsFound(){int n=0;for(Round r:rounds)if(r.status!=Status.NOT_FOUND)n++;return n;}
    }

    private Sub124060QcAnalyzer(){}

    static boolean supports(String modelRef){return Sub124060Layout.supports(modelRef);}

    /**
     * @param manual hand-aligned dial (12/6 dial-edge taps), used instead of the automatic seed, or null
     */
    static Result analyse(Bitmap watch,PerspectiveGmtOverlay.DialSeed manual){
        Result res=new Result();
        if(watch==null){res.dialReason="watch image missing";return res;}
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            analyseBgr(src,manual,res);
        }catch(Throwable t){
            res.dialReason="analysis failed closed: "+t.getClass().getSimpleName();
            res.dialSource=DialSource.UNAVAILABLE;res.triangle=null;
        }finally{src.release();}
        res.drawing=Sub124060Overlay.Drawing.of(res);
        return res;
    }

    // ------------------------------------------------------------------------------------------ dial
    static void analyseBgr(Mat src,PerspectiveGmtOverlay.DialSeed manual,Result res){
        double cx,cy,r;
        if(manual!=null){
            res.seedX=manual.x;res.seedY=manual.y;res.seedR=manual.r;res.seedQuality=manual.quality;
        }else{
            GmtDialSeedAnalyzer.Result seed=GmtDialSeedAnalyzer.analyse(src);
            if(!seed.valid){res.dialReason="no dial found in the photo ("+seed.reason+")";return;}
            res.seedX=seed.x;res.seedY=seed.y;res.seedR=seed.r;res.seedQuality=seed.quality;
            if(!(seed.r>20)||seed.quality<0.45){res.dialReason="dial location confidence is too low";return;}
        }
        DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(src,res.seedX,res.seedY,res.seedR);
        if(edge!=null){
            res.edge=edge;
            res.dialSource=manual!=null?DialSource.MANUAL_EDGE_FIT:DialSource.AUTO_EDGE_FIT;
            res.frame=new GmtRoundMarkerAnalyzer.DialFrame(edge.cx,edge.cy,edge.axisA,edge.axisB,edge.angleDeg);
            checkDialReproducible(src,manual,res);
        }else if(manual!=null){
            res.dialSource=DialSource.MANUAL_CIRCLE;
            res.frame=GmtRoundMarkerAnalyzer.DialFrame.circle(manual.x,manual.y,manual.r);
            res.dialReproNote="no edge fit to re-check";
        }else{
            res.dialReason="the dial edge could not be fitted automatically";
            return;
        }
        cx=res.frame.cx;cy=res.frame.cy;r=res.frame.r;

        Mat g8=new Mat();
        try{
            Imgproc.cvtColor(src,g8,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(g8,g8,new org.opencv.core.Size(5,5),1.2);
            DialEdgeEllipseFit.Intensity img=GmtHumanQcAnalyzerV2.intensityOf(g8);
            int w=g8.cols(),h=g8.rows();
            twelve(src,img,w,h,res);
            double twelveClock=res.tick60!=null?SubTwelveTriangle.clock(cx,cy,res.tick60[0],res.tick60[1]):Double.NaN;
            for(GmtSixLandmarkAnalyzer.Position p:Sub124060Layout.BATONS)res.batons.add(baton(src,img,w,h,cx,cy,r,p,twelveClock));
            rounds(src,img,w,h,res);
        }finally{g8.release();}
    }

    /** The edge fit is repeated on the photo reduced to 94% and 88%; it must land on the same dial. */
    static void checkDialReproducible(Mat src,PerspectiveGmtOverlay.DialSeed manual,Result res){
        StringBuilder note=new StringBuilder();boolean all=true;
        for(double k:RESIZE_SCALES){
            Mat m=new Mat();
            try{
                Imgproc.resize(src,m,new org.opencv.core.Size(Math.round(src.cols()*k),Math.round(src.rows()*k)),0,0,Imgproc.INTER_LINEAR);
                DialEdgeEllipseFit.Fit f=null;
                if(manual!=null)f=DialEdgeFitter.fitBgr(m,manual.x*k,manual.y*k,manual.r*k);
                else{
                    GmtDialSeedAnalyzer.Result s=GmtDialSeedAnalyzer.analyse(m);
                    if(s.valid&&s.r>20&&s.quality>=0.45)f=DialEdgeFitter.fitBgr(m,s.x,s.y,s.r);
                }
                boolean same=f!=null&&sameDial(res.edge.cx,res.edge.cy,res.edge.meanRadius(),f.cx/k,f.cy/k,f.meanRadius()/k);
                all&=same;
                note.append(String.format(Locale.US,"%s%.0f%%: %s",note.length()>0?", ":"",100*k,
                        f==null?"no edge fit":same?"same dial":String.format(Locale.US,"different dial (centre moved %.1f px, radius %+.1f%%)",
                                Math.hypot(f.cx/k-res.edge.cx,f.cy/k-res.edge.cy),100*(f.meanRadius()/k/res.edge.meanRadius()-1))));
            }finally{m.release();}
        }
        res.dialReproducible=all;res.dialReproNote=note.toString();
    }

    static boolean sameDial(double cx,double cy,double r,double cx2,double cy2,double r2){
        if(!(r>0))return false;
        return Math.hypot(cx2-cx,cy2-cy)<=DIAL_SAME_CENTRE_R*r&&Math.abs(r2/r-1)<=DIAL_SAME_RADIUS_REL;
    }

    // ------------------------------------------------------------------------------------------ 12 triangle
    static void twelve(Mat src,DialEdgeEllipseFit.Intensity img,int w,int h,Result res){
        GmtRoundMarkerAnalyzer.DialFrame f=res.frame;
        SubTwelveTriangle.Result tr=SubTwelveTriangle.detect(src,f);
        res.triangles=tr;
        SubTwelveTriangle.Cand c=tr.best();
        if(c==null){
            res.triangleReason=!tr.reason.isEmpty()?tr.reason
                    :tr.cands.isEmpty()?"no triangle-shaped outline near 12"
                    :"no candidate passed the 12-triangle plausibility checks (best: "+tr.cands.get(0).implausible+")";
            res.twelveWithheld="12 triangle not found";
            res.rotationWithheld=res.gapWithheld=res.centringWithheld=res.twelveWithheld;
            return;
        }
        res.triangle=c;
        res.lumeOutline="inner".equals(c.outline);
        double cx=f.cx,cy=f.cy;
        res.tick60=Double.isFinite(c.trackR)&&Double.isFinite(c.tickAngle)?SubTwelveTriangle.polar(cx,cy,c.trackR,c.tickAngle):c.tickRaw60;
        res.tick59=c.ref59!=null?c.ref59:c.tickRaw59;
        res.tick01=c.ref01!=null?c.ref01:c.tickRaw01;

        double widthPx=Math.hypot(c.R[0]-c.L[0],c.R[1]-c.L[1]);
        if(widthPx<MIN_TRIANGLE_PX){
            res.tooSmall=true;
            res.twelveWithheld=String.format(Locale.US,"the 12 triangle is only %.0f px wide in this photo (minimum %.0f)",Math.floor(widthPx),MIN_TRIANGLE_PX);
        }else if(res.tick60!=null&&res.tick59!=null&&res.tick01!=null){
            HandIntrusion.Result hi=HandIntrusion.measure(img,w,h,cx,cy,f.r,new double[][]{c.L,c.R,c.T},res.tick59,res.tick60,res.tick01);
            if(hi.present){res.handAtTwelve=true;res.twelveWithheld="a hand is at the 12 triangle";}
        }
        if(res.twelveWithheld==null)checkTriangleResize(src,res);
        if(res.twelveWithheld==null&&res.dialSource==DialSource.MANUAL_CIRCLE)
            res.twelveWithheld="the dial is a hand-aligned circle whose edge could not be re-fitted";
        if(res.twelveWithheld==null&&Boolean.FALSE.equals(res.dialReproducible))
            res.twelveWithheld="the dial-edge fit changes when the photo is reduced by 6% or 12%";

        if(res.twelveWithheld!=null){
            res.rotationWithheld=res.gapWithheld=res.centringWithheld=res.twelveWithheld;
            return;
        }
        if(Double.isFinite(c.tickAngle)&&Double.isFinite(c.rotationDeg))res.rotationDeg=c.rotationDeg;
        else res.rotationWithheld="the 60-minute tick was not located";
        if(res.lumeOutline){
            res.gapWithheld=res.centringWithheld="only the inner (lume) outline of the triangle was found";
        }else if(!Double.isFinite(c.gapR)||!Double.isFinite(c.centring)){
            res.gapWithheld=res.centringWithheld="the minute track next to the 12 was not located";
        }else{
            res.gapR=c.gapR;res.centringW=c.centring;
        }
    }

    /** The selected outline must be selected again, as the same outline, at 94% and 88%. */
    static void checkTriangleResize(Mat src,Result res){
        SubTwelveTriangle.Cand c=res.triangle;GmtRoundMarkerAnalyzer.DialFrame f=res.frame;
        StringBuilder note=new StringBuilder();boolean all=true;
        for(double k:RESIZE_SCALES){
            Mat m=new Mat();
            try{
                Imgproc.resize(src,m,new org.opencv.core.Size(Math.round(src.cols()*k),Math.round(src.rows()*k)),0,0,Imgproc.INTER_LINEAR);
                SubTwelveTriangle.Cand b=SubTwelveTriangle.detect(m,f.scaled(k)).best();
                boolean same=b!=null&&sameOutline(c.cx,c.cy,c.widthR,b.cx/k,b.cy/k,b.widthR,f.r);
                all&=same;
                note.append(String.format(Locale.US,"%s%.0f%%: %s",note.length()>0?", ":"",100*k,b==null?"not found":same?"same outline":"a different outline"));
            }finally{m.release();}
        }
        res.triangleResizeStable=all;res.triangleResizeNote=note.toString();
        if(!all)res.twelveWithheld="the 12 triangle is not found as the same outline when the photo is reduced by 6% and 12%";
    }

    static boolean sameOutline(double x,double y,double widthR,double x2,double y2,double widthR2,double dialR){
        return Math.hypot(x2-x,y2-y)<=SAME_OUTLINE_CENTRE_R*dialR&&widthR>0&&Math.abs(widthR2/widthR-1)<=SAME_OUTLINE_WIDTH_REL;
    }

    // ------------------------------------------------------------------------------------------ batons
    static Baton baton(Mat src,DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r,
                       GmtSixLandmarkAnalyzer.Position pos,double twelveClockDeg){
        Baton o=new Baton(pos);
        GmtSixLandmarkAnalyzer.Result b=GmtSixLandmarkAnalyzer.analyse(src,cx,cy,r,pos,Double.NaN);
        o.result=b;
        if(!b.valid){o.status=Status.NOT_FOUND;o.note=b.reason;return o;}
        GmtSixLandmarkAnalyzer.Geometry g=b.geometry;
        if(Double.isFinite(twelveClockDeg)&&g!=null&&g.tickAfter!=null&&g.tickBefore!=null){
            double[] m={(g.tickAfter[0]+g.tickBefore[0])/2,(g.tickAfter[1]+g.tickBefore[1])/2};
            double d=SubTwelveTriangle.wrap180(SubTwelveTriangle.clock(cx,cy,m[0],m[1])-twelveClockDeg-pos.angleFromTwelveDeg);
            if(Math.abs(d)>BATON_MAX_ANGLE_DEG){
                o.status=Status.WRONG_PLACE;
                o.note=String.format(Locale.US,"the outline found is %.0f° from where the %s baton sits relative to the 12 (photo turned?)",Math.abs(d),pos.label);
                return o;
            }
        }
        if(g!=null&&g.tickAfter!=null&&g.tickCentre!=null&&g.tickBefore!=null){
            HandIntrusion.Result hi=HandIntrusion.measure(img,w,h,cx,cy,r,g.polygon(),g.tickAfter,g.tickCentre,g.tickBefore);
            if(hi.present){o.status=Status.HAND;o.note="a hand is next to it";return o;}
        }
        if(!b.stable){o.status=Status.LOW_CONFIDENCE;o.note=b.lowReason;return o;}
        if(!Double.isFinite(twelveClockDeg)){o.status=Status.LOW_CONFIDENCE;o.note="the 12 was not found, so the dial orientation is unknown";return o;}
        o.status=Status.FOUND;
        return o;
    }

    // ------------------------------------------------------------------------------------------ round markers
    static void rounds(Mat src,DialEdgeEllipseFit.Intensity img,int w,int h,Result res){
        List<GmtRoundMarkerAnalyzer.Marker> ms=GmtRoundMarkerAnalyzer.analyse(src,res.frame,res.tick60);
        double cx=res.frame.cx,cy=res.frame.cy;
        for(GmtRoundMarkerAnalyzer.Marker m:ms){
            if(!m.found){res.rounds.add(new Round(m,Status.NOT_FOUND,m.reason));continue;}
            // Hand check as on the GMT path (GmtHumanQcAnalyzerV2.judgeRound), without any verdict.
            boolean hand=false;
            if(m.tickBefore!=null&&m.tickAfter!=null){
                double[] beside={0};
                m.ringBright=HandIntrusion.ringBrightFraction(img,w,h,m.x,m.y,m.radiusPx,m.expectedRadiusPx*1.05,m.tickBefore,m.tickAfter,cx,cy,beside);
                m.handBeside=beside[0]>0;
            }
            m.coloured=GmtRoundMarkerAnalyzer.colouredFraction(src,m,cx,cy);
            if((m.ringBright>HandIntrusion.MAX_RING_BRIGHT_FRACTION&&!m.handBeside)||m.coloured>GmtRoundMarkerAnalyzer.MAX_COLOURED_FRACTION)hand=true;
            m.hand=hand;
            m.attention=GmtHumanQcMath.Attention.UNASSESSABLE;   // never judged on the 124060 path
            if(hand)res.rounds.add(new Round(m,Status.HAND,"a hand is over or next to it"));
            else if(!m.stable)res.rounds.add(new Round(m,Status.LOW_CONFIDENCE,m.lowReason));
            else res.rounds.add(new Round(m,Status.FOUND,""));
        }
    }

    // ------------------------------------------------------------------------------------------ core entry
    /** The 124060 route of WatchAlignCoreV13.analyse: same inputs, same result type, own analysis. */
    static WatchAlignCoreV13.AnalysisResult analyseForCore(Bitmap watch,FullResSource full,PerspectiveGmtOverlay.DialSeed manual){
        GmtDialCrop.Crop crop=manual==null&&full!=null?GmtDialCrop.make(watch,full):null;
        Result res=crop!=null?analyse(crop.bitmap,null):analyse(watch,manual);
        Bitmap closeUp=null;
        if(crop!=null&&res.drawing.hasTriangle())
            closeUp=Sub124060Overlay.closeUp(crop.bitmap,Sub124060Overlay.render(crop.bitmap,res.drawing),res.drawing,540);
        if(crop!=null)res.drawing.mapTo(crop);
        Bitmap overlay=res.drawing.hasAnything()?Sub124060Overlay.render(watch,res.drawing):null;
        if(closeUp==null&&res.drawing.hasTriangle())closeUp=Sub124060Overlay.closeUp(watch,overlay,res.drawing,540);
        String report="Rolex Submariner 124060 (experimental) · Watch Align Core "+WatchAlignCoreV13.CORE_VERSION+"\n\n"
                +Sub124060Summary.build(res)
                +"\n\nDETAILS\n"+Sub124060Summary.details(res,crop!=null?crop.describe():null);
        WatchAlignCoreV13.AnalysisResult out=new WatchAlignCoreV13.AnalysisResult(watch,null,null,overlay,null,report,Double.NaN,
                res.triangle!=null?1.0:0.0,res.triangle!=null);
        out.twelveCloseUp=closeUp;
        out.submariner=true;
        out.dialNeedsManual=res.needsManual();
        out.sub124060=res;
        return out;
    }
}
