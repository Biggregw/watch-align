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
        final double[][] expected;
        /** Display only: the span of each independently fitted side that the detector actually sampled
         *  (Alpha94 measurePolygon, u = SAMPLED_FROM..SAMPLED_TO of the master side). Everything else on the
         *  scored polygon (corners, apex, side ends) is inferred by intersecting adjacent fitted lines.
         *  Null for rounds (one fitted circle) and when unavailable. */
        double[][][] observedSpans;
        Result(MarkerPolygonMismatch.Score score,String reason){this(score,reason,null);}
        Result(MarkerPolygonMismatch.Score score,String reason,double[][] expected){
            this.score=score;this.reason=reason;
            this.expected=score==null?MarkerPolygonMismatch.copyPolygon(expected):score.expectedPolygon;
        }
        /** Existing photo/edge checks cleared; this does not establish physical edge accuracy. */
        boolean trusted(){return score!=null && "measured".equals(reason);}
        String display(){
            if(!trusted())return "Outline mismatch withheld: "+reason+".";
            return String.format(Locale.US,"Measured outline mismatch: %.3f%% (extra %.3f%% + missing %.3f%% of filled yellow template). Geometric fit area; no severity assigned. Photographic edge uncertainty remains.",
                    score.mismatchPct,score.extraPct,score.missingPct)
                    +(observedSpans==null?"":" Each edge is fitted independently from the middle of its length (cyan); corners are inferred where the fitted edges meet (red) and are not directly observed.");
        }
    }

    static Result score(Alpha94MarkerMeasurement.Report r,ModelSpec.Marker spec,ModelReference ref,
                        Alpha99Findings.Finding finding,Alpha99MarkerInterference.Check interference) {
        if(r==null||spec==null||r.ring==null||!r.ring.usable||r.ring.model==null||r.ring.model.length<4)
            return new Result(null,"marker ring unavailable");
        double[][] expected=ExpectedMarkerOutline.polygon(spec,ref,r.ring.model);
        Alpha94MarkerMeasurement.Marker m=spec.shape==ModelSpec.Shape.TRIANGLE?r.triangle:r.atHour(spec.hour);
        if(m==null||!m.usable)return new Result(null,m==null?"unmeasured":m.reason,expected);
        double[][] actual=actual(spec,m,r.dialRadiusPx);
        MarkerPolygonMismatch.Score s=MarkerPolygonMismatch.compare(expected,actual);
        if(s==null)return new Result(null,"invalid or incomplete polygon",expected);
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
        Result measured=new Result(s,"measured");measured.observedSpans=observedSpans(spec,actual);
        return measured;
    }

    static final double SAMPLED_FROM=0.12,SAMPLED_TO=0.88;
    /** Sampled stretch of each fitted side: the master side's sampled range projected onto the fitted line.
     *  Side i of the fitted polygon runs actual[i] -> actual[i+1] and was fitted to master side i. */
    static double[][][] observedSpans(ModelSpec.Marker spec,double[][] actual){
        if(spec==null||actual==null||spec.shape==ModelSpec.Shape.ROUND)return null;
        double[][] master=spec.shape==ModelSpec.Shape.TRIANGLE?spec.trianglePolygon():spec.batonPolygon();
        int k=actual.length;if(master==null||master.length!=k)return null;
        double[][][] spans=new double[k][][];
        for(int i=0;i<k;i++){
            double[] a=actual[i],b=actual[(i+1)%k],m0=master[i],m1=master[(i+1)%k];
            spans[i]=new double[][]{onLine(a,b,m0,m1,SAMPLED_FROM),onLine(a,b,m0,m1,SAMPLED_TO)};
        }
        return spans;
    }
    private static double[] onLine(double[] a,double[] b,double[] m0,double[] m1,double u){
        double px=m0[0]+u*(m1[0]-m0[0]),py=m0[1]+u*(m1[1]-m0[1]),dx=b[0]-a[0],dy=b[1]-a[1];
        double t=((px-a[0])*dx+(py-a[1])*dy)/(dx*dx+dy*dy);
        return new double[]{a[0]+t*dx,a[1]+t*dy};
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
