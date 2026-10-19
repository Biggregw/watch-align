package com.watchalign.mobile;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watch Align 1.6.0: is each hour marker CLEARLY OFF, judged the way the owner judges it by eye (eye test, 17 Oct 2026)?
 * Port of tools/research/track (track.py, with gross.py's gate, flags.py and overlay_gap.centroid_fit); validated
 * offline on 1,841 genuine markers (no marker flagged) and on markers moved / turned by known amounts.
 *
 * References, all from the single photo:
 *   hour LINES   from each hour tick of the printed minute track, through the centre to the opposite hour tick; the hour
 *                positions come from the minute ticks themselves (robust local tick lattice), not from the markers;
 *   RING / CIRCLE where the round markers' centres and the batons' outer ends should sit: the genuine marker geometry
 *                (the yellow outer-metal reference) scaled by this dial's own ring (the OTHER clean round markers).
 * Per marker (lume centre = brightness-weighted centre of the lume, area-checked):
 *   sideways  distance of the centre from its hour line;   in/out  distance from the ring along the line;
 *   tilt      batons: long axis against the hour line.
 *   gap       1.6.1, the 12 triangle: the clear dial between its top edge and the minute-track tick ends (the owner's
 *             "equidistant between the minute markers and a clear gap": the centre stays in place when the triangle
 *             is too big, the gap does not).
 * A marker is CLEARLY OFF when a measurement exceeds its limit (genuine maximum with headroom, at or below what the eye
 * sees with guide lines); NO VISIBLE ISSUE otherwise; CAN'T CHECK when a hand, glare, blur or hidden minute track makes
 * the reading unreliable (fail closed). The measured numbers are always kept for the details.
 */
final class ClearlyOff {
    private ClearlyOff(){}

    /** Limits (fractions of the dial radius / degrees). Genuine maxima: sideways 0.98 %, in/out 0.64 % (triangle 0.96 %),
     *  baton tilt 1.97 deg. */
    static final double SIDE_LIMIT_R=0.010,INOUT_LIMIT_R=0.010,INOUT_TRIANGLE_LIMIT_R=0.0125,TILT_LIMIT_DEG=2.25;
    /** 1.6.2: round markers and batons, distance from where the centre should be (any direction; replaces the separate
     *  sideways / in-out limits of 1.6.0-1.6.1 for these shapes, the owner's "circle around the position"). The same
     *  1.0 %: it flags everything the square did, and more only on the diagonal. Genuine maximum 0.97 % (1,548 round
     *  markers and batons, assets genuine_track.csv). */
    static final double POS_LIMIT_R=0.010;
    /** The owner's "clear gap" (17 Oct 2026): the 12 triangle is clearly off when the dial left clear between its top edge
     *  and the minute-track tick ends is smaller than this (less the low-res allowance). Genuine minima: GMT 2.55 %,
     *  Sub 2.34 % (159 photos); owner's two BLNR photos 1.08 % and 1.90 %, the Sub rejected for its 12 1.90 %. */
    static final double GAP_TRIANGLE_LIMIT_R=0.022;
    /** Rays between the minute ticks along which the triangle's gap is measured (deg either side of the hour line). */
    static final double[] GAP_OFFS_DEG={-4.5,-3.0,-1.5,1.5,3.0,4.5};
    /** 12 triangle rotation (owner's method, 18 Oct 2026): the line of the triangle's top outer-metal edge between its two
     *  corners against the line through the inner ends of minute ticks 59 and 1. Default limit 1.2 deg (owner, 19 Oct 2026:
     *  verified genuine triangles reach about 1.17 deg). The reading is
     *  withheld below ROT_MIN_RPX, when an edge column lacks contrast, when the edge fit is rough, or when the left and
     *  right halves of the edge (two independent fits) disagree. */
    static final double TRI_ROT_LIMIT_DEG=1.2,ROT_MIN_RPX=200,ROT_MIN_CONTRAST=30,ROT_MAX_RMS_PX=0.35,ROT_MAX_HALF_DIFF_DEG=0.6,
            ROT_T0=0.015,ROT_T1=0.090;
    /** Added to the position limits so that a genuine marker on a small photo never tips over (px). */
    static final double LOWRES_ALLOWANCE_PX=0.5;
    static final double MIN_RPX=150;
    static final int MIN_RING=3;

    /** POSSIBLE (experimental 1.6.3): over a limit, but measured from an uncertain outer edge ("Possible misalignment -
     *  inspect"; shown with its evidence, never a silent pass). */
    enum State{CLEARLY_OFF,NO_VISIBLE_ISSUE,CANT_CHECK,POSSIBLE}

    /** Experimental 1.6.3: measure each marker from its visible outer metal (MetalFit) instead of its lume centre. The
     *  desktop harness can switch it off (-Dwatchalign.metal=off) to compare with 1.6.2. */
    static final boolean METAL=!"off".equals(System.getProperty("watchalign.metal"));

    /**
     * 1.6.2: the limits a marker is judged against, one set per marker TYPE (genuine markers of a type all vary alike).
     * The owner can change them on the calibration screen; DEFAULTS are the tested values. Fractions of the dial radius
     * and degrees; the low-resolution allowance is added on top of every position limit (subtracted from the gap).
     *   round, baton  POSITION: distance of the centre from where it should be (hour line x ring), any direction
     *   baton         TILT against the hour line
     *   triangle      SIDEWAYS from the hour line, GAP under the minute track, TILT (not judged by default: NaN)
     * The triangle's centre in/out limit (INOUT_TRIANGLE_LIMIT_R) is not adjustable: it catches a triangle sunk towards
     * the centre, which the one-sided gap does not.
     */
    static final class Limits {
        double roundPosR,batonPosR,batonTiltDeg,triSideR,triGapR,triTiltDeg;
        static Limits defaults(){Limits l=new Limits();l.roundPosR=POS_LIMIT_R;l.batonPosR=POS_LIMIT_R;l.batonTiltDeg=TILT_LIMIT_DEG;
            l.triSideR=SIDE_LIMIT_R;l.triGapR=GAP_TRIANGLE_LIMIT_R;l.triTiltDeg=TRI_ROT_LIMIT_DEG;return l;}
        Limits copy(){Limits l=new Limits();l.roundPosR=roundPosR;l.batonPosR=batonPosR;l.batonTiltDeg=batonTiltDeg;
            l.triSideR=triSideR;l.triGapR=triGapR;l.triTiltDeg=triTiltDeg;return l;}
        double[] values(){return new double[]{roundPosR,batonPosR,batonTiltDeg,triSideR,triGapR,triTiltDeg};}
        boolean same(Limits o){double[] a=values(),b=o.values();for(int i=0;i<a.length;i++){if(Double.isNaN(a[i])!=Double.isNaN(b[i]))return false;
            if(!Double.isNaN(a[i])&&Math.abs(a[i]-b[i])>1e-9)return false;}return true;}
        boolean isDefault(){return same(defaults());}
    }
    /** The owner's saved limits per model id (set by the app at start-up and by the calibration screen); defaults otherwise. */
    static final Map<String,Limits> USER=new java.util.concurrent.ConcurrentHashMap<>();
    static Limits forModel(String id){Limits l=id==null?null:USER.get(id);return l==null?Limits.defaults():l.copy();}

    static final class Marker {
        final int hour;final ModelSpec.Shape shape;
        State state=State.CANT_CHECK;
        /** why it can't be checked (plain words), empty otherwise */
        String reason="";
        /** gate codes as in the research tool (for the technical details and parity checks) */
        final List<String> gate=new ArrayList<>();
        double sidePct=Double.NaN,inoutPct=Double.NaN,tiltDeg=Double.NaN,sidePx=Double.NaN,inoutPx=Double.NaN;
        double tiltRaw=Double.NaN,scale=Double.NaN;
        double sideLimitPct=Double.NaN,inoutLimitPct=Double.NaN;
        /** round / baton: distance from where it should be (% R) and its limit; tilt limit used (NaN = not judged) */
        double posPct=Double.NaN,posLimitPct=Double.NaN,tiltLimitDeg=Double.NaN;
        boolean posOff;
        /** triangle rotation: the earlier lume-axis reading (kept for the details), the two half-edge readings, the edge fit
         *  roughness (px) and, when withheld, why */
        double tiltCentroidDeg=Double.NaN,rotLeftDeg=Double.NaN,rotRightDeg=Double.NaN,rotRmsPx=Double.NaN;String rotNote="";
        /** diagnostics: the same top edge against the hour line (tick lattice), and the radial offset of tick 1's inner end from tick 59's (px) */
        double rotLineDeg=Double.NaN,tipDrPx=Double.NaN;
        /** triangle: the clear gap under the minute track (% R, px), its limit, and where the edge and the tick ends sit
         *  (canonical radius, for the evidence picture) */
        double gapPct=Double.NaN,gapPx=Double.NaN,gapLimitPct=Double.NaN,gapEdgeR=Double.NaN,gapTrackR=Double.NaN;
        boolean sideOff,inoutOff,tiltOff,gapOff;
        double[] cObs,cGen;
        /** outer-metal fit (METAL): why the reading is uncertain (empty when reliable), how much of the outline was seen,
         *  fit roughness (px), share of inconsistent edge, standard errors; the genuine and fitted outlines and the status
         *  of each fitted outline point (MetalFit.SEEN / INCONSISTENT / HIDDEN), and the triangle's 59/1 tick ends */
        String uncertain="",source="lume";
        double cover=Double.NaN,fitRmsPx=Double.NaN,badFrac=Double.NaN,sePosPct=Double.NaN,seRotDeg=Double.NaN,fitTiltDeg=Double.NaN;
        double[][] yellow,fitPoly,fitPts;byte[] fitStatus;double[] tick59,tick01;boolean gapFromFit;
        Marker(int hour,ModelSpec.Shape shape){this.hour=hour;this.shape=shape;}
        /** a copy to judge against other limits without changing the shown answer (calibration screen) */
        Marker copy(){Marker m=new Marker(hour,shape);m.state=state;m.reason=reason;m.gate.addAll(gate);
            m.sidePct=sidePct;m.inoutPct=inoutPct;m.tiltDeg=tiltDeg;m.sidePx=sidePx;m.inoutPx=inoutPx;m.tiltRaw=tiltRaw;m.scale=scale;
            m.gapPct=gapPct;m.gapPx=gapPx;m.gapEdgeR=gapEdgeR;m.gapTrackR=gapTrackR;m.cObs=cObs;m.cGen=cGen;
            m.tiltCentroidDeg=tiltCentroidDeg;m.rotLeftDeg=rotLeftDeg;m.rotRightDeg=rotRightDeg;m.rotRmsPx=rotRmsPx;m.rotNote=rotNote;
            m.uncertain=uncertain;m.source=source;m.cover=cover;m.fitRmsPx=fitRmsPx;m.badFrac=badFrac;m.sePosPct=sePosPct;m.seRotDeg=seRotDeg;
            m.fitTiltDeg=fitTiltDeg;m.yellow=yellow;m.fitPoly=fitPoly;m.fitPts=fitPts;m.fitStatus=fitStatus;m.tick59=tick59;m.tick01=tick01;
            m.gapFromFit=gapFromFit;return m;}
        boolean measured(){return Double.isFinite(sidePct)||Double.isFinite(inoutPct);}
    }

    static final class Result {
        final List<Marker> markers=new ArrayList<>();
        /** hour -> hour-line angle (rad, clockwise from 12) from the minute ticks; missing when hidden */
        final Map<Integer,Double> lines=new HashMap<>();
        double rpx=Double.NaN,trackScale=Double.NaN,ringTurnPct=Double.NaN;
        int badTicks;
        /** METAL: this photo's outer-edge offset per shape (px, + = outward; median over fully seen markers) */
        final Map<ModelSpec.Shape,Double> edgeBiasPx=new HashMap<>();
        /** genuine canonical radius of the round centres and the baton outer ends (for drawing the ring / circle) */
        double roundCentreR=Double.NaN,batonEndR=Double.NaN,ringScale=Double.NaN;
        String photoReason;
        /** the limits the answers were judged against */
        Limits limits=Limits.defaults();
        Marker at(int h){for(Marker m:markers)if(m.hour==h)return m;return null;}
    }

    /** One marker as the app knows it: shape, the yellow outer-metal reference (canonical) and the hand/glare check. */
    static final class Input {
        final int hour;final ModelSpec.Shape shape;final double[][] yellow;final Boolean handClean;final String handReason;
        Input(int hour,ModelSpec.Shape shape,double[][] yellow,Boolean handClean,String handReason){
            this.hour=hour;this.shape=shape;this.yellow=yellow;this.handClean=handClean;this.handReason=handReason;}
    }

    /**
     * @param gray     8-bit grey photo (the analysis bitmap)
     * @param H        dial pose, canonical -> photo px (row-major 3x3)
     * @param rpx      dial radius in px
     * @param insets   genuine luminous inset (R) per supported shape; a shape without one cannot be checked
     * @param trackInnerR the model's minute-track inner radius (canonical)
     */
    static Result analyse(Mat gray,double[] H,double rpx,List<Input> in,Map<ModelSpec.Shape,Double> insets,double trackInnerR){
        return analyse(gray,H,rpx,in,insets,trackInnerR,Limits.defaults());
    }
    static Result analyse(Mat gray,double[] H,double rpx,List<Input> in,Map<ModelSpec.Shape,Double> insets,double trackInnerR,Limits L){
        Result res=new Result();res.rpx=rpx;res.limits=L.copy();
        Mat f=new Mat();gray.convertTo(f,CvType.CV_32F);
        try{
            Img img=new Img(f,H);
            Ctx ctx=context(img,rpx,in);
            Map<ModelSpec.Shape,double[]> sib=siblings(ctx,in,insets);
            List<Tick> ticks=tickTrack(img,rpx);
            for(int h=0;h<12;h++){Double a=hourLine(ticks,h,rpx);if(a!=null)res.lines.put(h,a);}
            List<Double> rin=new ArrayList<>();int bad=0;
            for(Tick t:ticks){if(t.bad.isEmpty()){if(Double.isFinite(t.rIn))rin.add(t.rIn);}else bad++;}
            res.badTicks=bad;
            res.trackScale=rin.isEmpty()?Double.NaN:median(toArray(rin))/trackInnerR;
            Map<Integer,Base> base=new LinkedHashMap<>();
            Map<Integer,MetalFit.Fit> fits=new HashMap<>();
            if(METAL){
                MetalFit.Fit[] fa=MetalFit.fitAll(img,in,rpx,ctx.hands);
                for(Map.Entry<ModelSpec.Shape,Double> e:MetalFit.lastBias.entrySet())res.edgeBiasPx.put(e.getKey(),e.getValue()*rpx);
                for(int i=0;i<in.size();i++){Input m=in.get(i);if(fa[i]!=null)fits.put(m.hour,fa[i]);base.put(m.hour,metalBase(m,fa[i],insets,rpx));}
            }else for(Input m:in)base.put(m.hour,centre(img,ctx,m,insets,sib,rpx));
            for(Input m:in){
                Marker r=new Marker(m.hour,m.shape);res.markers.add(r);
                Base b=base.get(m.hour);r.gate.addAll(b.gate);
                if(!res.lines.containsKey(m.hour%12))r.gate.add("minute ticks near it hidden");
                metrics(r,b,res.lines,img,ringScale(base,in,m.hour),rpx);
                MetalFit.Fit mf=fits.get(m.hour);r.yellow=m.yellow;
                if(mf!=null&&mf.cObs!=null){r.source="outer metal";r.uncertain=mf.uncertain;r.cover=mf.cover;r.fitRmsPx=mf.rmsPx;r.badFrac=mf.badFrac;
                    r.sePosPct=100*mf.sePos;r.seRotDeg=mf.seRot;r.fitTiltDeg=mf.tiltDeg;r.fitPoly=mf.poly;r.fitPts=mf.pts;r.fitStatus=mf.status;}
                if(m.shape==ModelSpec.Shape.TRIANGLE){
                    gap(r,m,ticks,res.lines,img,rpx);
                    if(mf!=null&&mf.ok()){if(!Double.isFinite(r.gapPct))gapFromFit(r,mf,ticks,res.lines,img,rpx);metalTriangleRotation(r,mf,ticks);}
                    else triangleRotation(r,m,ticks,res.lines,img,rpx);
                }
            }
            List<Double> clean=new ArrayList<>();
            for(Marker r:res.markers)if(r.gate.isEmpty()&&Double.isFinite(r.sidePct))clean.add(r.sidePct);
            res.ringTurnPct=clean.size()>=3?median(toArray(clean)):Double.NaN;
            List<Double> s=new ArrayList<>();
            for(Input m:in){Base b=base.get(m.hour);if(m.shape==ModelSpec.Shape.ROUND&&b.gate.isEmpty()&&b.cObs!=null)s.add(norm(b.cObs)/norm(b.cGen));}
            res.ringScale=s.size()>=MIN_RING?median(toArray(s)):Double.NaN;
            List<Double> rc=new ArrayList<>(),be=new ArrayList<>();
            for(Input m:in){if(m.yellow==null||m.yellow.length<3)continue;
                if(m.shape==ModelSpec.Shape.ROUND)rc.add(norm(mean(m.yellow)));
                if(m.shape==ModelSpec.Shape.BATON){double mx=0;for(double[] p:m.yellow)mx=Math.max(mx,norm(p));be.add(mx);}}
            res.roundCentreR=rc.isEmpty()?Double.NaN:median(toArray(rc));res.batonEndR=be.isEmpty()?Double.NaN:median(toArray(be));
            for(Marker r:res.markers)decide(r,rpx,in,L);
        }finally{f.release();}
        return res;
    }

    // ------------------------------------------------------------------------------------------------ decision

    static void decide(Marker r,double rpx,List<Input> in){decide(r,rpx,in,Limits.defaults());}

    static void decide(Marker r,double rpx,List<Input> in,Limits L){
        Input m=null;if(in!=null)for(Input x:in)if(x.hour==r.hour)m=x;
        double allow=100*LOWRES_ALLOWANCE_PX/rpx;
        boolean tri=r.shape==ModelSpec.Shape.TRIANGLE,baton=r.shape==ModelSpec.Shape.BATON;
        r.sideLimitPct=tri?100*L.triSideR+allow:Double.NaN;
        r.inoutLimitPct=tri?100*INOUT_TRIANGLE_LIMIT_R+allow:Double.NaN;
        r.posLimitPct=tri?Double.NaN:100*(baton?L.batonPosR:L.roundPosR)+allow;
        r.tiltLimitDeg=baton?L.batonTiltDeg:tri?L.triTiltDeg:Double.NaN;
        r.gapLimitPct=tri?100*L.triGapR-allow:Double.NaN;
        r.sideOff=r.inoutOff=r.posOff=r.tiltOff=r.gapOff=false;r.posPct=Double.NaN;
        if(!r.gate.isEmpty()){r.state=State.CANT_CHECK;r.reason=reason(r.gate,m);return;}
        r.reason="";
        if(tri){
            r.sideOff=Double.isFinite(r.sidePct)&&Math.abs(r.sidePct)>r.sideLimitPct;
            r.inoutOff=Double.isFinite(r.inoutPct)&&Math.abs(r.inoutPct)>r.inoutLimitPct;
            r.gapOff=Double.isFinite(r.gapPct)&&r.gapPct<r.gapLimitPct;
        }else{
            // what is measured of the position counts (a clearly-off reading stands when the other component is missing)
            double a=Double.isFinite(r.sidePct)?r.sidePct:0,b=Double.isFinite(r.inoutPct)?r.inoutPct:0;
            if(Double.isFinite(r.sidePct)||Double.isFinite(r.inoutPct))r.posPct=Math.hypot(a,b);
            r.posOff=Double.isFinite(r.posPct)&&r.posPct>r.posLimitPct;
        }
        r.tiltOff=Double.isFinite(r.tiltLimitDeg)&&Double.isFinite(r.tiltDeg)&&Math.abs(r.tiltDeg)>r.tiltLimitDeg;
        if(r.sideOff||r.inoutOff||r.posOff||r.tiltOff||r.gapOff){
            // over a limit from an uncertain edge: shown as a possible misalignment to inspect, never passed
            if(r.uncertain.isEmpty()&&!(r.gapOff&&r.gapFromFit))r.state=State.CLEARLY_OFF;
            else{r.state=State.POSSIBLE;r.reason=r.uncertain.isEmpty()?"its top edge was placed from the rest of its outline":r.uncertain;}
            return;}
        // a pass needs every measurement of the marker (fail closed)
        if(!Double.isFinite(r.sidePct)){r.state=State.CANT_CHECK;r.reason="its hour line could not be placed from the minute track";return;}
        if(!Double.isFinite(r.inoutPct)){r.state=State.CANT_CHECK;r.reason="too few other round markers could be measured to place the ring";return;}
        if(Double.isFinite(r.tiltLimitDeg)&&!Double.isFinite(r.tiltDeg)){r.state=State.CANT_CHECK;
            r.reason=tri?"its rotation could not be measured reliably"+(r.rotNote.isEmpty()?"":" ("+r.rotNote+")"):"its tilt could not be measured (the opposite hour line is hidden)";return;}
        if(tri&&!Double.isFinite(r.gapPct)){r.state=State.CANT_CHECK;r.reason="the gap under the minute track could not be measured";return;}
        r.state=State.NO_VISIBLE_ISSUE;
    }

    /** 1.6.2: judge every marker again against other limits (the calibration screen); withheld markers stay withheld. */
    static void rescore(Result res,Limits L){
        res.limits=L.copy();
        for(Marker r:res.markers){if(!r.gate.isEmpty())continue;decide(r,res.rpx,null,L);}
    }

    static String reason(List<String> gate,Input m){
        String g=gate.get(0);
        if(g.equals("hand (app check)"))return m!=null&&m.handReason!=null&&!m.handReason.isEmpty()?m.handReason:"a hand or glare is over or next to it";
        if(g.equals("no overlay for this marker"))return "it could not be located";
        if(g.equals("no luminous reference"))return "there is no verified genuine reference for this marker type";
        if(g.equals("hand"))return "a hand reaches it";
        if(g.equals("obstructed"))return "something crosses it (a hand or glare)";
        if(g.equals("merged"))return "it merges with something next to it (a hand or glare)";
        if(g.equals("unusual"))return "its visible shape is unlike the other markers (a hand, glare or blur)";
        if(g.equals("lume not the expected size"))return "its visible lume is not the expected size (a hand, glare or a lit surround)";
        if(g.equals("photo too small"))return "the photo's resolution is too low";
        if(g.equals("minute ticks near it hidden"))return "the minute track next to it is hidden";
        return "it could not be found clearly";
    }

    // ------------------------------------------------------------------------------------------------ per marker

    static final class Base {final List<String> gate=new ArrayList<>();double[] cObs,cGen;double tiltDeg=Double.NaN;}

    /** gross.measure_marker: the gate and the lume centre. */
    static Base centre(Img img,Ctx ctx,Input m,Map<ModelSpec.Shape,Double> insets,Map<ModelSpec.Shape,double[]> sib,double rpx){
        Base b=new Base();
        if(m.yellow==null||m.yellow.length<3){b.gate.add("no overlay for this marker");return b;}
        Double inset=insets.get(m.shape);
        if(inset==null){b.gate.add("no luminous reference");return b;}
        if(m.handClean==null||!m.handClean)b.gate.add("hand (app check)");
        Flags fl=measureFlags(ctx,m,inset,sib);
        if(!fl.flag.isEmpty())b.gate.add(fl.flag);
        else if(!fl.status.equals("measured"))b.gate.add(fl.status);
        Centroid c=centroidFit(img,m,inset,rpx);
        if(!"measured".equals(c.status))b.gate.add("lume not the expected size");
        if(rpx<MIN_RPX)b.gate.add("photo too small");
        if(c.cpoly!=null){
            b.cGen=c.cpoly;b.cObs=new double[]{c.cpoly[0]+c.d[0],c.cpoly[1]+c.d[1]};
            if(m.shape!=ModelSpec.Shape.ROUND)b.tiltDeg=Math.toDegrees(c.th);
        }
        return b;
    }

    /** METAL: the gate and centre from the outer-metal fit. A hand or glare only removes the edge it covers; the marker is
     *  withheld only when its visible edge cannot fix its geometry. */
    static Base metalBase(Input m,MetalFit.Fit f,Map<ModelSpec.Shape,Double> insets,double rpx){
        Base b=new Base();
        if(m.yellow==null||m.yellow.length<3||f==null){b.gate.add("no overlay for this marker");return b;}
        if(insets.get(m.shape)==null){b.gate.add("no luminous reference");return b;}
        if(!f.note.isEmpty())b.gate.add(f.note);
        if(rpx<MIN_RPX)b.gate.add("photo too small");
        if(f.cObs!=null){b.cGen=f.cGen;b.cObs=f.cObs;if(m.shape!=ModelSpec.Shape.ROUND)b.tiltDeg=f.tiltDeg;}
        return b;
    }

    /** METAL, the 12 triangle: the fitted outline's rotation (its top edge's direction) against the line through the inner
     *  ends of minute ticks 59 and 1. Hidden ticks: against the 0-30 line (metrics), said so in the note. */
    static void metalTriangleRotation(Marker r,MetalFit.Fit f,List<Tick> ticks){
        r.tiltCentroidDeg=Double.NaN;r.rotNote="";r.rotLineDeg=r.tiltDeg;
        Tick t59=ticks.get(59),t01=ticks.get(1);
        if(!t59.bad.isEmpty()||!t01.bad.isEmpty()||!Double.isFinite(t59.rIn)||!Double.isFinite(t01.rIn)){
            r.rotNote="minute ticks 59 and 1 are not clear; measured against the 0-30 line";return;}
        double[] p59=cpt(t59.rIn,t59.a),p01=cpt(t01.rIn,t01.a);
        double dev=wrapHalfPi(Math.atan2(p01[1]-p59[1],p01[0]-p59[0]));
        r.tiltDeg=f.tiltDeg-Math.toDegrees(dev);r.tick59=p59;r.tick01=p01;
    }

    /** METAL, the 12 triangle, when its top edge itself could not be read (reflection): the gap from the fitted outline */
    static void gapFromFit(Marker r,MetalFit.Fit f,List<Tick> ticks,Map<Integer,Double> L,Img img,double rpx){
        Double a0=L.get(r.hour%12);if(a0==null)return;
        int k0=(5*r.hour)%60;List<Double> rin=new ArrayList<>();
        for(int dk=-2;dk<=2;dk++){Tick t=ticks.get((int)pmod(k0+dk,60));if(t.bad.isEmpty())rin.add(t.rIn);}
        if(rin.size()<3)return;
        double rt=median(toArray(rin));List<Double> gaps=new ArrayList<>(),edges=new ArrayList<>();
        for(double off:GAP_OFFS_DEG){double a=a0+Math.toRadians(off);Double re=rayOut(f.poly,a);if(re==null)continue;
            gaps.add(Math.copySign(img.pxBetween(cpt(Math.min(re,rt),a),cpt(rt,a)),rt-re));edges.add(re);}
        if(gaps.size()<2)return;
        r.gapPx=median(toArray(gaps));r.gapPct=100*r.gapPx/rpx;r.gapEdgeR=median(toArray(edges));r.gapTrackR=rt;r.gapFromFit=true;
    }

    static double ringScaleOf(Map<Integer,Base> base,List<Input> in,int hour){return ringScale(base,in,hour);}
    static double ringScale(Map<Integer,Base> base,List<Input> in,int hour){
        List<Double> s=new ArrayList<>();
        for(Input m:in){if(m.hour==hour||m.shape!=ModelSpec.Shape.ROUND)continue;Base b=base.get(m.hour);
            if(b.gate.isEmpty()&&b.cObs!=null)s.add(norm(b.cObs)/norm(b.cGen));}
        return s.size()>=MIN_RING?median(toArray(s)):Double.NaN;
    }

    static void metrics(Marker r,Base b,Map<Integer,Double> L,Img img,double scale,double rpx){
        if(b.cObs==null)return;
        r.cObs=b.cObs;r.cGen=b.cGen;r.scale=scale;r.tiltRaw=b.tiltDeg;
        double[] c=b.cObs,g=b.cGen;
        double rc=norm(c),ac=Math.atan2(c[0],-c[1]);
        Double a=L.get(r.hour%12);
        if(a!=null){
            double da=wrapPi(ac-a);
            r.sidePx=Math.copySign(img.pxBetween(cpt(rc,a),cpt(rc,ac)),da);r.sidePct=100*r.sidePx/rpx;
        }
        if(Double.isFinite(scale)){
            double re=scale*norm(g);
            r.inoutPx=Math.copySign(img.pxBetween(cpt(re,ac),cpt(rc,ac)),rc-re);r.inoutPct=100*r.inoutPx/rpx;
        }
        if(r.shape!=ModelSpec.Shape.ROUND&&Double.isFinite(b.tiltDeg)){
            Double a1=L.get(r.hour%12),a2=L.get((r.hour+6)%12);
            if(a1!=null&&a2!=null){
                double[] p1=cpt(0.93,a1),p2=cpt(0.93,a2);
                double[] n1=cpt(0.93,Math.toRadians(30*(r.hour%12))),n2=cpt(0.93,Math.toRadians(30*((r.hour+6)%12)));
                double dev=wrapHalfPi(Math.atan2(p2[1]-p1[1],p2[0]-p1[0])-Math.atan2(n2[1]-n1[1],n2[0]-n1[0]));
                r.tiltDeg=b.tiltDeg-Math.toDegrees(dev);
            }
        }
    }

    /** track.outer_gap: the owner's gap, the clear dial between the triangle's outer edge and the inner ends of the
     *  minute ticks above it, along rays between the ticks. The edge is where the bright surround falls half way to the
     *  dial; the tick ends are this photo's own (median of the clean ticks near the hour). */
    static void gap(Marker r,Input m,List<Tick> ticks,Map<Integer,Double> L,Img img,double rpx){
        Double a0=L.get(m.hour%12);
        if(a0==null||m.yellow==null||m.yellow.length<3)return;
        int k0=(5*m.hour)%60;List<Double> rin=new ArrayList<>();
        for(int dk=-2;dk<=2;dk++){Tick t=ticks.get((int)pmod(k0+dk,60));if(t.bad.isEmpty())rin.add(t.rIn);}
        if(rin.size()<3)return;
        double rt=median(toArray(rin));
        List<Double> gaps=new ArrayList<>(),edges=new ArrayList<>();
        for(double off:GAP_OFFS_DEG){
            double a=a0+Math.toRadians(off);
            Double rg=rayOut(m.yellow,Math.toRadians(30*(m.hour%12)+off));
            if(rg==null)continue;
            double[] radii=arange(rg-0.03,rt,0.25/rpx);
            double[][] v=img.polar(radii,new double[]{a});double[] p=new double[radii.length];boolean nan=false;
            for(int i=0;i<p.length;i++){p[i]=v[i][0];if(Double.isNaN(p[i]))nan=true;}
            if(nan||p.length<8)continue;
            int lim=0;while(lim<radii.length&&radii[lim]<rt-0.004)lim++;
            int ip=argmax(Arrays.copyOf(p,Math.max(2,lim)));
            double dial=ip<p.length-1?percentile(Arrays.copyOfRange(p,ip,p.length),10):p[p.length-1];
            Double e=firstCross(p,dial+0.5*(p[ip]-dial),ip,1);
            double re=e==null?rt:interp(e,radii);
            gaps.add(Math.copySign(img.pxBetween(cpt(Math.min(re,rt),a),cpt(rt,a)),rt-re));edges.add(re);
        }
        if(gaps.size()<Math.max(2,GAP_OFFS_DEG.length/2))return;
        r.gapPx=median(toArray(gaps));r.gapPct=100*r.gapPx/rpx;r.gapEdgeR=median(toArray(edges));r.gapTrackR=rt;
    }

    /** The 12 triangle's rotation (owner's method): replaces the lume-axis reading for the triangle (kept as tiltCentroidDeg).
     *  Edge samples: across the top edge, between the corners (|t| in ROT_T0..ROT_T1 R from the hour line, clear of the
     *  ticks at 0 and +-1 min), the outer metal edge along the hour line's direction, where the bright surround falls half
     *  way to the dial (as gap()). Reference: the inner ends of minute ticks 59 and 1 (the tick track). */
    static void triangleRotation(Marker r,Input m,List<Tick> ticks,Map<Integer,Double> L,Img img,double rpx){
        r.tiltCentroidDeg=r.tiltDeg;r.tiltDeg=Double.NaN;r.rotNote="";
        Double a0=L.get(0);
        if(a0==null||m.yellow==null||m.yellow.length<3){r.rotNote="the minute track at 12 is hidden";return;}
        if(rpx<ROT_MIN_RPX){r.rotNote="the photo's resolution is too low for it";return;}
        Tick t59=ticks.get(59),t01=ticks.get(1);
        if(!t59.bad.isEmpty()||!t01.bad.isEmpty()||!Double.isFinite(t59.rIn)||!Double.isFinite(t01.rIn)){r.rotNote="minute ticks 59 and 1 are not clear";return;}
        Double rg=rayOut(m.yellow,0);if(rg==null){r.rotNote="its top edge could not be placed";return;}
        double rt=Double.isFinite(r.gapTrackR)?r.gapTrackR:0.5*(t59.rIn+t01.rIn);
        double[] er={Math.sin(a0),-Math.cos(a0)},et={Math.cos(a0),Math.sin(a0)};
        double[] radii=arange(rg-0.03,rt-0.002,0.25/rpx);
        if(radii.length<8){r.rotNote="its top edge could not be placed";return;}
        int lim=0;while(lim<radii.length&&radii[lim]<rt-0.004)lim++;
        List<double[]> left=new ArrayList<>(),right=new ArrayList<>();int cols=0,weak=0;
        for(double t=-ROT_T1;t<=ROT_T1+1e-12;t+=0.5/rpx){
            if(Math.abs(t)<ROT_T0)continue;cols++;
            double[] X=new double[radii.length],Y=new double[radii.length];
            for(int i=0;i<radii.length;i++){X[i]=radii[i]*er[0]+t*et[0];Y[i]=radii[i]*er[1]+t*et[1];}
            double[] p=img.sampleCanon(X,Y);boolean nan=false;for(double v:p)if(Double.isNaN(v))nan=true;
            if(nan){weak++;continue;}
            // the OUTERMOST bright-to-dark crossing (scanning in from the tick ends), so a bright lume inside the rim never
            // stands in for the rim's outer edge
            double pk=Double.NEGATIVE_INFINITY;for(int i=0;i<lim;i++)pk=Math.max(pk,p[i]);
            double dial=percentile(p,10);
            if(pk-dial<ROT_MIN_CONTRAST){weak++;continue;}
            double thr=dial+0.5*(pk-dial);int j=Math.min(lim,p.length-1)-1;
            while(j>=0&&p[j]<thr)j--;
            if(j<0||j>=Math.min(lim,p.length-1)-1){weak++;continue;}
            double e=j+(p[j]-thr)/(p[j]-p[j+1]);
            (t<0?left:right).add(new double[]{t,interp(e,radii)});
        }
        RotFit f=rotationFit(left,right,ClearlyOff.cpt(t59.rIn,t59.a),ClearlyOff.cpt(t01.rIn,t01.a),er,et,rpx,cols==0?1:(double)weak/cols);
        r.rotLeftDeg=f.left;r.rotRightDeg=f.right;r.rotRmsPx=f.rmsPx;r.rotNote=f.fail;r.rotLineDeg=f.line;r.tipDrPx=(t01.rIn-t59.rIn)*rpx;
        if(f.fail.isEmpty())r.tiltDeg=f.rot;
    }

    static final class RotFit {double rot=Double.NaN,left=Double.NaN,right=Double.NaN,rmsPx=Double.NaN,line=Double.NaN;String fail="";}

    /** The rotation from top-edge samples {t, s} (t along the tick line, s along the hour line; canonical) of the left and
     *  right halves and the inner tick tips p59, p01, with the reliability checks. + = clockwise. */
    static RotFit rotationFit(List<double[]> left,List<double[]> right,double[] p59,double[] p01,double[] er,double[] et,double rpx,double weakShare){
        RotFit f=new RotFit();
        int need=(int)Math.floor(0.8*(ROT_T1-ROT_T0)*rpx/0.5);
        if(weakShare>0.2||left.size()<Math.max(8,need)||right.size()<Math.max(8,need)){f.fail="its top edge is not clear enough";return f;}
        double[] kl=edgeLine(left,rpx),kr=edgeLine(right,rpx);
        List<double[]> both=new ArrayList<>(left);both.addAll(right);double[] kb=edgeLine(both,rpx);
        double[] q={p01[0]-p59[0],p01[1]-p59[1]};
        f.left=angleTo(q,kl[0],er,et);f.right=angleTo(q,kr[0],er,et);f.rot=angleTo(q,kb[0],er,et);f.rmsPx=kb[1];f.line=angleTo(et,kb[0],er,et);
        if(f.rmsPx>ROT_MAX_RMS_PX)f.fail="its top edge is not straight enough in this photo";
        else if(Math.abs(f.left-f.right)>ROT_MAX_HALF_DIFF_DEG)f.fail="the two halves of its top edge disagree";
        return f;
    }
    /** robust straight line s = c + k t through edge samples: {k, rms px} */
    static double[] edgeLine(List<double[]> pts,double rpx){
        int n=pts.size();double[] x=new double[n],y=new double[n];boolean[] keep=new boolean[n];
        for(int i=0;i<n;i++){x[i]=pts.get(i)[0];y[i]=pts.get(i)[1];keep[i]=true;}
        double[] ab=polyfit1(x,y,keep);double rms=0;
        for(int it=0;it<3;it++){
            double ss=0;int m=0;for(int i=0;i<n;i++)if(keep[i]){double d=y[i]-(ab[0]*x[i]+ab[1]);ss+=d*d;m++;}
            double sd=Math.max(Math.sqrt(ss/Math.max(1,m)),0.2/rpx);
            for(int i=0;i<n;i++)keep[i]=Math.abs(y[i]-(ab[0]*x[i]+ab[1]))<=3*sd;
            ab=polyfit1(x,y,keep);
        }
        double ss=0;int m=0;for(int i=0;i<n;i++)if(keep[i]){double d=y[i]-(ab[0]*x[i]+ab[1]);ss+=d*d;m++;}
        rms=Math.sqrt(ss/Math.max(1,m))*rpx;
        return new double[]{ab[0],rms};
    }
    /** degrees from direction q to the edge direction et + k er (canonical, y down: + = clockwise) */
    static double angleTo(double[] q,double k,double[] er,double[] et){
        double dx=et[0]+k*er[0],dy=et[1]+k*er[1];
        return Math.toDegrees(Math.atan2(q[0]*dy-q[1]*dx,q[0]*dx+q[1]*dy));
    }

    /** largest radius at which the ray from the centre at angle a crosses the polygon (null if it misses) */
    static Double rayOut(double[][] P,double a){
        double ux=Math.sin(a),uy=-Math.cos(a);Double best=null;
        for(int i=0;i<P.length;i++){
            double[] p=P[i],q=P[(i+1)%P.length];double ex=q[0]-p[0],ey=q[1]-p[1];
            double den=ux*(-ey)+uy*ex;if(Math.abs(den)<1e-12)continue;
            double t=(p[0]*(-ey)+p[1]*ex)/den,s=(ux*p[1]-uy*p[0])/den;
            if(t>0&&s>=0&&s<=1)best=best==null?t:Math.max(best,t);
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ minute track

    static final double TR0=0.78,TR1=1.03,STEP=0.25,D6=Math.toRadians(6.0);

    static final class Tick {int k;double a,amp,width,bg,rIn;boolean hour;final List<String> bad=new ArrayList<>();}

    /** The 60 minute ticks: sub-pixel angle, amplitude, width, local background, inner-end radius; ticks under a hand,
     *  glare or dial text are marked bad (unlike their neighbours). */
    static List<Tick> tickTrack(Img img,double rpx){
        double[] radii=arange(TR0,TR1,STEP/rpx);
        int na=(int)Math.rint(2*Math.PI*0.95*rpx/STEP);na-=na%60;
        double[] ang=new double[na];for(int j=0;j<na;j++)ang[j]=j*2*Math.PI/na;
        double[][] V=img.polar(radii,ang);
        double fill=nanmedian(V);
        for(double[] row:V)for(int j=0;j<row.length;j++)if(Double.isNaN(row[j]))row[j]=fill;
        int nr=radii.length;double[] A=new double[nr];
        double[] ec=new double[na],es=new double[na];
        for(int j=0;j<na;j++){ec[j]=Math.cos(60*ang[j]);es[j]=-Math.sin(60*ang[j]);}
        for(int i=0;i<nr;i++){
            double re=0,im=0,mu=0,sq=0;
            for(int j=0;j<na;j++){double v=V[i][j];re+=v*ec[j];im+=v*es[j];mu+=v;}
            re/=na;im/=na;mu/=na;
            for(int j=0;j<na;j++){double d=V[i][j]-mu;sq+=d*d;}
            A[i]=Math.hypot(re,im)/(Math.sqrt(sq/na)+1e-6);
        }
        int j0=argmax(A),jb=j0,je=j0;
        while(jb>0&&A[jb-1]>0.35*A[j0])jb--;
        while(je<nr-1&&A[je+1]>0.35*A[j0])je++;
        int ca=Integer.MAX_VALUE,cb=-1;
        for(int j=jb;j<=je;j++)if(A[j]>0.6*A[j0]){ca=Math.min(ca,j);cb=Math.max(cb,j);}
        int cb2=ca+Math.max(2,(int)Math.rint((cb-ca)*0.7));
        double[] prof=new double[na];
        for(int j=0;j<na;j++){double s=0;for(int i=ca;i<=cb2;i++)s+=V[i][j];prof[j]=s/(cb2-ca+1);}
        double re=0,im=0;for(int j=0;j<na;j++){re+=prof[j]*ec[j];im+=prof[j]*es[j];}
        double th0=pmod(Math.atan2(im,re)/60.0+D6/2,D6)-D6/2;
        double spa=2*Math.PI/na;
        int wcol=(int)Math.rint(3.0*Math.PI/180/spa);
        double[] bg=circularPercentile(prof,wcol,25);
        double[] res=new double[na];for(int j=0;j<na;j++)res[j]=prof[j]-bg[j];
        List<Tick> ticks=new ArrayList<>();
        int w=(int)Math.rint(1.5*Math.PI/180/spa);
        for(int k=0;k<60;k++){
            int c=(int)pmod(Math.rint((th0+k*D6)/spa),na);
            int[] idx=new int[2*w+1];double[] seg=new double[2*w+1];
            for(int q=0;q<idx.length;q++){idx[q]=(int)pmod(c-w+q,na);seg[q]=res[idx[q]];}
            int ip=argmax(seg);double pk=seg[ip];
            int lo=ip;while(lo>0&&seg[lo-1]>0.5*pk)lo--;
            int hi=ip;while(hi<seg.length-1&&seg[hi+1]>0.5*pk)hi++;
            double sw=0,so=0;for(int q=lo;q<=hi;q++){double ww=Math.max(0,seg[q]-0.5*pk);sw+=ww;so+=q*ww;}
            double off=so/Math.max(sw,1e-9);
            int hw=Math.max(1,(hi-lo+1)/3);
            int cc=c-w+(int)Math.rint(off);
            double[] rp=new double[nr];
            for(int i=0;i<nr;i++){double s=0;for(int q=cc-hw;q<=cc+hw;q++)s+=V[i][(int)pmod(q,na)];rp[i]=s/(2*hw+1);}
            List<Integer> bcols=new ArrayList<>();
            for(int q=cc-wcol;q<cc-wcol/2;q++)bcols.add((int)pmod(q,na));
            for(int q=cc+wcol/2;q<cc+wcol;q++)bcols.add((int)pmod(q,na));
            double[] dd=new double[nr];
            double[] tmp=new double[bcols.size()];
            for(int i=0;i<nr;i++){for(int q=0;q<tmp.length;q++)tmp[q]=V[i][bcols.get(q)];dd[i]=rp[i]-median(tmp);}
            double[] core=Arrays.copyOfRange(dd,ca,cb2+1);double dl=median(core);
            Double inner=dl>0?firstCross(dd,0.5*dl,(ca+cb2)/2,-1):null;
            Tick t=new Tick();t.k=k;t.a=(c-w+off)*spa;t.amp=pk;t.width=(hi-lo+1)*spa;
            double[] bgs=new double[idx.length];for(int q=0;q<idx.length;q++)bgs[q]=bg[idx[q]];t.bg=median(bgs);
            t.hour=k%5==0;t.rIn=inner==null?Double.NaN:interp(inner,radii);
            ticks.add(t);
        }
        for(boolean kind:new boolean[]{true,false}){
            List<Tick> T=new ArrayList<>();for(Tick t:ticks)if(t.hour==kind)T.add(t);
            double ma=median(T.stream().mapToDouble(t->t.amp).toArray()),mw=median(T.stream().mapToDouble(t->t.width).toArray());
            double mr=nanmedian(T.stream().mapToDouble(t->t.rIn).toArray());
            for(Tick t:T){
                List<Double> nb=new ArrayList<>();
                for(int dk:new int[]{-4,-3,-2,-1,1,2,3,4}){Tick o=ticks.get((int)pmod(t.k+dk,60));if(o.hour==kind)nb.add(o.amp);}
                double na_=nb.isEmpty()?ma:median(toArray(nb));
                if(!(0.6*na_<=t.amp&&t.amp<=1.6*na_)||t.amp<0.25*ma)t.bad.add("amp");
                if(!(0.6*mw<=t.width&&t.width<=1.7*mw))t.bad.add("width");
                double[] nbg=new double[6];int q=0;for(int dk:new int[]{-4,-3,-2,2,3,4})nbg[q++]=ticks.get((int)pmod(t.k+dk,60)).bg;
                if(Math.abs(t.bg-median(nbg))>Math.max(0.3*ma,12))t.bad.add("bg");
                if(!Double.isFinite(t.rIn)||Math.abs(t.rIn-mr)*rpx>3.0)t.bad.add("inner");
            }
        }
        return ticks;
    }

    /** Hour angle from a robust straight-line fit of tick angle against index over the 5 ticks either side. */
    static Double hourLine(List<Tick> ticks,int h,double rpx){
        int k0=(5*h)%60;double ref=Math.toRadians(6*k0);
        List<Double> ks=new ArrayList<>(),as=new ArrayList<>();
        for(int dk=-5;dk<=5;dk++){Tick t=ticks.get((int)pmod(k0+dk,60));if(!t.bad.isEmpty())continue;
            ks.add((double)dk);as.add(ref+pmod(t.a-ref+Math.PI,2*Math.PI)-Math.PI);}
        int n=ks.size(),left=0,right=0;for(double k:ks){if(k<0)left++;if(k>0)right++;}
        if(n<5||left<2||right<2)return null;
        double[] K=toArray(ks),Av=toArray(as);boolean[] keep=new boolean[n];Arrays.fill(keep,true);
        for(int it=0;it<3;it++){
            double[] p=polyfit1(K,Av,keep);
            double[] r=new double[n];List<Double> ra=new ArrayList<>();
            for(int i=0;i<n;i++){r[i]=Av[i]-(p[0]*K[i]+p[1]);if(keep[i])ra.add(Math.abs(r[i]));}
            double s=Math.max(1.4826*median(toArray(ra)),0.3/rpx/0.95);
            int c=0;for(int i=0;i<n;i++){keep[i]=Math.abs(r[i])<=3*s;if(keep[i])c++;}
            if(c<4)return null;
        }
        return polyfit1(K,Av,keep)[1];
    }

    // ------------------------------------------------------------------------------------------------ gate (flags.py)

    static final double HALF_DEG=14.0;

    static final class Wedge {double a0;double[][] D;ModelSpec.Shape shape;}
    static final class Ctx {double rpx,r0,dr,da;int nw;double[] radii,med;final Map<Integer,Wedge> W=new HashMap<>();
        final Map<ModelSpec.Shape,int[]> band=new HashMap<>();final List<double[]> hands=new ArrayList<>();}

    static double hourAngle(int h){return Math.toRadians((h%12)*30.0);}

    static Ctx context(Img img,double rpx,List<Input> in){
        Ctx ctx=new Ctx();ctx.rpx=rpx;
        double mn=Double.POSITIVE_INFINITY,mx=Double.NEGATIVE_INFINITY;
        for(Input m:in){if(m.yellow==null||m.yellow.length<3)continue;for(double[] p:m.yellow){double r=norm(p);mn=Math.min(mn,r);mx=Math.max(mx,r);}}
        double r0=Math.max(0.2,mn-0.06),r1=Math.min(1.0,mx+0.06),dr=0.5/rpx;
        double[] radii=arange(r0,r1+1e-12,dr);
        int na=1440;double[] ang=new double[na];for(int j=0;j<na;j++)ang[j]=j*2*Math.PI/na;
        double[][] ring=img.polar(radii,ang);
        double[] med=new double[radii.length];for(int i=0;i<radii.length;i++)med[i]=nanmedian(ring[i]);
        ctx.radii=radii;ctx.med=med;ctx.r0=r0;ctx.dr=dr;
        double da=(0.5/rpx)/((r0+r1)/2);int nw=(int)Math.rint(2*Math.toRadians(HALF_DEG)/da)+1;ctx.da=da;ctx.nw=nw;
        for(Input m:in){
            Wedge w=new Wedge();w.a0=hourAngle(m.hour)-Math.toRadians(HALF_DEG);w.shape=m.shape;
            double[] aa=new double[nw];for(int j=0;j<nw;j++)aa[j]=w.a0+j*da;
            double[][] v=img.polar(radii,aa);
            for(int i=0;i<radii.length;i++)for(int j=0;j<nw;j++){double x=v[i][j]-med[i];v[i][j]=Double.isNaN(x)?0:x;}
            w.D=v;ctx.W.put(m.hour,w);
        }
        List<Integer> all=new ArrayList<>();for(Input m:in)all.add(m.hour);
        int[] shared=learn(ctx,all);
        for(ModelSpec.Shape s:ModelSpec.Shape.values()){
            List<Integer> hs=new ArrayList<>();for(Input m:in)if(m.shape==s)hs.add(m.hour);
            if(hs.isEmpty())continue;
            int[] b=learn(ctx,hs);int pad=(int)Math.rint(0.05/dr);
            if(b==null)b=shared;
            if(b!=null)ctx.band.put(s,new int[]{Math.max(0,b[0]-pad),Math.min(radii.length-1,b[1]+pad)});
        }
        // hands: straight runs from the pivot (0.1-0.38 R) that differ from the ring median
        double[] rr=new double[40];for(int i=0;i<40;i++)rr[i]=0.1+(0.38-0.1)*i/39.0;
        double[] a2=new double[720];for(int j=0;j<720;j++)a2[j]=j*Math.PI/360;
        double[][] hv=img.polar(rr,a2);
        double[] frac=new double[720];
        for(int i=0;i<40;i++){double hm=nanmedian(hv[i]);for(int j=0;j<720;j++)if(Math.abs(hv[i][j]-hm)>0.07*255)frac[j]+=1.0/40;}
        int[] run=null;List<double[]> hands=new ArrayList<>();
        for(int i=0;i<=720;i++){
            double f=i<720?frac[i%720]:0;
            if(f>=0.85)run=run==null?new int[]{i,i}:new int[]{run[0],i};
            else if(run!=null){double wd=(run[1]-run[0]+1)*0.5;if(wd<=25)hands.add(new double[]{(run[0]+run[1])/2.0*0.5,wd});run=null;}
        }
        // how far out each hand reaches (owner, 17 Oct 2026: a short hour hand pointing at a marker does not touch it):
        // walking out from 0.38 R along the hand, the hand ends where it no longer differs from the ring median
        double[] rr2=arange(0.38,0.97,0.005);
        double[][] ring2=img.polar(rr2,a2);double[] med2=new double[rr2.length];for(int i=0;i<rr2.length;i++)med2[i]=nanmedian(ring2[i]);
        for(double[] hd:hands){
            double half=Math.min(hd[1]/2,3.0);double[] cols=new double[5];for(int j=0;j<5;j++)cols[j]=Math.toRadians(hd[0]-half+j*half/2);
            double[][] v=img.polar(rr2,cols);
            double end=rr2[0];int miss=0;
            for(int i=0;i<rr2.length;i++){
                boolean on=false;for(int j=0;j<5;j++)if(Math.abs(v[i][j]-med2[i])>0.07*255)on=true;   // a thin hand covers one column
                if(on){end=rr2[i];miss=0;}else if(++miss>=3)break;
            }
            ctx.hands.add(new double[]{hd[0],hd[1],end});
        }
        return ctx;
    }

    static int[] learn(Ctx ctx,List<Integer> hours){
        if(hours.isEmpty())return null;
        int nw=ctx.nw,c0=(int)(nw*0.34),c1=(int)Math.ceil(nw*0.66),nr=ctx.radii.length;
        double[][] prof=new double[hours.size()][nr];
        for(int k=0;k<hours.size();k++){double[][] D=ctx.W.get(hours.get(k)).D;
            for(int i=0;i<nr;i++){double m=Double.NEGATIVE_INFINITY;for(int j=c0;j<c1;j++)m=Math.max(m,D[i][j]);prof[k][i]=m;}}
        double[] mm=new double[nr];double[] col=new double[hours.size()];
        for(int i=0;i<nr;i++){for(int k=0;k<col.length;k++)col[k]=prof[k][i];mm[i]=median(col);}
        double pk=Double.NEGATIVE_INFINITY;for(double v:mm)pk=Math.max(pk,v);
        double thr=0.35*pk,best=0;int ja=-1,jb=-1,j=0;
        while(j<nr){
            if(mm[j]<=thr){j++;continue;}
            int k=j;double I=0;while(k<nr&&mm[k]>thr){I+=mm[k];k++;}
            if(I>best){best=I;ja=j;jb=k-1;}
            j=k;
        }
        return ja>=0?new int[]{ja,jb}:null;
    }

    static final class Blob {double[] xs,ys,w;String flag="";String status="";}

    static Blob blob(Ctx ctx,Input m){
        Blob out=new Blob();
        Wedge wd=ctx.W.get(m.hour);double[][] D=wd.D;
        int[] band=ctx.band.get(m.shape);
        if(band==null){out.status="no marker band";return out;}
        int lo=band[0],hi=band[1],nr=D.length,nw=ctx.nw;
        double[][] Dm=new double[nr][nw];
        List<Double> pos=new ArrayList<>();
        for(int i=0;i<nr;i++)for(int j=0;j<nw;j++){double v=(i<lo||i>hi)?0:D[i][j];Dm[i][j]=v;if(v>0)pos.add(v);}
        double peak=pos.isEmpty()?0:percentile(toArray(pos),98);
        if(peak<0.04*255){out.status="no contrast";return out;}
        double T=Math.max(0.5*peak,0.03*255);
        Mat bin=new Mat(nr,nw,CvType.CV_8U),lab=new Mat();
        byte[] bb=new byte[nr*nw];for(int i=0;i<nr;i++)for(int j=0;j<nw;j++)bb[i*nw+j]=(byte)(Dm[i][j]>T?1:0);
        bin.put(0,0,bb);
        int n=Imgproc.connectedComponents(bin,lab,4,CvType.CV_32S);
        int[] L=new int[nr*nw];lab.get(0,0,L);bin.release();lab.release();
        List<List<Integer>> comp=new ArrayList<>();for(int k=0;k<n;k++)comp.add(new ArrayList<>());
        for(int p=0;p<L.length;p++)if(L[p]>0)comp.get(L[p]).add(p);
        double h0=hourAngle(m.hour);
        double bestSc=Double.NEGATIVE_INFINITY;List<Integer> best=null;boolean bSide=false,bInner=false;
        for(int k=1;k<n;k++){
            List<Integer> sel=comp.get(k);if(sel.size()<24)continue;
            double sw=0,sx=0,sy=0;int jmin=Integer.MAX_VALUE,jmax=-1,imin=Integer.MAX_VALUE,imax=-1;
            for(int p:sel){int i=p/nw,j=p%nw;double r=ctx.radii[i],a=wd.a0+j*ctx.da,ww=Dm[i][j]*r;
                sw+=ww;sx+=ww*r*Math.sin(a);sy+=ww*(-r*Math.cos(a));
                jmin=Math.min(jmin,j);jmax=Math.max(jmax,j);imin=Math.min(imin,i);imax=Math.max(imax,i);}
            double mx=sx/sw,my=sy/sw;
            double dev=Math.toDegrees(pmod(Math.atan2(mx,-my)-h0+Math.PI,2*Math.PI)-Math.PI);
            boolean side=jmin==0||jmax==nw-1;
            boolean inner=imin<=lo;
            if(!inner&&imax>=hi&&hi<nr-1){for(int p:sel){int i=p/nw,j=p%nw;if(i==hi&&D[hi+1][j]>T){inner=true;break;}}}
            double sc=sw/(1+Math.abs(dev)/4)*(side?0.08:1);
            if(best==null||sc>bestSc){bestSc=sc;best=sel;bSide=side;bInner=inner;}
        }
        if(best==null){out.status="not found";return out;}
        int q=best.size();out.xs=new double[q];out.ys=new double[q];out.w=new double[q];
        for(int t=0;t<q;t++){int p=best.get(t),i=p/nw,j=p%nw;double r=ctx.radii[i],a=wd.a0+j*ctx.da;
            out.xs[t]=r*Math.sin(a);out.ys[t]=-r*Math.cos(a);out.w[t]=Math.min(1,Math.max(0,Dm[i][j]/peak))*r;}
        out.flag=bInner?"obstructed":bSide?"merged":"";
        return out;
    }

    /** weighted moments: mx, my, axis angle, length, width */
    static double[] moments(double[] xs,double[] ys,double[] w){
        double W=0,mx=0,my=0;for(int i=0;i<xs.length;i++){W+=w[i];mx+=w[i]*xs[i];my+=w[i]*ys[i];}mx/=W;my/=W;
        double cxx=0,cyy=0,cxy=0;for(int i=0;i<xs.length;i++){double dx=xs[i]-mx,dy=ys[i]-my;cxx+=w[i]*dx*dx;cyy+=w[i]*dy*dy;cxy+=w[i]*dx*dy;}
        cxx/=W;cyy/=W;cxy/=W;
        double tr=cxx+cyy,det=cxx*cyy-cxy*cxy,disc=Math.sqrt(Math.max(0,tr*tr/4-det));
        double l1=tr/2+disc,l2=Math.max(1e-12,tr/2-disc);
        return new double[]{mx,my,0.5*Math.atan2(2*cxy,cxx-cyy),2*Math.sqrt(3*l1),2*Math.sqrt(3*l2)};
    }

    static final class Flags {String status="";String flag="";double len,width;}

    static Flags measureFlags(Ctx ctx,Input m,double inset,Map<ModelSpec.Shape,double[]> sib){
        Flags f=new Flags();
        Blob b=blob(ctx,m);
        if(b.xs==null){f.status=b.status;return f;}
        double[] mo=moments(b.xs,b.ys,b.w);
        f.len=mo[3];f.width=mo[4];
        String flag=b.flag;
        if(sib!=null&&flag.isEmpty()&&sib.containsKey(m.shape)){
            double[] s=sib.get(m.shape);
            if(mo[4]>1.6*s[1]||mo[4]<0.6*s[1]||mo[3]>1.3*s[0]||mo[3]<0.42*s[0])flag="unusual";
        }
        if(flag.isEmpty()){
            double ang=pmod(Math.toDegrees(Math.atan2(mo[0],-mo[1])),360);
            double rIn=Double.POSITIVE_INFINITY;for(double[] p:m.yellow)rIn=Math.min(rIn,norm(p));
            for(double[] hd:ctx.hands)if(Math.abs(pmod(ang-hd[0]+180,360)-180)<=hd[1]/2+4&&hd[2]>=rIn-0.02)flag="hand";
        }
        if(m.shape!=ModelSpec.Shape.ROUND){
            double[][] poly=template(m,inset);double[] cp=areaCentroid(poly);
            double half=0;for(double[] p:poly)half=Math.max(half,Math.max(Math.abs(p[0]-cp[0]),Math.abs(p[1]-cp[1])));half*=1.2;
            Mat tm=Mat.zeros(801,801,CvType.CV_8U);
            Point[] pts=new Point[poly.length];
            for(int i=0;i<poly.length;i++)pts[i]=new Point(Math.rint(((poly[i][0]-cp[0])/half*400+400)*16),Math.rint(((poly[i][1]-cp[1])/half*400+400)*16));
            List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(pts));
            Imgproc.fillPoly(tm,l,new Scalar(1),Imgproc.LINE_8,4,new Point(0,0));
            byte[] tb=new byte[801*801];tm.get(0,0,tb);tm.release();
            List<Double> ii=new ArrayList<>(),jj=new ArrayList<>();
            for(int p=0;p<tb.length;p++)if(tb[p]!=0){jj.add((double)(p/801));ii.add((double)(p%801));}
            double[] one=new double[ii.size()];Arrays.fill(one,1);
            double at=moments(toArray(ii),toArray(jj),one)[2];
            double th=pmod(mo[2]-at+Math.PI/2,Math.PI)-Math.PI/2;
            if(Math.abs(Math.toDegrees(th))>25&&flag.isEmpty())flag="unusual";
        }
        f.flag=flag;f.status=flag.isEmpty()?"measured":"withheld: "+flag;
        return f;
    }

    static Map<ModelSpec.Shape,double[]> siblings(Ctx ctx,List<Input> in,Map<ModelSpec.Shape,Double> insets){
        Map<ModelSpec.Shape,List<double[]>> by=new HashMap<>();
        for(Input m:in){
            if(m.yellow==null||m.yellow.length<3||!insets.containsKey(m.shape))continue;
            Flags f=measureFlags(ctx,m,insets.get(m.shape),null);
            if(f.status.equals("measured"))by.computeIfAbsent(m.shape,k->new ArrayList<>()).add(new double[]{f.len,f.width});
        }
        Map<ModelSpec.Shape,double[]> out=new HashMap<>();
        for(Map.Entry<ModelSpec.Shape,List<double[]>> e:by.entrySet()){
            List<double[]> L=e.getValue();if(L.size()<3)continue;
            double[] a=new double[L.size()],b=new double[L.size()];for(int i=0;i<L.size();i++){a[i]=L.get(i)[0];b[i]=L.get(i)[1];}
            out.put(e.getKey(),new double[]{median(a),median(b)});
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ lume centre

    static final class Centroid {String status;double[] cpoly,d;double th;double sizeRatio=Double.NaN;}

    /** The lume's brightness-weighted centre (region connected to the marker's middle), area-checked; batons and the
     *  triangle also give their tilt from the region's principal axis against the template's. */
    static Centroid centroidFit(Img img,Input m,double inset,double rpx){
        Centroid out=new Centroid();
        double[][] poly=template(m,inset);
        int n=poly.length;
        double[] cpoly=m.shape==ModelSpec.Shape.ROUND?mean(poly):areaCentroid(poly);
        double A0=Math.abs(area(poly));
        double[] lv=levels(img,poly,cpoly,rpx,inset);
        if(lv==null){out.status="too close to the photo edge";return out;}
        double lume=lv[0],dial=lv[1];
        if(lume-dial<25){out.status="marker not distinguishable from the dial";return out;}
        double T=dial+0.5*(lume-dial);
        double half=0;for(double[] p:poly)half=Math.max(half,Math.hypot(p[0]-cpoly[0],p[1]-cpoly[1]));half+=inset+6.0/rpx;
        double st=0.5/rpx;
        double[] g=arange(-half,half+1e-12,st);int N=g.length;
        double[] X=new double[N*N],Y=new double[N*N];
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){X[r*N+c]=cpoly[0]+g[c];Y[r*N+c]=cpoly[1]+g[r];}
        double[] v=img.sampleCanon(X,Y);
        for(double x:v)if(!Double.isFinite(x)){out.status="too close to the photo edge";return out;}
        Mat mask=new Mat(N,N,CvType.CV_8U),lab=new Mat();
        byte[] mb=new byte[N*N];for(int p=0;p<mb.length;p++)mb[p]=(byte)(v[p]>=T?1:0);mask.put(0,0,mb);
        Imgproc.connectedComponents(mask,lab,4,CvType.CV_32S);
        int[] L=new int[N*N];lab.get(0,0,L);mask.release();lab.release();
        Mat inner=Mat.zeros(N,N,CvType.CV_8U);
        Point[] qq=new Point[n];
        for(int i=0;i<n;i++){double qx=cpoly[0]+0.5*(poly[i][0]-cpoly[0]),qy=cpoly[1]+0.5*(poly[i][1]-cpoly[1]);
            qq[i]=new Point((int)((qx-(cpoly[0]-half))/st),(int)((qy-(cpoly[1]-half))/st));}
        List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(qq));
        Imgproc.fillPoly(inner,l,new Scalar(1));
        byte[] ib=new byte[N*N];inner.get(0,0,ib);inner.release();
        Map<Integer,Integer> counts=new HashMap<>();
        for(int p=0;p<L.length;p++)if(ib[p]!=0&&L[p]>0)counts.merge(L[p],1,Integer::sum);
        if(counts.isEmpty()){out.status="its lume is not visible";return out;}
        List<Integer> ids=new ArrayList<>(counts.keySet());Collections.sort(ids);
        int k=ids.get(0);for(int id:ids)if(counts.get(id)>counts.get(k))k=id;
        double[] w=new double[N*N];double sw=0,sx=0,sy=0;
        for(int p=0;p<w.length;p++){if(L[p]!=k)continue;double x=Math.min(1,Math.max(0,(v[p]-dial)/(lume-dial)));w[p]=x;sw+=x;sx+=x*X[p];sy+=x*Y[p];}
        double areaR=sw*st*st;out.sizeRatio=areaR/A0;
        double cx=sx/sw,cy=sy/sw;
        double[] d=new double[]{cx-cpoly[0],cy-cpoly[1]};
        double th=0;
        if(m.shape!=ModelSpec.Shape.ROUND){
            Mat tm=Mat.zeros(N,N,CvType.CV_8U);
            Point[] pp=new Point[n];
            for(int i=0;i<n;i++)pp[i]=new Point(Math.rint((poly[i][0]-(cpoly[0]-half))/st*16),Math.rint((poly[i][1]-(cpoly[1]-half))/st*16));
            List<MatOfPoint> l2=new ArrayList<>();l2.add(new MatOfPoint(pp));
            Imgproc.fillPoly(tm,l2,new Scalar(1),Imgproc.LINE_8,4,new Point(0,0));
            byte[] tb=new byte[N*N];tm.get(0,0,tb);tm.release();
            double[] wt=new double[N*N];for(int p=0;p<wt.length;p++)wt[p]=tb[p]!=0?1:0;
            double at=axis(wt,X,Y,cpoly[0],cpoly[1]),am=axis(w,X,Y,cx,cy);
            th=pmod(am-at+Math.PI/2,Math.PI)-Math.PI/2;
        }
        double per=0;for(int i=0;i<n;i++){double[] a=poly[i],b=poly[(i+1)%n];per+=Math.hypot(b[0]-a[0],b[1]-a[1]);}
        boolean ok=0.88<=out.sizeRatio&&out.sizeRatio<=1.0+2*inset*per/A0+0.05;
        out.status=ok?"measured":"size";out.cpoly=cpoly;out.d=d;out.th=th;
        return out;
    }

    static double axis(double[] w,double[] X,double[] Y,double cx,double cy){
        double m20=0,m02=0,m11=0;
        for(int p=0;p<w.length;p++){if(w[p]==0)continue;double dx=X[p]-cx,dy=Y[p]-cy;m20+=w[p]*dx*dx;m02+=w[p]*dy*dy;m11+=w[p]*dx*dy;}
        return 0.5*Math.atan2(2*m11,m20-m02);
    }

    /** lume level (60th percentile inside the inner 60 % of the template), dial level (30th percentile of a band just
     *  outside it); null when either has fewer than 10 samples. */
    static double[] levels(Img img,double[][] poly,double[] c,double rpx,double outside){
        int n=poly.length;
        double lx=Double.POSITIVE_INFINITY,ly=lx,hx=Double.NEGATIVE_INFINITY,hy=hx;
        for(double[] p:poly){lx=Math.min(lx,p[0]);ly=Math.min(ly,p[1]);hx=Math.max(hx,p[0]);hy=Math.max(hy,p[1]);}
        lx-=0.05+outside;ly-=0.05+outside;hx+=0.05+outside;hy+=0.05+outside;
        double[] gx=arange(lx,hx,1.0/rpx),gy=arange(ly,hy,1.0/rpx);
        double[][] inner=new double[n][];for(int i=0;i<n;i++)inner[i]=new double[]{c[0]+0.6*(poly[i][0]-c[0]),c[1]+0.6*(poly[i][1]-c[1])};
        MatOfPoint2f pin=new MatOfPoint2f(hull(inner)),pout=new MatOfPoint2f(hull(poly));
        List<double[]> pts=new ArrayList<>();
        long idx=0;
        for(double x:gx)for(double y:gy){if(idx++%7==0)pts.add(new double[]{x,y});}
        double[] xs=new double[pts.size()],ys=new double[pts.size()];for(int i=0;i<xs.length;i++){xs[i]=pts.get(i)[0];ys[i]=pts.get(i)[1];}
        double[] vals=img.sampleCanon(xs,ys);
        List<Double> ins=new ArrayList<>(),band=new ArrayList<>();
        for(int i=0;i<xs.length;i++){
            if(!Double.isFinite(vals[i]))continue;
            Point p=new Point(xs[i],ys[i]);
            if(Imgproc.pointPolygonTest(pin,p,false)>0)ins.add(vals[i]);
            double dd=Imgproc.pointPolygonTest(pout,p,true);
            if(dd<-0.008-outside&&dd>-0.025-outside)band.add(vals[i]);
        }
        pin.release();pout.release();
        if(ins.size()<10||band.size()<10)return null;
        return new double[]{percentile(toArray(ins),60),percentile(toArray(band),30)};
    }

    /** Luminous outline expected inside the yellow outer-metal reference: the yellow inset by the genuine inset. */
    static double[][] template(Input m,double d){
        double[][] poly=m.yellow;int n=poly.length;
        if(m.shape==ModelSpec.Shape.ROUND){
            double[] c=mean(poly);double r=0;for(double[] p:poly)r+=Math.hypot(p[0]-c[0],p[1]-c[1]);r/=n;
            double k=(r-d)/r;double[][] o=new double[n][];for(int i=0;i<n;i++)o[i]=new double[]{c[0]+(poly[i][0]-c[0])*k,c[1]+(poly[i][1]-c[1])*k};return o;
        }
        double[] c=mean(poly);double[][] N=new double[n][];double[] O=new double[n];
        for(int i=0;i<n;i++){double[] a=poly[i],b=poly[(i+1)%n];double L=Math.hypot(b[0]-a[0],b[1]-a[1]);
            double tx=(b[0]-a[0])/L,ty=(b[1]-a[1])/L;double nx=ty,ny=-tx;
            if(nx*((a[0]+b[0])/2-c[0])+ny*((a[1]+b[1])/2-c[1])<0){nx=-nx;ny=-ny;}
            N[i]=new double[]{nx,ny};O[i]=nx*a[0]+ny*a[1]-d;}
        double[][] out=new double[n][];
        for(int i=0;i<n;i++){int p=(i+n-1)%n;
            double a11=N[p][0],a12=N[p][1],a21=N[i][0],a22=N[i][1],det=a11*a22-a12*a21;
            out[i]=new double[]{(O[p]*a22-a12*O[i])/det,(a11*O[i]-O[p]*a21)/det};}
        return out;
    }

    // ------------------------------------------------------------------------------------------------ image access

    /** Grey float image + pose; bilinear sampling, NaN outside the photo. */
    static final class Img {
        final Mat f;final double[] H;
        Img(Mat f,double[] H){this.f=f;this.H=H;}
        /** grey values row-major (unit tests: no OpenCV needed) */
        Img(float[] px,int w,int h,double[] H){this.f=null;this.H=H;this.px=px;this.w=w;this.h=h;}
        double[] project(double x,double y){double w=H[6]*x+H[7]*y+H[8];return new double[]{(H[0]*x+H[1]*y+H[2])/w,(H[3]*x+H[4]*y+H[5])/w};}
        double pxBetween(double[] p,double[] q){double[] a=project(p[0],p[1]),b=project(q[0],q[1]);return Math.hypot(b[0]-a[0],b[1]-a[1]);}
        double[] sampleCanon(double[] X,double[] Y){
            double[] px=new double[X.length],py=new double[X.length];
            for(int i=0;i<X.length;i++){double[] p=project(X[i],Y[i]);px[i]=p[0];py[i]=p[1];}
            return samplePx(px,py);
        }
        /** Bilinear grey values at photo px (exact interpolation; NaN when a neighbour lies outside the photo). */
        double[] samplePx(double[] x,double[] y){
            if(px==null){w=f.cols();h=f.rows();px=new float[w*h];f.get(0,0,px);}
            double[] out=new double[x.length];
            for(int i=0;i<x.length;i++){
                float fx=(float)x[i],fy=(float)y[i];
                int x0=(int)Math.floor(fx),y0=(int)Math.floor(fy);
                if(x0<0||y0<0||x0+1>=w||y0+1>=h){out[i]=Double.NaN;continue;}
                float ax=fx-x0,ay=fy-y0;int o=y0*w+x0;
                out[i]=(1-ay)*((1-ax)*px[o]+ax*px[o+1])+ay*((1-ax)*px[o+w]+ax*px[o+w+1]);
            }
            return out;
        }
        private float[] px;private int w,h;
        /** values[i][j] at radius radii[i], angle ang[j] (canonical: x = r sin a, y = -r cos a) */
        double[][] polar(double[] radii,double[] ang){
            int nr=radii.length,na=ang.length;double[] X=new double[nr*na],Y=new double[nr*na];
            for(int i=0;i<nr;i++)for(int j=0;j<na;j++){X[i*na+j]=radii[i]*Math.sin(ang[j]);Y[i*na+j]=-radii[i]*Math.cos(ang[j]);}
            double[] v=sampleCanon(X,Y);double[][] out=new double[nr][na];
            for(int i=0;i<nr;i++)System.arraycopy(v,i*na,out[i],0,na);
            return out;
        }
    }

    // ------------------------------------------------------------------------------------------------ numerics (numpy semantics)

    static double[] cpt(double r,double a){return new double[]{r*Math.sin(a),-r*Math.cos(a)};}
    static double norm(double[] p){return Math.hypot(p[0],p[1]);}
    static double pmod(double a,double m){double r=a%m;return r<0?r+m:r;}
    static double wrapPi(double a){return pmod(a+Math.PI,2*Math.PI)-Math.PI;}
    static double wrapHalfPi(double a){return pmod(a+Math.PI/2,Math.PI)-Math.PI/2;}
    /** numpy.arange(start, stop, step) */
    static double[] arange(double a,double b,double step){int n=(int)Math.ceil((b-a)/step);if(n<0)n=0;double[] o=new double[n];for(int i=0;i<n;i++)o[i]=a+i*step;return o;}
    static double[] toArray(List<Double> l){double[] a=new double[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);return a;}
    /** numpy.percentile (linear) */
    static double percentile(double[] v,double q){
        double[] s=v.clone();Arrays.sort(s);if(s.length==0)return Double.NaN;
        double pos=(s.length-1)*q/100.0;int lo=(int)Math.floor(pos);int hi=Math.min(lo+1,s.length-1);
        return s[lo]+(s[hi]-s[lo])*(pos-lo);
    }
    static double median(double[] v){return percentile(v,50);}
    static double nanmedian(double[] v){int n=0;for(double x:v)if(!Double.isNaN(x))n++;double[] a=new double[n];n=0;for(double x:v)if(!Double.isNaN(x))a[n++]=x;return median(a);}
    static double nanmedian(double[][] v){List<Double> l=new ArrayList<>();for(double[] r:v)for(double x:r)if(!Double.isNaN(x))l.add(x);return median(toArray(l));}
    static int argmax(double[] a){int k=0;for(int i=1;i<a.length;i++)if(a[i]>a[k])k=i;return k;}
    static double interp(double x,double[] fp){int i=(int)Math.floor(x);if(i<0)return fp[0];if(i>=fp.length-1)return fp[fp.length-1];return fp[i]+(fp[i+1]-fp[i])*(x-i);}
    static Double firstCross(double[] p,double thr,int start,int step){
        int i=start;
        while(0<=i+step&&i+step<p.length){double a=p[i],b=p[i+step];
            if((a-thr)*(b-thr)<=0&&a!=b)return i+step*(a-thr)/(a-b);i+=step;}
        return null;
    }
    /** scipy.ndimage.percentile_filter on a circular signal (window 2*half+1, rank int(size*q/100)) */
    static double[] circularPercentile(double[] x,int half,double q){
        int n=x.length,size=2*half+1,rank=(int)(size*q/100.0);double[] out=new double[n],win=new double[size];
        for(int i=0;i<n;i++){for(int k=0;k<size;k++)win[k]=x[(int)pmod(i-half+k,n)];Arrays.sort(win);out[i]=win[rank];}
        return out;
    }
    /** least-squares straight line through the kept points: {slope, intercept} */
    static double[] polyfit1(double[] x,double[] y,boolean[] keep){
        double n=0,sx=0,sy=0,sxx=0,sxy=0;
        for(int i=0;i<x.length;i++){if(!keep[i])continue;n++;sx+=x[i];sy+=y[i];sxx+=x[i]*x[i];sxy+=x[i]*y[i];}
        double b=(n*sxy-sx*sy)/(n*sxx-sx*sx);return new double[]{b,(sy-b*sx)/n};
    }
    static double[] mean(double[][] p){double x=0,y=0;for(double[] q:p){x+=q[0];y+=q[1];}return new double[]{x/p.length,y/p.length};}
    static double area(double[][] p){double a=0;int n=p.length;for(int i=0;i<n;i++){double[] u=p[i],v=p[(i+1)%n];a+=u[0]*v[1]-v[0]*u[1];}return a/2;}
    static double[] areaCentroid(double[][] p){
        int n=p.length;double A=0,cx=0,cy=0;
        for(int i=0;i<n;i++){double[] u=p[i],v=p[(i+1)%n];double cr=u[0]*v[1]-v[0]*u[1];A+=cr;cx+=(u[0]+v[0])*cr;cy+=(u[1]+v[1])*cr;}
        A/=2;return new double[]{cx/(6*A),cy/(6*A)};
    }
    /** convex hull (monotone chain); the point-in-polygon test is orientation-independent */
    static Point[] hull(double[][] pts){
        double[][] p=pts.clone();Arrays.sort(p,(a,b)->a[0]!=b[0]?Double.compare(a[0],b[0]):Double.compare(a[1],b[1]));
        int n=p.length,k=0;double[][] h=new double[2*n][];
        for(int i=0;i<n;i++){while(k>=2&&cross(h[k-2],h[k-1],p[i])<=0)k--;h[k++]=p[i];}
        for(int i=n-2,t=k+1;i>=0;i--){while(k>=t&&cross(h[k-2],h[k-1],p[i])<=0)k--;h[k++]=p[i];}
        Point[] out=new Point[Math.max(1,k-1)];for(int i=0;i<out.length;i++)out[i]=new Point(h[i][0],h[i][1]);return out;
    }
    static double cross(double[] o,double[] a,double[] b){return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0]);}

    // ------------------------------------------------------------------------------------------------ text

    static String fmtPct(double v){return String.format(Locale.US,"%.2f%%",Math.abs(v));}

    /** What is clearly off, in plain words (screen-independent: along / across the hour line). */
    static String headline(Marker r){
        List<String> s=new ArrayList<>();
        if(r.tiltOff)s.add("tilted "+String.format(Locale.US,"%.1f°",Math.abs(r.tiltDeg))+(r.tiltDeg>0?" clockwise":" anticlockwise"));
        if(r.posOff)s.add("sits "+fmtPct(r.posPct)+" of the dial radius from where it should be ("+where(r)+")");
        if(r.sideOff)s.add("sits "+fmtPct(r.sidePct)+" of the dial radius "+(r.sidePct>0?"clockwise":"anticlockwise")+" of its hour line");
        if(r.inoutOff)s.add("sits "+fmtPct(r.inoutPct)+" of the dial radius too far "+(r.inoutPct>0?"out (towards the minute track)":"in (towards the centre)"));
        if(r.gapOff)s.add("leaves only "+fmtPct(r.gapPct)+" of the dial radius clear under the minute track (clearly off below "+fmtPct(r.gapLimitPct)+")");
        return s.isEmpty()?"":AlignmentDecision.capital(String.join("; ",s));
    }

    /** the components of a position reading in plain words, the larger first */
    static String where(Marker r){
        List<String> w=new ArrayList<>();
        String side=Double.isFinite(r.sidePct)?fmtPct(r.sidePct)+(r.sidePct>0?" clockwise of its hour line":" anticlockwise of its hour line"):null;
        String io=Double.isFinite(r.inoutPct)?fmtPct(r.inoutPct)+(r.inoutPct>0?" too far out":" too far in"):null;
        boolean sideFirst=!Double.isFinite(r.inoutPct)||(Double.isFinite(r.sidePct)&&Math.abs(r.sidePct)>=Math.abs(r.inoutPct));
        if(sideFirst){if(side!=null)w.add(side);if(io!=null)w.add(io);}else{if(io!=null)w.add(io);if(side!=null)w.add(side);}
        return String.join(", ",w);
    }

    /** Every measured number for the details. */
    static String numbers(Marker r){
        StringBuilder b=new StringBuilder();
        boolean tri=r.shape==ModelSpec.Shape.TRIANGLE;
        if(!tri&&Double.isFinite(r.posPct))b.append(String.format(Locale.US,"From where it should be: %.2f%% of the dial radius; clearly off beyond %.2f%%.",r.posPct,r.posLimitPct));
        if(Double.isFinite(r.sidePct)){if(b.length()>0)b.append('\n');
            b.append(String.format(Locale.US,"Sideways from the hour line: %+.2f%% of the dial radius (%+.1f px)",r.sidePct,r.sidePx))
             .append(tri?String.format(Locale.US,"; clearly off beyond %.2f%%.",r.sideLimitPct):".");}
        if(Double.isFinite(r.inoutPct))b.append(String.format(Locale.US,"\nIn/out from the ring: %+.2f%% (%+.1f px)",r.inoutPct,r.inoutPx))
             .append(tri?String.format(Locale.US,"; clearly off beyond %.2f%%.",r.inoutLimitPct):".");
        if(Double.isFinite(r.gapPct))b.append(String.format(Locale.US,"\nGap under the minute track: %.2f%% (%.1f px); clearly off below %.2f%%.",r.gapPct,r.gapPx,r.gapLimitPct));
        if(tri&&r.source.equals("outer metal"))b.append(Double.isFinite(r.tiltDeg)?String.format(Locale.US,"\nRotation (fitted outline's top edge against the line through the inner ends of minute ticks 59 and 1): %+.2f°; clearly off beyond %s.%s",
                r.tiltDeg,Double.isFinite(r.tiltLimitDeg)?String.format(Locale.US,"%.2f°",r.tiltLimitDeg):"(not judged)",r.rotNote.isEmpty()?"":" ("+r.rotNote+")")
                :"\nRotation: not measured.");
        else if(tri)b.append(Double.isFinite(r.tiltDeg)?String.format(Locale.US,"\nRotation (top edge against the line through the inner ends of minute ticks 59 and 1): %+.2f°; clearly off beyond %s. Halves %+.2f° / %+.2f°, edge fit %.2f px.",
                r.tiltDeg,Double.isFinite(r.tiltLimitDeg)?String.format(Locale.US,"%.2f°",r.tiltLimitDeg):"(not judged)",r.rotLeftDeg,r.rotRightDeg,r.rotRmsPx)
                :"\nRotation: not measured ("+(r.rotNote.isEmpty()?"withheld":r.rotNote)+").");
        else if(r.shape!=ModelSpec.Shape.ROUND&&Double.isFinite(r.tiltDeg)){
            b.append(String.format(Locale.US,"\nTilt (long edges against the line from its hour tick to the opposite one): %+.2f°",r.tiltDeg));
            b.append(Double.isFinite(r.tiltLimitDeg)?String.format(Locale.US,"; clearly off beyond %.2f°.",r.tiltLimitDeg):" (not judged).");
        }
        if(r.gapFromFit)b.append(" (top edge placed from the rest of its outline)");
        if(r.source.equals("outer metal")&&Double.isFinite(r.cover))
            b.append(String.format(Locale.US,"\nMeasured from its outer metal: %.0f%% of the outline seen, edge fit %.2f px, %.0f%% of the seen edge inconsistent, position ± %.2f%%%s.",
                100*r.cover,r.fitRmsPx,100*r.badFrac,r.sePosPct,Double.isFinite(r.seRotDeg)?String.format(Locale.US,", rotation ± %.2f°",r.seRotDeg):""));
        if(!r.uncertain.isEmpty())b.append("\nUncertain: ").append(r.uncertain).append('.');
        if(!r.gate.isEmpty())b.append(b.length()>0?"\n":"").append("Withheld: ").append(String.join("; ",r.gate)).append('.');
        return b.toString();
    }

    static String technical(Result res){
        StringBuilder s=new StringBuilder("Clearly-off check (hour lines from the printed minute track; ring from the other round markers).\n");
        s.append(String.format(Locale.US,"Dial radius %.0f px · minute ticks rejected %d/60 · track scale %.4f · marker ring turned %+.2f%% against the track\n",res.rpx,res.badTicks,res.trackScale,res.ringTurnPct));
        for(Marker r:res.markers){
            s.append('\n').append(r.hour).append(" o'clock (").append(AlignmentReference.key(r.shape)).append("): ")
             .append(r.state==State.CLEARLY_OFF?"Clearly off":r.state==State.POSSIBLE?"Possible misalignment - inspect":r.state==State.NO_VISIBLE_ISSUE?"No visible issue":"Can't check");
            if(r.state==State.CANT_CHECK)s.append(" - ").append(r.reason);
            String n=numbers(r);if(!n.isEmpty())s.append("\n  ").append(n.replace("\n","\n  "));
        }
        return s.toString();
    }
}
