package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only selective review layer for frozen Alpha90.
 *
 * The fixed Alpha90 master remains authoritative. Candidate feature detectors are used only
 * after Alpha90 has finished, to trace the physical applied-marker boundary for reporting.
 * Nothing measured here can change Alpha90 centre, scale, phase, perspective or master geometry.
 *
 * The generic comparison is a bidirectional boundary excursion between a detected candidate
 * feature and the already-projected yellow genuine reference. That same boundary interface can
 * later support other features such as a coronet or text without changing Alpha90 pose.
 */
final class Alpha90SelectiveMarkerReporter {
    enum Level { NORMAL, BORDERLINE, CLEAR_DIFFERENCE, UNASSESSABLE }

    static final class Feature {
        final int hour;
        final String label;
        final Level level;
        final double excursionPx;
        final double excursionOverDialR;
        final String reason;

        Feature(int hour, Level level, double px, double norm, String reason) {
            this.hour=hour;
            this.label=hour+" o'clock";
            this.level=level;
            this.excursionPx=px;
            this.excursionOverDialR=norm;
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

    /*
     * Provisional review triggers only. They are deliberately not called QC tolerances and must
     * not be tuned to replica labels. Genuine variation will eventually replace these with a
     * feature-specific genuine envelope. Values are dial-normalised so image resolution does not
     * change the decision.
     */
    static final double BORDERLINE_EXCURSION_R = 0.0060;
    static final double CLEAR_EXCURSION_R = 0.0120;
    private static final double REF_STROKE_ALLOWANCE_R = 0.0040;
    private static final int MIN_REFERENCE_PIXELS = 24;
    private static final double REFERENCE_MIN_R = 0.55;
    private static final double REFERENCE_MAX_R = 0.915; // safely inside minute track (starts 0.925R)
    private static final double REFERENCE_HALF_SECTOR_DEG = 13.0;

    private Alpha90SelectiveMarkerReporter(){}

    static Report analyse(Bitmap watch, AutomaticDialOverlay.Result alpha) {
        if(alpha==null||!alpha.valid)return unavailable("Alpha90 overlay unavailable");
        return analyse(watch,alpha.overlay,alpha.dialCx,alpha.dialCy,alpha.dialRadius);
    }

    /** Allows a second-stage test to consume Alpha90's already-saved overlay and pose numbers. */
    static Report analyse(Bitmap watch, Bitmap overlay, double dialCx, double dialCy, double dialRadius) {
        List<Feature> out=new ArrayList<>();
        if(watch==null||overlay==null||!(dialRadius>20))return unavailable("Alpha90 overlay unavailable");

        Map<Integer,List<double[]>> refs=referencePixelsByHour(overlay,dialCx,dialCy,dialRadius);
        Mat rgba=new Mat(),bgr=new Mat();
        try {
            Utils.bitmapToMat(watch,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);

            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(bgr,dialCx,dialCy,dialRadius);
            double cx=edge!=null?edge.cx:dialCx;
            double cy=edge!=null?edge.cy:dialCy;
            double r=edge!=null?edge.meanRadius():dialRadius;

            GmtTwelveLandmarkAnalyzer.Result twelve=GmtTwelveLandmarkAnalyzer.analyse(bgr,cx,cy,r);
            double twelveClock=Double.NaN;
            if(twelve.valid&&twelve.geometry!=null) {
                twelveClock=Math.toDegrees(Math.atan2(twelve.geometry.tick60[0]-cx,cy-twelve.geometry.tick60[1]));
            }

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
                    // Frozen BLNR master has the date window here, not an applied marker.
                    out.add(unassessable(hour,"no applied marker exists at 3 in the frozen master"));
                    continue;
                }
                List<double[]> ref=refs.get(hour);
                if(ref==null||ref.size()<MIN_REFERENCE_PIXELS) {
                    out.add(unassessable(hour,"projected genuine marker outline was not isolated"));
                    continue;
                }

                List<double[]> candidate;
                String why="";
                if(hour==12) {
                    if(!twelve.valid||twelve.geometry==null||!twelve.geometry.outerEdge) {
                        why=twelve.reason.isEmpty()?"12 outer surround was not traced reliably":twelve.reason;
                        out.add(unassessable(hour,why));
                        continue;
                    }
                    candidate=polygonBoundary(new double[][]{
                            twelve.geometry.triLeft,twelve.geometry.triRight,twelve.geometry.triTip
                    });
                } else if(hour==6||hour==9) {
                    GmtSixLandmarkAnalyzer.Result baton=hour==6?six:nine;
                    if(!baton.valid||!baton.stable||baton.geometry==null||!baton.geometry.outerEdge) {
                        why=baton.reason.isEmpty()?(baton.lowReason.isEmpty()?hour+" baton outer surround was not traced reliably":baton.lowReason):baton.reason;
                        out.add(unassessable(hour,why));
                        continue;
                    }
                    candidate=polygonBoundary(baton.geometry.polygon());
                } else {
                    GmtRoundMarkerAnalyzer.Marker m=roundByHour.get(hour);
                    if(m==null||!m.found||!m.stable||!(m.radiusPx>1)) {
                        why=m==null?"round marker detector unavailable":(!m.reason.isEmpty()?m.reason:(!m.lowReason.isEmpty()?m.lowReason:"round marker outer surround was not traced reliably"));
                        out.add(unassessable(hour,why));
                        continue;
                    }
                    candidate=circleBoundary(m.x,m.y,m.radiusPx,180);
                }

                double raw=symmetricP90(ref,candidate);
                if(!Double.isFinite(raw)) {
                    out.add(unassessable(hour,"marker boundary comparison failed"));
                    continue;
                }
                // The projected genuine outline is a stroked anti-aliased line rather than an
                // infinitesimal curve. Remove that display stroke before treating separation as
                // physical marker excursion.
                double effective=Math.max(0.0,raw-REF_STROKE_ALLOWANCE_R*dialRadius);
                double norm=effective/dialRadius;
                Level level=norm>=CLEAR_EXCURSION_R?Level.CLEAR_DIFFERENCE
                        :norm>=BORDERLINE_EXCURSION_R?Level.BORDERLINE:Level.NORMAL;
                out.add(new Feature(hour,level,effective,norm,""));
            }
        } catch(Throwable t) {
            return unavailable("selective reporter failed: "+t.getClass().getSimpleName());
        } finally {
            bgr.release();rgba.release();
        }
        return new Report(out);
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

    /** Robust symmetric boundary distance. p90 keeps a local glare/hand edge from dominating. */
    private static double symmetricP90(List<double[]> a,List<double[]> b) {
        if(a==null||b==null||a.isEmpty()||b.isEmpty())return Double.NaN;
        List<Double> d=new ArrayList<>(a.size()+b.size());
        appendNearestDistances(d,a,b);
        appendNearestDistances(d,b,a);
        if(d.isEmpty())return Double.NaN;
        Collections.sort(d);
        int i=(int)Math.ceil(0.90*d.size())-1;
        return d.get(clamp(i,0,d.size()-1));
    }

    private static void appendNearestDistances(List<Double> out,List<double[]> from,List<double[]> to) {
        for(double[] p:from) {
            double best=Double.POSITIVE_INFINITY;
            for(double[] q:to) {
                double dx=p[0]-q[0],dy=p[1]-q[1],dd=dx*dx+dy*dy;
                if(dd<best)best=dd;
            }
            if(Double.isFinite(best))out.add(Math.sqrt(best));
        }
    }

    static String csvHeader(){return "hour,level,excursion_px,excursion_over_dial_r,reason";}
    static String csvRow(Feature f){
        return String.format(Locale.US,"%d,%s,%.3f,%.6f,%s",f.hour,f.level,f.excursionPx,f.excursionOverDialR,csv(f.reason));
    }

    private static Feature unassessable(int h,String why){return new Feature(h,Level.UNASSESSABLE,Double.NaN,Double.NaN,why);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static double wrapPi(double x){while(x>Math.PI)x-=2*Math.PI;while(x<=-Math.PI)x+=2*Math.PI;return x;}
    private static String csv(String s){if(s==null)return "";return "\""+s.replace("\"","\"\"")+"\"";}
}
