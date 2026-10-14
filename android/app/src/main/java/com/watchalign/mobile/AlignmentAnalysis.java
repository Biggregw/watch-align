package com.watchalign.mobile;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the luminous alignment fit and the alignment-only decision for EVERY marker of the model (triangle, batons and
 * every round), each on its own: a marker that cannot be assessed never hides or suppresses another.
 * Inputs are the existing frozen pose H, the existing marker measurement (only to place the ring of the OTHER markers)
 * and the existing hand/glare check. Nothing here changes those.
 */
final class AlignmentAnalysis {
    private AlignmentAnalysis(){}

    static final class Output {
        final List<AlignmentDecision.Marker> markers=new ArrayList<>();
        /** photo-wide reason every marker could not be assessed, or null */
        String photoReason;
        int count(AlignmentDecision.Outcome o){int n=0;for(AlignmentDecision.Marker m:markers)if(m.outcome==o)n++;return n;}
    }

    /** Fit setups per shape; from the genuine reference in the app, explicit in the calibration driver. */
    interface Setups{LuminousAlignment.Setup of(ModelSpec.Shape s);}
    /** Grey image around one marker (the app: a spline crop of the photo; tests: synthetic scenes). */
    interface Source{LuminousAlignment.Image around(ModelSpec.Marker mk,double[] ring);}

    static Output run(Mat gray,double[] H,Alpha94MarkerMeasurement.Report report,Map<Integer,Alpha99MarkerInterference.Check> checks,
                      ModelSpec model,ModelReference metal,AlignmentReference ref){
        return run(gray==null?null:(mk,ring)->image(gray,H,mk,ring),H,report,checks,model,metal,ref,ref==null?s->null:ref::setup);
    }

    /**
     * Two passes. 1: every marker fitted on its own. 2: the photo's own edge definition (threshold, lighting, blur are
     * shared by the whole dial) is the median uniform edge offset g of the OTHER markers whose g their edges determine;
     * a marker whose own g departs from it by more than the genuine spread of that difference has picked up a lit bevel
     * or glare instead of its luminous edge (a triangle cannot reveal that from its own three sides) and is withheld;
     * a marker whose g its edges could not determine is refitted with the photo's g (known-size inference).
     */
    static Output run(Source src,double[] H,Alpha94MarkerMeasurement.Report report,Map<Integer,Alpha99MarkerInterference.Check> checks,
                      ModelSpec model,ModelReference metal,AlignmentReference ref,Setups setups){
        Output o=new Output();
        double rpx=LuminousAlignment.pxPerR(H);
        if(src==null||H==null||!(rpx>20))o.photoReason="the dial could not be located in this photo";
        else if(rpx<AlignmentDecision.MIN_DIAL_RADIUS_PX)o.photoReason="the photo's resolution is too low (the dial is under "+(int)(2*AlignmentDecision.MIN_DIAL_RADIUS_PX)+" pixels across)";
        else if(report==null)o.photoReason="the markers could not be measured";
        Map<Integer,double[]> rings=new java.util.HashMap<>();
        for(ModelSpec.Marker mk:model.markers){
            AlignmentDecision.Marker m=new AlignmentDecision.Marker(mk.hour,mk.shape);o.markers.add(m);
            if(o.photoReason!=null){m.reason=o.photoReason;continue;}
            Alpha99MarkerInterference.Check c=checks==null?null:checks.get(mk.hour);
            double[] ring=LuminousAlignment.ringWithout(report,mk.hour);rings.put(mk.hour,ring);
            m.yellow=ExpectedMarkerOutline.polygon(mk,metal,ring);   // the existing yellow outer-metal reference, placed by the OTHER markers
            if(c==null){m.reason="its surroundings could not be checked for hands or glare";continue;}
            if(!c.clean){m.reason=c.reason;continue;}
            LuminousAlignment.Setup setup=setups.of(mk.shape);
            if(setup==null){m.reason="there is no verified genuine luminous reference for this marker type";continue;}
            m.bevelR=setup.insetR;
            try{m.fit=LuminousAlignment.measure(src.around(mk,ring),H,mk,ring,setup);}
            catch(Throwable t){m.reason="measurement failed";}
        }
        if(o.photoReason==null)photoConsistency(o,src,H,model,setups,rings);
        for(AlignmentDecision.Marker m:o.markers)if(m.reason.isEmpty()||m.fit!=null)AlignmentDecision.decide(m,ref);
        return o;
    }

    static void photoConsistency(Output o,Source src,double[] H,ModelSpec model,Setups setups,Map<Integer,double[]> rings){
        Map<AlignmentDecision.Marker,double[]> photo=new java.util.HashMap<>();   // from the first pass only: no order effects
        for(AlignmentDecision.Marker m:o.markers)photo.put(m,photoGrowth(o,m,setups));
        for(AlignmentDecision.Marker m:o.markers){
            if(m.fit==null||!m.fit.assessed&&m.fit.samples==0)continue;
            double[] g=photo.get(m);
            m.photoGrowthR=g==null?Double.NaN:g[0];
            if(g==null)continue;
            LuminousAlignment.Setup setup=setups.of(m.shape);
            if(m.fit.assessed&&m.fit.growthFree){
                double d=(m.fit.growthR-setup.growthNominalR)-g[0];
                m.fit.photoGrowthDiffR=d;
                if(Double.isFinite(setup.photoGrowthLimitR)&&Math.abs(d)>setup.photoGrowthLimitR+2*m.fit.seGrowthR){
                    m.fit.assessed=false;
                    m.fit.reason=String.format(java.util.Locale.US,"its detected luminous edge sits %.1f px %s than on the other markers in this photo (a bright bevel or glare)",
                            Math.abs(d)*m.fit.rpx,d>0?"further out":"further in");
                }
            }else if(!m.fit.growthFree&&m.fit.assessed){
                setup.fixedGrowthR=setup.growthNominalR+g[0];
                ModelSpec.Marker mk=model.atHour(m.hour);double[] ring=rings.get(m.hour);
                try{LuminousAlignment.Result again=LuminousAlignment.measure(src.around(mk,ring),H,mk,ring,setup);
                    if(again.assessed||!again.reason.isEmpty())m.fit=again;}catch(Throwable t){m.reason="measurement failed";}
            }
        }
    }

    /** {median of (g - shape nominal)} over the OTHER markers whose g their own edges determined; null with fewer than 3. */
    static double[] photoGrowth(Output o,AlignmentDecision.Marker self,Setups setups){
        List<Double> g=new ArrayList<>();
        for(AlignmentDecision.Marker x:o.markers){
            if(x==self||x.fit==null||!x.fit.assessed||!x.fit.growthFree)continue;
            LuminousAlignment.Setup s=setups.of(x.shape);if(s==null)continue;
            g.add(x.fit.growthR-s.growthNominalR);
        }
        if(g.size()<3)return null;
        double[] a=new double[g.size()];for(int i=0;i<a.length;i++)a[i]=g.get(i);
        return new double[]{LuminousAlignment.median(a),a.length};
    }

    /** Spline-interpolated crop around the marker (same interpolant as the existing measurement). */
    static LuminousAlignment.Image image(Mat gray,double[] H,ModelSpec.Marker mk,double[] ring){
        double[] c=LuminousAlignment.move(mk.masterPoint(),ring);
        double half=Math.max(mk.outerEdgeR()-mk.innerR(),2*mk.halfWidth())*0.5+0.08;
        double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
        for(int k=0;k<16;k++){double a=2*Math.PI*k/16;double[] p=LuminousAlignment.project(H,c[0]+half*Math.cos(a)*1.42,c[1]+half*Math.sin(a)*1.42);
            if(p==null)continue;x0=Math.min(x0,p[0]);x1=Math.max(x1,p[0]);y0=Math.min(y0,p[1]);y1=Math.max(y1,p[1]);}
        if(!Double.isFinite(x0))return (x,y)->Double.NaN;
        Alpha91SplineImage img=new Alpha91SplineImage(gray,(int)Math.floor(x0)-4,(int)Math.floor(y0)-4,(int)Math.ceil(x1)+5,(int)Math.ceil(y1)+5);
        return img::at;
    }

    /** Plain counts line, e.g. "1 defect · 9 no defect · 1 cannot assess". */
    static String counts(Output o){
        StringBuilder b=new StringBuilder();
        int d=o.count(AlignmentDecision.Outcome.DEFECT),n=o.count(AlignmentDecision.Outcome.NO_DEFECT),c=o.count(AlignmentDecision.Outcome.CANNOT_ASSESS);
        if(d>0)b.append(d).append(d==1?" defect":" defects");
        if(n>0)b.append(b.length()>0?"  ·  ":"").append(n).append(" no defect");
        if(c>0)b.append(b.length()>0?"  ·  ":"").append(c).append(" cannot assess");
        return b.toString();
    }

    static String headline(Output o){
        int d=o.count(AlignmentDecision.Outcome.DEFECT),n=o.count(AlignmentDecision.Outcome.NO_DEFECT);
        if(o.photoReason!=null)return "Cannot assess this photo";
        if(d>0)return d==1?"1 marker: defect detected":d+" markers: defect detected";
        if(n==0)return "Cannot assess";
        return "No defect detected";
    }

    /** Technical lines (collapsed panel): the measured numbers for every marker. */
    static String technical(Output o,AlignmentReference ref){
        StringBuilder s=new StringBuilder("Luminous alignment (translation + rotation of the known luminous shape; reference from the other markers only).\n");
        if(ref!=null)s.append("Genuine luminous reference: ").append(ref.provenance).append('\n');
        for(AlignmentDecision.Marker m:o.markers){
            s.append(String.format(java.util.Locale.US,"\n%s (%s): %s",m.title(),AlignmentReference.key(m.shape),m.label()));
            LuminousAlignment.Result f=m.fit;
            if(m.outcome==AlignmentDecision.Outcome.CANNOT_ASSESS)s.append(" - ").append(m.reason);
            if(f==null)continue;
            s.append(String.format(java.util.Locale.US,"\n  lume %.0f / dial %.0f · %d edge samples · edge noise %.2f px%s",f.lume,f.dial,f.samples,f.noisePx,f.inferred?" · known-size inference":""));
            if(f.shape==ModelSpec.Shape.ROUND)s.append(String.format(java.util.Locale.US," · outline seen %.0f%%",100*f.roundCover));
            else{StringBuilder sd=new StringBuilder();for(LuminousAlignment.Side x:f.sides)sd.append(sd.length()==0?"":"/").append(x.observed?String.format(java.util.Locale.US,"%.0f%%",100*x.cover()):"-");s.append(" · sides seen ").append(sd);}
            AlignmentReference.HourRef h=ref==null?null:ref.hours.get(m.hour);
            if(f.radialOk)s.append(String.format(java.util.Locale.US,"\n  radial %+.3f%%R ± %.3f (genuine nominal %+.3f)",100*f.radialR,100*f.seRadialR,h==null?Double.NaN:100*h.radialR));
            if(f.tangentialOk)s.append(String.format(java.util.Locale.US,"\n  tangential %+.3f%%R ± %.3f (genuine nominal %+.3f)",100*f.tangentialR,100*f.seTangentialR,h==null?Double.NaN:100*h.tangentialR));
            if(f.rotationOk)s.append(String.format(java.util.Locale.US,"\n  rotation %+.2f° ± %.2f (genuine nominal %+.2f)",f.rotationDeg,f.seRotationDeg,h==null?Double.NaN:h.rotationDeg));
            if(Double.isFinite(f.growthR))s.append(String.format(java.util.Locale.US,"\n  edge offset g %+.3f%%R (%s)",100*f.growthR,f.growthFree?"measured":"assumed"));
            for(AlignmentDecision.Issue i:allIssues(m))s.append(String.format(java.util.Locale.US,"\n  %s: deviation %s, genuine envelope %s, photo uncertainty %s -> %s",
                    AlignmentDecision.name(i.param),fmt(i.param,i.deviation),fmt(i.param,i.limit),fmt(i.param,i.uncertainty),i.outside()?"OUTSIDE":"within"));
        }
        return s.toString();
    }
    static List<AlignmentDecision.Issue> allIssues(AlignmentDecision.Marker m){
        List<AlignmentDecision.Issue> l=new ArrayList<>();if(m.primary!=null)l.add(m.primary);l.addAll(m.secondary);l.addAll(m.within);return l;
    }
    static String fmt(LuminousAlignment.Param p,double v){return p==LuminousAlignment.Param.ROTATION?String.format(java.util.Locale.US,"%+.2f°",v):String.format(java.util.Locale.US,"%+.3f%%R",100*v);}
}
