package com.watchalign.mobile;

import java.util.Locale;

/**
 * Non-classifying outline measurement using the same expected shapes as the yellow
 * ALPHA99 close-ups (including its ring adjustment and genuine 12 position).
 *
 * Does not change detector fits, pose, reference data or QC thresholds/statuses.
 * In unreliable cases an exploratory raw score is returned but is explicitly
 * withheld as a QC decision. A robust future implementation must validate
 * position and photo reliability separately for every marker type.
 */
final class MarkerMismatchResearch {
    private MarkerMismatchResearch() {}

    static final class Result {
        final MarkerPolygonMismatch.Score score;
        final String reason;
        Result(MarkerPolygonMismatch.Score score,String reason){this.score=score;this.reason=reason;}
        /** Existing photo/edge checks cleared; this does not establish physical edge accuracy. */
        boolean trusted(){return score!=null && "measured".equals(reason);}
        String display(){
            if(!trusted())return "Outline mismatch withheld: "+reason+".";
            return String.format(Locale.US,"Measured outline mismatch: %.3f%% (extra %.3f%% + missing %.3f%% of filled yellow template). No severity assigned; detector edge uncertainty remains.",
                    score.mismatchPct,score.extraPct,score.missingPct);
        }
    }

    static Result score(Alpha94MarkerMeasurement.Report r,ModelSpec.Marker spec,ModelReference ref,
                        Alpha99Findings.Finding finding,Alpha99MarkerInterference.Check interference) {
        if(r==null||spec==null||r.ring==null||!r.ring.usable||r.ring.model==null||r.ring.model.length<4)
            return new Result(null,"marker ring unavailable");
        Alpha94MarkerMeasurement.Marker m=spec.shape==ModelSpec.Shape.TRIANGLE?r.triangle:r.atHour(spec.hour);
        if(m==null||!m.usable)return new Result(null,m==null?"unmeasured":m.reason);
        double[][] expected=ExpectedMarkerOutline.polygon(spec,ref,r.ring.model);
        double[][] actual=actual(spec,m,r.dialRadiusPx);
        MarkerPolygonMismatch.Score s=MarkerPolygonMismatch.compare(expected,actual);
        if(s==null)return new Result(null,"invalid or incomplete polygon");
        // We do not silently score unreliable photo edges as genuine defects.
        if(interference==null||!interference.clean)
            return new Result(s,"hand/glare or interference not cleared");
        if(finding==null)return new Result(s,"marker reliability check unavailable");
        if(finding.status==Alpha99Findings.Status.NOT_ASSESSED)
            return new Result(s,"measurement was not assessed");
        if(finding.partNotAssessed!=null)
            return new Result(s,"partial measurement withheld: "+finding.partNotAssessed);
        for(Alpha99Findings.Measure measure:finding.measures)
            if(measure.capAtWorth)return new Result(s,"photo/edge reliability limited: "+measure.capReason);
        if(spec.shape==ModelSpec.Shape.TRIANGLE)
            return new Result(s,"provisional: radial edges of 12 vary with polished-surround lighting");
        return new Result(s,"measured");
    }

    static double[][] actual(ModelSpec.Marker spec,Alpha94MarkerMeasurement.Marker m,double R){
        if(m==null||!m.usable)return null;
        if(spec.shape!=ModelSpec.Shape.ROUND)return m.fitVertices;
        if(!Double.isFinite(m.canonDx)||!Double.isFinite(m.canonDy)||!Double.isFinite(m.radiusErrPx)||!(R>0))return null;
        double radius=spec.outerR+m.radiusErrPx/R;
        if(!(radius>0))return null;
        double[] master=spec.masterPoint();
        return MarkerPolygonMismatch.circle(master[0]+m.canonDx,master[1]+m.canonDy,radius,128);
    }
}
