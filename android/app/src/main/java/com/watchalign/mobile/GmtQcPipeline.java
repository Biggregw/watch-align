package com.watchalign.mobile;
import android.graphics.Bitmap;import com.watchalign.mobile.qc.RectificationConfidenceService;
/** Runs all automated GMT modules against one canonical rectified dial. */
final class GmtQcPipeline {
 static final class Result {final GmtIndexAutoAnalyzer.Result indices;final GmtTriangleAutoAnalyzer.Result triangle;final QcExtendedAnalyzer.Result components;final RectificationConfidenceService.Assessment rectification;Result(GmtIndexAutoAnalyzer.Result i,GmtTriangleAutoAnalyzer.Result t,QcExtendedAnalyzer.Result c,RectificationConfidenceService.Assessment r){indices=i;triangle=t;components=c;rectification=r;}}
 /**
  * Runs one canonical-dial creation attempt for this QC pass. If this returns null, callers must
  * report {@link #lastFailureReason()} rather than retrying CanonicalGmtDial.create with the same
  * bitmap/pose. The warp is expensive and a second attempt can hide the original failure.
  */
 static Result analyse(Bitmap watch,String model,PerspectiveMasterRenderer.Pose pose){try(CanonicalGmtDial dial=CanonicalGmtDial.create(watch,pose)){if(dial==null)return null;return new Result(GmtIndexAutoAnalyzer.analyse(watch,dial),GmtTriangleAutoAnalyzer.analyse(dial),QcExtendedAnalyzer.analyse(watch,null,model,dial),dial.rectification);}}
 static String lastFailureReason(){return CanonicalGmtDial.lastFailureReason();}
 private GmtQcPipeline(){}
}
