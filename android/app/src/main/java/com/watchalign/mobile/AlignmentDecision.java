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

    static final class Issue {
        LuminousAlignment.Param param;
        double deviation;     // R for positions, degrees for rotation (signed)
        double limit;         // genuine envelope (same unit)
        double uncertainty;   // 2 SE / resolution floor (same unit)
        double excessR;       // visible excess beyond envelope + uncertainty, R
        double rpx;double[] screen;   // unit screen direction of a positional deviation
        boolean outside(){return excessR>0;}
        String headline(){
            if(param==LuminousAlignment.Param.ROTATION)return deviation>0?"Rotated clockwise":"Rotated anticlockwise";
            String w=words(screen);
            return "Positioned too "+w;
        }
        String measured(){
            if(param==LuminousAlignment.Param.ROTATION)
                return String.format(Locale.US,"Measured rotation: %.1f° %s from the genuine position.",Math.abs(deviation),deviation>0?"clockwise":"anticlockwise");
            return String.format(Locale.US,"Measured shift: %.2f%% of the dial radius (about %.1f px in this photo).",100*Math.abs(deviation),Math.abs(deviation)*rpx);
        }
        String genuine(){
            if(param==LuminousAlignment.Param.ROTATION)return String.format(Locale.US,"Genuine Rolex range: up to %.1f° either way.",limit);
            return String.format(Locale.US,"Genuine Rolex range: up to %.2f%% of the dial radius (about %.1f px here).",100*limit,limit*rpx);
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
        Marker(int hour,ModelSpec.Shape shape){this.hour=hour;this.shape=shape;}
        String title(){return hour+" o'clock";}
        String label(){return outcome==Outcome.DEFECT?"Defect detected":outcome==Outcome.NO_DEFECT?"No defect":"Cannot assess";}
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
        double rpx=f.rpx,floorR=MIN_RESOLVABLE_PX/rpx;
        double lever=f.halfLengthR();
        List<Issue> all=new ArrayList<>();
        if(f.radialOk)all.add(issue(LuminousAlignment.Param.RADIAL,f.radialR-h.radialR,s.radialLimitR,Math.max(2*Math.hypot(f.seRadialR,s.radialPhotoSdR),floorR),1,rpx,f.er()));
        else m.notAssessed.add("distance from the centre");
        if(f.tangentialOk)all.add(issue(LuminousAlignment.Param.TANGENTIAL,f.tangentialR-h.tangentialR,s.tangentialLimitR,Math.max(2*Math.hypot(f.seTangentialR,s.tangentialPhotoSdR),floorR),1,rpx,f.et()));
        else m.notAssessed.add("sideways position");
        if(m.shape!=ModelSpec.Shape.ROUND){
            if(f.rotationOk&&Double.isFinite(lever)&&lever>0){
                double floorDeg=Math.toDegrees(floorR/lever);
                all.add(issue(LuminousAlignment.Param.ROTATION,f.rotationDeg-h.rotationDeg,s.rotationLimitDeg,Math.max(2*Math.hypot(f.seRotationDeg,s.rotationPhotoSdDeg),floorDeg),Math.toRadians(1)*lever,rpx,null));
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

    /** @param toR multiplies the deviation unit into R of visible displacement (1 for positions, lever per degree for rotation) */
    private static Issue issue(LuminousAlignment.Param p,double dev,double limit,double unc,double toR,double rpx,double[] axis){
        Issue i=new Issue();i.param=p;i.deviation=dev;i.limit=limit;i.uncertainty=unc;i.rpx=rpx;
        i.excessR=(Math.abs(dev)-limit-unc)*toR;
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
        if(m.outcome==Outcome.CANNOT_ASSESS)return capital(m.reason)+".";
        return m.notAssessed.isEmpty()?null:"Not assessed: "+String.join(", ",m.notAssessed)+".";
    }
    static String capital(String s){return s==null||s.isEmpty()?"":Character.toUpperCase(s.charAt(0))+s.substring(1);}
}
