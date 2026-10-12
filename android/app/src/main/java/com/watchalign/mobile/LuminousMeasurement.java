package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.util.*;

/** Opt-in measurement; never creates QC findings or changes the existing output. */
final class LuminousMeasurement {
    static final class Marker {
        final ModelSpec.Marker spec;final LuminousMarkerDetector.Detection detection;final double[][] expected;
        final MarkerPolygonMismatch.Score score,fittedScore,alignmentScore;final String reason;
        final LuminousPartialFit.Fit alignment,expectedAlignment;
        double rotationDeg=Double.NaN,dxR=Double.NaN,dyR=Double.NaN,widthPct=Double.NaN,lengthPct=Double.NaN,sizePct=Double.NaN;
        Marker(ModelSpec.Marker m,LuminousMarkerDetector.Detection d,double[][] e){
            spec=m;detection=d;expected=e;alignment=d.alignment;
            expectedAlignment=e==null?null:LuminousPartialFit.fit(sampleReference(e),m.shape,m.hour,1000);
            score=d.usable()&&e!=null?MarkerPolygonMismatch.compare(e,d.polygon):null;
            fittedScore=e!=null&&alignment!=null&&alignment.ideal!=null?MarkerPolygonMismatch.compare(e,alignment.ideal):null;
            double[][] placement=null;
            if(e!=null&&alignment!=null&&expectedAlignment!=null&&alignment.center!=null&&expectedAlignment.center!=null&&(m.shape==ModelSpec.Shape.ROUND||alignment.orientation&&expectedAlignment.orientation)){
                double theta=m.shape==ModelSpec.Shape.ROUND?0:Math.toRadians(LuminousGeometry.angleDifference(alignment.angle,expectedAlignment.angle));
                placement=rigid(e,expectedAlignment.center,alignment.center,theta);
            }
            alignmentScore=placement==null?null:MarkerPolygonMismatch.compare(e,placement);
            reason=!d.usable()?d.reason:e==null?"insufficient genuine inner-reference evidence":score==null?"invalid inner polygon":"measured";
            if(score!=null){double[] ec=LuminousGeometry.centroid(e),ac=LuminousGeometry.centroid(d.polygon);dxR=ac[0]-ec[0];dyR=ac[1]-ec[1];sizePct=100*(score.actualArea/score.expectedArea-1);
                double angle=m.shape==ModelSpec.Shape.ROUND?0:LuminousGeometry.angle(e);double[] es=LuminousGeometry.extent(e,angle),as=LuminousGeometry.extent(d.polygon,m.shape==ModelSpec.Shape.ROUND?0:LuminousGeometry.angle(d.polygon));
                lengthPct=100*(as[0]/es[0]-1);widthPct=100*(as[1]/es[1]-1);
                if(m.shape!=ModelSpec.Shape.ROUND&&d.rotationSpreadDeg<=1.5)rotationDeg=LuminousGeometry.angleDifference(LuminousGeometry.angle(d.polygon),angle);
            }
        }
        boolean assessed(){return score!=null;}
        MarkerPolygonMismatch.Score comparison(int mode){return mode==0?fittedScore:mode==1?alignmentScore:score;}
        MarkerMismatchResearch.Result displayPolygon(int mode){MarkerPolygonMismatch.Score q=comparison(mode);return new MarkerMismatchResearch.Result(q,q!=null?"measured":reason,expected);}
        static String scoreDescription(String title,MarkerPolygonMismatch.Score q,String withheld){return title+": "+(q==null?"withheld — "+withheld:String.format(Locale.US,"%.3f%% (extra %.3f%% + missing %.3f%%; displayed values rounded)",q.mismatchPct,q.extraPct,q.missingPct));}
        MarkerMismatchResearch.Result displayPolygon(){return new MarkerMismatchResearch.Result(score,assessed()?"measured":reason,expected);}
        boolean alignmentUsable(){return alignment!=null&&alignment.usable();}
        String alignmentDescription(){
            if(!alignmentUsable())return "Alignment not assessed: "+(alignment==null?reason:alignment.reason)+".";
            StringBuilder b=new StringBuilder("Alignment from visible edges (idealised fit):\n");
            if(alignment.orientation&&expectedAlignment!=null&&expectedAlignment.orientation)b.append(String.format(Locale.US,"Direction %+.3f° relative to inner reference (+ clockwise)\n",LuminousGeometry.angleDifference(alignment.angle,expectedAlignment.angle)));
            if(alignment.center!=null&&expectedAlignment!=null&&expectedAlignment.center!=null)b.append(String.format(Locale.US,"Centre displacement: horizontal %+.3f%% R · vertical %+.3f%% R\n",100*(alignment.center[0]-expectedAlignment.center[0]),100*(alignment.center[1]-expectedAlignment.center[1])));
            else if(Double.isFinite(alignment.crossC)&&expectedAlignment!=null&&expectedAlignment.center!=null)b.append(String.format(Locale.US,"Transverse centreline displacement %+.3f%% R; along-axis centre not assessed\n",100*(alignment.crossC-alignment.crossNormal[0]*expectedAlignment.center[0]-alignment.crossNormal[1]*expectedAlignment.center[1])));
            else b.append("Centre position not assessed\n");
            if(Double.isFinite(alignment.radius)&&expectedAlignment!=null&&Double.isFinite(expectedAlignment.radius))b.append(String.format(Locale.US,"Circle radius %.3f%% R (%+.3f%% size difference); rotation not applicable\n",100*alignment.radius,100*(alignment.radius/expectedAlignment.radius-1)));
            if(Double.isFinite(alignment.width))b.append(String.format(Locale.US,"Fitted width %.3f%% R\n",100*alignment.width));
            if(Double.isFinite(alignment.height))b.append(String.format(Locale.US,"Fitted length/height %.3f%% R\n",100*alignment.height));
            if(Double.isFinite(alignment.symmetry))b.append(String.format(Locale.US,"Triangle apex offset from base midpoint %+.3f%% of base width\n",alignment.symmetry));
            b.append(String.format(Locale.US,"Fit residual %.2f source px",alignment.rmsPx));
            if(Double.isFinite(alignment.angleSpanDeg))b.append(String.format(Locale.US,"; alternating-edge angle span %.3f°",alignment.angleSpanDeg));
            if(Double.isFinite(alignment.centerSpanPx))b.append(String.format(Locale.US,"; arc-subset centre/radius span %.2f / %.2f source px",alignment.centerSpanPx,alignment.radiusSpanPx));
            return b.append("\n").append(alignment.reason).append(". These are fit stability diagnostics, not calibrated confidence intervals.").toString();
        }
        String description(){
            String alignmentText=alignmentDescription()+"\n\n"+scoreDescription("FITTED GEOMETRIC mismatch",fittedScore,"independent edges do not establish a complete stable shape")+"\n"+scoreDescription("FITTED alignment-only mismatch",alignmentScore,"position and orientation are not both established")+"\nThe alignment-only comparison moves/rotates the expected template without resizing it. Fitted ideal intersections/circles are geometric measurements, not observed corners. The three percentages are separate and are never added.\n\nObserved full physical luminous footprint:\n";
            if(!assessed())return alignmentText+"Area mismatch withheld: "+reason+". Fitted geometric percentages above remain independent.\nDiagnostics: "+detection.failureCode;
            return alignmentText+String.format(Locale.US,"Mismatch %.3f%% = extra %.3f%% + missing %.3f%%\nHorizontal displacement %+.3f%% R · vertical %+.3f%% R\nArea size difference %+.3f%% · major span %+.3f%% · minor span %+.3f%%\n%s\nFootprint boundary movement across local edge levels: %.2f source px. This is a stability diagnostic, not a calibrated confidence interval. No pass/fail threshold.",score.mismatchPct,score.extraPct,score.missingPct,100*dxR,100*dyR,sizePct,lengthPct,widthPct,
                spec.shape==ModelSpec.Shape.ROUND?"Rotation: not applicable to a round":Double.isFinite(rotationDeg)?String.format(Locale.US,"Rotation %+.3f° (+ clockwise); contrast-level angle span %.3f°",rotationDeg,detection.rotationSpreadDeg):"Rotation withheld: inner orientation varies across contrast levels",detection.boundarySpreadPx);
        }
    }
    static double[][] rigid(double[][] p,double[] from,double[] to,double theta){double co=Math.cos(theta),si=Math.sin(theta);double[][] out=new double[p.length][];for(int i=0;i<p.length;i++){double x=p[i][0]-from[0],y=p[i][1]-from[1];out[i]=new double[]{to[0]+co*x-si*y,to[1]+si*x+co*y};}return out;}
    static double[][] sampleReference(double[][] polygon){double perimeter=0;for(int i=0;i<polygon.length;i++)perimeter+=Math.hypot(polygon[i][0]-polygon[(i+1)%polygon.length][0],polygon[i][1]-polygon[(i+1)%polygon.length][1]);double[][] p=new double[128][];int edge=0;double cumulative=0;for(int i=0;i<128;i++){double t=i*perimeter/128;while(edge<polygon.length-1){double length=Math.hypot(polygon[edge][0]-polygon[(edge+1)%polygon.length][0],polygon[edge][1]-polygon[(edge+1)%polygon.length][1]);if(t<=cumulative+length)break;cumulative+=length;edge++;}double[] a=polygon[edge],b=polygon[(edge+1)%polygon.length];double fraction=(t-cumulative)/Math.hypot(a[0]-b[0],a[1]-b[1]);p[i]=new double[]{a[0]+fraction*(b[0]-a[0]),a[1]+fraction*(b[1]-a[1])};}return p;}
    final Map<Integer,Marker> markers=new LinkedHashMap<>();
    static LuminousMeasurement run(Bitmap photo,Alpha99Pipeline.Output outer,LuminousReference ref){
        LuminousMeasurement out=new LuminousMeasurement();Map<Integer,LuminousMarkerDetector.Detection> d=LuminousMarkerDetector.detect(photo,outer);
        double[] ring=outer.measurement.ring!=null&&outer.measurement.ring.usable?outer.measurement.ring.model:null;
        for(ModelSpec.Marker m:outer.model.markers)out.markers.put(m.hour,new Marker(m,d.get(m.hour),ring==null?null:ref.expected(m,ring)));
        return out;
    }
}
