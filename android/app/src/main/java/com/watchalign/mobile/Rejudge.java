package com.watchalign.mobile;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

/** Watch Align 1.6.2: the photo on the results screen judged again after the owner saves new limits (nothing is
 *  measured again; the answers, the evidence pictures, the dial overview and the technical text are rebuilt). */
final class Rejudge {
    private Rejudge(){}
    static final String EARLIER="\n\nEarlier luminous / outer-metal analysis";

    static synchronized void current(ClearlyOff.Limits L){
        ClearlyOff.Result r=AlignmentResultsActivity.Store.clearlyOff;AlignmentAnalysis.Output o=AlignmentResultsActivity.Store.result;
        ModelSpec model=AlignmentResultsActivity.Store.model;
        if(r==null||o==null||model==null)return;
        ClearlyOff.rescore(r,L);
        AlignmentDecision.applyClearlyOff(o.markers,r,o.photoReason);
        String t=AlignmentResultsActivity.Store.technical;int k=t==null?-1:t.indexOf(EARLIER);
        AlignmentResultsActivity.Store.technical=ClearlyOff.technical(r)+(k>=0?t.substring(k):"");
        Mat rgba=new Mat();
        try{
            Utils.bitmapToMat(AlignmentResultsActivity.Store.photo,rgba);double[] H=AlignmentResultsActivity.Store.H;
            for(AlignmentDecision.Marker am:o.markers){
                try{AlignmentResultsActivity.Store.closeups.put(am.hour,ClearlyOffEvidence.render(rgba,H,model,r,am));}catch(Throwable ignored){}
            }
            try{AlignmentResultsActivity.Store.overview=AlignmentOverview.render(rgba,H,o,model);}catch(Throwable ignored){}
        }catch(Throwable ignored){}
        finally{rgba.release();}
    }
}
