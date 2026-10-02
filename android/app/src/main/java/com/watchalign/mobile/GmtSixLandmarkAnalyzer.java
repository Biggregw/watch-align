package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures the 6 o'clock baton against the 29/30/31 minute ticks (alpha55).
 *
 * The image is turned 180 degrees about the dial centre (a point reflection, so no
 * resampling) which puts the 6 baton where the 12 triangle normally is. That lets the
 * 12 marker's minute-tick finder and outer-edge line fitting be reused unchanged.
 * Everything returned is mapped back to the original image.
 *
 * Measured, each as a fraction of the baton width:
 *   centring  - sideways offset of the baton's outer end from the midpoint of the 29 and
 *               31 tick ends; positive = towards the 29 tick, i.e. to the viewer's RIGHT at 6
 *   gap       - outer end to the 29-31 tick line
 * and rotation, the baton axis against the square to the 29-31 chord, degrees, positive = CW.
 */
final class GmtSixLandmarkAnalyzer {
    /**
     * Which baton (alpha59: 6 and 9). The image is turned so the baton sits at the top, where
     * the 12 triangle normally is: 180 degrees for the 6 (a point reflection), 90 degrees
     * clockwise for the 9, 90 degrees anticlockwise for the 3. All are exact pixel moves with no
     * resampling. Only one of 3 and 9 is a baton: the date window takes the other (GmtDialLayout).
     */
    enum Position {
        SIX("6",29,30,31,180.0), NINE("9",44,45,46,-90.0),
        /** The baton at 3 on a date-at-9 dial (126720VTNR Sprite), turned 90 deg anticlockwise (alpha61). */
        THREE("3",14,15,16,90.0);
        final String label;final int before,centre,after;
        /** Where the marker sits relative to the 12, degrees clockwise in image coordinates. */
        final double angleFromTwelveDeg;
        Position(String l,int b,int c,int a,double ang){label=l;before=b;centre=c;after=a;angleFromTwelveDeg=ang;}
        String ticks(){return before+"/"+centre+"/"+after;}
        String pair(){return before+" and "+after;}
    }

    static final class Geometry {
        /**
         * Outer-end corners (outerRight is on the tickBefore side), inner corners, and the inner
         * ends of the minute ticks after / at / before the marker (6: 31/30/29; 9: 46/45/44).
         */
        final double[] outerLeft,outerRight,innerLeft,innerRight,tickAfter,tickCentre,tickBefore;
        final boolean outerEdge;
        Geometry(double[] ol,double[] or,double[] il,double[] ir,double[] t31,double[] t30,double[] t29,boolean outer){
            outerLeft=ol;outerRight=or;innerLeft=il;innerRight=ir;tickAfter=t31;tickCentre=t30;tickBefore=t29;outerEdge=outer;
        }
        double[][] polygon(){return new double[][]{innerLeft,outerLeft,outerRight,innerRight};}
    }

    static final class Result {
        final boolean valid,stable;final String reason;
        final double gap,centring,rotationDeg,widthPx,lengthPx;
        final Geometry geometry;
        Result(String why){valid=false;stable=false;reason=why;gap=centring=rotationDeg=widthPx=lengthPx=Double.NaN;geometry=null;}
        Result(double gap,double centring,double rot,double width,double length,boolean stable,Geometry g){
            valid=true;reason="";this.gap=gap;this.centring=centring;rotationDeg=rot;widthPx=width;lengthPx=length;this.stable=stable;geometry=g;
        }
        /** Resize check (alpha57): ranges over the original and 94%/88% re-measurements. */
        boolean stabilityRun,stabilitySameEdge;
        double centringMin=Double.NaN,centringMax=Double.NaN,rotMin=Double.NaN,rotMax=Double.NaN;

        /**
         * Same rule as the 12 (GmtTwelveLandmarkAnalyzer.Result.resampleGapStable): stable when
         * the same edge was found at every scale and each reading either moved by at most about
         * a pixel or stayed below the level where a verdict starts.
         */
        boolean resampleStable(){
            if(!stabilityRun)return true;
            if(!Double.isFinite(centringMax)||!Double.isFinite(rotMax))return false;
            // A different edge kind at one scale (outer surround against the band fallback) still
            // can't change the verdict when every reading is below both check levels (alpha61:
            // WOS CPO 6 batons read offset -0.01..+0.02 and rotation -0.7..+0.6 and were withheld).
            if(!stabilitySameEdge)return Math.max(Math.abs(centringMin),Math.abs(centringMax))<GmtHumanQcMath.SIX_CENTRING_CHECK
                    &&Math.max(Math.abs(rotMin),Math.abs(rotMax))<GmtHumanQcMath.SIX_ROTATION_CHECK_DEG;
            boolean c=(centringMax-centringMin)*widthPx<=GmtTwelveLandmarkAnalyzer.MAX_RESAMPLE_SHIFT_PX
                    ||Math.max(Math.abs(centringMin),Math.abs(centringMax))<GmtHumanQcMath.SIX_CENTRING_CHECK;
            boolean t=Math.tan(Math.toRadians(rotMax-rotMin))*lengthPx<=GmtTwelveLandmarkAnalyzer.MAX_RESAMPLE_SHIFT_PX
                    ||Math.max(Math.abs(rotMin),Math.abs(rotMax))<GmtHumanQcMath.SIX_ROTATION_CHECK_DEG;
            return c&&t;
        }

        Position position=Position.SIX;
        /** The 12's clock angle this was measured with (for the resize check), or NaN. */
        double twelveClockDeg=Double.NaN;
        /** Why a measured baton is low confidence (alpha57), or "" when stable. */
        String lowReason="";
        /** Diagnostics: long-side parallelism of the edge fit, tick frame score/pitch/inferred count. */
        double parallelDeg=Double.NaN,tickScore=Double.NaN,tickPitchDeg=Double.NaN,ticksInferred=Double.NaN;
        Result lowConfidence(String why){
            if(!valid)return this;
            Result x=new Result(gap,centring,rotationDeg,widthPx,lengthPx,false,geometry);
            x.position=position;x.lowReason=lowReason.isEmpty()?why:lowReason;x.parallelDeg=parallelDeg;x.tickScore=tickScore;x.tickPitchDeg=tickPitchDeg;x.ticksInferred=ticksInferred;
            return x;
        }
    }

    // Plausible baton size, dial radii (measured master: half-length 0.150R, half-width 0.060R).
    static final double MIN_LEN_R=0.20, MAX_LEN_R=0.40, MIN_WID_R=0.07, MAX_WID_R=0.18;
    static final double PARALLEL_TOLERANCE_DEG=2.0;
    static final double RECTANGULARITY_MIN=0.60;

    static boolean DEBUG=Boolean.getBoolean("wa.six.debug");
    private GmtSixLandmarkAnalyzer(){}

    static Result analyse(Mat bgr,double cx,double cy,double r){return analyse(bgr,cx,cy,r,Position.SIX);}

    static Result analyse(Mat bgr,double cx,double cy,double r,Position pos){return analyse(bgr,cx,cy,r,pos,Double.NaN);}

    /**
     * @param twelveClockDeg clock angle of the 12's 60 tick about (cx,cy), or NaN. When given and
     *        no baton-shaped outline is found (a hand lying along the baton merges with it), the
     *        baton's edges are searched for where the minute ticks say it must be (alpha61).
     */
    static Result analyse(Mat bgr,double cx,double cy,double r,Position pos,double twelveClockDeg){
        Result res=analyseTurned(bgr,cx,cy,r,pos,twelveClockDeg);
        res.position=pos;res.twelveClockDeg=twelveClockDeg;
        return res;
    }

    private static Result analyseTurned(Mat bgr,double cx,double cy,double r,Position pos,double twelveClockDeg){
        if(bgr==null||bgr.empty()||!(r>20))return new Result("invalid dial seed");
        Mat flipped=new Mat(),gray=new Mat(),enh=new Mat();
        try{
            if(pos==Position.NINE)Core.rotate(bgr,flipped,Core.ROTATE_90_CLOCKWISE);
            else if(pos==Position.THREE)Core.rotate(bgr,flipped,Core.ROTATE_90_COUNTERCLOCKWISE);
            else Core.flip(bgr,flipped,-1);
            final int W=flipped.cols(),H=flipped.rows();
            // Dial centre in the turned image. 180: (x,y)->(W-1-x,H-1-y). 90 CW: (x,y)->(Horig-1-y,x)
            // and the turned width W equals the original height.
            // 90 CCW: (x,y)->(y,Worig-1-x), and the turned height H equals the original width.
            final double fx=pos==Position.NINE?W-1-cy:pos==Position.THREE?cy:W-1-cx,
                    fy=pos==Position.NINE?cx:pos==Position.THREE?H-1-cx:H-1-cy;
            Imgproc.cvtColor(flipped,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
            clahe.apply(gray,enh);

            double[][] rect=batonCandidate(enh,fx,fy,r);
            // Turned so the baton is at the top: its clock angle there equals the 12's in the
            // original (180 deg flip for the 6 adds 180; 90 deg CW for the 9 adds 90).
            boolean fromTicks=false;
            if(rect==null&&Double.isFinite(twelveClockDeg)){rect=priorFromTicks(enh,fx,fy,r,twelveClockDeg);fromTicks=rect!=null;
                if(DEBUG&&rect!=null){
                    Mat dbg=new Mat();Imgproc.cvtColor(enh,dbg,Imgproc.COLOR_GRAY2BGR);
                    for(int i=0;i<4;i++)Imgproc.line(dbg,new Point(rect[i][0],rect[i][1]),new Point(rect[(i+1)%4][0],rect[(i+1)%4][1]),new org.opencv.core.Scalar(0,0,255),1);
                    int x0=(int)Math.max(0,fx-0.4*r),y0=(int)Math.max(0,fy-1.05*r),x1=(int)Math.min(W,fx+0.4*r),y1=(int)Math.min(H,fy-0.45*r);
                    org.opencv.imgcodecs.Imgcodecs.imwrite(System.getProperty("wa.six.dbgdir","/tmp")+"/prior_"+pos+"_"+System.nanoTime()+".png",dbg.submat(y0,y1,x0,x1));dbg.release();
                }
            }
            if(rect==null)return new Result(pos.label+" baton not found");
            // rect = {innerLeft, outerLeft, outerRight, innerRight} in the flipped image
            double[] om=mid(rect[1],rect[2]);
            double[][] frame=GmtTwelveLandmarkAnalyzer.tickFrameNear(enh,fx,fy,r,om[0],om[1]);
            if(frame==null)return new Result(pos.ticks()+" minute-track frame not sufficiently constrained");

            boolean outerEdge=false;
            String[] why={""};double[] par={Double.NaN};
            double[][] refined=refine(enh,rect,frame,r,why,par);
            // A baton placed from the ticks is only a search window: without traced edges there
            // is nothing measured.
            if(fromTicks&&refined==null)return new Result(pos.label+" baton outline not found where the minute ticks put it, usually because a hand lies along it");
            if(refined!=null){
                double[] om2=mid(refined[1],refined[2]);
                double[][] again=GmtTwelveLandmarkAnalyzer.tickFrameNear(enh,fx,fy,r,om2[0],om2[1]);
                if(again!=null){rect=refined;frame=again;outerEdge=true;}
                else why[0]="the minute ticks were not found again around the fitted baton";
            }

            // Back to the original image.
            double[] il=back(rect[0],W,H,pos),ol=back(rect[1],W,H,pos),or=back(rect[2],W,H,pos),ir=back(rect[3],W,H,pos);
            double[] tA=back(frame[0],W,H,pos),t30=back(frame[1],W,H,pos),tB=back(frame[2],W,H,pos);
            // In the flipped image, left is the 29 tick; after flipping back, the 29 tick is
            // on the viewer's right at 6. Name by distance so it cannot be swapped.
            double[] t29=tA,t31=tB;
            // Viewer's left at 6 is image-left for an upright photo; name corners by the
            // 31->29 direction so a rotated photo still gets them right.
            double sx=t29[0]-t31[0],sy=t29[1]-t31[1],sl=Math.hypot(sx,sy);
            if(sl<1e-9)return new Result(pos.pair()+" ticks coincide");
            sx/=sl;sy/=sl;
            if((or[0]-ol[0])*sx+(or[1]-ol[1])*sy<0){double[] z=ol;ol=or;or=z;z=il;il=ir;ir=z;}

            double width=Math.hypot(or[0]-ol[0],or[1]-ol[1]);
            if(!(width>1))return new Result(pos.label+" baton width is degenerate");
            double[] outerMid=mid(ol,or),innerMid=mid(il,ir);
            double length=Math.hypot(outerMid[0]-innerMid[0],outerMid[1]-innerMid[1]);

            // Local reference only: the midpoint of the 29 and 31 tick ends and the chord
            // between them. There is no printed 30 tick (the SWISS MADE coronet sits there),
            // and a line from the fitted dial centre swings with any centre error (a genuine
            // photo read 3.9 deg that way while its 12 read -2.5 deg).
            double[] tm=mid(t31,t29);
            double gap=Math.abs(pointLineDistance(outerMid,t31,t29))/width;
            double centring=((outerMid[0]-tm[0])*sx+(outerMid[1]-tm[1])*sy)/width;
            double ux=-sy,uy=sx;                                   // square to the chord
            if((tm[0]-cx)*ux+(tm[1]-cy)*uy<0){ux=-ux;uy=-uy;}      // pointing outward
            double axis=Math.atan2(outerMid[1]-innerMid[1],outerMid[0]-innerMid[0]);
            double rot=wrap90(Math.toDegrees(axis-Math.atan2(uy,ux)));

            double pitch=frame[3][1],score=frame[3][2],inferred=frame[3][3];
            boolean stable=outerEdge&&score>=MIN_TICK_SCORE&&pitch>=MIN_TICK_PITCH_DEG&&pitch<=MAX_TICK_PITCH_DEG&&inferred<=MAX_TICKS_INFERRED;
            Geometry g=new Geometry(ol,or,il,ir,t31,t30,t29,outerEdge);
            Result res=new Result(gap,centring,rot,width,length,stable,g);
            res.parallelDeg=par[0];res.tickScore=score;res.tickPitchDeg=pitch;res.ticksInferred=inferred;
            if(!stable){
                res.lowReason=!outerEdge?(why[0].isEmpty()?"the baton's edges could not be fitted cleanly":why[0])
                        :inferred>MAX_TICKS_INFERRED?"the 29 and 31 ticks could not both be seen"
                        :score<MIN_TICK_SCORE?"the minute ticks either side of the 6 are faint"
                        :"the tick spacing at 6 does not match the minute track";
            }
            return res;
        }catch(Throwable t){
            return new Result(pos.label+" marker analysis failed: "+t.getClass().getSimpleName());
        }finally{flipped.release();gray.release();enh.release();}
    }

    /** Baton as a rotated rectangle near the top of the (flipped) image: {innerL, outerL, outerR, innerR}. */
    private static double[][] batonCandidate(Mat gray,double cx,double cy,double r){
        int x0=Math.max(0,(int)Math.floor(cx-.30*r)),x1=Math.min(gray.cols(),(int)Math.ceil(cx+.30*r));
        int y0=Math.max(0,(int)Math.floor(cy-1.01*r)),y1=Math.min(gray.rows(),(int)Math.ceil(cy-.45*r));
        if(x1<=x0||y1<=y0)return null;
        Mat patch=gray.submat(new Rect(x0,y0,x1-x0,y1-y0));
        List<Mat> masks=new ArrayList<>();
        double[][] best=null;double bestScore=-Double.MAX_VALUE;
        try{
            Mat otsu=new Mat();Imgproc.threshold(patch,otsu,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);masks.add(otsu);
            Mat adaptive=new Mat();int block=Math.max(15,((int)Math.round(r*.10))|1);
            Imgproc.adaptiveThreshold(patch,adaptive,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,-3);masks.add(adaptive);
            for(Mat input:masks){
                Mat m=input.clone();
                Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));
                Imgproc.morphologyEx(m,m,Imgproc.MORPH_OPEN,k);k.release();
                List<MatOfPoint> contours=new ArrayList<>();Mat hier=new Mat();
                Imgproc.findContours(m,contours,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
                m.release();hier.release();
                for(MatOfPoint c:contours){
                    double area=Imgproc.contourArea(c);
                    if(area<20){c.release();continue;}
                    MatOfPoint2f c2=new MatOfPoint2f(c.toArray());
                    RotatedRect rr=Imgproc.minAreaRect(c2);c2.release();c.release();
                    double mx=rr.center.x+x0,my=rr.center.y+y0;
                    double rad=Math.hypot(mx-cx,my-cy)/r;
                    double ang=Math.toDegrees(Math.atan2(mx-cx,cy-my));
                    if(rad<.62||rad>.88||Math.abs(ang)>15)continue;
                    if(DEBUG){Point[] dv=new Point[4];rr.points(dv);double d1=Math.hypot(dv[1].x-dv[0].x,dv[1].y-dv[0].y),d2=Math.hypot(dv[2].x-dv[1].x,dv[2].y-dv[1].y);
                        System.err.printf(java.util.Locale.US,"cand area %.0f rad %.2f ang %.1f len %.3f wid %.3f fill %.2f%n",area,rad,ang,Math.max(d1,d2)/r,Math.min(d1,d2)/r,area/(d1*d2));}
                    Point[] v=new Point[4];rr.points(v);
                    for(Point p:v){p.x+=x0;p.y+=y0;}
                    // Long axis and its direction relative to the radial line.
                    double e1=Math.hypot(v[1].x-v[0].x,v[1].y-v[0].y),e2=Math.hypot(v[2].x-v[1].x,v[2].y-v[1].y);
                    double len=Math.max(e1,e2),wid=Math.min(e1,e2);
                    if(len<MIN_LEN_R*r||len>MAX_LEN_R*r||wid<MIN_WID_R*r||wid>MAX_WID_R*r)continue;
                    double aspect=len/wid;if(aspect<1.8||aspect>3.8)continue;
                    // Rectangle, not a blob or a ring. Was 0.80 until alpha61: clean studio photos (WOS
                    // CPO) and the official renders read 0.64-0.78 on the lume outline (anti-aliased,
                    // slightly rounded ends), so their 6 and 9 were reported "not found". A ring
                    // (surround only) reads well under 0.5.
                    if(area/(len*wid)<RECTANGULARITY_MIN)continue;
                    double lx,ly;if(e1>=e2){lx=v[1].x-v[0].x;ly=v[1].y-v[0].y;}else{lx=v[2].x-v[1].x;ly=v[2].y-v[1].y;}
                    double rx=mx-cx,ry=my-cy;
                    double cosA=Math.abs(lx*rx+ly*ry)/(Math.hypot(lx,ly)*Math.hypot(rx,ry));
                    if(cosA<Math.cos(Math.toRadians(15)))continue;           // long axis must be radial
                    double s=area-.3*area*Math.min(1,Math.abs(ang)/12.0)-.1*area*Math.abs(aspect-2.5);
                    if(s>bestScore){bestScore=s;best=orderCorners(v,cx,cy);}
                }
            }
            return best;
        }finally{for(Mat m:masks)m.release();patch.release();}
    }

    /**
     * Rough baton rectangle from the minute ticks either side of it (alpha61), used as the
     * search window when no outline passed the shape tests. The tick chord gives the local frame,
     * so this follows perspective and dial-centre error: outer end just inside the tick line,
     * length 0.30 and width 0.12 dial radii (measured master 0.304-0.314 / 0.120-0.126).
     */
    private static double[][] priorFromTicks(Mat enh,double cx,double cy,double r,double clockDeg){
        double t=Math.toRadians(clockDeg);
        double ox=cx+Math.sin(t)*0.908*r,oy=cy-Math.cos(t)*0.908*r;
        double[][] f=GmtTwelveLandmarkAnalyzer.tickFrameNear(enh,cx,cy,r,ox,oy);
        if(f==null||f[3][3]>MAX_TICKS_INFERRED)return null;
        double mx=(f[0][0]+f[2][0])/2,my=(f[0][1]+f[2][1])/2;
        double ux=f[2][0]-f[0][0],uy=f[2][1]-f[0][1],un=Math.hypot(ux,uy);if(un<1e-6)return null;
        ux/=un;uy/=un;
        double nx=-uy,ny=ux;if(nx*(cx-mx)+ny*(cy-my)<0){nx=-nx;ny=-ny;}
        double om=0.012*r,len=0.30*r,hw=0.06*r;
        double oX=mx+nx*om,oY=my+ny*om,iX=oX+nx*len,iY=oY+ny*len;
        double[][] q={{iX-ux*hw,iY-uy*hw},{oX-ux*hw,oY-uy*hw},{oX+ux*hw,oY+uy*hw},{iX+ux*hw,iY+uy*hw}};
        if(q[1][0]>q[2][0]){double[] z=q[0];q[0]=q[3];q[3]=z;z=q[1];q[1]=q[2];q[2]=z;}
        return q;
    }

    /** Orders rectangle corners as {innerLeft, outerLeft, outerRight, innerRight} (image left/right). */
    private static double[][] orderCorners(Point[] v,double cx,double cy){
        double mx=0,my=0;for(Point p:v){mx+=p.x;my+=p.y;}mx/=4;my/=4;
        double on=Math.hypot(mx-cx,my-cy);final double ox=(mx-cx)/on,oy=(my-cy)/on;
        double tx=-oy,ty=ox;
        Point[] s=v.clone();
        java.util.Arrays.sort(s,(a,b)->Double.compare(a.x*ox+a.y*oy,b.x*ox+b.y*oy));
        Point i1=s[0],i2=s[1],o1=s[2],o2=s[3];
        // "left" = smaller tangential coordinate
        if((i1.x-cx)*tx+(i1.y-cy)*ty>(i2.x-cx)*tx+(i2.y-cy)*ty){Point z=i1;i1=i2;i2=z;}
        if((o1.x-cx)*tx+(o1.y-cy)*ty>(o2.x-cx)*tx+(o2.y-cy)*ty){Point z=o1;o1=o2;o2=z;}
        double[][] q={{i1.x,i1.y},{o1.x,o1.y},{o2.x,o2.y},{i2.x,i2.y}};
        // Image-left first for an upright (flipped) marker.
        if(q[1][0]>q[2][0]){double[] z=q[0];q[0]=q[3];q[3]=z;z=q[1];q[1]=q[2];q[2]=z;}
        return q;
    }

    /** Long sides and outer end re-fitted on the outer edge; null keeps the rough rectangle. */
    // Tick-frame confidence, the same test as the 12 (GmtTwelveLandmarkAnalyzer.analyse).
    static final double MIN_TICK_SCORE=5.0, MIN_TICK_PITCH_DEG=5.35, MAX_TICK_PITCH_DEG=6.65, MAX_TICKS_INFERRED=1;

    /** Re-measures the 6 at the 12's resize-check scales and records the ranges (alpha57). */
    static void measureStability(Mat bgr,double cx,double cy,double r,Result res){
        if(res==null||!res.valid||bgr==null||bgr.empty())return;
        boolean same=true;
        double cMin=res.centring,cMax=res.centring,rMin=res.rotationDeg,rMax=res.rotationDeg;
        boolean outer=res.geometry!=null&&res.geometry.outerEdge;
        for(double s:GmtTwelveLandmarkAnalyzer.STABILITY_SCALES){
            Mat m=new Mat();
            try{
                Imgproc.resize(bgr,m,new Size(Math.round(bgr.cols()*s),Math.round(bgr.rows()*s)),0,0,Imgproc.INTER_LINEAR);
                Result q=analyse(m,cx*s,cy*s,r*s,res.position,res.twelveClockDeg);
                if(!q.valid){same=false;cMin=cMax=rMin=rMax=Double.NaN;break;}
                if((q.geometry!=null&&q.geometry.outerEdge)!=outer)same=false;
                cMin=Math.min(cMin,q.centring);cMax=Math.max(cMax,q.centring);
                rMin=Math.min(rMin,q.rotationDeg);rMax=Math.max(rMax,q.rotationDeg);
            }finally{m.release();}
        }
        res.stabilityRun=true;res.stabilitySameEdge=same;
        res.centringMin=cMin;res.centringMax=cMax;res.rotMin=rMin;res.rotMax=rMax;
    }

    /**
     * Calibrated edge level first; when that fit fails, retry with the outer-band level, as the
     * 12 does (TriangleEdgeRefiner.refine). A dim or shadowed stretch of surround on one long
     * side drops that side's edge onto the lume, and the two sides then fit 3-5 deg apart
     * (user photo, date 4: 4.2 deg; with the band level 0.3 deg). Genuine batons fit within
     * 1.6 deg either way.
     */
    /**
     * 124060 only (alpha76): when both level passes fail the parallel test, retry with each long
     * side taken from whichever pass traced it parallel to the other side (user photo, 3 baton:
     * calibrated level traced the right side, band level the left; each pass alone fitted 5 deg
     * apart, the mixed pair 0.4 deg). Off by default; only Sub124060QcAnalyzer turns it on, so the
     * GMT route never runs this pass.
     */
    static final ThreadLocal<Boolean> MIXED_EDGE_PASS=ThreadLocal.withInitial(()->Boolean.FALSE);

    private static double[][] refine(Mat gray,double[][] q,double[][] frame,double r,String[] why,double[] parOut){
        double[][] sa=new double[2][],sb=new double[2][];
        double[][] a=refine(gray,q,frame,r,why,parOut,false,sa,null,null);
        if(a!=null)return a;
        String firstWhy=why[0];double firstPar=parOut[0];
        double[][] b=refine(gray,q,frame,r,why,parOut,true,sb,null,null);
        if(b!=null){why[0]="";return b;}
        if(Boolean.TRUE.equals(MIXED_EDGE_PASS.get())&&sa[0]!=null&&sa[1]!=null&&sb[0]!=null&&sb[1]!=null){
            double p1=parallelDeg(sa[0],sb[1]),p2=parallelDeg(sb[0],sa[1]);
            double[] L=p1<=p2?sa[0]:sb[0],R=p1<=p2?sb[1]:sa[1];
            double[] parMix=new double[1];String[] whyMix={""};
            double[][] c=refine(gray,q,frame,r,whyMix,parMix,false,null,L,R);
            if(c!=null){why[0]="";parOut[0]=parMix[0];return c;}
        }
        why[0]=firstWhy;parOut[0]=firstPar;
        return null;
    }

    private static double parallelDeg(double[] a,double[] b){
        return Math.toDegrees(Math.acos(Math.min(1,Math.abs(a[2]*b[2]+a[3]*b[3]))));
    }

    private static double[][] refine(Mat gray,double[][] q,double[][] frame,double r,String[] why,double[] parOut,boolean band,
                                     double[][] sidesOut,double[] forceLeft,double[] forceRight){
        try{
            final int w=gray.cols(),h=gray.rows();
            final byte[] px=new byte[w*h];gray.get(0,0,px);
            DialEdgeEllipseFit.Intensity img=(x,y)->{
                int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double ffx=x-x0,ffy=y-y0;int i=y0*w+x0;
                double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+w]&0xff,d=px[i+w+1]&0xff;
                return (a*(1-ffx)+b*ffx)*(1-ffy)+(c*(1-ffx)+d*ffx)*ffy;
            };
            double gx=(q[0][0]+q[1][0]+q[2][0]+q[3][0])/4,gy=(q[0][1]+q[1][1]+q[2][1]+q[3][1])/4;
            double[] left=forceLeft!=null?forceLeft:TriangleEdgeRefiner.fitSide(img,w,h,q[0],q[1],gx,gy,r,0.15,0.85,0.035,null,null,band);
            double[] right=forceRight!=null?forceRight:TriangleEdgeRefiner.fitSide(img,w,h,q[3],q[2],gx,gy,r,0.15,0.85,0.035,null,null,band);
            if(sidesOut!=null){sidesOut[0]=left;sidesOut[1]=right;}
            double[] end=TriangleEdgeRefiner.fitSide(img,w,h,q[1],q[2],gx,gy,r,0.15,0.85,0.045,frame[0],frame[2],band);
            double[] inner=TriangleEdgeRefiner.fitSide(img,w,h,q[0],q[3],gx,gy,r,0.15,0.85,0.035,null,null,band);
            if(DEBUG)System.err.println("six refine: left="+(left!=null)+" right="+(right!=null)+" end="+(end!=null)+" inner="+(inner!=null));
            if(left==null||right==null||end==null){
                why[0]=left==null||right==null?"a long side of the baton could not be traced, often because a hand or a shadow is next to it"
                        :"the outer end of the baton could not be traced";
                return null;
            }
            double par=Math.toDegrees(Math.acos(Math.min(1,Math.abs(left[2]*right[2]+left[3]*right[3]))));
            if(DEBUG)System.err.printf("six refine: parallel %.2f%n",par);
            parOut[0]=par;
            if(par>PARALLEL_TOLERANCE_DEG){
                why[0]=String.format(java.util.Locale.US,"the baton's two long edges fitted %.1f° apart instead of parallel, usually because a hand or a shadow is next to it",par);
                return null;
            }
            double ax=left[2]+Math.signum(left[2]*right[2]+left[3]*right[3])*right[2],ay=left[3]+Math.signum(left[2]*right[2]+left[3]*right[3])*right[3];
            double sq=Math.toDegrees(Math.acos(Math.min(1,Math.abs(ax*end[2]+ay*end[3])/Math.hypot(ax,ay))));
            if(DEBUG)System.err.printf("six refine: square %.2f%n",90-sq);
            // The end is short (about 0.7 baton widths of usable edge), so its own direction
            // is noisy (+/-5 deg on a 26 px baton). The baton is a rectangle: keep the end's
            // position from the fit and take its direction square to the long sides.
            double an=Math.hypot(ax,ay);
            end=new double[]{end[0],end[1],-ay/an,ax/an};
            if(inner!=null)inner=new double[]{inner[0],inner[1],-ay/an,ax/an};
            double[] ol=TriangleEdgeRefiner.intersect(left,end),or=TriangleEdgeRefiner.intersect(right,end);
            double[] il=inner!=null?TriangleEdgeRefiner.intersect(left,inner):null,ir=inner!=null?TriangleEdgeRefiner.intersect(right,inner):null;
            if(ol==null||or==null){why[0]="the baton's corners could not be located";return null;}
            if(il==null||ir==null){il=q[0];ir=q[3];}
            double lim=0.06*r;
            if(Math.hypot(ol[0]-q[1][0],ol[1]-q[1][1])>lim||Math.hypot(or[0]-q[2][0],or[1]-q[2][1])>lim){why[0]="the fitted baton edges moved too far from the detected baton";return null;}
            if(Math.hypot(il[0]-q[0][0],il[1]-q[0][1])>lim||Math.hypot(ir[0]-q[3][0],ir[1]-q[3][1])>lim){il=q[0];ir=q[3];}
            return new double[][]{il,ol,or,ir};
        }catch(Throwable t){why[0]="the baton edge fit failed";return null;}
    }

    /** Turned-image point back to the original image (W,H are the turned image's size). */
    private static double[] back(double[] p,int W,int H,Position pos){
        if(pos==Position.NINE)return new double[]{p[1],W-1-p[0]};
        if(pos==Position.THREE)return new double[]{H-1-p[1],p[0]};
        return new double[]{W-1-p[0],H-1-p[1]};
    }
    private static double[] mid(double[] a,double[] b){return new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2};}
    private static double pointLineDistance(double[] p,double[] a,double[] b){
        double dx=b[0]-a[0],dy=b[1]-a[1],l=Math.hypot(dx,dy);if(l<1e-9)return Double.NaN;
        return ((p[0]-a[0])*dy-(p[1]-a[1])*dx)/l;
    }
    private static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
}
