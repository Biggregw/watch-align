package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.util.*;

/** Opt-in measurement; never creates QC findings or changes the existing output. */
final class LuminousMeasurement {
    static final class Marker {
        final ModelSpec.Marker spec;final LuminousMarkerDetector.Detection detection;final double[][] expected;
        final MarkerPolygonMismatch.Score score;final String reason;
        double rotationDeg=Double.NaN,dxR=Double.NaN,dyR=Double.NaN,widthPct=Double.NaN,lengthPct=Double.NaN,sizePct=Double.NaN;
        Marker(ModelSpec.Marker m,LuminousMarkerDetector.Detection d,double[][] e){
            spec=m;detection=d;expected=e;score=d.usable()&&e!=null?MarkerPolygonMismatch.compare(e,d.polygon):null;
            reason=!d.usable()?d.reason:e==null?"insufficient genuine inner-reference evidence":score==null?"invalid inner polygon":"measured";
            if(score!=null){double[] ec=LuminousGeometry.centroid(e),ac=LuminousGeometry.centroid(d.polygon);dxR=ac[0]-ec[0];dyR=ac[1]-ec[1];sizePct=100*(score.actualArea/score.expectedArea-1);
                double angle=m.shape==ModelSpec.Shape.ROUND?0:LuminousGeometry.angle(e);double[] es=LuminousGeometry.extent(e,angle),as=LuminousGeometry.extent(d.polygon,m.shape==ModelSpec.Shape.ROUND?0:LuminousGeometry.angle(d.polygon));
                lengthPct=100*(as[0]/es[0]-1);widthPct=100*(as[1]/es[1]-1);
                if(m.shape!=ModelSpec.Shape.ROUND&&d.rotationSpreadDeg<=1.5)rotationDeg=LuminousGeometry.angleDifference(LuminousGeometry.angle(d.polygon),angle);
            }
        }
        boolean assessed(){return score!=null;}
        MarkerMismatchResearch.Result displayPolygon(){return new MarkerMismatchResearch.Result(score,assessed()?"measured":reason,expected);}
        String description(){
            if(!assessed())return "Not assessed: "+reason+". No percentage or detected outline is presented.";
            return String.format(Locale.US,"Mismatch %.3f%% = extra %.3f%% + missing %.3f%%\nHorizontal displacement %+.3f%% R · vertical %+.3f%% R\nArea size difference %+.3f%% · major span %+.3f%% · minor span %+.3f%%\n%s\nBoundary movement across local contrast levels: %.2f source px. This is a stability diagnostic, not a calibrated confidence interval. No pass/fail threshold.",score.mismatchPct,score.extraPct,score.missingPct,100*dxR,100*dyR,sizePct,lengthPct,widthPct,
                spec.shape==ModelSpec.Shape.ROUND?"Rotation: not applicable to a round":Double.isFinite(rotationDeg)?String.format(Locale.US,"Rotation %+.3f° (+ clockwise); contrast-level angle span %.3f°",rotationDeg,detection.rotationSpreadDeg):"Rotation withheld: inner orientation varies across contrast levels",detection.boundarySpreadPx);
        }
    }
    final Map<Integer,Marker> markers=new LinkedHashMap<>();
    static LuminousMeasurement run(Bitmap photo,Alpha99Pipeline.Output outer,LuminousReference ref){
        LuminousMeasurement out=new LuminousMeasurement();Map<Integer,LuminousMarkerDetector.Detection> d=LuminousMarkerDetector.detect(photo,outer);
        double[] ring=outer.measurement.ring!=null&&outer.measurement.ring.usable?outer.measurement.ring.model:null;
        for(ModelSpec.Marker m:outer.model.markers)out.markers.put(m.hour,new Marker(m,d.get(m.hour),ring==null?null:ref.expected(m,ring)));
        return out;
    }
}
