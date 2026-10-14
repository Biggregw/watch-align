package com.watchalign.mobile;

/** The filled outer-metal nominal used for individual marker inspection and area comparison.
 * Uses the existing model geometry and triangle nominal; never changes calibration or pose.
 * The ring's existing linear similarity model removes global ring motion from local mismatch.
 */
final class ExpectedMarkerOutline {
    private ExpectedMarkerOutline() {}

    static double[][] polygon(ModelSpec.Marker spec,ModelReference ref,double[] ring){
        if(spec==null)return null;
        if(spec.shape==ModelSpec.Shape.BATON)return moved(spec.batonPolygon(),ring);
        if(spec.shape==ModelSpec.Shape.TRIANGLE)return moved(nominalTriangle(spec,ref),ring);
        double[] c=moved(new double[][]{spec.masterPoint()},ring)[0];
        // Preserve the close-up/research radius convention, rather than refitting the genuine model.
        double scale=ring==null||ring.length<4?1:1+ring[2];
        return MarkerPolygonMismatch.circle(c[0],c[1],spec.outerR*scale,128);
    }

    static double[][] moved(double[][] v,double[] x){
        if(x==null||x.length<4)return v;
        double[][] o=new double[v.length][];
        for(int i=0;i<v.length;i++){double px=v[i][0],py=v[i][1];o[i]=new double[]{px+x[0]+x[2]*px-x[3]*py,py+x[1]+x[2]*py+x[3]*px};}
        return o;
    }

    static double[][] nominalTriangle(ModelSpec.Marker spec,ModelReference ref){
        double[][] v=spec.trianglePolygon();
        if(ref==null||ref.triangle==null)return v;
        double a=Math.toRadians(spec.hour*30.0),er0=spec.hour%12==0?0:Math.sin(a),er1=spec.hour%12==0?-1:-Math.cos(a),et0=spec.hour%12==0?1:Math.cos(a),et1=spec.hour%12==0?0:Math.sin(a);
        double dr=ref.triangle.nominalRadialR,dt=ref.triangle.nominalTangentialR;
        double dx=dr*er0+dt*et0,dy=dr*er1+dt*et1;
        double[][] o=new double[v.length][];for(int i=0;i<v.length;i++)o[i]=new double[]{v[i][0]+dx,v[i][1]+dy};
        return o;
    }
}
