package com.watchalign.mobile;

/**
 * Research-only mismatch percentage using the same expected shapes as the yellow
 * ALPHA99 close-ups (including its ring adjustment and genuine 12 position).
 *
 * Does not change measurement, pose, reference data, reporting or QC thresholds.
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
        boolean trusted(){return score!=null && "measured".equals(reason);}
    }

    static Result score(Alpha94MarkerMeasurement.Report r,ModelSpec.Marker spec,ModelReference ref,
                        Alpha99Findings.Finding finding,Alpha99MarkerInterference.Check interference) {
        if(r==null||spec==null||r.ring==null||!r.ring.usable||r.ring.model==null)
            return new Result(null,"marker ring unavailable");
        Alpha94MarkerMeasurement.Marker m=spec.shape==ModelSpec.Shape.TRIANGLE?r.triangle:r.atHour(spec.hour);
        if(m==null||!m.usable)return new Result(null,m==null?"unmeasured":m.reason);
        double[][] expected,actual;
        if(spec.shape==ModelSpec.Shape.TRIANGLE){
            expected=Alpha98Closeups.moved(Alpha98Closeups.nominalTriangle(spec,ref),r.ring.model);
            actual=m.fitVertices;
        }else if(spec.shape==ModelSpec.Shape.BATON){
            expected=Alpha98Closeups.moved(spec.batonPolygon(),r.ring.model);
            actual=m.fitVertices;
        }else {
            double[] master=spec.masterPoint();
            if(!Double.isFinite(m.canonDx)||!Double.isFinite(m.canonDy)||!Double.isFinite(m.radiusErrPx))
                return new Result(null,"round center or radius not reliable");
            double radius=spec.outerR+m.radiusErrPx/r.dialRadiusPx;
            if(!(radius>0))return new Result(null,"round radius invalid");
            double[] ring=r.ring.model;
            double cx=master[0]+ring[0]+ring[2]*master[0]-ring[3]*master[1];
            double cy=master[1]+ring[1]+ring[2]*master[1]+ring[3]*master[0];
            expected=MarkerPolygonMismatch.circle(cx,cy,spec.outerR*(1+ring[2]),128);
            actual=MarkerPolygonMismatch.circle(master[0]+m.canonDx,master[1]+m.canonDy,radius,128);
        }
        MarkerPolygonMismatch.Score s=MarkerPolygonMismatch.compare(expected,actual);
        if(s==null)return new Result(null,"invalid or incomplete polygon");
        // We do not silently score unreliable photo edges as genuine defects.
        if(interference==null||!interference.clean)
            return new Result(s,"hand/glare or interference not cleared");
        if(finding!=null&&finding.status==Alpha99Findings.Status.NOT_ASSESSED)
            return new Result(s,"measurement was not assessed");
        if(finding!=null&&finding.partNotAssessed!=null)
            return new Result(s,"partial measurement withheld: "+finding.partNotAssessed);
        if(spec.shape==ModelSpec.Shape.TRIANGLE)
            return new Result(s,"provisional: radial edges of 12 vary with polished-surround lighting");
        return new Result(s,"measured");
    }
}
