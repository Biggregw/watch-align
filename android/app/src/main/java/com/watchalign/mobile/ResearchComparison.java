package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH ONLY, off by default (owner-approved Phase C comparison, 23 Oct 2026): the existing findings beside the shared
 * MarkerGeometry readings, marker by marker and for the whole ring. The existing results and the watch classification
 * stay authoritative; nothing here is read by WatchClassification, ClearlyOff or AlignmentDecision.
 *
 * The new readings are judged against the candidate shared eight-reading structure of
 * tools/research/genuine_calibration/REPORT.md (analysis run 39841529058): one multiplier k on pooled genuine scales.
 * These are research candidates, not tolerances: the 5 % operating point (k 3.83) and the stricter one (k 4.56). The
 * triangle's in/out stays on the approved top-edge measurement and is not judged here.
 */
final class ResearchComparison {
    private ResearchComparison(){}

    /** pooled genuine scales sigma (% R, deg, %) and ring centres, REPORT.md section 4 (shared/maxz) */
    static final double SIG_POS=0.082,SIG_BATON_TILT=0.315,SIG_TRI_SIDE=0.077,SIG_TRI_TILT=0.471,
            SIG_RING_PLACE=0.085,SIG_RING_ROT=0.058,SIG_RING_SCALE=0.067;
    static final double RING_X=-0.010,RING_Y=0.007,RING_ROT=0.006,RING_SCALE=-0.016;
    /** the two candidate operating points: genuine whole-watch false alarms about 5 % and about 1 % (2.4 % cross-validated) */
    static final double K5=3.83,K1=4.56;
    static final String SOURCE="research candidate, analysis run 39841529058";

    enum Level{NOT_ASSESSED,WITHIN,OUTSIDE_5,OUTSIDE_STRICT}

    static final class Row {
        final String what;final String existing;final boolean existingFlag;final String reading;final double z;final Level level;
        Row(String what,String existing,boolean existingFlag,String reading,double z,Level level){
            this.what=what;this.existing=existing;this.existingFlag=existingFlag;this.reading=reading;this.z=z;this.level=level;}
        boolean newFlag(){return level==Level.OUTSIDE_5||level==Level.OUTSIDE_STRICT;}
        String agreement(){
            if(level==Level.NOT_ASSESSED)return "new: not assessed";
            if(existingFlag&&newFlag())return "both flag";
            if(existingFlag)return "existing only";
            if(newFlag())return "new only";
            return "both clear";
        }
    }

    static Level level(double z){
        if(!Double.isFinite(z))return Level.NOT_ASSESSED;
        return z>K1?Level.OUTSIDE_STRICT:z>K5?Level.OUTSIDE_5:Level.WITHIN;
    }

    static String label(Level l){
        switch(l){
            case OUTSIDE_STRICT:return "outside both candidates";
            case OUTSIDE_5:return "outside the 5 % candidate";
            case WITHIN:return "within the candidates";
            default:return "not assessed";
        }
    }

    /** the existing answer for one marker, as the results screen states it */
    static String existing(WatchClassification.MarkerCheck c){
        if(c==null)return "no reading";
        String qc;
        switch(c.qc()){
            case CLEARLY_OFF:qc="Clearly off";break;
            case POSSIBLE:qc="Possible";break;
            case NO_VISIBLE_ISSUE:qc="No visible issue";break;
            default:qc="Can't check";
        }
        return qc+(c.genuineFail()?"; outside the strict genuine standard":c.genuinePass()?"; meets the strict genuine standard":"");
    }

    static boolean existingFlag(WatchClassification.MarkerCheck c){return c!=null&&(c.qcFail()||c.genuineFail());}

    static List<Row> rows(WatchClassification.Result cls,MarkerGeometry.Result g){
        List<Row> out=new ArrayList<>();
        if(g==null)return out;
        for(MarkerGeometry.Marker m:g.markers){
            WatchClassification.MarkerCheck c=cls==null?null:cls.at(m.hour);
            String ex=existing(c);boolean ef=existingFlag(c);
            String name=m.hour+" "+(m.shape==ModelSpec.Shape.TRIANGLE?"triangle":m.shape==ModelSpec.Shape.ROUND?"round":"baton");
            if(!m.eligible||!Double.isFinite(m.tangPct)){
                out.add(new Row(name,ex,ef,m.eligible?"too few other markers for a ring":"not eligible (hand, glare, coverage or edge)",Double.NaN,Level.NOT_ASSESSED));
                continue;
            }
            double z;String reading;
            if(m.shape==ModelSpec.Shape.TRIANGLE){
                double zs=Math.abs(m.tangPct)/SIG_TRI_SIDE,zt=Double.isFinite(m.tiltDeg)?Math.abs(m.tiltDeg)/SIG_TRI_TILT:Double.NaN;
                z=Double.isFinite(zt)?Math.max(zs,zt):zs;
                reading=String.format(Locale.US,"sideways %+.2f %% R%s; in/out %+.2f %% R (top edge stays authoritative)",m.tangPct,
                        Double.isFinite(m.tiltDeg)?String.format(Locale.US,", turned %+.2f°",m.tiltDeg):"",m.radPct);
            }else{
                double zp=m.posPct()/SIG_POS;
                double zt=m.shape==ModelSpec.Shape.BATON&&Double.isFinite(m.tiltDeg)?Math.abs(m.tiltDeg)/SIG_BATON_TILT:Double.NaN;
                z=Double.isFinite(zt)?Math.max(zp,zt):zp;
                reading=String.format(Locale.US,"off by %.2f %% R (sideways %+.2f, in/out %+.2f)%s",m.posPct(),m.tangPct,m.radPct,
                        Double.isFinite(zt)?String.format(Locale.US,", turned %+.2f°",m.tiltDeg):"");
            }
            out.add(new Row(name,ex,ef,reading,z,level(z)));
        }
        MarkerGeometry.Ring r=g.ring;
        if(r.ok){
            double zp=Math.hypot(r.placeXPct-RING_X,r.placeYPct-RING_Y)/SIG_RING_PLACE,zr=Math.abs(r.rotDeg-RING_ROT)/SIG_RING_ROT,
                    zs=Math.abs(r.scalePct-RING_SCALE)/SIG_RING_SCALE;
            double z=Math.max(zp,Math.max(zr,zs));
            out.add(new Row("whole ring","not measured by the existing check",false,String.format(Locale.US,
                    "moved %.2f %% R (after parallax), turned %+.2f°, size %+.2f %% (%d markers)",
                    Math.hypot(r.placeXPct-RING_X,r.placeYPct-RING_Y),r.rotDeg-RING_ROT,r.scalePct-RING_SCALE,r.n),z,level(z)));
        }else out.add(new Row("whole ring","not measured by the existing check",false,"fewer than 5 eligible markers",Double.NaN,Level.NOT_ASSESSED));
        return out;
    }

    static final String NOTE="Research comparison, not used for the result above. The new readings compare each marker with "
            +"the ring of the other eligible markers and are judged against research candidates (not tolerances); the existing "
            +"results and the watch classification are unchanged and authoritative. The triangle's in/out stays on the approved "
            +"top-edge measurement.";

    static String text(WatchClassification.Result cls,MarkerGeometry.Result g){
        StringBuilder s=new StringBuilder();
        for(Row r:rows(cls,g)){
            s.append(r.what).append("\n  existing: ").append(r.existing)
             .append("\n  new: ").append(r.reading)
             .append(r.level==Level.NOT_ASSESSED?"":String.format(Locale.US," — %.1f× the genuine scale, %s",r.z,label(r.level)))
             .append("\n  ").append(r.agreement()).append('\n');
        }
        return s.toString().trim();
    }
}
