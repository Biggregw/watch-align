package com.watchalign.mobile;

import java.util.*;

/** Photographic evidence for circular lume boundaries, independent of the expected template.
 * A small interior brightness drop is not a separator merely because distant metal recovers.
 * EXPERIMENTAL (first-fall): each ray supplies at most one selected sample, the first significant
 * outward fall from the seed-connected luminous region. A polished bevel that reflects as brightly
 * as the lume can merge with it; a later groove/metal trough is then never substituted because it
 * is darker or fits a steadier circle. An insignificant dip that recovers (a possible unresolved
 * lume-to-bevel edge) makes the ray ambiguous: it supplies no selected sample.
 * All numeric gates below reuse the experimental separator/fit pixel-evidence gates; no QC bands.
 */
final class LuminousCircularBoundary {
    static final class Evidence {
        final double[][] primary,alternatives;
        Evidence(List<double[]> primary,List<double[]> alternatives){
            this.primary=primary.toArray(new double[0][]);this.alternatives=alternatives.toArray(new double[0][]);
        }
    }
    static double[][] samples(byte[] pixels,int n,int seed){
        return evidence(pixels,n,seed).primary;
    }
    /** Significant fall: the trough leaves the luminous level by this fraction of the local
     * face-to-background contrast (same face/background definitions as the detector). Provisional. */
    static final double SIGNIFICANT_FALL=.35;
    static Evidence evidence(byte[] pixels,int n,int seed){
        List<double[]> points=new ArrayList<>(),alternatives=new ArrayList<>();
        List<Double> central=new ArrayList<>();
        for(int y=seed-2;y<=seed+2;y++)for(int x=seed-2;x<=seed+2;x++)central.add((double)(pixels[y*n+x]&255));
        Collections.sort(central);double face=central.get(central.size()/2);
        int[] sorted=new int[pixels.length];for(int i=0;i<sorted.length;i++)sorted[i]=pixels[i]&255;Arrays.sort(sorted);
        double significant=face-SIGNIFICANT_FALL*(face-sorted[sorted.length/5]);
        int rays=LuminousSeparator.RAYS;double[] selected=new double[rays],dip=new double[rays];double[][] sample=new double[rays][];
        Arrays.fill(selected,Double.NaN);Arrays.fill(dip,Double.NaN);
        for(int k=0;k<rays;k++){
            double a=k*2*Math.PI/LuminousSeparator.RAYS,dx=Math.cos(a),dy=Math.sin(a);
            int count=(int)(((n/2.0-2)/Math.max(Math.abs(dx),Math.abs(dy)))/.25);
            double[] v=new double[count];for(int i=0;i<count;i++)v[i]=LuminousSeparator.smooth(pixels,n,seed+i*.25*dx,seed+i*.25*dy);
            boolean regionEnded=false;
            for(int j=12;j<count-8;j++){
                double drop=v[j-4]-v[j+4];
                if(drop<12||drop<v[j-5]-v[j+3]||drop<v[j-3]-v[j+5])continue;
                int trough=j+4;while(trough<count-2&&v[trough+1]<=v[trough])trough++;
                int recovery=trough;while(recovery<count-2&&v[recovery+1]>=v[recovery])recovery++;
                boolean ownRecovery=v[recovery]-v[trough]>=12&&v[j+4]-v[trough]<=12;
                if(!regionEnded){
                    // Judge the fall itself (the level just after it), not a later descent it runs into.
                    if(v[j+4]>significant){
                        // Still bright: a shading step stays inside the luminous region; a dip that
                        // recovers may be an unresolved lume-to-bevel edge, so this ray is ambiguous.
                        if(ownRecovery){regionEnded=true;double[] q=crossing(v,j,drop,dx,dy,seed);if(q!=null)dip[k]=Math.hypot(q[0]-seed,q[1]-seed);}
                        continue;
                    }
                    regionEnded=true;
                    // Recovery must belong to this trough. A later polished rim must not validate
                    // an interior texture edge, or a hand/shadow followed by more luminous material.
                    if(!ownRecovery)continue;
                    double[] q=crossing(v,j,drop,dx,dy,seed);if(q==null)continue;
                    int bright=0;for(int i=0;i<j-4;i++)if(v[i]>=v[j+4]+.6*drop)bright++;
                    if(bright<.90*(j-4))alternatives.add(q);else{sample[k]=q;selected[k]=Math.hypot(q[0]-seed,q[1]-seed);}
                    continue;
                }
                // Later transitions (groove, outer metal) never supply the selected boundary. A dark
                // interior ring can hide the true outer boundary from the bright-prefix gate: keep
                // such directly observed troughs as contrary evidence only, as before.
                if(!ownRecovery)continue;
                int bright=0;for(int i=0;i<j-4;i++)if(v[i]>=v[j+4]+.6*drop)bright++;
                if(bright<.90*(j-4)){double[] q=crossing(v,j,drop,dx,dy,seed);if(q!=null)alternatives.add(q);}
            }
        }
        // Where the dip is too faint to register on a ray, its first significant fall can be the groove.
        // An unresolved dip observed on a neighbouring ray (within one 30-degree sector) inside that
        // fall, beyond the contour-localisation allowance (crop px ~ source px), marks it as a later
        // transition: the ray supplies no selected sample.
        int window=Math.max(1,rays/12);
        for(int k=0;k<rays;k++){
            if(sample[k]==null)continue;boolean later=false;
            for(int o=-window;o<=window&&!later;o++){double r=dip[((k+o)%rays+rays)%rays];if(r<selected[k]-1.25)later=true;}
            if(!later)points.add(sample[k]);
        }
        return new Evidence(points,alternatives);
    }
    private static double[] crossing(double[] v,int j,double drop,double dx,double dy,int seed){
        double target=v[j+4]+.50*drop;int crossing=-1;
        for(int i=j-4;i<j+4;i++)if(v[i]>=target&&v[i+1]<target)crossing=i;
        if(crossing<0)return null;
        double r=.25*(crossing+(target-v[crossing])/(v[crossing+1]-v[crossing]));
        return new double[]{seed+r*dx,seed+r*dy};
    }

    static LuminousPartialFit.Fit fit(double[][] points,int hour,double R){
        return fit(points,null,hour,R);
    }
    static LuminousPartialFit.Fit fit(double[][] points,double[][] alternatives,int hour,double R){
        LuminousPartialFit.Fit f=LuminousPartialFit.fit(points,ModelSpec.Shape.ROUND,hour,R);
        if(f.ideal==null)return f;
        List<double[]> in=new ArrayList<>(),other=new ArrayList<>();
        for(double[] p:points)(Math.abs(Math.hypot(p[0]-f.center[0],p[1]-f.center[1])-f.radius)*R<=.8?in:other).add(p);
        if(alternatives!=null)for(double[] p:alternatives)
            if(Math.abs(Math.hypot(p[0]-f.center[0],p[1]-f.center[1])-f.radius)*R>.8)other.add(p);
        Set<Integer> contradictoryRegions=new HashSet<>();
        for(double[] p:other)if(Math.abs(Math.hypot(p[0]-f.center[0],p[1]-f.center[1])-f.radius)*R>1.25){
            double a=Math.atan2(p[1]-f.center[1],p[0]-f.center[0]);if(a<0)a+=2*Math.PI;
            contradictoryRegions.add((int)(a/(Math.PI/2)));
        }
        // A second circle need not itself be identifiable to contradict the selected edge.
        // Rejected optical observations spread over three independent regions cannot be
        // dismissed as a local occlusion. Reuse the fitter's three-region evidence rule.
        if(contradictoryRegions.size()>=3)return withheld(points,"photographic separator arcs contradict the selected circle in multiple independent regions");
        LuminousPartialFit.Fit competing=LuminousPartialFit.fit(other.toArray(new double[0][]),ModelSpec.Shape.ROUND,hour,R);
        if(competing.ideal!=null){return withheld(points,"competing distributed circular boundaries; luminous-to-surround identity unresolved");}
        // Alternating samples can agree on the same wrong arc. Check separated arc regions too.
        for(int sector=0;sector<6;sector++){
            List<double[]> subset=new ArrayList<>();
            for(double[] p:in){double a=Math.atan2(p[1]-f.center[1],p[0]-f.center[0]);if(a<0)a+=2*Math.PI;if((int)(a/(Math.PI/3))!=sector)subset.add(new double[]{p[0]*R,p[1]*R});}
            double[] c=LuminousPartialFit.leastCircle(subset);
            if(c==null||Math.hypot(c[0]-f.center[0]*R,c[1]-f.center[1]*R)>1.25||Math.abs(c[2]-f.radius*R)>1.25)
                return withheld(points,"circle geometry changes between separated supported arc regions");
        }
        f.reason="distributed arcs at directly observed luminous-to-surround troughs support circle geometry; separated arc regions agree and no competing distributed boundary is supported; full physical footprint remains separate";
        return f;
    }
    static LuminousPartialFit.Fit withheld(double[][] points,String reason){LuminousPartialFit.Fit f=new LuminousPartialFit.Fit();f.observed=points;f.reason=reason;return f;}
    static void validate(LuminousMarkerDetector.Detection d,int hour,double R){
        double[][] evidence=d.circularEvidence;
        if(evidence==null)return;
        LuminousPartialFit.Fit verified=fit(evidence,d.circularAlternatives,hour,R),old=d.alignment;
        if(verified.ideal==null){
            d.alignment=verified;
            if(d.polygon!=null){d.polygon=null;d.method="none";d.fail("CIRCULAR_BOUNDARY_UNRESOLVED",verified.reason);}
            return;
        }
        // Preserve a previous fit only when independent photographic arcs confirm its boundary
        // within the existing contour-localisation allowance. Percentage never selects a fit.
        boolean agrees=old!=null&&old.ideal!=null&&old.center!=null&&
            R*(Math.hypot(old.center[0]-verified.center[0],old.center[1]-verified.center[1])+Math.abs(old.radius-verified.radius))<=1.25;
        if(agrees){old.reason=verified.reason+"; previous geometry independently confirmed within contour-localisation allowance";}
        else {d.alignment=verified;d.attempts.add("CIRCULAR_BOUNDARY_CORRECTED: prior arc samples did not follow the independently evidenced separator");}
        if(d.polygon!=null){
            // A fitted circle never replaces observed area. An accepted physical contour
            // supporting a different stable circle is independent contradictory evidence;
            // an isolated noncircular contour departure can still leave partial arcs usable.
            double[][] contour=d.polygon;double maximum=0;
            for(double[] p:contour)maximum=Math.max(maximum,R*Math.abs(Math.hypot(p[0]-verified.center[0],p[1]-verified.center[1])-verified.radius));
            if(maximum>1.25){
                LuminousPartialFit.Fit observed=LuminousPartialFit.fit(contour,ModelSpec.Shape.ROUND,hour,R);
                if(observed.ideal!=null&&R*(Math.hypot(observed.center[0]-verified.center[0],observed.center[1]-verified.center[1])+Math.abs(observed.radius-verified.radius))>1.25)
                    d.alignment=withheld(evidence,"accepted observed boundary and photographic arcs support competing circle geometry; luminous-to-surround identity unresolved");
                d.polygon=null;d.method="none";d.fail("CIRCULAR_BOUNDARY_UNRESOLVED",d.alignment.ideal==null?d.alignment.reason:"observed contour disagrees with independently supported luminous boundary");
            }
        }
    }
}
