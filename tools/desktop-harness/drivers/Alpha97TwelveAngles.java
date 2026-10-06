package com.watchalign.mobile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH PROTOTYPE (desktop harness only; not compiled into the app): lighting-robust 12-triangle report.
 *
 * Offline tests showed the 12's radial position is moved by studio lighting (which surround facet gives the outermost
 * edge on each side), while the triangle's angles are not, and its left/right position is affected about half as much.
 * This reports, from the unchanged production Alpha94MarkerMeasurement.Report and the genuine nominal
 * (Alpha97TriangleNominal, m12_nominal.properties):
 *   centreline rotation   deg, re-centred, + = clockwise                     (primary, lighting-robust)
 *   left / right side     deg, re-centred; sides = max |left|,|right|        (primary, lighting-robust)
 *   lateral position      px and R, re-centred tangential (left/right)       (primary, keeps translation sensitivity)
 *   radial position       px, re-centred, shown only as a lighting-sensitive note, never part of the primary readout
 * and, as context only, how many genuine watches read at least as far on each primary component (per-watch values from
 * m12_genuine_reference.csv, each computed without that watch). Nothing is thresholded or classified; fail-closed exactly
 * as production (unusable triangle or unfitted ring gives no value).
 */
final class Alpha97TwelveAngles {
    final Alpha97TriangleNominal nom;
    final double[] lateralR,centrelineDeg,sidesDeg;

    Alpha97TwelveAngles(Alpha97TriangleNominal nom,double[] lateralR,double[] centrelineDeg,double[] sidesDeg){
        this.nom=nom;this.lateralR=lateralR;this.centrelineDeg=centrelineDeg;this.sidesDeg=sidesDeg;
    }

    static Alpha97TwelveAngles load(String nominalPath,String referencePath) throws IOException{
        List<String> lines=Files.readAllLines(Path.of(referencePath));
        String[] h=lines.get(0).split(",",-1);int il=-1,ic=-1,is=-1;
        for(int i=0;i<h.length;i++){if(h[i].equals("lateral_R"))il=i;if(h[i].equals("centreline_deg"))ic=i;if(h[i].equals("sides_deg"))is=i;}
        if(il<0||ic<0||is<0)throw new IllegalArgumentException("reference needs lateral_R, centreline_deg, sides_deg");
        List<double[]> v=new ArrayList<>();
        for(int i=1;i<lines.size();i++){String[] f=lines.get(i).split(",",-1);
            v.add(new double[]{Double.parseDouble(f[il]),Double.parseDouble(f[ic]),Double.parseDouble(f[is])});}
        double[] l=new double[v.size()],c=new double[v.size()],s=new double[v.size()];
        for(int i=0;i<v.size();i++){l[i]=v.get(i)[0];c[i]=v.get(i)[1];s[i]=v.get(i)[2];}
        return new Alpha97TwelveAngles(Alpha97TriangleNominal.load(nominalPath),l,c,s);
    }

    static final class Result {
        boolean usable;String reason="";
        double centrelineDeg=Double.NaN,leftSideDeg=Double.NaN,rightSideDeg=Double.NaN,sidesDeg=Double.NaN;
        /** + = right in the upright dial frame. */
        double lateralPx=Double.NaN,lateralR=Double.NaN;
        /** Lighting-sensitive note only. + = outward (up, toward 12), - = toward the dial centre. */
        double radialPx=Double.NaN;
        int nGenuine,atLeastCentreline,atLeastSides,atLeastLateral;

        String line(){
            if(!usable)return "12 (angles + lateral, research): "+reason;
            return String.format(Locale.US,"12 (angles + lateral, research): centreline %s · L/R sides %+.2f°/%+.2f° · lateral %.2f px %s (%.4f R)"
                            +" · genuine watches at least as far: centreline %d/%d, sides %d/%d, lateral %d/%d"
                            +" · [radial %.2f px %s: lighting-sensitive, not assessed]",
                    Alpha94MarkerMeasurement.Report.rot(centrelineDeg),leftSideDeg,rightSideDeg,Math.abs(lateralPx),lateralPx<0?"left":"right",
                    Math.abs(lateralR),atLeastCentreline,nGenuine,atLeastSides,nGenuine,atLeastLateral,nGenuine,
                    Math.abs(radialPx),radialPx<0?"toward centre":"outward");
        }
    }

    Result apply(Alpha94MarkerMeasurement.Report r){
        Result o=new Result();
        Alpha97TriangleNominal.Result t=nom.apply(r);
        if(!t.usable){o.reason=t.reason;return o;}
        double rpx=r.dialRadiusPx;
        o.usable=true;
        o.centrelineDeg=t.rotationDeg;o.leftSideDeg=t.leftSideDeg;o.rightSideDeg=t.rightSideDeg;
        o.sidesDeg=Math.max(Math.abs(t.leftSideDeg),Math.abs(t.rightSideDeg));
        o.lateralPx=t.rightPx;o.lateralR=t.rightPx/rpx;o.radialPx=t.radialPx;
        o.nGenuine=lateralR.length;
        o.atLeastCentreline=atLeast(centrelineDeg,Math.abs(o.centrelineDeg));
        o.atLeastSides=atLeast(sidesDeg,o.sidesDeg);
        o.atLeastLateral=atLeast(lateralR,Math.abs(o.lateralR));
        return o;
    }
    private static int atLeast(double[] ref,double v){int n=0;for(double x:ref)if(x>=v-1e-12)n++;return n;}
}
