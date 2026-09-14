package com.watchalign.mobile;
import android.graphics.Bitmap;import com.watchalign.mobile.qc.RectificationConfidenceService;
/** Runs all automated GMT modules against one canonical rectified dial. */
final class GmtQcPipeline {
 static final class Result {final GmtIndexAutoAnalyzer.Result indices;final QcExtendedAnalyzer.Result components;final RectificationConfidenceService.Assessment rectification;Result(GmtIndexAutoAnalyzer.Result i,QcExtendedAnalyzer.Result c,RectificationConfidenceService.Assessment r){indices=i;components=c;rectification=r;}}
 static Result analyse(Bitmap watch,String model,PerspectiveMasterRenderer.Pose pose){try(CanonicalGmtDial dial=CanonicalGmtDial.create(watch,pose)){if(dial==null)return null;return new Result(GmtIndexAutoAnalyzer.analyse(watch,dial),QcExtendedAnalyzer.analyse(watch,null,model,dial),dial.rectification);}}
 private GmtQcPipeline(){}
}
