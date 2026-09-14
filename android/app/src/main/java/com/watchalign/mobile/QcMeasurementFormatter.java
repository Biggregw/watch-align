package com.watchalign.mobile;

import java.util.Locale;

/** Explainable numeric wording for image-derived controls. */
final class QcMeasurementFormatter {
    static String range(String name,float value,float median,float min,float max,String unit){
        boolean inside=value>=min&&value<=max;float signed=value-median;
        float margin=inside?Math.min(value-min,max-value):Math.min(Math.abs(value-min),Math.abs(value-max));
        String direction=Math.abs(signed)<0.0005f?"at median":signed>0?"high vs median":"low vs median";
        return String.format(Locale.US,"%s: %.3f %s · median %.3f · observed %.3f–%.3f · Δ median %+.3f (%s) · %s by %.3f",
                name,value,unit,median,min,max,signed,direction,inside?"inside observed genuine range, nearest-boundary margin":"outside observed genuine range",margin);
    }
    static String rotation(float value){float a=Math.abs(value),max=GenTriangle12RelationalReference.ROTATION_OBSERVED_GEN_MAX_DEG;return String.format(Locale.US,"Rotation: %+.2f° · observed genuine range −%.2f° to +%.2f° · %s · boundary margin %.2f°",value,max,max,a<=max?"inside range":"outside range",Math.abs(max-a));}
    private QcMeasurementFormatter(){}
}
