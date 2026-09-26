package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-English summary shown at the top of the GMT QC checks. It only restates the
 * decisions already made by the 12-marker QC; it adds no thresholds of its own and
 * never calls a watch genuine or fake.
 */
final class GmtHumanSummary {
    static final class Input {
        GmtHumanQcMath.PoseLabel pose=GmtHumanQcMath.PoseLabel.UNASSESSABLE;
        boolean twelveValid;
        boolean stableFrame;
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.GapTrend gapTrend=GmtHumanQcMath.GapTrend.UNKNOWN;
        double observedGap=Double.NaN;
        GmtHumanQcMath.Attention alignment=GmtHumanQcMath.Attention.UNASSESSABLE;
        double rotationDeg=Double.NaN;
        double spacing59=Double.NaN, spacing01=Double.NaN;
        boolean overlayDrawn;
    }

    // Reference only, for the reader: what genuine images have measured on the same
    // outer-edge gap definition (official renders 0.086-0.096, real BLNR photo 0.084).
    // Not a decision boundary; decisions come from GmtHumanQcMath.
    static final String GENUINE_GAP_SEEN = "about 0.085–0.095";

    private GmtHumanSummary(){}

    static String build(Input in){
        StringBuilder s=new StringBuilder("SUMMARY\n");
        s.append("Photo: ").append(photoLine(in)).append("\n");
        s.append("12 gap: ").append(gapLine(in)).append("\n");
        s.append("12 alignment: ").append(alignmentLine(in)).append("\n");
        s.append("Overlay: ").append(in.overlayDrawn
                ?"red outlines are fitted to this dial. Compare them with the real markers by eye, especially the 12 triangle and the 6 and 9 batons."
                :"not drawn for this photo, so there is no visual template to compare against.").append("\n");
        s.append("\n").append(bottomLine(in)).append("\n");
        s.append("This flags things to look at closely. It does not prove a watch is genuine or fake.\n");
        return s.toString();
    }

    static String photoLine(Input in){
        switch(in.pose){
            case GOOD: return "good, near straight-on angle. Measurements are reliable.";
            case CORRECTABLE: return "slight angle. Usable; the gap check allows for it.";
            case RETAKE: return "too angled for fine checks. Retake straight-on before trusting the 12 marker results.";
            default: return "angle could not be judged. Treat the 12 marker results with caution.";
        }
    }

    static String gapLine(Input in){
        if(!in.twelveValid)return "could not be measured on this photo (12 triangle or minute track not found). This is not a pass.";
        String v=Double.isFinite(in.observedGap)?String.format(Locale.US," Measured %.2f; genuine photos tested so far read %s.",in.observedGap,GENUINE_GAP_SEEN):"";
        if(!in.stableFrame)v+=" The 12 marker could only be measured with low confidence on this photo, so treat this with caution.";
        switch(in.gap){
            case CLEAR: return "normal. Clear space between the triangle and the minute track."+v;
            case CHECK:
                if(in.gapTrend==GmtHumanQcMath.GapTrend.COMPRESSED)
                    return "looks small, but the photo angle may be making it look smaller. Check by eye or retake straight-on."+v;
                return "small. The triangle sits closer to the minute track than expected. Check by eye."+v;
            case STRONG: return "very small or touching. The triangle is right up against the minute track."+v;
            default: return "could not be judged reliably on this photo.";
        }
    }

    static String alignmentLine(Input in){
        if(!in.twelveValid)return "could not be measured on this photo. This is not a pass.";
        String sp=Double.isFinite(in.spacing59)&&Double.isFinite(in.spacing01)
                ?String.format(Locale.US," (rotation %+.1f°, space to 59 tick %.2f vs 01 tick %.2f)",in.rotationDeg,in.spacing59,in.spacing01):"";
        String caution=in.stableFrame?"":" The 12 marker could only be measured with low confidence on this photo, so treat this with caution.";
        switch(in.alignment){
            case CLEAR: return "straight and centred. No visible rotation, even spacing either side."+sp+caution;
            case CHECK: return "possibly slightly rotated or off-centre. Look closely; a hand touching the triangle can cause this."+sp+caution;
            case STRONG: return "visibly rotated or off-centre."+sp+caution;
            default: return "could not be judged reliably on this photo.";
        }
    }

    static String bottomLine(Input in){
        List<String> items=new ArrayList<>();
        boolean gapFlag=in.twelveValid&&(in.gap==GmtHumanQcMath.Attention.CHECK||in.gap==GmtHumanQcMath.Attention.STRONG);
        boolean alignFlag=in.twelveValid&&(in.alignment==GmtHumanQcMath.Attention.CHECK||in.alignment==GmtHumanQcMath.Attention.STRONG);
        if(gapFlag)items.add("the gap at 12");
        if(alignFlag)items.add("the 12 marker alignment");
        if(!in.twelveValid)return "Bottom line: the 12 marker could not be checked on this photo. Try a clearer, straight-on photo with the hands away from 12.";
        if(in.pose==GmtHumanQcMath.PoseLabel.RETAKE)
            return items.isEmpty()?"Bottom line: nothing flagged, but the photo is too angled to rely on that. Retake straight-on."
                    :"Bottom line: flagged "+join(items)+", but the photo is too angled to be sure. Retake straight-on.";
        if(items.isEmpty())return in.stableFrame?"Bottom line: nothing flagged at 12. Still compare the red outlines with the markers by eye."
                :"Bottom line: nothing flagged, but the 12 marker was only measured with low confidence. A clearer photo with the hands away from 12 would help.";
        String line="Bottom line: "+items.size()+(items.size()==1?" thing":" things")+" to check: "+join(items)+".";
        if(!in.stableFrame)line+=" Measured with low confidence, so confirm by eye or with a clearer photo with the hands away from 12.";
        return line;
    }

    private static String join(List<String> items){
        return items.size()==1?items.get(0):items.get(0)+" and "+items.get(1);
    }
}
