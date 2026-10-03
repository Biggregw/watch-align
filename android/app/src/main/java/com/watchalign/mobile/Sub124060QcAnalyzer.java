package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Experimental Rolex Submariner 124060 analysis. It finds and measures the dial landmarks and
 * reports MEASURED / NOT YET JUDGED values. It issues no QC verdict of any kind: there are no
 * 124060 tolerances yet (see docs/124060_CHECKPOINT.md).
 *
 * Reused unchanged from the GMT path: the dark-dial seed (GmtDialSeedAnalyzer), the dial-edge fit
 * (DialEdgeFitter), the baton detector (GmtSixLandmarkAnalyzer, with Position.THREE), the round-marker
 * detector (GmtRoundMarkerAnalyzer; its seed priors, 0.816 R ring and 0.088 R surround, match the
 * 124060 measurements of the research study: ring 0.817 R, fitted radius about 0.083-0.090 R), the
 * hand checks (HandIntrusion), marker-layout pose diagnostic and resize/re-measure reliability
 * pattern. Not used: GMT QC thresholds, GmtDialLayout (date side) or the GMT 12-triangle detector
 * with its 44.3 deg apex gate. The 12 comes from SubTwelveTriangle (frozen research v2).
 *
 * Fail-closed rules (each only withholds, none can produce a verdict):
 *  - no dial geometry unless the dial edge was fitted (automatically or from a hand alignment);
 *    a hand alignment whose edge could not be re-fitted is used for finding landmarks only;
 *  - 12 measurements need an automatic or hand-seeded edge fit that is reproduced on the photo reduced
 *    to 94% and 88% (centre within 0.01 R, radius within 2%: the research dial-consistency rule);
 *  - and a 12 triangle at least MIN_TRIANGLE_PX wide, with no hand at 12, found as the same outline at
 *    94% and 88%; each reported fine measurement must itself also repeat to about one source pixel;
 *  - shared baton and round-marker resize checks are run and reported diagnostically. Until 124060
 *    marker tolerances are calibrated, their numeric resize movement does not downgrade an otherwise
 *    stable marker. A changed round-marker physical edge only suppresses the size comparison.
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
        /** Registered reason code (CoreReasons / Sub12Reasons), set in the same branch as dialReason. */
        String dialReasonCode;
        double seedX=Double.NaN,seedY=Double.NaN,seedR=Double.NaN,seedQuality=Double.NaN;
        DialEdgeEllipseFit.Fit edge;
        GmtRoundMarkerAnalyzer.DialFrame frame;
        /** Edge fit reproduced at 94% and 88% (null: not checked). */
        Boolean dialReproducible;
        String dialReproNote="";

        SubTwelveTriangle.Result triangles;
        SubTwelveTriangle.Cand triangle;
        String triangleReason="";
        /** Why all 12 measurements are withheld, or null when per-metric checks may report them. */
        String twelveWithheld;
        /** Registered reason code set in the same branch as twelveWithheld. */
        String twelveWithheldCode;
        boolean handAtTwelve,tooSmall,lumeOutline;
        Boolean triangleResizeStable;String triangleResizeNote="";
        double[] tick59,tick60,tick01;

        /** Numeric resize repeatability, independent for each 12 measurement. */
        Boolean rotationResizeStable,gapResizeStable,centringResizeStable;
        double rotationMin=Double.NaN,rotationMax=Double.NaN,rotationShiftPx=Double.NaN;
        double gapMin=Double.NaN,gapMax=Double.NaN,gapShiftPx=Double.NaN;
        double centringMin=Double.NaN,centringMax=Double.NaN,centringShiftPx=Double.NaN;

        double rotationDeg=Double.NaN,gapR=Double.NaN,centringW=Double.NaN;
        String rotationWithheld,gapWithheld,centringWithheld;
        /** Registered reason codes set in the same branch as the three withheld messages. */
        String rotationWithheldCode,gapWithheldCode,centringWithheldCode;

        final List<Baton> batons=new ArrayList<>();
        final List<Round> rounds=new ArrayList<>();
        /** GMT-developed round-marker layout pose estimator, diagnostic only on 124060. */
        GmtMarkerPose.Result markerPose;
        Sub124060Overlay.Drawing drawing=new Sub124060Overlay.Drawing();

        boolean dialAssessable(){return dialSource!=DialSource.UNAVAILABLE;}
        boolean needsManual(){return dialSource==DialSource.UNAVAILABLE||triangle==null;}
        int batonsFound(){int n=0;for(Baton b:batons)if(b.status!=Status.NOT_FOUND&&b.status!=Status.WRONG_PLACE)n++;return n;}
        int roundsFound(){int n=0;for(Round r:rounds)if(r.status!=Status.NOT_FOUND)n++;return n;}
    }

    private Sub124060QcAnalyzer(){}

    static boolean supports(String modelRef){return Sub124060Layout.supports(modelRef);}

    /** @param manual hand-aligned dial (12/6 dial-edge taps), used instead of the automatic seed, or null */
    static Result analyse(Bitmap watch,PerspectiveGmtOverlay.DialSeed manual){
        Result res=new Result();
        if(watch==null){res.dialReason="watch image missing";res.dialReasonCode=CoreReasons.IMAGE_UNREADABLE;return res;}
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            analyseBgr(src,manual,res);
        }catch(Throwable t){
            res.dialReason="analysis failed closed: "+t.getClass().getSimpleName();res.dialReasonCode=CoreReasons.ANALYSIS_EXCEPTION;
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
            if(!seed.valid){res.dialReason="no dial found in the photo ("+seed.reason+")";res.dialReasonCode=Sub12Reasons.DIAL_NOT_FOUND;return;}
            res.seedX=seed.x;res.seedY=seed.y;res.seedR=seed.r;res.seedQuality=seed.quality;
            if(!(seed.r>20)||seed.quality<0.45){res.dialReason="dial location confidence is too low";res.dialReasonCode=Sub12Reasons.DIAL_SEED_LOW_CONFIDENCE;return;}
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
            res.dialReason="the dial edge could not be fitted automatically";res.dialReasonCode=Sub12Reasons.DIAL_EDGE_FIT_FAILED;
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
            res.twelveWithheld="12 triangle not found";res.twelveWithheldCode=Sub12Reasons.TRIANGLE_NOT_FOUND;
            res.rotationWithheld=res.gapWithheld=res.centringWithheld=res.twelveWithheld;
            res.rotationWithheldCode=res.gapWithheldCode=res.centringWithheldCode=res.twelveWithheldCode;
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
            res.twelveWithheldCode=Sub12Reasons.TRIANGLE_TOO_SMALL;
        }else if(res.tick60!=null&&res.tick59!=null&&res.tick01!=null){
            HandIntrusion.Result hi=HandIntrusion.measure(img,w,h,cx,cy,f.r,new double[][]{c.L,c.R,c.T},res.tick59,res.tick60,res.tick01);
            if(hi.present){res.handAtTwelve=true;res.twelveWithheld="a hand is touching or right beside the 12 triangle, which can shift its measured outline";res.twelveWithheldCode=Sub12Reasons.HAND_AT_TWELVE;}
        }
        if(res.twelveWithheld==null)checkTriangleResize(src,res);
        if(res.twelveWithheld==null&&res.dialSource==DialSource.MANUAL_CIRCLE){
            res.twelveWithheld="the dial is a hand-aligned circle whose edge could not be re-fitted";res.twelveWithheldCode=Sub12Reasons.DIAL_MANUAL_CIRCLE;}
        if(res.twelveWithheld==null&&Boolean.FALSE.equals(res.dialReproducible)){
            res.twelveWithheld="the dial-edge fit changes when the photo is reduced by 6% or 12%";res.twelveWithheldCode=Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE;}

        if(res.twelveWithheld!=null){
            res.rotationWithheld=res.gapWithheld=res.centringWithheld=res.twelveWithheld;
            res.rotationWithheldCode=res.gapWithheldCode=res.centringWithheldCode=res.twelveWithheldCode;
            return;
        }
        String rotation=rotationFailure(c);
        if(rotation==null){
            if(Boolean.FALSE.equals(res.rotationResizeStable)){res.rotationWithheld=resizeReason("rotation",res.rotationShiftPx);res.rotationWithheldCode=Sub12Reasons.ROTATION_RESIZE_UNSTABLE;}
            else res.rotationDeg=c.rotationDeg;
        }else{res.rotationWithheld="the 60-minute tick was not located";res.rotationWithheldCode=rotation;}

        String track=minuteTrackFailure(c,res.lumeOutline);
        if(track!=null){
            res.gapWithheld=res.centringWithheld=Sub12Reasons.LUME_OUTLINE_ONLY.equals(track)
                    ?"only the inner (lume) outline of the triangle was found":"the minute track next to the 12 was not located";
            res.gapWithheldCode=res.centringWithheldCode=track;
        }else{
            if(Boolean.FALSE.equals(res.gapResizeStable)){res.gapWithheld=resizeReason("gap",res.gapShiftPx);res.gapWithheldCode=Sub12Reasons.GAP_RESIZE_UNSTABLE;}
            else res.gapR=c.gapR;
            if(Boolean.FALSE.equals(res.centringResizeStable)){res.centringWithheld=resizeReason("centring",res.centringShiftPx);res.centringWithheldCode=Sub12Reasons.CENTRING_RESIZE_UNSTABLE;}
            else res.centringW=c.centring;
        }
    }

    /**
     * Why the 12 rotation is not measured on this triangle candidate (null: it is). This is the raw
     * rotation's only definition: the analyser gates on it and Sub124060Calibration.decide reads it.
     */
    static String rotationFailure(SubTwelveTriangle.Cand c){
        if(c==null)return Sub12Reasons.TRIANGLE_NOT_FOUND;
        return Double.isFinite(c.tickAngle)&&Double.isFinite(c.rotationDeg)?null:Sub12Reasons.MINUTE_TICK_NOT_FOUND;
    }

    /**
     * Why the 12 gap and centring are not measured on this candidate (null: both are). Only the outer
     * outline is the gap edge: an inner (lume) outline measures a different edge, so it yields no gap.
     */
    static String minuteTrackFailure(SubTwelveTriangle.Cand c,boolean lumeOutline){
        if(c==null)return Sub12Reasons.TRIANGLE_NOT_FOUND;
        if(lumeOutline)return Sub12Reasons.LUME_OUTLINE_ONLY;
        return Double.isFinite(c.gapR)&&Double.isFinite(c.centring)?null:Sub12Reasons.MINUTE_TRACK_NOT_FOUND;
    }

    /**
     * The selected outline must be selected again at 94% and 88%, and the fine measurements are
     * independently checked for approximately one-pixel repeatability. This is the mature GMT
     * lesson applied without any GMT QC threshold.
     */
    static void checkTriangleResize(Mat src,Result res){
        SubTwelveTriangle.Cand c=res.triangle;GmtRoundMarkerAnalyzer.DialFrame f=res.frame;
        StringBuilder note=new StringBuilder();boolean all=true;
        boolean rotOk=Double.isFinite(c.rotationDeg),gapOk=Double.isFinite(c.gapR),cenOk=Double.isFinite(c.centring);
        double rMin=c.rotationDeg,rMax=c.rotationDeg,gMin=c.gapR,gMax=c.gapR,cMin=c.centring,cMax=c.centring;
        for(double k:RESIZE_SCALES){
            Mat m=new Mat();
            try{
                Imgproc.resize(src,m,new org.opencv.core.Size(Math.round(src.cols()*k),Math.round(src.rows()*k)),0,0,Imgproc.INTER_LINEAR);
                SubTwelveTriangle.Cand b=SubTwelveTriangle.detect(m,f.scaled(k)).best();
                boolean same=b!=null&&sameOutline(c.cx,c.cy,c.widthR,b.cx/k,b.cy/k,b.widthR,f.r);
                all&=same;
                note.append(String.format(Locale.US,"%s%.0f%%: %s",note.length()>0?", ":"",100*k,b==null?"not found":same?"same outline":"a different outline"));
                if(!same)continue;
                if(rotOk&&Double.isFinite(b.rotationDeg)){rMin=Math.min(rMin,b.rotationDeg);rMax=Math.max(rMax,b.rotationDeg);}else rotOk=false;
                if(gapOk&&Double.isFinite(b.gapR)){gMin=Math.min(gMin,b.gapR);gMax=Math.max(gMax,b.gapR);}else gapOk=false;
                if(cenOk&&Double.isFinite(b.centring)){cMin=Math.min(cMin,b.centring);cMax=Math.max(cMax,b.centring);}else cenOk=false;
            }finally{m.release();}
        }
        res.triangleResizeStable=all;res.triangleResizeNote=note.toString();
        if(!all){
            res.twelveWithheld="the 12 triangle is not found as the same outline when the photo is reduced by 6% and 12%";
            res.twelveWithheldCode=Sub12Reasons.TRIANGLE_RESIZE_OUTLINE_CHANGED;
            return;
        }

        double widthPx=c.widthR*f.r,heightPx=c.heightR*f.r;
        if(rotOk){
            res.rotationMin=rMin;res.rotationMax=rMax;
            res.rotationShiftPx=MeasurementRepeatability.angleShiftPx(rMin,rMax,heightPx);
            res.rotationResizeStable=MeasurementRepeatability.stable(res.rotationShiftPx);
        }else res.rotationResizeStable=false;
        if(gapOk){
            res.gapMin=gMin;res.gapMax=gMax;
            res.gapShiftPx=MeasurementRepeatability.radiusShiftPx(gMin,gMax,f.r);
            res.gapResizeStable=MeasurementRepeatability.stable(res.gapShiftPx);
        }else res.gapResizeStable=false;
        if(cenOk){
            res.centringMin=cMin;res.centringMax=cMax;
            res.centringShiftPx=MeasurementRepeatability.widthShiftPx(cMin,cMax,widthPx);
            res.centringResizeStable=MeasurementRepeatability.stable(res.centringShiftPx);
        }else res.centringResizeStable=false;
    }

    static String resizeReason(String what,double shiftPx){
        return Double.isFinite(shiftPx)
                ?String.format(Locale.US,"the %s measurement moves by %.1f px when the photo is reduced by 6%% and 12%%",what,shiftPx)
                :"the "+what+" measurement is not reproduced at both resize scales";
    }

    static boolean sameOutline(double x,double y,double widthR,double x2,double y2,double widthR2,double dialR){
        return Math.hypot(x2-x,y2-y)<=SAME_OUTLINE_CENTRE_R*dialR&&widthR>0&&Math.abs(widthR2/widthR-1)<=SAME_OUTLINE_WIDTH_REL;
    }

    // ------------------------------------------------------------------------------------------ batons
    static Baton baton(Mat src,DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r,
                       GmtSixLandmarkAnalyzer.Position pos,double twelveClockDeg){
        Baton o=new Baton(pos);
        GmtSixLandmarkAnalyzer.Result b;
        GmtSixLandmarkAnalyzer.MIXED_EDGE_PASS.set(Boolean.TRUE);
        try{b=GmtSixLandmarkAnalyzer.analyse(src,cx,cy,r,pos,Double.NaN);}
        finally{GmtSixLandmarkAnalyzer.MIXED_EDGE_PASS.set(Boolean.FALSE);}
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
        if(g!=null&&handAlongBaton(img,w,h,g)){o.status=Status.HAND;o.note="a hand lies across it";return o;}
        if(!b.stable){o.status=Status.LOW_CONFIDENCE;o.note=b.lowReason;return o;}
        if(!Double.isFinite(twelveClockDeg)){o.status=Status.LOW_CONFIDENCE;o.note="the 12 was not found, so the dial orientation is unknown";return o;}

        // Reuse the mature GMT re-measurement machinery, but keep it diagnostic until the 124060
        // has its own marker tolerances. GMT's resampleStable() contains GMT verdict levels, so using
        // it here to change status would silently import GMT calibration into the Sub path.
        GmtSixLandmarkAnalyzer.MIXED_EDGE_PASS.set(Boolean.TRUE);
        try{GmtSixLandmarkAnalyzer.measureStability(src,cx,cy,r,b);}
        finally{GmtSixLandmarkAnalyzer.MIXED_EDGE_PASS.set(Boolean.FALSE);}
        if(!batonRepeatable(b))o.note="resize diagnostic only: "+batonResizeReason(b)+"; no 124060 marker tolerance uses this yet";
        o.status=Status.FOUND;
        return o;
    }

    /**
     * 124060-only (alpha76): a thin hand lying ALONG a baton (user photo: the seconds hand over the 9)
     * barely touches the wedge HandIntrusion samples, and inside the baton it shows as a DARK stripe
     * across the bright lume. Sample the lume interior row by row (rows across the baton width, at
     * 15-85% of its length, central 70% of its width): a clean baton is uniformly bright, a hand
     * leaves a notch darker than HAND_NOTCH of the row's bright level. A hand is present when more
     * than HAND_ROW_FRACTION of rows carry a notch. Shared GMT code is not touched.
     */
    static final double HAND_NOTCH=0.6, HAND_ROW_FRACTION=0.4;
    static boolean handAlongBaton(DialEdgeEllipseFit.Intensity img,int w,int h,GmtSixLandmarkAnalyzer.Geometry g){
        if(img==null||g==null||g.outerLeft==null||g.outerRight==null||g.innerLeft==null||g.innerRight==null)return false;
        double[] il=g.innerLeft,ir=g.innerRight,ol=g.outerLeft,or=g.outerRight;
        double width=Math.hypot(ol[0]-or[0],ol[1]-or[1]);
        if(!(width>=8))return false;
        int rows=24,cols=Math.max(9,(int)Math.round(width*0.7));
        int notched=0,used=0;
        for(int i=0;i<rows;i++){
            double t=0.15+0.70*i/(rows-1.0);
            double lx=il[0]+t*(ol[0]-il[0]),ly=il[1]+t*(ol[1]-il[1]),rx=ir[0]+t*(or[0]-ir[0]),ry=ir[1]+t*(or[1]-ir[1]);
            double[] v=new double[cols];boolean ok=true;
            for(int j=0;j<cols;j++){
                double u=0.15+0.70*j/(cols-1.0),x=lx+u*(rx-lx),y=ly+u*(ry-ly);
                if(x<1||y<1||x>=w-2||y>=h-2){ok=false;break;}
                v[j]=img.at(x,y);
            }
            if(!ok)continue;
            double[] sorted=v.clone();java.util.Arrays.sort(sorted);
            double bright=sorted[(int)(0.75*(cols-1))];
            if(!(bright>40))continue;
            used++;
            // A hand is a dark notch with bright lume on BOTH sides. A misfitted outline that
            // reaches the dark surround on one side is not a hand.
            int k=0;for(int j=1;j<cols;j++)if(v[j]<v[k])k=j;
            double leftMax=0,rightMax=0;
            for(int j=0;j<k;j++)leftMax=Math.max(leftMax,v[j]);
            for(int j=k+1;j<cols;j++)rightMax=Math.max(rightMax,v[j]);
            if(v[k]<HAND_NOTCH*bright&&leftMax>=0.85*bright&&rightMax>=0.85*bright)notched++;
        }
        return used>=rows/2&&notched>HAND_ROW_FRACTION*used;
    }

    /** Strict one-pixel diagnostic only. It is not currently a 124060 FOUND/LOW_CONFIDENCE gate. */
    static boolean batonRepeatable(GmtSixLandmarkAnalyzer.Result b){
        if(b==null||!b.stabilityRun||!b.stabilitySameEdge)return false;
        double c=MeasurementRepeatability.widthShiftPx(b.centringMin,b.centringMax,b.widthPx);
        double r=MeasurementRepeatability.angleShiftPx(b.rotMin,b.rotMax,b.lengthPx);
        return MeasurementRepeatability.stable(c)&&MeasurementRepeatability.stable(r);
    }

    static String batonResizeReason(GmtSixLandmarkAnalyzer.Result b){
        if(b==null||!b.stabilityRun)return "resize repeatability was not measured";
        if(!b.stabilitySameEdge)return "the baton is not found as the same physical edge at 94% and 88%";
        double c=MeasurementRepeatability.widthShiftPx(b.centringMin,b.centringMax,b.widthPx);
        double r=MeasurementRepeatability.angleShiftPx(b.rotMin,b.rotMax,b.lengthPx);
        if(!Double.isFinite(c)||!Double.isFinite(r))return "the baton measurement is not reproduced at both resize scales";
        return String.format(Locale.US,"the baton measurement moves under resize (centring %.1f px; rotation end %.1f px)",c,r);
    }

    // ------------------------------------------------------------------------------------------ round markers
    static void rounds(Mat src,DialEdgeEllipseFit.Intensity img,int w,int h,Result res){
        List<GmtRoundMarkerAnalyzer.Marker> ms=GmtRoundMarkerAnalyzer.analyse(src,res.frame,res.tick60);
        GmtRoundMarkerAnalyzer.measureStability(src,res.frame,res.tick60,ms);
        res.markerPose=GmtMarkerPose.estimate(ms,res.frame.r);   // diagnostic only; no Sub pose threshold
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
            if(hand){res.rounds.add(new Round(m,Status.HAND,"a hand is over or next to it"));continue;}
            if(!m.stable){res.rounds.add(new Round(m,Status.LOW_CONFIDENCE,m.lowReason));continue;}

            String note="";
            if(!roundOffsetRepeatable(m))note="resize diagnostic only: "+roundResizeReason(m)+"; no 124060 marker tolerance uses this yet";
            // GMT lesson: if resize picks a different concentric physical edge, centre placement can
            // still be useful but a surround-size comparison is not like-for-like.
            if(m.stabilityRun&&!m.stabilitySameEdge){
                m.sizeRatio=Double.NaN;
                note=note.isEmpty()?"centre measured; outline edge identity changes under resize, so size is not compared"
                        :note+"; outline edge identity also changes, so size is not compared";
            }
            res.rounds.add(new Round(m,Status.FOUND,note));
        }
    }

    /** Strict one-pixel centre-offset diagnostic only; not currently a 124060 status gate. */
    static boolean roundOffsetRepeatable(GmtRoundMarkerAnalyzer.Marker m){
        if(m==null||!m.stabilityRun)return false;
        return MeasurementRepeatability.stable(MeasurementRepeatability.widthShiftPx(m.offMin,m.offMax,m.diameterPx()));
    }

    static String roundResizeReason(GmtRoundMarkerAnalyzer.Marker m){
        if(m==null||!m.stabilityRun)return "resize repeatability was not measured";
        double p=MeasurementRepeatability.widthShiftPx(m.offMin,m.offMax,m.diameterPx());
        return Double.isFinite(p)?String.format(Locale.US,"the marker centre offset moves by %.1f px under resize",p)
                :"the marker centre is not reproduced at both resize scales";
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
