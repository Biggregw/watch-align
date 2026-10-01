package com.watchalign.mobile;

import java.util.Locale;

/**
 * Explicit dial layout of the Rolex Submariner 124060 (no date). It comes from the model, never from
 * the GMT date-side probe (GmtDialLayout): triangle at 12, batons at 3, 6 and 9, round markers at
 * 1, 2, 4, 5, 7, 8, 10 and 11, no date window.
 */
final class Sub124060Layout {
    static final String MODEL = "124060";
    static final int TRIANGLE_HOUR = 12;
    static final GmtSixLandmarkAnalyzer.Position[] BATONS = {
            GmtSixLandmarkAnalyzer.Position.THREE, GmtSixLandmarkAnalyzer.Position.SIX, GmtSixLandmarkAnalyzer.Position.NINE};
    static final int[] ROUND_HOURS = {1, 2, 4, 5, 7, 8, 10, 11};
    static final boolean HAS_DATE = false;

    private Sub124060Layout(){}

    /** True only for the 124060. 126610LN/LV and every other Submariner are not supported yet. */
    static boolean supports(String modelRef){
        return modelRef!=null&&MODEL.equals(modelRef.trim().toUpperCase(Locale.US));
    }

    /** What sits at an hour position: "triangle", "baton" or "round". */
    static String kindAt(int hour){
        if(hour==TRIANGLE_HOUR)return "triangle";
        for(GmtSixLandmarkAnalyzer.Position p:BATONS)if(p.label.equals(String.valueOf(hour)))return "baton";
        for(int h:ROUND_HOURS)if(h==hour)return "round";
        throw new IllegalArgumentException("no hour "+hour);
    }
}
