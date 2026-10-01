package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * 124060 app-facing presentation route. Analysis remains model-specific, but the visible overlay and
 * close-up planning are the same measured renderer used by GMT.
 */
final class Sub124060CommonPresentation {
    private Sub124060CommonPresentation(){}

    static WatchAlignCoreV13.AnalysisResult analyseForCore(Bitmap watch,FullResSource full,PerspectiveGmtOverlay.DialSeed manual){
        GmtDialCrop.Crop crop=manual==null&&full!=null?GmtDialCrop.make(watch,full):null;
        Sub124060QcAnalyzer.Result res=crop!=null?Sub124060QcAnalyzer.analyse(crop.bitmap,null):Sub124060QcAnalyzer.analyse(watch,manual);
        MeasuredOverlayRenderer.Drawing drawing=Sub124060PresentationAdapter.adapt(res);

        Bitmap closeUp=null;
        if(crop!=null&&drawing.hasAnything()){
            Bitmap cropOverlay=MeasuredOverlayRenderer.render(crop.bitmap,drawing);
            closeUp=MeasuredOverlayRenderer.closeUp(crop.bitmap,cropOverlay,drawing,540);
            drawing.mapTo(crop);
        }
        Bitmap overlay=drawing.hasAnything()?MeasuredOverlayRenderer.render(watch,drawing):null;
        if(closeUp==null&&drawing.hasAnything())closeUp=MeasuredOverlayRenderer.closeUp(watch,overlay,drawing,540);

        String report="Rolex Submariner 124060 (experimental) · Watch Align Core "+WatchAlignCoreV13.CORE_VERSION+"\n\n"
                +Sub124060Summary.build(res)
                +"\n\nDETAILS\n"+Sub124060Summary.details(res,crop!=null?crop.describe():null);
        WatchAlignCoreV13.AnalysisResult out=new WatchAlignCoreV13.AnalysisResult(watch,null,null,overlay,null,report,Double.NaN,
                res.triangle!=null?1.0:0.0,res.triangle!=null);
        out.twelveCloseUp=closeUp;
        out.submariner=true;
        out.dialNeedsManual=res.needsManual();
        out.sub124060=res;
        return out;
    }
}
