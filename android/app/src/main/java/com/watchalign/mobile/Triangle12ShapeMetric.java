package com.watchalign.mobile;

/** Pure geometry for comparing a tapped 12 o'clock triangle with the perspective-projected target. */
final class Triangle12ShapeMetric {
    static final class Result {
        final float radialPx, radialPct, lateralPx, rotationDeg, widthPct, heightPct, apexCentrePx;
        Result(float radialPx,float radialPct,float lateralPx,float rotationDeg,float widthPct,float heightPct,float apexCentrePx){
            this.radialPx=radialPx;this.radialPct=radialPct;this.lateralPx=lateralPx;this.rotationDeg=rotationDeg;this.widthPct=widthPct;this.heightPct=heightPct;this.apexCentrePx=apexCentrePx;
        }
    }

    static Result measure(float[] actual,float[] expected,float outwardX,float outwardY,float rightX,float rightY,float dialR){
        if(actual==null||expected==null||actual.length<6||expected.length<6)throw new IllegalArgumentException("three vertices required");
        float acx=(actual[0]+actual[2]+actual[4])/3f, acy=(actual[1]+actual[3]+actual[5])/3f;
        float ecx=(expected[0]+expected[2]+expected[4])/3f, ecy=(expected[1]+expected[3]+expected[5])/3f;
        float on=(float)Math.hypot(outwardX,outwardY);if(on<1e-6f)on=1f;outwardX/=on;outwardY/=on;
        float rn=(float)Math.hypot(rightX,rightY);if(rn<1e-6f)rn=1f;rightX/=rn;rightY/=rn;
        float dx=acx-ecx,dy=acy-ecy;
        float radial=dx*outwardX+dy*outwardY,lateral=dx*rightX+dy*rightY;

        float abx=(actual[0]+actual[2])/2f,aby=(actual[1]+actual[3])/2f;
        float ebx=(expected[0]+expected[2])/2f,eby=(expected[1]+expected[3])/2f;
        float aax=actual[4]-abx,aay=actual[5]-aby,eax=expected[4]-ebx,eay=expected[5]-eby;
        float rotation=(float)Math.toDegrees(Math.atan2(eax*aay-eay*aax,eax*aax+eay*aay));
        float aw=dist(actual[0],actual[1],actual[2],actual[3]),ew=dist(expected[0],expected[1],expected[2],expected[3]);
        float ah=(float)Math.hypot(aax,aay),eh=(float)Math.hypot(eax,eay);
        float widthPct=ew<1e-6f?0f:(aw/ew-1f)*100f,heightPct=eh<1e-6f?0f:(ah/eh-1f)*100f;
        float bux=actual[2]-actual[0],buy=actual[3]-actual[1],bn=(float)Math.hypot(bux,buy);if(bn<1e-6f)bn=1f;bux/=bn;buy/=bn;
        float apexCentre=(actual[4]-abx)*bux+(actual[5]-aby)*buy;
        return new Result(radial,dialR>1e-6f?radial/dialR*100f:0f,lateral,rotation,widthPct,heightPct,apexCentre);
    }

    private static float dist(float x1,float y1,float x2,float y2){return (float)Math.hypot(x2-x1,y2-y1);}
    private Triangle12ShapeMetric(){}
}
