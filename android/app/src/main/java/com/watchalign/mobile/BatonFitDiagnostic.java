package com.watchalign.mobile;

import java.util.Locale;

/**
 * Display-only diagnostics for the exact four-sided baton polygon fitted by Alpha94.
 *
 * The yellow close-up outline is the model geometry moved by the measured marker ring.
 * The cyan outline uses the detector's fitVertices. Both are in the same canonical,
 * perspective-corrected dial frame; their long-edge angular differences are thus
 * directly visible on the close-up. No scores, tolerances or reference data change.
 */
final class BatonFitDiagnostic {
    private BatonFitDiagnostic() {}

    static final class Angles {
        final double side0Deg,side2Deg;
        Angles(double side0Deg,double side2Deg) {
            this.side0Deg=side0Deg;this.side2Deg=side2Deg;
        }
    }

    /** Sides 0 and 2 are the two opposing LONG edges in ModelSpec.Marker.batonPolygon(). */
    static Angles angles(double[][] expected,double[][] fitted) {
        if(expected==null||fitted==null||expected.length!=4||fitted.length!=4)return null;
        double side0=angleBetween(expected,fitted,0);
        double side2=angleBetween(expected,fitted,2);
        if(!Double.isFinite(side0)||!Double.isFinite(side2))return null;
        return new Angles(side0,side2);
    }

    private static double angleBetween(double[][] expected,double[][] fitted,int side) {
        int next=(side+1)%4;
        if(expected[side]==null||expected[next]==null||fitted[side]==null||fitted[next]==null
                ||expected[side].length<2||expected[next].length<2
                ||fitted[side].length<2||fitted[next].length<2)return Double.NaN;
        double ex=expected[next][0]-expected[side][0],ey=expected[next][1]-expected[side][1];
        double fx=fitted[next][0]-fitted[side][0],fy=fitted[next][1]-fitted[side][1];
        if(Math.hypot(ex,ey)<1e-9||Math.hypot(fx,fy)<1e-9)return Double.NaN;
        double delta=Math.toDegrees(Math.atan2(fy,fx)-Math.atan2(ey,ex));
        while(delta>90)delta-=180;
        while(delta<=-90)delta+=180;
        return delta;
    }

    /** Same ring transform used by Alpha98Closeups.moved, kept independent of UI/OpenCV for unit tests. */
    static double[][] moved(double[][] v,double[] ring) {
        if(ring==null||ring.length<4)return v;
        double[][] out=new double[v.length][];
        for(int i=0;i<v.length;i++){
            double x=v[i][0],y=v[i][1];
            out[i]=new double[]{x+ring[0]+ring[2]*x-ring[3]*y,
                    y+ring[1]+ring[2]*y+ring[3]*x};
        }
        return out;
    }

    private static String direction(double d){return d>=0?"clockwise":"anticlockwise";}

    static String describe(ModelSpec.Marker spec,Alpha94MarkerMeasurement.Marker marker,double[] ring) {
        if(spec==null||marker==null||!marker.usable||marker.fitVertices==null
                ||spec.shape!=ModelSpec.Shape.BATON)return null;
        Angles a=angles(moved(spec.batonPolygon(),ring),marker.fitVertices);
        if(a==null)return null;
        String first=spec.hour==9?"lower":spec.hour==6?"right":"side A";
        String second=spec.hour==9?"upper":spec.hour==6?"left":"side B";
        // For a 9 marker present upper first, so it reads naturally against the photograph.
        if(spec.hour==9)
            return String.format(Locale.US,
                    "Fitted long-edge angles vs scoring reference: upper %.2f° %s; lower %.2f° %s.",
                    Math.abs(a.side2Deg),direction(a.side2Deg),
                    Math.abs(a.side0Deg),direction(a.side0Deg));
        return String.format(Locale.US,
                "Fitted long-edge angles vs scoring reference: %s %.2f° %s; %s %.2f° %s.",
                first,Math.abs(a.side0Deg),direction(a.side0Deg),
                second,Math.abs(a.side2Deg),direction(a.side2Deg));
    }
}
