package com.watchalign.mobile;

import java.util.*;

/** Photographic evidence for circular lume boundaries, independent of the expected template.
 * A small interior brightness drop is not a separator merely because distant metal recovers.
 * Use the final observed fall into a local dark trough and its immediately following recovery.
 * All numeric gates below reuse the experimental separator/fit pixel-evidence gates; no QC bands.
 */
final class LuminousCircularBoundary {
    static double[][] samples(byte[] pixels,int n,int seed){
        List<double[]> points=new ArrayList<>();
        for(int k=0;k<LuminousSeparator.RAYS;k++){
            double a=k*2*Math.PI/LuminousSeparator.RAYS,dx=Math.cos(a),dy=Math.sin(a);
            int count=(int)(((n/2.0-2)/Math.max(Math.abs(dx),Math.abs(dy)))/.25);
            double[] v=new double[count];for(int i=0;i<count;i++)v[i]=LuminousSeparator.smooth(pixels,n,seed+i*.25*dx,seed+i*.25*dy);
            for(int j=12;j<count-8;j++){
                double drop=v[j-4]-v[j+4];
                if(drop<12||drop<v[j-5]-v[j+3]||drop<v[j-3]-v[j+5])continue;
                int trough=j+4;while(trough<count-2&&v[trough+1]<=v[trough])trough++;
                int recovery=trough;while(recovery<count-2&&v[recovery+1]>=v[recovery])recovery++;
                // Recovery must belong to this trough. A later polished rim must not validate
                // an interior texture edge, or a hand/shadow followed by more luminous material.
                if(v[recovery]-v[trough]<12||v[j+4]-v[trough]>12)continue;
                int bright=0;for(int i=0;i<j-4;i++)if(v[i]>=v[j+4]+.6*drop)bright++;
                if(bright<.90*(j-4))continue;
                double target=v[j+4]+.50*drop;int crossing=-1;
                for(int i=j-4;i<j+4;i++)if(v[i]>=target&&v[i+1]<target)crossing=i;
                if(crossing<0)continue;
                double r=.25*(crossing+(target-v[crossing])/(v[crossing+1]-v[crossing]));
                points.add(new double[]{seed+r*dx,seed+r*dy});
            }
        }
        return points.toArray(new double[0][]);
    }

    static LuminousPartialFit.Fit fit(double[][] points,int hour,double R){
        LuminousPartialFit.Fit f=LuminousPartialFit.fit(points,ModelSpec.Shape.ROUND,hour,R);
        if(f.ideal==null)return f;
        List<double[]> in=new ArrayList<>(),other=new ArrayList<>();
        for(double[] p:points)(Math.abs(Math.hypot(p[0]-f.center[0],p[1]-f.center[1])-f.radius)*R<=.8?in:other).add(p);
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
        LuminousPartialFit.Fit verified=fit(evidence,hour,R),old=d.alignment;
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
            // Full observed contours are never replaced by the fitted circle. Contradictory
            // physical-area evidence is withheld, while independent arcs may still measure.
            double[][] contour=d.polygon;double maximum=0;
            for(double[] p:contour)maximum=Math.max(maximum,R*Math.abs(Math.hypot(p[0]-verified.center[0],p[1]-verified.center[1])-verified.radius));
            if(maximum>1.25){d.polygon=null;d.method="none";d.fail("CIRCULAR_BOUNDARY_UNRESOLVED","observed contour disagrees with independently supported luminous boundary");}
        }
    }
}
