package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH-ONLY raw measurement driver for 12-series Submariners (issue #34).
 *
 * It never calls GmtHumanQcAnalyzerV2, GmtHumanPosePolicy, GmtDialLayout, GmtHumanQcMath's
 * decisions, the no-readable-dial decision or any CLEAR/CHECK/STRONG logic, and it outputs no
 * verdict, pose label or pass/fail field. tools/dataset_harvester/tests/test_submariner_research.py
 * checks this file's source and the classes javac pulls in when it is compiled on its own.
 *
 * Reused primitives (detection and fitting only):
 *   GmtDialSeedAnalyzer          dark-dial proposal (Hough + dark-inside/bright-outside ring)
 *   DialEdgeFitter/EllipseFit    dial-edge ellipse
 *   GmtTwelveLandmarkAnalyzer    12 triangle contour + 59/60/01 tick frame (its own outer-edge refit
 *                                applies the GMT 44.3 deg apex prior; this driver re-fits the three
 *                                sides with TriangleEdgeRefiner.fitSide and NO apex/squareness gate)
 *   GmtSixLandmarkAnalyzer       baton outline + tick frame at 3, 6 and 9 (the hours come from the
 *                                model layout passed in, never from GmtDialLayout)
 *   GmtRoundMarkerAnalyzer       round-marker circle fits and local tick frames
 *   GmtMarkerPose.fit            affine singular-value ratio of the round-marker layout (raw, ungated)
 *   GmtRehaut*Analyzer           raw rehaut sector widths / coverage (no pose label, no gap trend)
 *   GmtEllipsePoseAnalyzer       raw dial-contour ellipse ratio
 *
 * GMT-derived values used ONLY as labelled wide search priors (see PRIORS below and the
 * "priors" field of every record): the 126710BLNR master round-marker centre radius and surround
 * size (seed placement and search window), the GMT-tuned search windows inside the reused
 * detectors, and the GMT dial-crop resolution (230 px preview limit, 380 px target radius).
 *
 * Usage: SubMeasure <jobs.tsv> <out.jsonl> [shards shardIndex]
 *   jobs.tsv: path TAB sha256 TAB model TAB batonHours(e.g. 3,6,9) TAB dateHours(e.g. 3 or -) TAB variants(comma list)
 *   variants: orig, s94, s88, x+1, x-1, x+2, x-2, y+1, y-1, y+2, y-2, r+5, r-5
 * One JSON line per (photo, variant). Coordinates are ORIGINAL-image pixels (EXIF-oriented); every
 * variant is analysed from scratch (dial included) and mapped back through its known transform.
 */
final class SubMeasure {
    static final String DRIVER_VERSION="submeasure-v1";
    /** Labelled search priors (not Submariner calibration). */
    static final String PRIORS="round_seed_radius=gmt_126710BLNR_master_0.816R;round_size_window=gmt_master_0.088R(x0.55-1.45);"
            +"detector_windows=gmt_tuned(12:0.52-0.88R,batons:0.62-0.88R,ticks:0.835-0.99R);"
            +"crop=gmt_tuned(preview_r<230px,target_r<=380px);triangle_shape_prior=NOT_APPLIED";
    static final int PREVIEW_MAX_SIDE=1600;
    static final double CROP_MAX_PREVIEW_R=230, CROP_TARGET_R=380, CROP_MIN_GAIN=1.25, CROP_MARGIN_R=1.35;
    static final int[] ROUND_HOURS={1,2,4,5,7,8,10,11};

    public static void main(String[] a) throws Exception{
        nu.pattern.OpenCV.loadLocally();
        if(a.length>0&&a[0].equals("--selftest")){System.out.println(selftest());return;}
        List<String> lines=Files.readAllLines(Paths.get(a[0]),StandardCharsets.UTF_8);
        int shards=a.length>3?Integer.parseInt(a[2]):1,shard=a.length>3?Integer.parseInt(a[3]):0;
        try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[1]),StandardCharsets.UTF_8))){
            int i=0;
            for(String line:lines){
                if(line.trim().isEmpty())continue;
                if((i++)%shards!=shard)continue;
                String[] f=line.split("\t",-1);
                Job job=new Job(f[0],f[1],f[2],hours(f[3]),hours(f[4]),f[5].split(","));
                for(String rec:measurePhoto(job)){out.println(rec);out.flush();}
            }
        }
    }

    /** Transform round trips and variant geometry; prints "SELFTEST OK" or the first failure. */
    static String selftest(){
        Mat img=new Mat(400,600,CvType.CV_8UC3,new Scalar(20,20,20));
        String[] vs={"orig","s94","s88","x+1","x-1","x+2","x-2","y+1","y-1","y+2","y-2","r+5","r-5"};
        for(String v:vs){
            double[] T=transform(v,img),I=inverse(T);
            for(double[] p:new double[][]{{0,0},{123.4,56.7},{599,399}}){
                double[] q=map(T,p[0],p[1]),b=map(I,q[0],q[1]);
                if(Math.hypot(b[0]-p[0],b[1]-p[1])>1e-9)return "SELFTEST FAIL round trip "+v;
            }
        }
        double[] tx=transform("x+2",img);if(tx[2]!=12||tx[5]!=0)return "SELFTEST FAIL x+2 shift "+tx[2];
        double[] ty=transform("y-1",img);if(ty[5]!=-4||ty[2]!=0)return "SELFTEST FAIL y-1 shift "+ty[5];
        // A bright dot moved by the x+2 variant must land 12 px to the right.
        img.put(200,300,new double[]{255,255,255});
        Mat m=apply(img,transform("x+2",img),"x+2");
        double[] px=m.get(200,312);if(px[0]!=255)return "SELFTEST FAIL x+2 pixel";
        m.release();
        Mat s=apply(img,transform("s94",img),"s94");if(s.cols()!=564||s.rows()!=376)return "SELFTEST FAIL s94 size";
        s.release();img.release();
        J j=new J();j.num("a",Double.NaN);j.str("b","q\"x");if(!j.done().equals("{\"a\":null,\"b\":\"q\\\"x\"}"))return "SELFTEST FAIL json "+j.done();
        return "SELFTEST OK";
    }

    static int[] hours(String s){
        if(s==null||s.trim().isEmpty()||s.trim().equals("-"))return new int[0];
        String[] p=s.split(",");int[] h=new int[p.length];
        for(int i=0;i<p.length;i++)h[i]=Integer.parseInt(p[i].trim());
        return h;
    }

    static final class Job{
        final String path,sha,model;final int[] batons,dates;final String[] variants;
        Job(String p,String s,String m,int[] b,int[] d,String[] v){path=p;sha=s;model=m;batons=b;dates=d;variants=v;}
    }

    /** Working image = preview, or a dial crop from the original; maps working px -> original px. */
    static final class Working{
        Mat img;double originX,originY,scale;String path;double previewR=Double.NaN;
        double[] toOrig(double x,double y){return new double[]{x/scale+originX,y/scale+originY};}
    }

    static List<String> measurePhoto(Job job){
        List<String> recs=new ArrayList<>();
        Mat orig=Imgcodecs.imread(job.path,Imgcodecs.IMREAD_COLOR);   // applies EXIF orientation
        if(orig==null||orig.empty()){
            J j=head(job,"orig");j.str("error","unreadable");recs.add(j.done());return recs;
        }
        try{
            Working w=working(orig);
            boolean baseDial=false;
            for(String v:job.variants){
                v=v.trim();if(v.isEmpty())continue;
                // No perturbations when the original has no located dial or no detected landmark: there is
                // nothing to compare across variants (box, papers, bracelet and thumbnail photos).
                if(!v.equals("orig")&&!baseDial)break;
                J j=head(job,v);
                j.num("orig_w",orig.cols());j.num("orig_h",orig.rows());
                j.str("analysis_path",w.path);j.num("working_scale",w.scale);j.num("working_origin_x",w.originX);j.num("working_origin_y",w.originY);
                j.num("preview_dial_r",w.previewR);
                double[] T=transform(v,w.img);   // 2x3 working -> variant
                j.num("t_a",T[0]);j.num("t_b",T[1]);j.num("t_c",T[2]);j.num("t_d",T[3]);j.num("t_e",T[4]);j.num("t_f",T[5]);
                Mat img=apply(w.img,T,v);
                try{
                    int detected=measure(img,job,inverse(T),w,j);
                    if(v.equals("orig"))baseDial=detected>0;
                }catch(Throwable t){
                    j.str("error","measurement failed: "+t.getClass().getSimpleName()+": "+t.getMessage());
                }finally{if(img!=w.img)img.release();}
                recs.add(j.done());
            }
            if(w.img!=orig)w.img.release();
        }finally{orig.release();}
        return recs;
    }

    static J head(Job job,String variant){
        J j=new J();
        j.str("driver",DRIVER_VERSION);j.str("path",job.path);j.str("sha256",job.sha);j.str("model",job.model);j.str("variant",variant);
        j.str("priors",PRIORS);
        return j;
    }

    static Working working(Mat orig){
        Working w=new Working();
        double s=Math.min(1.0,PREVIEW_MAX_SIDE/(double)Math.max(orig.cols(),orig.rows()));
        Mat preview=orig;
        if(s<1.0){preview=new Mat();Imgproc.resize(orig,preview,new Size(Math.round(orig.cols()*s),Math.round(orig.rows()*s)),0,0,Imgproc.INTER_AREA);}
        double k=orig.cols()/(double)preview.cols();
        w.img=preview;w.originX=0;w.originY=0;w.scale=1.0/k;w.path="preview";
        double[] d=locate(preview);
        if(d==null)return w;
        w.previewR=d[2];
        if(d[2]>=CROP_MAX_PREVIEW_R)return w;
        double rOrig=d[2]*k,target=Math.min(rOrig,CROP_TARGET_R);
        if(target<d[2]*CROP_MIN_GAIN)return w;
        double cx=d[0]*k,cy=d[1]*k,half=CROP_MARGIN_R*rOrig;
        int x0=(int)Math.max(0,Math.floor(cx-half)),y0=(int)Math.max(0,Math.floor(cy-half));
        int x1=(int)Math.min(orig.cols(),Math.ceil(cx+half)),y1=(int)Math.min(orig.rows(),Math.ceil(cy+half));
        if(x1-x0<50||y1-y0<50)return w;
        double want=target/rOrig;
        Mat crop=new Mat();
        Mat roi=orig.submat(new Rect(x0,y0,x1-x0,y1-y0));
        int cw=(int)Math.max(1,Math.round((x1-x0)*want)),ch=(int)Math.max(1,Math.round((y1-y0)*want));
        Imgproc.resize(roi,crop,new Size(cw,ch),0,0,Imgproc.INTER_AREA);
        roi.release();
        if(preview!=orig)preview.release();
        w.img=crop;w.originX=x0;w.originY=y0;w.scale=cw/(double)(x1-x0);w.path="fullres_crop";
        return w;
    }

    /** Seed + edge fit on an image: {cx, cy, r} or null. */
    static double[] locate(Mat img){
        GmtDialSeedAnalyzer.Result s=GmtDialSeedAnalyzer.analyse(img);
        if(!s.valid||!(s.r>20))return null;
        DialEdgeEllipseFit.Fit f=DialEdgeFitter.fitBgr(img,s.x,s.y,s.r);
        return f!=null?new double[]{f.cx,f.cy,f.meanRadius()}:new double[]{s.x,s.y,s.r};
    }

    /** Variant transform working -> variant, 2x3 row-major {a,b,c,d,e,f}: x'=ax+by+c, y'=dx+ey+f. */
    static double[] transform(String v,Mat img){
        int W=img.cols(),H=img.rows();
        switch(v){
            case "orig":return new double[]{1,0,0,0,1,0};
            case "s94":return new double[]{0.94,0,0,0,0.94,0};
            case "s88":return new double[]{0.88,0,0,0,0.88,0};
            default:break;
        }
        char axis=v.charAt(0);double amount=Double.parseDouble(v.substring(1));
        if(axis=='x')return new double[]{1,0,Math.round(W*amount/100.0),0,1,0};
        if(axis=='y')return new double[]{1,0,0,0,1,Math.round(H*amount/100.0)};
        if(axis=='r'){
            // In-plane rotation by `amount` degrees clockwise (image coordinates) about the image centre.
            double t=Math.toRadians(amount),c=Math.cos(t),s=Math.sin(t),cx=(W-1)/2.0,cy=(H-1)/2.0;
            return new double[]{c,-s,cx-c*cx+s*cy,s,c,cy-s*cx-c*cy};
        }
        throw new IllegalArgumentException("unknown variant "+v);
    }

    static double[] inverse(double[] T){
        double det=T[0]*T[4]-T[1]*T[3];
        double a=T[4]/det,b=-T[1]/det,d=-T[3]/det,e=T[0]/det;
        return new double[]{a,b,-(a*T[2]+b*T[5]),d,e,-(d*T[2]+e*T[5])};
    }

    static double[] map(double[] T,double x,double y){return new double[]{T[0]*x+T[1]*y+T[2],T[3]*x+T[4]*y+T[5]};}

    /** Scales resize the canvas (as the production resize check); shifts/rotations keep it, flat fill. */
    static Mat apply(Mat img,double[] T,String v){
        if(v.equals("orig"))return img;
        Mat out=new Mat();
        if(v.startsWith("s")){
            Imgproc.resize(img,out,new Size(Math.round(img.cols()*T[0]),Math.round(img.rows()*T[4])),0,0,Imgproc.INTER_LINEAR);
            return out;
        }
        Mat M=new Mat(2,3,CvType.CV_64F);M.put(0,0,T);
        Imgproc.warpAffine(img,out,M,img.size(),v.startsWith("r")?Imgproc.INTER_LINEAR:Imgproc.INTER_NEAREST,Core.BORDER_CONSTANT,borderMedian(img));
        M.release();
        return out;
    }

    static Scalar borderMedian(Mat img){
        int W=img.cols(),H=img.rows();
        double[][] ch=new double[3][2*W+2*H];int n=0;
        for(int x=0;x<W;x++)for(int y:new int[]{0,H-1}){double[] p=img.get(y,x);for(int c=0;c<3;c++)ch[c][n]=p[c];n++;}
        for(int y=0;y<H;y++)for(int x:new int[]{0,W-1}){double[] p=img.get(y,x);for(int c=0;c<3;c++)ch[c][n]=p[c];n++;}
        double[] m=new double[3];
        for(int c=0;c<3;c++){double[] q=Arrays.copyOf(ch[c],n);Arrays.sort(q);m[c]=q[n/2];}
        return new Scalar(m[0],m[1],m[2]);
    }

    /**
     * Everything on one (variant) image. inv maps variant px -> working px; w maps working -> original.
     * Returns the number of landmarks detected (-1 when no dial was located).
     */
    static int measure(Mat img,Job job,double[] inv,Working w,J j){
        // ---- dial: dark-dial proposal, then edge ellipse -------------------------------------------
        GmtDialSeedAnalyzer.Result seed=GmtDialSeedAnalyzer.analyse(img);
        j.bool("seed_valid",seed.valid);
        if(!seed.valid){j.str("dial_reason",seed.reason);j.bool("dial_found",false);return -1;}
        double[] so=orig(inv,w,seed.x,seed.y);double sc=scaleToOrig(inv,w);
        j.num("seed_cx",so[0]);j.num("seed_cy",so[1]);j.num("seed_r",seed.r*sc);j.num("seed_quality",seed.quality);
        j.num("seed_boundary_strength",seed.boundaryStrength);j.num("seed_boundary_coverage",seed.boundaryCoverage);j.num("seed_marker_hits",seed.markerHits);
        DialEdgeEllipseFit.Fit fit=DialEdgeFitter.fitBgr(img,seed.x,seed.y,seed.r);
        double cx,cy,r;GmtRoundMarkerAnalyzer.DialFrame frame;
        j.bool("edge_fit_valid",fit!=null);
        if(fit!=null){
            cx=fit.cx;cy=fit.cy;r=fit.meanRadius();
            double[] fo=orig(inv,w,fit.cx,fit.cy);
            j.num("fit_cx",fo[0]);j.num("fit_cy",fo[1]);j.num("fit_a",fit.axisA*sc);j.num("fit_b",fit.axisB*sc);
            // Angle of the a-axis in ORIGINAL image coordinates (undo the variant's rotation).
            double ang=fit.angleDeg+Math.toDegrees(Math.atan2(inv[3],inv[0]));
            j.num("fit_angle_deg",wrapHalf(ang));j.num("fit_axis_ratio",Math.min(fit.axisA,fit.axisB)/Math.max(fit.axisA,fit.axisB));
            j.num("fit_rms_px",fit.rmsPx*sc);j.num("fit_inliers",fit.points);j.num("fit_rays",fit.rays);
            j.num("seed_to_fit_px",Math.hypot(fit.cx-seed.x,fit.cy-seed.y)*sc);
            j.num("seed_to_fit_over_r",Math.hypot(fit.cx-seed.x,fit.cy-seed.y)/r);
            frame=new GmtRoundMarkerAnalyzer.DialFrame(fit.cx,fit.cy,fit.axisA,fit.axisB,fit.angleDeg);
            j.str("dial_source","edge_fit");
        }else{
            cx=seed.x;cy=seed.y;r=seed.r;frame=GmtRoundMarkerAnalyzer.DialFrame.circle(cx,cy,r);
            j.str("dial_source","seed_circle");
        }
        double[] co=orig(inv,w,cx,cy);
        j.num("dial_cx",co[0]);j.num("dial_cy",co[1]);j.num("dial_r",r*sc);j.num("dial_r_analysis_px",r);
        j.bool("dial_found",true);
        j.bool("dial_complete",complete(frame,img.cols(),img.rows()));

        List<J> lms=new ArrayList<>();
        // ---- 12 triangle ------------------------------------------------------------------------------
        GmtTwelveLandmarkAnalyzer.Result t=GmtTwelveLandmarkAnalyzer.analyse(img,cx,cy,r);
        double[] tick60=null;double twelveClock=Double.NaN;
        J tri=new J();tri.str("landmark","12");tri.str("kind","triangle");tri.num("hour",12);
        if(t.valid&&t.geometry!=null){
            tick60=t.geometry.tick60;
            twelveClock=clock(cx,cy,tick60[0],tick60[1]);
            triangle(img,t,cx,cy,r,inv,w,frame,tri);
        }else{tri.bool("detected",false);tri.str("reason",t.reason);}
        lms.add(tri);
        j.num("twelve_clock_deg_analysis",twelveClock);
        j.str("orientation_source",tick60!=null?"tick60":"image_up");

        // ---- batons from the model layout ---------------------------------------------------------------
        for(int h:job.batons){
            J b=new J();b.str("landmark","b"+h);b.str("kind","baton");b.num("hour",h);
            GmtSixLandmarkAnalyzer.Position pos=h==6?GmtSixLandmarkAnalyzer.Position.SIX:h==9?GmtSixLandmarkAnalyzer.Position.NINE:h==3?GmtSixLandmarkAnalyzer.Position.THREE:null;
            if(pos==null){b.bool("detected",false);b.str("reason","unsupported baton hour");lms.add(b);continue;}
            // NaN twelve angle: no search window from GMT master baton dimensions.
            GmtSixLandmarkAnalyzer.Result s=GmtSixLandmarkAnalyzer.analyse(img,cx,cy,r,pos,Double.NaN);
            baton(s,cx,cy,r,inv,w,frame,twelveClock,b);
            lms.add(b);
        }
        for(int h:job.dates){
            J d=new J();d.str("landmark","date"+h);d.str("kind","date");d.num("hour",h);d.bool("detected",false);d.str("reason","not_measured_phase1");lms.add(d);
        }

        // ---- round markers ---------------------------------------------------------------------------------
        List<GmtRoundMarkerAnalyzer.Marker> round=GmtRoundMarkerAnalyzer.analyse(img,frame,tick60);
        List<Integer> hs=new ArrayList<>();List<double[]> pts=new ArrayList<>();
        for(GmtRoundMarkerAnalyzer.Marker m:round){
            J q=new J();q.str("landmark","r"+m.hour);q.str("kind","round");q.num("hour",m.hour);
            roundMarker(m,cx,cy,r,inv,w,frame,twelveClock,q);
            lms.add(q);
            if(m.found){hs.add(m.hour);pts.add(new double[]{m.x,m.y});}
        }
        j.num("round_found",hs.size());

        // ---- marker-layout pose (raw affine singular-value ratio, no gating) ----------------------------------
        j.num("mpose_markers",hs.size());
        if(hs.size()>=5){
            double[] f=GmtMarkerPose.fit(hs,pts);
            if(f!=null){
                j.num("mpose_ratio",f[0]);j.num("mpose_tilt_deg",Math.toDegrees(Math.acos(Math.min(1,f[0]))));
                j.num("mpose_residual_over_r",f[3]);j.num("mpose_scale_over_r",f[4]/r);
            }
        }

        // ---- raw rehaut and ellipse diagnostics --------------------------------------------------------------
        double roll=tick60!=null?twelveClock:0.0;
        j.str("rehaut_roll_source",tick60!=null?"tick60":"image_up");
        GmtRehautPoseAnalyzer.Result rp=GmtRehautPoseAnalyzer.analyse(img,cx,cy,r,roll);
        j.bool("rh_global_valid",rp.valid);
        if(rp.valid){
            j.num("rh_global_top_px",rp.topWidthPx*sc);j.num("rh_global_bottom_px",rp.bottomWidthPx*sc);j.num("rh_global_left_px",rp.leftWidthPx*sc);j.num("rh_global_right_px",rp.rightWidthPx*sc);
            j.num("rh_global_mean_px",rp.meanWidthPx*sc);j.num("rh_harmonic",rp.firstHarmonicStrength);j.num("rh_widest_clock_deg",rp.widestClockDeg);
            j.num("rh_min_over_mean",rp.minWidthOverMean);j.num("rh_edge_coverage",rp.edgeCoverage);j.num("rh_fit_residual",rp.fitResidual);
        }
        GmtRehautSectorAnalyzer.Result sec=rp.valid?GmtRehautSectorAnalyzer.analyse(img,cx,cy,rp.innerSeedPx,rp.outerSeedPx,roll):null;
        String secSrc="global_seeds";
        if(sec==null||!sec.valid){sec=GmtRehautSectorAutoAnalyzer.analyse(img,cx,cy,r,roll);secSrc="auto";}
        j.str("rh_sector_source",sec.valid?secSrc:"none");
        if(sec.valid){
            j.num("rh_w12_px",sec.width12*sc);j.num("rh_w3_px",sec.width3*sc);j.num("rh_w6_px",sec.width6*sc);j.num("rh_w9_px",sec.width9*sc);
            j.num("rh_cov12",sec.coverage12);j.num("rh_cov3",sec.coverage3);j.num("rh_cov6",sec.coverage6);j.num("rh_cov9",sec.coverage9);
        }
        GmtEllipsePoseAnalyzer.Result el=GmtEllipsePoseAnalyzer.analyse(img,cx,cy,r);
        j.bool("ell_valid",el.valid);
        if(el.valid){j.num("ell_ratio",el.axisRatio);j.num("ell_tilt_deg",el.tiltDeg);j.num("ell_minor_clock_deg",el.minorAxisClockDeg);j.num("ell_centre_err_over_r",el.centerErrorOverR);j.num("ell_major_over_r",el.majorRadiusRatio);}

        j.arr("landmarks",lms);
        int n=0;for(J q:lms)if(Boolean.TRUE.equals(q.get("detected")))n++;
        j.num("landmarks_detected",n);
        return n;
    }

    // ------------------------------------------------------------------------------------------------ 12
    static void triangle(Mat img,GmtTwelveLandmarkAnalyzer.Result t,double cx,double cy,double r,double[] inv,Working w,
                         GmtRoundMarkerAnalyzer.DialFrame frame,J q){
        GmtTwelveLandmarkAnalyzer.Geometry g=t.geometry;
        // The primitive's own result (its outer-edge refit is dropped by the GMT apex prior when the
        // shape differs; then these are contour corners). Kept as diagnostics, never as a decision.
        q.str("primitive_path",g.outerEdge?"gmt_primitive_refined":"gmt_primitive_contour");
        q.num("primitive_gap_over_width",t.topClearance);q.num("primitive_axis_rot_deg",t.wholeAxisErrorDeg);
        q.num("primitive_width_px",t.triangleWidthPx*scaleToOrig(inv,w));
        // Research re-fit of the three outer edges, NO shape prior (TriangleEdgeRefiner.fitSide + intersect).
        double[] L=g.triLeft,R=g.triRight,T=g.triTip;String path=g.outerEdge?"gmt_primitive_refined":"gmt_primitive_contour";
        boolean band=false;double[][] re=null;
        Mat enh=GmtTwelveLandmarkAnalyzer.enhance(img);
        try{
            DialEdgeEllipseFit.Intensity I=intensity(enh);
            re=refitTriangle(I,enh.cols(),enh.rows(),L,R,T,g.tick59,g.tick01,r,false);
            if(re==null){re=refitTriangle(I,enh.cols(),enh.rows(),L,R,T,g.tick59,g.tick01,r,true);band=re!=null;}
        }finally{enh.release();}
        if(re!=null){
            L=re[0];R=re[1];T=re[2];
            if(L[0]>R[0]){double[] z=L;L=R;R=z;}
            path=band?"unconstrained_refit_band":"unconstrained_refit";
        }
        q.str("fit_path",path);
        q.bool("detected",true);
        double apex=TriangleEdgeRefiner.apexAngleDeg(L,R,T),square=TriangleEdgeRefiner.squarenessDeg(L,R,T);
        q.num("apex_deg",apex);q.num("squareness_deg",square);
        double[] bm=mid(L,R);double width=Math.hypot(R[0]-L[0],R[1]-L[1]),height=Math.hypot(T[0]-bm[0],T[1]-bm[1]);
        double[] centre={(L[0]+R[0]+T[0])/3,(L[1]+R[1]+T[1])/3};
        // Local tick frame (59 = image-left, 01 = image-right after the primitive's swap).
        double[] tl=g.tick59,tr=g.tick01,tc=g.tick60;
        double kx=tr[0]-tl[0],ky=tr[1]-tl[1],kn=Math.hypot(kx,ky);
        double ux=-ky/kn,uy=kx/kn;if((cx-tc[0])*ux+(cy-tc[1])*uy<0){ux=-ux;uy=-uy;}   // inward normal
        double gapPx=Math.abs(pointLine(bm,tl,tr));
        double axisErr=wrap90(Math.toDegrees(Math.atan2(T[1]-bm[1],T[0]-bm[0])-Math.atan2(uy,ux)));
        double edgeErr=wrap90(Math.toDegrees(Math.atan2(R[1]-L[1],R[0]-L[0])-Math.atan2(tr[1]-tl[1],tr[0]-tl[0])));
        double vx=-uy,vy=ux,horiz=((bm[0]-tc[0])*vx+(bm[1]-tc[1])*vy)/width;
        double leftSp=Math.hypot(L[0]-tl[0],L[1]-tl[1])/width,rightSp=Math.hypot(R[0]-tr[0],R[1]-tr[1])/width;
        double inset=((centre[0]-(tl[0]+tr[0])/2)*ux+(centre[1]-(tl[1]+tr[1])/2)*uy)/kn;
        common(q,centre,cx,cy,r,inv,w,frame,clock(cx,cy,tc[0],tc[1]),0.0);
        double sc=scaleToOrig(inv,w);
        q.num("width_px",width*sc);q.num("length_px",height*sc);q.num("width_over_r",width/r);q.num("length_over_r",height/r);
        q.num("rotation_deg",axisErr);q.num("base_edge_rot_deg",edgeErr);
        q.num("gap_raw",gapPx/width);q.num("gap_over_r",gapPx/r);q.num("inset",inset);q.num("centring_raw",horiz);
        q.num("side_spacing_left",leftSp);q.num("side_spacing_right",rightSp);
        q.num("tick_pitch_deg",t.tickPitchDeg);q.num("tick_score",t.minuteFrameScore);q.num("ticks_inferred",t.inferredMinutePoints);
        q.num("axis_ref_disagreement_deg",t.axisReferenceDisagreementDeg);
        pt(q,"p_left",L,inv,w);pt(q,"p_right",R,inv,w);pt(q,"p_tip",T,inv,w);
        pt(q,"tick_before",tl,inv,w);pt(q,"tick_centre",tc,inv,w);pt(q,"tick_after",tr,inv,w);
    }

    /** TriangleEdgeRefiner's side fits and intersections WITHOUT the apex/squareness/displacement gates. */
    static double[][] refitTriangle(DialEdgeEllipseFit.Intensity img,int w,int h,double[] left,double[] right,double[] tip,
                                    double[] tickA,double[] tickB,double dialR,boolean band){
        double gx=(left[0]+right[0]+tip[0])/3.0,gy=(left[1]+right[1]+tip[1])/3.0;
        double[] base=TriangleEdgeRefiner.fitSide(img,w,h,left,right,gx,gy,dialR,0.12,0.88,0.045,tickA,tickB,band);
        double[] rs=TriangleEdgeRefiner.fitSide(img,w,h,right,tip,gx,gy,dialR,0.10,0.75,0.035,null,null,band);
        double[] ls=TriangleEdgeRefiner.fitSide(img,w,h,tip,left,gx,gy,dialR,0.25,0.90,0.035,null,null,band);
        if(base==null||rs==null||ls==null)return null;
        double[] l=TriangleEdgeRefiner.intersect(ls,base),r=TriangleEdgeRefiner.intersect(base,rs),t=TriangleEdgeRefiner.intersect(rs,ls);
        if(l==null||r==null||t==null)return null;
        return new double[][]{l,r,t};
    }

    // ------------------------------------------------------------------------------------------------ batons
    static void baton(GmtSixLandmarkAnalyzer.Result s,double cx,double cy,double r,double[] inv,Working w,
                      GmtRoundMarkerAnalyzer.DialFrame frame,double twelveClock,J q){
        if(!s.valid||s.geometry==null){q.bool("detected",false);q.str("reason",s.reason);return;}
        GmtSixLandmarkAnalyzer.Geometry g=s.geometry;
        q.bool("detected",true);
        q.str("fit_path",g.outerEdge?"edge_refit":"contour_rectangle");
        double[] centre={(g.outerLeft[0]+g.outerRight[0]+g.innerLeft[0]+g.innerRight[0])/4,(g.outerLeft[1]+g.outerRight[1]+g.innerLeft[1]+g.innerRight[1])/4};
        common(q,centre,cx,cy,r,inv,w,frame,twelveClock,((Number)q.get("hour")).doubleValue()*30.0);
        double sc=scaleToOrig(inv,w);
        q.num("width_px",s.widthPx*sc);q.num("length_px",s.lengthPx*sc);q.num("width_over_r",s.widthPx/r);q.num("length_over_r",s.lengthPx/r);
        q.num("rotation_deg",s.rotationDeg);q.num("gap_raw",s.gap);q.num("gap_over_r",s.gap*s.widthPx/r);q.num("centring_raw",s.centring);
        double[] a=g.tickAfter,b=g.tickBefore;double kx=b[0]-a[0],ky=b[1]-a[1],kn=Math.hypot(kx,ky);
        double ux=-ky/kn,uy=kx/kn;double mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2;if((cx-mx)*ux+(cy-my)*uy<0){ux=-ux;uy=-uy;}
        q.num("inset",((centre[0]-mx)*ux+(centre[1]-my)*uy)/kn);
        q.num("long_side_parallel_deg",s.parallelDeg);
        q.num("tick_pitch_deg",s.tickPitchDeg);q.num("tick_score",s.tickScore);q.num("ticks_inferred",s.ticksInferred);
        pt(q,"p_outer_left",g.outerLeft,inv,w);pt(q,"p_outer_right",g.outerRight,inv,w);pt(q,"p_inner_left",g.innerLeft,inv,w);pt(q,"p_inner_right",g.innerRight,inv,w);
        pt(q,"tick_before",g.tickBefore,inv,w);pt(q,"tick_centre",g.tickCentre,inv,w);pt(q,"tick_after",g.tickAfter,inv,w);
    }

    // ------------------------------------------------------------------------------------------------ round
    static void roundMarker(GmtRoundMarkerAnalyzer.Marker m,double cx,double cy,double r,double[] inv,Working w,
                            GmtRoundMarkerAnalyzer.DialFrame frame,double twelveClock,J q){
        double sc=scaleToOrig(inv,w);
        q.num("seed_placed_by_affine",m.seedPlaced?1:0);
        if(Double.isFinite(m.seedX)){double[] so=orig(inv,w,m.seedX,m.seedY);q.num("seed_x",so[0]);q.num("seed_y",so[1]);}
        q.num("edge_contrast",m.contrast);q.num("reject_fraction",m.rejectFraction);q.num("angle_from_seed_deg",m.angleFromExpectedDeg);
        if(!m.found||!Double.isFinite(m.x)){
            q.bool("detected",false);q.str("reason",m.reason);
            if(Double.isFinite(m.x)){double[] o=orig(inv,w,m.x,m.y);q.num("x",o[0]);q.num("y",o[1]);q.num("radius_px",m.radiusPx*sc);}
            return;
        }
        q.bool("detected",true);q.str("fit_path","circle_fit");
        common(q,new double[]{m.x,m.y},cx,cy,r,inv,w,frame,twelveClock,m.hour*30.0);
        q.num("radius_px",m.radiusPx*sc);q.num("radius_over_r",m.radiusPx/r);
        double sur=GmtRoundMarkerAnalyzer.surroundRadius(m);
        List<Double> rings=GmtRoundMarkerAnalyzer.ringRadii(m);
        q.num("surround_radius_over_r",sur/r);q.num("rings_found",rings.size());
        if(!rings.isEmpty())q.num("inner_ring_over_r",rings.get(0)/r);
        q.num("width_px",2*m.radiusPx*sc);q.num("width_over_r",2*m.radiusPx/r);
        q.num("gap_raw",m.gap);q.num("gap_over_r",m.gap*2*m.radiusPx/r);q.num("inset",m.inset);
        q.num("centring_raw",m.offset);q.num("centring_vs_centre_tick",m.offsetFromCentreTick);
        q.num("tick_pitch_deg",m.tickPitchDeg);q.num("tick_score",m.tickScore);q.num("ticks_inferred",m.ticksInferred);q.num("tick_chord_px",m.chordPx*sc);
        if(m.tickBefore!=null){pt(q,"tick_before",m.tickBefore,inv,w);pt(q,"tick_centre",m.tickCentre,inv,w);pt(q,"tick_after",m.tickAfter,inv,w);}
    }

    /** Centre in original px, and polar position in the dial frame (squash undone), relative to the 12 direction. */
    static void common(J q,double[] c,double cx,double cy,double r,double[] inv,Working w,GmtRoundMarkerAnalyzer.DialFrame frame,
                       double twelveClock,double nominalDeg){
        double[] o=orig(inv,w,c[0],c[1]);
        q.num("x",o[0]);q.num("y",o[1]);
        double[] u=frame.rect(c[0],c[1]);
        double rho=Math.hypot(u[0],u[1])/frame.r;
        // Clock angle in the rectified frame, then relative to the 12 direction (rectified too).
        double theta=Math.toDegrees(Math.atan2(u[0],-u[1]));
        double ref=0;
        if(Double.isFinite(twelveClock)){
            double t=Math.toRadians(twelveClock);
            double[] p=frame.rect(cx+Math.sin(t)*r,cy-Math.cos(t)*r);
            ref=Math.toDegrees(Math.atan2(p[0],-p[1]));
        }
        double rel=wrap360(theta-ref);
        q.num("rho_r",rho);q.num("theta_from12_deg",rel);q.num("dtheta_from_nominal_deg",wrap180(rel-nominalDeg));
        q.str("theta_reference",Double.isFinite(twelveClock)?"tick60":"image_up");
    }

    static boolean complete(GmtRoundMarkerAnalyzer.DialFrame f,int W,int H){
        for(int d=0;d<360;d+=15){double[] p=f.at(Math.toRadians(d),1.03);if(p[0]<0||p[1]<0||p[0]>=W||p[1]>=H)return false;}
        return true;
    }

    // ------------------------------------------------------------------------------------------------ helpers
    static double[] orig(double[] inv,Working w,double x,double y){double[] p=map(inv,x,y);return w.toOrig(p[0],p[1]);}
    /** Length scale variant px -> original px (similarity transforms only). */
    static double scaleToOrig(double[] inv,Working w){return Math.hypot(inv[0],inv[3])/w.scale;}
    static void pt(J q,String k,double[] p,double[] inv,Working w){if(p==null)return;double[] o=orig(inv,w,p[0],p[1]);q.num(k+"_x",o[0]);q.num(k+"_y",o[1]);}
    static DialEdgeEllipseFit.Intensity intensity(Mat gray){
        final int W=gray.cols(),H=gray.rows();final byte[] px=new byte[W*H];gray.get(0,0,px);
        return (x,y)->{int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double fx=x-x0,fy=y-y0;int i=y0*W+x0;
            double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+W]&0xff,d=px[i+W+1]&0xff;return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;};
    }
    static double clock(double cx,double cy,double x,double y){return Math.toDegrees(Math.atan2(x-cx,cy-y));}
    static double[] mid(double[] a,double[] b){return new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2};}
    static double pointLine(double[] p,double[] a,double[] b){double dx=b[0]-a[0],dy=b[1]-a[1],l=Math.hypot(dx,dy);return ((p[0]-a[0])*dy-(p[1]-a[1])*dx)/l;}
    static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    static double wrap180(double d){while(d>180)d-=360;while(d<=-180)d+=360;return d;}
    static double wrap360(double d){d%=360;if(d<0)d+=360;return d;}
    static double wrapHalf(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}

    /** Minimal JSON object builder (insertion ordered). */
    static final class J{
        final java.util.LinkedHashMap<String,Object> m=new java.util.LinkedHashMap<>();
        void num(String k,double v){m.put(k,v);}
        void str(String k,String v){m.put(k,v==null?"":v);}
        void bool(String k,boolean v){m.put(k,v);}
        void arr(String k,List<J> v){m.put(k,v);}
        Object get(String k){return m.get(k);}
        String done(){StringBuilder b=new StringBuilder();write(b,this);return b.toString();}
        static void write(StringBuilder b,Object o){
            if(o instanceof J){
                b.append('{');boolean first=true;
                for(java.util.Map.Entry<String,Object> e:((J)o).m.entrySet()){
                    if(!first)b.append(',');first=false;
                    str(b,e.getKey());b.append(':');write(b,e.getValue());
                }
                b.append('}');
            }else if(o instanceof List){
                b.append('[');boolean first=true;for(Object x:(List<?>)o){if(!first)b.append(',');first=false;write(b,x);}b.append(']');
            }else if(o instanceof Double){double v=(Double)o;b.append(Double.isFinite(v)?String.format(Locale.US,"%.7g",v):"null");}
            else if(o instanceof Boolean)b.append(o.toString());
            else str(b,String.valueOf(o));
        }
        static void str(StringBuilder b,String s){
            b.append('"');
            for(char c:s.toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c<0x20)b.append(' ');else b.append(c);}
            b.append('"');
        }
    }

    private SubMeasure(){}
}
