package com.watchalign.mobile;

import android.graphics.PointF;
import java.util.Locale;

/**
 * Explainable displacement of a corrected 12-marker against the fixed projected
 * image-derived genuine reference. Values are normalized to the reference triangle
 * base width so they stay comparable across image scale and perspective.
 */
final class Triangle12ReferenceDiagnostics {
    static final class Result {
        final float baseRadialPct;
        final float apexRadialPct;
        final float widthDeltaPct;
        final float centreLateralPct;
        final float leftRadialPct;
        final float rightRadialPct;
        Result(float baseRadialPct,float apexRadialPct,float widthDeltaPct,float centreLateralPct,float leftRadialPct,float rightRadialPct){
            this.baseRadialPct=baseRadialPct;this.apexRadialPct=apexRadialPct;this.widthDeltaPct=widthDeltaPct;this.centreLateralPct=centreLateralPct;this.leftRadialPct=leftRadialPct;this.rightRadialPct=rightRadialPct;
        }
        String compact(float rotationDeg){
            return String.format(Locale.US,
                    "Vs projected ref: base %s %+.1f%% BW · apex %s %+.1f%% BW · width %+.1f%% · centre %+.1f%% BW · rotation %+.2f°",
                    radialWord(baseRadialPct),Math.abs(baseRadialPct),radialWord(apexRadialPct),Math.abs(apexRadialPct),widthDeltaPct,centreLateralPct,rotationDeg);
        }
        String vertexCompact(){
            return String.format(Locale.US,"Base corners radial: left %+.1f%% BW · right %+.1f%% BW",leftRadialPct,rightRadialPct);
        }
    }

    private Triangle12ReferenceDiagnostics(){}

    static Result measure(PerspectiveMasterRenderer.Pose pose,PointF[] actual){
        if(pose==null||actual==null||actual.length<3||actual[0]==null||actual[1]==null||actual[2]==null)return null;
        PointF l=PerspectiveRectifier.toDial(pose,actual[0]);
        PointF r=PerspectiveRectifier.toDial(pose,actual[1]);
        PointF a=PerspectiveRectifier.toDial(pose,actual[2]);
        if(l==null||r==null||a==null)return null;

        float refLx=Gmt126710BlnrTriangleReference.LEFT_X,refLy=Gmt126710BlnrTriangleReference.LEFT_Y;
        float refRx=Gmt126710BlnrTriangleReference.RIGHT_X,refRy=Gmt126710BlnrTriangleReference.RIGHT_Y;
        float refAx=Gmt126710BlnrTriangleReference.APEX_X,refAy=Gmt126710BlnrTriangleReference.APEX_Y;
        float refW=(float)Math.hypot(refRx-refLx,refRy-refLy);if(refW<1e-6f)return null;

        float baseCx=(l.x+r.x)/2f,baseCy=(l.y+r.y)/2f;
        float refBaseCx=(refLx+refRx)/2f,refBaseCy=(refLy+refRy)/2f;
        float actualW=(float)Math.hypot(r.x-l.x,r.y-l.y);

        // Canonical dial has 12 o'clock toward negative y. Positive radial means outward/toward minute track.
        float leftRadial=-(l.y-refLy)/refW*100f;
        float rightRadial=-(r.y-refRy)/refW*100f;
        float baseRadial=-(baseCy-refBaseCy)/refW*100f;
        float apexRadial=-(a.y-refAy)/refW*100f;
        float widthDelta=(actualW/refW-1f)*100f;
        float centreLateral=(baseCx-refBaseCx)/refW*100f;
        return new Result(baseRadial,apexRadial,widthDelta,centreLateral,leftRadial,rightRadial);
    }

    private static String radialWord(float v){return v>=0?"outward":"inward";}
}
