package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Watch Align's alignment-only answer for one marker: No defect, Defect detected, or Cannot assess.
 *
 * Owner requirement (14 Oct 2026, BUILD_BRIEF section 2), superseding the multi-level statuses for the main display:
 * for an assessable marker the answer is binary. A component (radial position, tangential position, rotation) is a
 * defect only when its deviation from the per-hour genuine nominal exceeds the WHOLE genuine envelope for that marker
 * type (the largest deviation of any verified genuine watch, one value per physical watch per marker, after a documented
 * far-out measurement-failure screen) by more than the measurement uncertainty: 2 x the combination of this photo's fit
 * standard error (including the known-size uncertainty of inferred edges) and the genuine photo-to-photo repeatability
 * of that component, never below half a native pixel. Crossing the genuine nominal, or a tiny excursion within the photo's
 * uncertainty, is never a defect. Replica/QC photos never set any of these numbers.
 *
 * Round markers only (owner, 14 Oct 2026): a round marker's position must ALSO be visibly displaced. A radial or
 * tangential component of a round is a defect only when it exceeds the genuine envelope plus uncertainty AND its absolute
 * deviation from the genuine nominal exceeds ROUND_VISUAL_FLOOR_R (1.00 % of the dial radius). The two conditions are
 * combined as the larger threshold, never added together. This is a product acceptability choice: the genuine
 * references are unchanged, and triangles and batons are not affected.
 *
 * Severity is compared as visible displacement: a rotation counts as the distance it moves the marker's far end.
 * The dominant issue is the largest excess beyond the genuine envelope; others are shown below it as context.
 */
final class AlignmentDecision {
    enum Outcome{NO_DEFECT,DEFECT,CANNOT_ASSESS}

    /** Native pixels below which no difference is resolvable (photo resolution floor of the uncertainty). */
    static final double MIN_RESOLVABLE_PX=0.5;
    /** Dial radius below which luminous edges and their dark separators are too few pixels to measure (genuine readings
     *  that set the first envelopes clustered at 150-200 px). */
    static final double MIN_DIAL_RADIUS_PX=200;
    /** Minimum visible displacement for a ROUND marker's position (radial or tangential) to be a defect: 1.00 % of the
     *  dial radius from its genuine reference position. Smaller round displacements are visually acceptable even when
     *  they lie beyond measured genuine variation. Applies to round markers only. */
    static final double ROUND_VISUAL_FLOOR_R=0.010;

    static final class Issue {
        LuminousAlignment.Param param;
        double deviation;     // R for positions, degrees for rotation (signed)
        double limit;         // genuine envelope (same unit)
        double uncertainty;   // 2 SE / resolution floor (same unit)
        double excessR;       // visible excess beyond the decision threshold, R
        double visualFloor;   // minimum visible displacement (same unit; 0 when none applies)
        double rpx;double[] screen;   // unit screen direction of a positional deviation
        /** the outer metal surround (MetalAlignment), not the luminous fill */
        boolean metal;
        boolean outside(){return excessR>0;}
        String headline(){
            String who=metal?"Outer metal ":"";
            if(param==LuminousAlignment.Param.ROTATION)return (metal?who+"rotated ":"Rotated ")+(deviation>0?"clockwise":"anticlockwise");
            String w=words(screen);
            return (metal?who+"positioned":"Positioned")+" too "+w;
        }
        String measured(){
            String what=metal?"outer-metal ":"";
            if(param==LuminousAlignment.Param.ROTATION)
                return String.format(Locale.US,"Measured %srotation: %.1f° %s from the genuine position.",what,Math.abs(deviation),deviation>0?"clockwise":"anticlockwise");
            return String.format(Locale.US,"Measured %sshift: %.2f%% of the dial radius (about %.1f px in this photo).",what,100*Math.abs(deviation),Math.abs(deviation)*rpx);
        }
        String genuine(){
            String what=metal?" (outer metal)":"";
            if(param==LuminousAlignment.Param.ROTATION)return String.format(Locale.US,"Genuine Rolex range%s: up to %.1f° either way.",what,limit);
            String g=String.format(Locale.US,"Genuine Rolex range%s: up to %.2f%% of the dial radius (about %.1f px here).",what,100*limit,limit*rpx);
            if(visualFloor>0)g+=String.format(Locale.US," Round markers are reported only when displaced by more than %.2f%% of the dial radius.",100*visualFloor);
            return g;
        }
    }

    static final class Marker {
        final int hour;final ModelSpec.Shape shape;
        Outcome outcome=Outcome.CANNOT_ASSESS;String reason="";
        Issue primary;final List<Issue> secondary=new ArrayList<>();
        /** components measured within the genuine range, and components not determinable */
        final List<Issue> within=new ArrayList<>();final List<String> notAssessed=new ArrayList<>();
        LuminousAlignment.Result fit;
        /** yellow outer-metal reference (ring of the other markers), canonical */
        double[][] yellow;
        double bevelR=Double.NaN,photoGrowthR=Double.NaN;
        /** outer-metal alignment (triangle, batons): clear deviations, and plain notes (inconclusive / not assessed) */
        final List<Issue> metal=new ArrayList<>();final List<String> metalNotes=new ArrayList<>();
        /** an outer-metal reading beyond every genuine watch but within this photo's uncertainty (not a confirmed defect) */
        boolean metalBorderline;
        /** display only: where the genuine luminous shape is expected (ring of the other markers + per-hour genuine nominal) */
        double[][] expectedPolygon;double[] expectedCircle;
        Marker(int hour,ModelSpec.Shape shape){this.hour=hour;this.shape=shape;}
        String title(){return hour+" o'clock";}
        /** No defect confirmed, but a borderline outer-metal reading to look at. */
        boolean worthALook(){return outcome==Outcome.NO_DEFECT&&metalBorderline;}
        String label(){return outcome==Outcome.DEFECT?"Defect detected":worthALook()?"Worth a look":outcome==Outcome.NO_DEFECT?"No defect":"Cannot assess";}
    }

    private AlignmentDecision(){}

    static void decide(Marker m,AlignmentReference ref){
        LuminousAlignment.Result f=m.fit;
        if(f==null){m.outcome=Outcome.CANNOT_ASSESS;if(m.reason.isEmpty())m.reason="not measured";return;}
        if(!f.assessed){m.outcome=Outcome.CANNOT_ASSESS;m.reason=f.reason;return;}
        AlignmentReference.ShapeRef s=ref==null?null:ref.shape(m.shape);
        AlignmentReference.HourRef h=ref==null?null:ref.hours.get(m.hour);
        if(s==null||!s.supported||h==null){m.outcome=Outcome.CANNOT_ASSESS;
            m.reason="there is not enough verified genuine luminous evidence for this marker type yet";return;}
        expected(m,f,h);
        double rpx=f.rpx,floorR=MIN_RESOLVABLE_PX/rpx;
        double lever=f.halfLengthR();
        double posFloor=m.shape==ModelSpec.Shape.ROUND?ROUND_VISUAL_FLOOR_R:0;
        List<Issue> all=new ArrayList<>();
        if(f.radialOk)all.add(issue(LuminousAlignment.Param.RADIAL,f.radialR-h.radialR,s.radialLimitR,Math.max(2*Math.hypot(f.seRadialR,s.radialPhotoSdR),floorR),posFloor,1,rpx,f.er()));
        else m.notAssessed.add("distance from the centre");
        if(f.tangentialOk)all.add(issue(LuminousAlignment.Param.TANGENTIAL,f.tangentialR-h.tangentialR,s.tangentialLimitR,Math.max(2*Math.hypot(f.seTangentialR,s.tangentialPhotoSdR),floorR),posFloor,1,rpx,f.et()));
        else m.notAssessed.add("sideways position");
        if(m.shape!=ModelSpec.Shape.ROUND){
            if(f.rotationOk&&Double.isFinite(lever)&&lever>0){
                double floorDeg=Math.toDegrees(floorR/lever);
                all.add(issue(LuminousAlignment.Param.ROTATION,f.rotationDeg-h.rotationDeg,s.rotationLimitDeg,Math.max(2*Math.hypot(f.seRotationDeg,s.rotationPhotoSdDeg),floorDeg),0,Math.toRadians(1)*lever,rpx,null));
            }else m.notAssessed.add("rotation");
        }
        Issue worst=null;
        for(Issue i:all){
            if(!Double.isFinite(i.limit)){m.notAssessed.add(name(i.param));continue;}
            if(i.outside()){if(worst==null||i.excessR>worst.excessR)worst=i;}else m.within.add(i);
        }
        if(worst!=null){
            m.outcome=Outcome.DEFECT;m.primary=worst;
            for(Issue i:all)if(i!=worst&&Double.isFinite(i.limit)&&i.outside())m.secondary.add(i);
            m.secondary.sort((a,b)->Double.compare(b.excessR,a.excessR));
        }else if(m.within.isEmpty()){m.outcome=Outcome.CANNOT_ASSESS;m.reason="its position could not be compared with genuine watches";}
        else m.outcome=Outcome.NO_DEFECT;
    }

    /** Display only: the known luminous shape at the genuine nominal for this hour, placed by the other markers' ring. */
    static void expected(Marker m,LuminousAlignment.Result f,AlignmentReference.HourRef h){
        if(f.refCentre==null)return;
        double[] er=f.er(),et=f.et();
        double rr=Double.isFinite(h.radialR)?h.radialR:0,tt=Double.isFinite(h.tangentialR)?h.tangentialR:0;
        double tx=rr*er[0]+tt*et[0],ty=rr*er[1]+tt*et[1];
        if(m.shape==ModelSpec.Shape.ROUND){if(Double.isFinite(f.refRadius))m.expectedCircle=new double[]{f.refCentre[0]+tx,f.refCentre[1]+ty,f.refRadius};return;}
        if(f.refPolygon==null)return;
        double th=Double.isFinite(h.rotationDeg)?Math.toRadians(h.rotationDeg):0,c=Math.cos(th),s=Math.sin(th);
        m.expectedPolygon=new double[f.refPolygon.length][];
        for(int i=0;i<f.refPolygon.length;i++){double vx=f.refPolygon[i][0]-f.refCentre[0],vy=f.refPolygon[i][1]-f.refCentre[1];
            m.expectedPolygon[i]=new double[]{f.refCentre[0]+tx+c*vx-s*vy,f.refCentre[1]+ty+s*vx+c*vy};}
    }

    /** Threshold = the larger of (genuine envelope + uncertainty) and the visual floor; never their sum.
     *  @param visualFloor minimum visible displacement, same unit as dev (0 for none)
     *  @param toR multiplies the deviation unit into R of visible displacement (1 for positions, lever per degree for rotation) */
    static Issue issue(LuminousAlignment.Param p,double dev,double limit,double unc,double visualFloor,double toR,double rpx,double[] axis){
        Issue i=new Issue();i.param=p;i.deviation=dev;i.limit=limit;i.uncertainty=unc;i.visualFloor=visualFloor;i.rpx=rpx;
        i.excessR=(Math.abs(dev)-Math.max(limit+unc,visualFloor))*toR;
        if(axis!=null)i.screen=new double[]{Math.signum(dev)*axis[0],Math.signum(dev)*axis[1]};
        return i;
    }

    static String name(LuminousAlignment.Param p){
        switch(p){case RADIAL:return "distance from the centre";case TANGENTIAL:return "sideways position";case ROTATION:return "rotation";default:return "size";}
    }

    /** Screen words for a displacement direction (y down): the dominant axis, e.g. "far right", "high". */
    static String words(double[] v){
        if(v==null)return "far out of place";
        if(Math.abs(v[0])>=Math.abs(v[1]))return v[0]>0?"far right":"far left";
        return v[1]>0?"low":"high";
    }

    /** Short line for a list row. */
    static String summary(Marker m){
        if(m.outcome==Outcome.DEFECT)return m.primary.headline()+".";
        if(m.outcome==Outcome.NO_DEFECT&&!m.metalNotes.isEmpty())return m.metalNotes.get(0);
        if(m.outcome==Outcome.CANNOT_ASSESS)return capital(m.reason)+".";
        return m.notAssessed.isEmpty()?null:"Not assessed: "+String.join(", ",m.notAssessed)+".";
    }
    static String capital(String s){return s==null||s.isEmpty()?"":Character.toUpperCase(s.charAt(0))+s.substring(1);}
}
