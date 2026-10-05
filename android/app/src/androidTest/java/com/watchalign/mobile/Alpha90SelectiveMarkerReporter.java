package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only selective review layer for frozen Alpha90.
 *
 * Alpha90 remains authoritative for pose and the projected genuine master. Candidate feature
 * detectors run only after Alpha90 has finished and can never feed anything back into pose.
 *
 * Important edge-identity rule: the older local detectors can sometimes trace the lume edge and
 * sometimes the outer metal surround. A raw boundary-to-boundary distance therefore creates false
 * alerts even when a marker is correctly placed. This reporter intentionally separates ALIGNMENT
 * from SIZE/SHAPE. For selective alignment close-ups it compares the candidate marker's centre and,
 * for elongated markers, its axis against the already-projected genuine marker. Size is withheld
 * until the same physical outer edge can be proven on both candidate and master.
 *
 * The reported excursion is the image-space displacement a reviewer would see at the marker edge:
 * max(centre displacement, edge travel implied by rotation). It is normalised by dial radius so
 * image resolution does not alter the review trigger.
 */
final class Alpha90SelectiveMarkerReporter {
    enum Level { NORMAL, BORDERLINE, CLEAR_DIFFERENCE, UNASSESSABLE }

    static final class Feature {
        final int hour;
        final String label;
        final Level level;
        final double excursionPx;
        final double excursionOverDialR;
        final double centreShiftPx;
        final double rotationEdgePx;
        final double rotationDeg;
        final String reason;

        Feature(int hour, Level level, double px, double norm, double centreShift,
                double rotationEdge, double rotationDeg, String reason) {
            this.hour=hour;
            this.label=hour+" o'clock";
            this.level=level;
            this.excursionPx=px;
            this.excursionOverDialR=norm;
            this.centreShiftPx=centreShift;
            this.rotationEdgePx=rotationEdge;
            this.rotationDeg=rotationDeg;
            this.reason=reason==null?"":reason;
        }
        boolean showCloseUp(){return level==Level.BORDERLINE||level==Level.CLEAR_DIFFERENCE;}
    }

    static final class Report {
        final List<Feature> features;
        Report(List<Feature> f){features=f;}
        Feature atHour(int h){for(Feature f:features)if(f.hour==h)return f;return null;}
        int shown(){int n=0;for(Feature f:features)if(f.showCloseUp())n++;return n;}
        int unassessable(){int n=0;for(Feature f:features)if(f.level==Level.UNASSESSABLE)n++;return n;}
    }

    /**
     * Provisional REVIEW triggers only, never QC tolerances. They are intentionally fixed before
     * inspecting RL/GL labels. Genuine variation will eventually replace them with a calibrated
     * per-feature envelope. 0.004R is roughly sub-pixel to low-single-pixel on normal QC crops.
     */
    static final double BORDERLINE_EXCURSION_R = 0.0040;
    static final double CLEAR_EXCURSION_R = 0.0100;
    private static final int MIN_REFERENCE_PIXELS = 24;
    private static final double REFERENCE_MIN_R = 0.55;
    private static final double REFERENCE_MAX_R = 0.915; // minute track starts at 0.925R
    private static final double REFERENCE_HALF_SECTOR_DEG = 13.0;

    private Alpha90SelectiveMarkerReporter(){}

    static Report analyse(Bitmap watch, AutomaticDialOverlay.Result alpha) {
        if(alpha==null||!alpha.valid)return unavailable("Alpha90 overlay unavailable");
        return analyse(watch,alpha.overlay,alpha.dialCx,alpha.dialCy,alpha.dialRadius);
    }

    /** Allows the second-stage test to consume Alpha90's already-saved overlay and pose numbers. */
    static Report analyse(Bitmap watch, Bitmap overlay, double dialCx, double dialCy, double dialRadius) {
        if(watch==null||overlay==null||!(dialRadius>20))return unavailable("Alpha90 overlay unavailable");
        List<Feature> out=new ArrayList<>();
        Map<Integer,List<double[]>> refs=referencePixelsByHour(overlay,dialCx,dialCy,dialRadius);
        Mat rgba=new Mat(),bgr=new Mat();
        try {
            Utils.bitmapToMat(watch,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);

            // Candidate local detectors need only a physical dial frame. This fit is reporting-only.
            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(bgr,dialCx,dialCy,dialRadius);
            double cx=edge!=null?edge.cx:dialCx;
            double cy=edge!=null?edge.cy:dialCy;
            double r=edge!=null?edge.meanRadius():dialRadius;

            GmtTwelveLandmarkAnalyzer.Result twelve=GmtTwelveLandmarkAnalyzer.analyse(bgr,cx,cy,r);
            double twelveClock=Double.NaN;
            if(twelve.valid&&twelve.geometry!=null)
                twelveClock=Math.toDegrees(Math.atan2(twelve.geometry.tick60[0]-cx,cy-twelve.geometry.tick60[1]));

            GmtRoundMarkerAnalyzer.DialFrame dialFrame=edge!=null
                    ?new GmtRoundMarkerAnalyzer.DialFrame(edge.cx,edge.cy,edge.axisA,edge.axisB,edge.angleDeg)
                    :GmtRoundMarkerAnalyzer.DialFrame.circle(cx,cy,r);
            double[] tick60=twelve.valid&&twelve.geometry!=null?twelve.geometry.tick60:null;
            List<GmtRoundMarkerAnalyzer.Marker> rounds=GmtRoundMarkerAnalyzer.analyse(bgr,dialFrame,tick60);
            Map<Integer,GmtRoundMarkerAnalyzer.Marker> roundByHour=new HashMap<>();
            for(GmtRoundMarkerAnalyzer.Marker m:rounds)roundByHour.put(m.hour,m);

            GmtSixLandmarkAnalyzer.Result six=GmtSixLandmarkAnalyzer.analyse(
                    bgr,cx,cy,r,GmtSixLandmarkAnalyzer.Position.SIX,twelveClock);
            GmtSixLandmarkAnalyzer.Result nine=GmtSixLandmarkAnalyzer.analyse(
                    bgr,cx,cy,r,GmtSixLandmarkAnalyzer.Position.NINE,twelveClock);

            for(int hour=1;hour<=12;hour++) {
                if(hour==3) {
                    out.add(unassessable(hour,"no applied marker exists at 3 in the frozen master"));
                    continue;
                }
                List<double[]> refPts=refs.get(hour);
                if(refPts==null||refPts.size()<MIN_REFERENCE_PIXELS) {
                    out.add(unassessable(hour,"projected genuine marker outline was not isolated"));
                    continue;
                }
                ShapeStats ref=ShapeStats.of(refPts);
                if(ref==null) {
                    out.add(unassessable(hour,"projected genuine marker geometry was degenerate"));
                    continue;
                }

                List<double[]> candidatePts;
                boolean elongated;
                String confidenceNote="";
                if(hour==12) {
                    if(!twelve.valid||twelve.geometry==null) {
                        out.add(unassessable(hour,twelve.reason.isEmpty()?"12 marker geometry unavailable":twelve.reason));
                        continue;
                    }
                    candidatePts=polygonBoundary(new double[][]{
                            twelve.geometry.triLeft,twelve.geometry.triRight,twelve.geometry.triTip
                    });
                    elongated=true;
                    if(!twelve.geometry.outerEdge)confidenceNote="12 outer surround edge was not proven; alignment only";
                } else if(hour==6||hour==9) {
                    GmtSixLandmarkAnalyzer.Result baton=hour==6?six:nine;
                    if(!baton.valid||baton.geometry==null) {
                        out.add(unassessable(hour,baton.reason.isEmpty()?hour+" baton geometry unavailable":baton.reason));
                        continue;
                    }
                    candidatePts=polygonBoundary(baton.geometry.polygon());
                    elongated=true;
                    if(!baton.geometry.outerEdge||!baton.stable)
                        confidenceNote=(baton.lowReason==null||baton.lowReason.isEmpty())
                                ?"outer surround edge was not proven; alignment only":baton.lowReason+"; alignment only";
                } else {
                    GmtRoundMarkerAnalyzer.Marker m=roundByHour.get(hour);
                    if(m==null||!m.found||!Double.isFinite(m.x)||!Double.isFinite(m.y)) {
                        String why=m==null?"round marker detector unavailable":(!m.reason.isEmpty()?m.reason:"round marker centre unavailable");
                        out.add(unassessable(hour,why));
                        continue;
                    }
                    // Radius is deliberately arbitrary for alignment: only the centre of a round
                    // marker is used unless an independently proven outer-surround size test is added.
                    double proxyRadius=Math.max(4.0,ref.minorHalfExtent);
                    candidatePts=circleBoundary(m.x,m.y,proxyRadius,180);
                    elongated=false;
                    if(!m.stable)confidenceNote=(m.lowReason==null||m.lowReason.isEmpty())?"round outline low confidence; centre alignment only":m.lowReason+"; centre alignment only";
                }

                ShapeStats cand=ShapeStats.of(candidatePts);
                if(cand==null) {
                    out.add(unassessable(hour,"candidate marker geometry was degenerate"));
                    continue;
                }

                double centre=Math.hypot(cand.cx-ref.cx,cand.cy-ref.cy);
                double rotDeg=Double.NaN,rotEdge=0.0;
                if(elongated) {
                    rotDeg=axisDifferenceDeg(cand.majorAngleRad,ref.majorAngleRad);
                    rotEdge=Math.abs(Math.sin(Math.toRadians(rotDeg)))*ref.majorHalfExtent;
                }
                double excursion=Math.max(centre,rotEdge);
                double norm=excursion/dialRadius;
                Level level=norm>=CLEAR_EXCURSION_R?Level.CLEAR_DIFFERENCE
                        :norm>=BORDERLINE_EXCURSION_R?Level.BORDERLINE:Level.NORMAL;
                out.add(new Feature(hour,level,excursion,norm,centre,rotEdge,rotDeg,confidenceNote));
            }
        } catch(Throwable t) {
            return unavailable("selective reporter failed: "+t.getClass().getSimpleName());
        } finally {
            bgr.release();rgba.release();
        }
        return new Report(out);
    }

    /** Weighted outline statistics. PCA provides the visible long axis after Alpha90 perspective. */
    private static final class ShapeStats {
        final double cx,cy,majorAngleRad,majorHalfExtent,minorHalfExtent;
        ShapeStats(double cx,double cy,double a,double maj,double min){this.cx=cx;this.cy=cy;majorAngleRad=a;majorHalfExtent=maj;minorHalfExtent=min;}
        static ShapeStats of(List<double[]> p){
            if(p==null||p.size()<3)return null;
            double cx=0,cy=0;int n=0;
            for(double[] q:p)if(q!=null&&q.length>=2&&Double.isFinite(q[0])&&Double.isFinite(q[1])){cx+=q[0];cy+=q[1];n++;}
            if(n<3)return null;cx/=n;cy/=n;
            double xx=0,xy=0,yy=0;
            for(double[] q:p){if(q==null||q.length<2)continue;double dx=q[0]-cx,dy=q[1]-cy;xx+=dx*dx;xy+=dx*dy;yy+=dy*dy;}
            xx/=n;xy/=n;yy/=n;
            double a=0.5*Math.atan2(2.0*xy,xx-yy);
            double ca=Math.cos(a),sa=Math.sin(a);
            double maj=0,min=0;
            for(double[] q:p){if(q==null||q.length<2)continue;double dx=q[0]-cx,dy=q[1]-cy;maj=Math.max(maj,Math.abs(dx*ca+dy*sa));min=Math.max(min,Math.abs(-dx*sa+dy*ca));}
            if(!(maj>0)&&!(min>0))return null;
            if(min>maj){double t=maj;maj=min;min=t;a+=Math.PI/2.0;}
            return new ShapeStats(cx,cy,a,maj,min);
        }
    }

    private static Report unavailable(String why){
        List<Feature> out=new ArrayList<>();
        for(int h=1;h<=12;h++)out.add(unassessable(h,why));
        return new Report(out);
    }

    /** Isolate every yellow applied-marker outline once. Minute ticks start outside 0.925R. */
    private static Map<Integer,List<double[]>> referencePixelsByHour(Bitmap overlay,double dialCx,double dialCy,double dialRadius) {
        Map<Integer,List<double[]>> out=new HashMap<>();
        for(int h=1;h<=12;h++)out.put(h,new ArrayList<double[]>());
        int xmin=clamp((int)Math.floor(dialCx-dialRadius),0,overlay.getWidth()-1);
        int xmax=clamp((int)Math.ceil(dialCx+dialRadius),0,overlay.getWidth()-1);
        int ymin=clamp((int)Math.floor(dialCy-dialRadius),0,overlay.getHeight()-1);
        int ymax=clamp((int)Math.ceil(dialCy+dialRadius),0,overlay.getHeight()-1);
        double maxDa=Math.toRadians(REFERENCE_HALF_SECTOR_DEG);
        for(int y=ymin;y<=ymax;y++)for(int x=xmin;x<=xmax;x++) {
            int c=overlay.getPixel(x,y);
            if(Color.alpha(c)<80||Color.red(c)<180||Color.green(c)<180||Color.blue(c)>140)continue;
            double dx=x-dialCx,dy=y-dialCy;
            double rr=Math.hypot(dx,dy)/dialRadius;
            if(rr<REFERENCE_MIN_R||rr>REFERENCE_MAX_R)continue;
            double a=Math.atan2(dy,dx);
            int hour=nearestHour(a);
            double expected=Math.toRadians(hour*30.0-90.0);
            if(Math.abs(wrapPi(a-expected))>maxDa)continue;
            out.get(hour).add(new double[]{x,y});
        }
        return out;
    }

    private static int nearestHour(double angle) {
        int slot=(int)Math.round((Math.toDegrees(angle)+90.0)/30.0);
        slot=((slot%12)+12)%12;
        return slot==0?12:slot;
    }

    private static List<double[]> polygonBoundary(double[][] p) {
        List<double[]> out=new ArrayList<>();
        if(p==null||p.length<2)return out;
        for(int i=0;i<p.length;i++)sampleSegment(out,p[i],p[(i+1)%p.length]);
        return out;
    }

    private static void sampleSegment(List<double[]> out,double[] a,double[] b) {
        if(a==null||b==null||a.length<2||b.length<2)return;
        double d=Math.hypot(b[0]-a[0],b[1]-a[1]);
        int n=Math.max(2,(int)Math.ceil(d));
        for(int i=0;i<n;i++) {
            double t=i/(double)n;
            out.add(new double[]{a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])});
        }
    }

    private static List<double[]> circleBoundary(double cx,double cy,double radius,int n) {
        List<double[]> out=new ArrayList<>();
        for(int i=0;i<n;i++) {
            double a=2.0*Math.PI*i/n;
            out.add(new double[]{cx+radius*Math.cos(a),cy+radius*Math.sin(a)});
        }
        return out;
    }

    private static double axisDifferenceDeg(double a,double b){
        double d=Math.toDegrees(a-b);
        while(d>90)d-=180;while(d<=-90)d+=180;
        return d;
    }

    static String csvHeader(){return "hour,level,excursion_px,excursion_over_dial_r,centre_shift_px,rotation_edge_px,rotation_deg,reason";}
    static String csvRow(Feature f){
        return String.format(Locale.US,"%d,%s,%.3f,%.6f,%.3f,%.3f,%s,%s",f.hour,f.level,f.excursionPx,f.excursionOverDialR,
                f.centreShiftPx,f.rotationEdgePx,Double.isFinite(f.rotationDeg)?String.format(Locale.US,"%.3f",f.rotationDeg):"",csv(f.reason));
    }

    private static Feature unassessable(int h,String why){return new Feature(h,Level.UNASSESSABLE,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,why);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static double wrapPi(double x){while(x>Math.PI)x-=2*Math.PI;while(x<=-Math.PI)x+=2*Math.PI;return x;}
    private static String csv(String s){if(s==null)return "";return "\""+s.replace("\"","\"\"")+"\"";}
}
