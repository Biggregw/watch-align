package com.watchalign.mobile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Seeded synthetic Sub124060QcAnalyzer results for decision and projection tests.
 *
 * Two kinds, both deterministic per seed:
 *  - analyser-like: each field set the way Sub124060QcAnalyzer sets it on one of its branches
 *    (dial failures, the 12 chain, resize outcomes, baton and marker statuses), including a failed
 *    closed analysis after the dial frame existed;
 *  - free: every field drawn independently (still one baton per position), to show the projection
 *    is the assessment on any input, not only on consistent ones.
 *
 * Only fields that exist in the v1 checkpoint are written directly, so this file also compiles
 * against that checkpoint to produce the golden assessment it is compared with. Reason codes are
 * written by reflection when the analyser has them, exactly beside the message the analyser pairs
 * them with.
 */
final class Sub124060Fixtures {
    private Sub124060Fixtures(){}

    static final String[] TWELVE_MESSAGES={
            "12 triangle not found","the 12 triangle is only 31 px wide in this photo (minimum 34)",
            "a hand is touching or right beside the 12 triangle, which can shift its measured outline",
            "the 12 triangle is not found as the same outline when the photo is reduced by 6% and 12%",
            "the dial is a hand-aligned circle whose edge could not be re-fitted",
            "the dial-edge fit changes when the photo is reduced by 6% or 12%"};
    static final String[] TWELVE_CODES={
            "sub12.triangle_not_found","sub12.triangle_too_small","sub12.hand_at_twelve",
            "sub12.triangle_resize_outline_changed","sub12.dial_manual_circle","sub12.dial_edge_not_reproducible"};

    static Sub124060QcAnalyzer.Result random(long seed){
        Random g=new Random(seed);
        return g.nextInt(5)==0?free(g):analyserLike(g);
    }

    // ------------------------------------------------------------------------------------ analyser-like
    static Sub124060QcAnalyzer.Result analyserLike(Random g){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        int dial=g.nextInt(20);
        if(dial==0){dialFailure(r,"watch image missing","core.image_unreadable");return r;}
        if(dial==1){dialFailure(r,"analysis failed closed: IllegalStateException","core.analysis_exception");return r;}
        if(dial==2){seed(r,g);dialFailure(r,"no dial found in the photo (no dark disc)","sub12.dial_not_found");return r;}
        if(dial==3){seed(r,g);dialFailure(r,"dial location confidence is too low","sub12.dial_seed_low_confidence");return r;}
        if(dial==4){seed(r,g);dialFailure(r,"the dial edge could not be fitted automatically","sub12.dial_edge_fit_failed");return r;}
        seed(r,g);
        double cx=400+g.nextDouble()*200,cy=400+g.nextDouble()*200,rad=200+g.nextDouble()*200;
        if(dial==5){
            r.dialSource=Sub124060QcAnalyzer.DialSource.MANUAL_CIRCLE;
            r.frame=GmtRoundMarkerAnalyzer.DialFrame.circle(cx,cy,rad);
            r.dialReproNote="no edge fit to re-check";
        }else{
            double a=rad*(1+0.04*(g.nextDouble()-0.5)),b=rad*(1+0.04*(g.nextDouble()-0.5)),ang=g.nextDouble()*180-90;
            r.edge=new DialEdgeEllipseFit.Fit(cx,cy,a,b,ang,150+g.nextInt(30),180,0.5+g.nextDouble());
            r.dialSource=g.nextInt(4)==0?Sub124060QcAnalyzer.DialSource.MANUAL_EDGE_FIT:Sub124060QcAnalyzer.DialSource.AUTO_EDGE_FIT;
            r.frame=new GmtRoundMarkerAnalyzer.DialFrame(cx,cy,a,b,ang);
            r.dialReproducible=g.nextInt(5)!=0;
            r.dialReproNote=r.dialReproducible?"94%: same dial, 88%: same dial":"94%: same dial, 88%: different dial (centre moved 6.2 px, radius +0.4%)";
        }
        twelve(r,g);
        batons(r,g);
        rounds(r,g);
        pose(r,g);
        // A failure after the dial frame existed: the analyser's catch resets the dial source and the
        // triangle and keeps everything measured before the failure.
        if(g.nextInt(25)==0){
            r.dialReason="analysis failed closed: ArrayIndexOutOfBoundsException";code(r,"dialReasonCode","core.analysis_exception");
            r.dialSource=Sub124060QcAnalyzer.DialSource.UNAVAILABLE;r.triangle=null;
            if(g.nextBoolean()&&!r.rounds.isEmpty())r.rounds.subList(g.nextInt(r.rounds.size()),r.rounds.size()).clear();
            if(g.nextInt(3)==0&&!r.batons.isEmpty())r.batons.subList(g.nextInt(r.batons.size()),r.batons.size()).clear();
        }
        return r;
    }

    static void seed(Sub124060QcAnalyzer.Result r,Random g){
        r.seedX=300+g.nextDouble()*400;r.seedY=300+g.nextDouble()*400;r.seedR=150+g.nextDouble()*250;r.seedQuality=0.3+0.7*g.nextDouble();
    }

    static void dialFailure(Sub124060QcAnalyzer.Result r,String why,String code){
        r.dialReason=why;code(r,"dialReasonCode",code);r.dialSource=Sub124060QcAnalyzer.DialSource.UNAVAILABLE;
    }

    static void twelve(Sub124060QcAnalyzer.Result r,Random g){
        GmtRoundMarkerAnalyzer.DialFrame f=r.frame;
        SubTwelveTriangle.Result tr=new SubTwelveTriangle.Result();
        r.triangles=tr;
        if(g.nextInt(6)==0){
            r.triangleReason=g.nextBoolean()?"no triangle-shaped outline near 12":"no candidate passed the 12-triangle plausibility checks (best: apex)";
            withholdTwelve(r,0);
            return;
        }
        SubTwelveTriangle.Cand c=new SubTwelveTriangle.Cand();
        double w=0.05*f.r*(0.6+0.8*g.nextDouble());
        double tx=f.cx+(g.nextDouble()-0.5)*4,ty=f.cy-0.78*f.r;
        c.L=new double[]{tx-w/2,ty-w};c.R=new double[]{tx+w/2,ty-w};c.T=new double[]{tx,ty};
        c.cx=g.nextInt(40)==0?Double.NaN:tx;c.cy=ty-w/2;
        c.rho=0.78;c.dthetaDeg=g.nextGaussian()*0.5;c.widthR=w/f.r;c.heightR=w/f.r;c.apex=44+g.nextGaussian();c.score=g.nextDouble();
        c.outline=g.nextInt(6)==0?"inner":"outer";c.fit=g.nextBoolean()?"refined":"contour";
        boolean tick=g.nextInt(8)!=0;
        c.tickAngle=tick?g.nextGaussian():Double.NaN;
        c.trackR=g.nextInt(8)!=0?0.86*f.r:Double.NaN;c.trackSpreadR=0.004;c.tickPitch=6.0;
        c.rotationDeg=tick||g.nextInt(3)==0?g.nextGaussian()*2:Double.NaN;
        boolean track=g.nextInt(7)!=0;
        c.gapR=track?0.01+0.05*g.nextDouble():Double.NaN;
        c.centring=track&&g.nextInt(10)!=0?(g.nextDouble()-0.5)*0.2:Double.NaN;
        c.tickRaw60=g.nextInt(10)!=0?new double[]{tx,f.cy-0.86*f.r}:null;
        c.tickRaw59=new double[]{tx-0.09*f.r,f.cy-0.855*f.r};c.tickRaw01=new double[]{tx+0.09*f.r,f.cy-0.855*f.r};
        tr.cands.add(c);
        r.triangle=c;
        r.lumeOutline="inner".equals(c.outline);
        r.tick60=Double.isFinite(c.trackR)&&Double.isFinite(c.tickAngle)?f.at(-Math.PI/2+Math.toRadians(c.tickAngle),0.86):c.tickRaw60;
        r.tick59=c.tickRaw59;r.tick01=c.tickRaw01;

        int chain=g.nextInt(10);
        if(chain==1){r.tooSmall=true;withholdTwelve(r,1);return;}
        if(chain==2&&r.tick60!=null){r.handAtTwelve=true;withholdTwelve(r,2);return;}
        // checkTriangleResize
        boolean same=g.nextInt(8)!=0;
        r.triangleResizeStable=same;r.triangleResizeNote=same?"94%: same outline, 88%: same outline":"94%: same outline, 88%: a different outline";
        if(!same){withholdTwelve(r,3);return;}
        r.rotationResizeStable=resize(g,c.rotationDeg,v->{r.rotationMin=v[0];r.rotationMax=v[1];r.rotationShiftPx=v[2];});
        r.gapResizeStable=resize(g,c.gapR,v->{r.gapMin=v[0];r.gapMax=v[1];r.gapShiftPx=v[2];});
        r.centringResizeStable=resize(g,c.centring,v->{r.centringMin=v[0];r.centringMax=v[1];r.centringShiftPx=v[2];});
        if(r.dialSource==Sub124060QcAnalyzer.DialSource.MANUAL_CIRCLE){withholdTwelve(r,4);return;}
        if(Boolean.FALSE.equals(r.dialReproducible)){withholdTwelve(r,5);return;}

        if(Double.isFinite(c.tickAngle)&&Double.isFinite(c.rotationDeg)){
            if(Boolean.FALSE.equals(r.rotationResizeStable)){r.rotationWithheld=resizeReason("rotation",r.rotationShiftPx);code(r,"rotationWithheldCode","sub12.rotation_resize_unstable");}
            else r.rotationDeg=c.rotationDeg;
        }else{r.rotationWithheld="the 60-minute tick was not located";code(r,"rotationWithheldCode","sub12.minute_tick_not_found");}
        if(r.lumeOutline){
            r.gapWithheld=r.centringWithheld="only the inner (lume) outline of the triangle was found";
            code(r,"gapWithheldCode","sub12.lume_outline_only");code(r,"centringWithheldCode","sub12.lume_outline_only");
        }else if(!Double.isFinite(c.gapR)||!Double.isFinite(c.centring)){
            r.gapWithheld=r.centringWithheld="the minute track next to the 12 was not located";
            code(r,"gapWithheldCode","sub12.minute_track_not_found");code(r,"centringWithheldCode","sub12.minute_track_not_found");
        }else{
            if(Boolean.FALSE.equals(r.gapResizeStable)){r.gapWithheld=resizeReason("gap",r.gapShiftPx);code(r,"gapWithheldCode","sub12.gap_resize_unstable");}
            else r.gapR=c.gapR;
            if(Boolean.FALSE.equals(r.centringResizeStable)){r.centringWithheld=resizeReason("centring",r.centringShiftPx);code(r,"centringWithheldCode","sub12.centring_resize_unstable");}
            else r.centringW=c.centring;
        }
    }

    interface Range{void set(double[] minMaxShift);}

    /** A resize re-measurement: not reproduced, moved by more than a pixel, or repeatable. */
    static Boolean resize(Random g,double v,Range out){
        if(!Double.isFinite(v)||g.nextInt(12)==0)return false;
        double shift=g.nextDouble()*2.5,spread=Math.abs(v)*0.01+0.001;
        out.set(new double[]{v-spread,v+spread,shift});
        return MeasurementRepeatability.stable(shift);
    }

    static String resizeReason(String what,double shiftPx){
        return Double.isFinite(shiftPx)
                ?String.format(Locale.US,"the %s measurement moves by %.1f px when the photo is reduced by 6%% and 12%%",what,shiftPx)
                :"the "+what+" measurement is not reproduced at both resize scales";
    }

    static void withholdTwelve(Sub124060QcAnalyzer.Result r,int which){
        r.twelveWithheld=TWELVE_MESSAGES[which];code(r,"twelveWithheldCode",TWELVE_CODES[which]);
        r.rotationWithheld=r.gapWithheld=r.centringWithheld=r.twelveWithheld;
        code(r,"rotationWithheldCode",TWELVE_CODES[which]);code(r,"gapWithheldCode",TWELVE_CODES[which]);code(r,"centringWithheldCode",TWELVE_CODES[which]);
    }

    static void batons(Sub124060QcAnalyzer.Result r,Random g){
        boolean twelve=r.tick60!=null;
        for(GmtSixLandmarkAnalyzer.Position p:Sub124060Layout.BATONS){
            Sub124060QcAnalyzer.Baton b=new Sub124060QcAnalyzer.Baton(p);
            int k=g.nextInt(12);
            if(k==0){b.result=new GmtSixLandmarkAnalyzer.Result(p.label+" baton not found");b.status=Sub124060QcAnalyzer.Status.NOT_FOUND;b.note=b.result.reason;}
            else{
                b.result=batonResult(r.frame,p,g);
                if(k==1){b.status=Sub124060QcAnalyzer.Status.WRONG_PLACE;b.note="the outline found is 12° from where the "+p.label+" baton sits relative to the 12 (photo turned?)";}
                else if(k==2){b.status=Sub124060QcAnalyzer.Status.HAND;b.note=g.nextBoolean()?"a hand is next to it":"a hand lies across it";}
                else if(k==3){b.status=Sub124060QcAnalyzer.Status.LOW_CONFIDENCE;b.note="tick pitch out of range";}
                else if(!twelve){b.status=Sub124060QcAnalyzer.Status.LOW_CONFIDENCE;b.note="the 12 was not found, so the dial orientation is unknown";}
                else{
                    b.status=Sub124060QcAnalyzer.Status.FOUND;
                    stability(b.result,g);
                    if(!Sub124060QcAnalyzer.batonRepeatable(b.result))b.note="resize diagnostic only: "+Sub124060QcAnalyzer.batonResizeReason(b.result)+"; no 124060 marker tolerance uses this yet";
                }
            }
            r.batons.add(b);
        }
    }

    static GmtSixLandmarkAnalyzer.Result batonResult(GmtRoundMarkerAnalyzer.DialFrame f,GmtSixLandmarkAnalyzer.Position p,Random g){
        double phi=-Math.PI/2+Math.toRadians(p.angleFromTwelveDeg)+Math.toRadians(g.nextGaussian()*0.3);
        double[] c=f.at(phi,0.80);
        double ux=Math.cos(phi),uy=Math.sin(phi),vx=-uy,vy=ux,len=0.12*f.r,wid=0.035*f.r;
        double[] ol={c[0]+ux*len/2+vx*wid/2,c[1]+uy*len/2+vy*wid/2},or={c[0]+ux*len/2-vx*wid/2,c[1]+uy*len/2-vy*wid/2};
        double[] il={c[0]-ux*len/2+vx*wid/2,c[1]-uy*len/2+vy*wid/2},ir={c[0]-ux*len/2-vx*wid/2,c[1]-uy*len/2-vy*wid/2};
        boolean outer=g.nextInt(10)!=0;
        if(g.nextInt(20)==0)ol=null;
        double[] t29=f.at(phi-0.1,0.86),t30=f.at(phi,0.86),t31=f.at(phi+0.1,0.86);
        GmtSixLandmarkAnalyzer.Geometry geo=g.nextInt(30)==0?null:new GmtSixLandmarkAnalyzer.Geometry(ol,or,il,ir,t31,t30,t29,outer);
        return new GmtSixLandmarkAnalyzer.Result(0.05,g.nextGaussian()*0.03,g.nextGaussian(),wid,len,g.nextInt(6)!=0,geo);
    }

    static void stability(GmtSixLandmarkAnalyzer.Result b,Random g){
        if(g.nextInt(15)==0)return;
        b.stabilityRun=true;b.stabilitySameEdge=g.nextInt(8)!=0;
        double cs=g.nextDouble()*2.2/b.widthPx,rs=Math.toDegrees(Math.atan(g.nextDouble()*2.2/b.lengthPx));
        b.centringMin=b.centring-cs/2;b.centringMax=b.centring+cs/2;b.rotMin=b.rotationDeg-rs/2;b.rotMax=b.rotationDeg+rs/2;
        if(g.nextInt(25)==0)b.rotMax=Double.NaN;
    }

    static void rounds(Sub124060QcAnalyzer.Result r,Random g){
        GmtRoundMarkerAnalyzer.DialFrame f=r.frame;
        int pattern=g.nextInt(4);
        for(int h:Sub124060Layout.ROUND_HOURS){
            GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);
            double phi=-Math.PI/2+Math.toRadians(30.0*h+g.nextGaussian()*(pattern==0?3:0.8));
            double[] q=f.at(phi,0.817*(1+0.01*g.nextGaussian()));
            m.seedX=q[0];m.seedY=q[1];m.expectedRadiusPx=0.086*f.r;
            int k=g.nextInt(pattern==1?3:10);
            if(k==0){m.found=false;m.reason="no circular outline at the "+h;r.rounds.add(new Sub124060QcAnalyzer.Round(m,Sub124060QcAnalyzer.Status.NOT_FOUND,m.reason));continue;}
            m.found=true;m.x=q[0];m.y=q[1];m.radiusPx=0.085*f.r;m.offset=g.nextGaussian()*0.02;m.inset=0.1;m.rejectFraction=0.05;
            m.tickBefore=f.at(phi-0.05,0.86);m.tickCentre=f.at(phi,0.86);m.tickAfter=f.at(phi+0.05,0.86);
            if(k==1){m.hand=true;r.rounds.add(new Sub124060QcAnalyzer.Round(m,Sub124060QcAnalyzer.Status.HAND,"a hand is over or next to it"));continue;}
            if(k==2){m.stable=false;m.lowReason="outline traced on the lume edge";r.rounds.add(new Sub124060QcAnalyzer.Round(m,Sub124060QcAnalyzer.Status.LOW_CONFIDENCE,m.lowReason));continue;}
            m.stable=true;
            if(g.nextInt(10)!=0){
                m.stabilityRun=true;m.stabilitySameEdge=g.nextInt(6)!=0;
                double s=g.nextDouble()*(pattern==2?2.5:1.4)/m.diameterPx();
                m.offMin=m.offset-s/2;m.offMax=g.nextInt(30)==0?Double.NaN:m.offset+s/2;
            }
            String note="";
            if(!Sub124060QcAnalyzer.roundOffsetRepeatable(m))note="resize diagnostic only: "+Sub124060QcAnalyzer.roundResizeReason(m)+"; no 124060 marker tolerance uses this yet";
            if(m.stabilityRun&&!m.stabilitySameEdge){m.sizeRatio=Double.NaN;note=note.isEmpty()?"centre measured; outline edge identity changes under resize, so size is not compared":note+"; outline edge identity also changes, so size is not compared";}
            r.rounds.add(new Sub124060QcAnalyzer.Round(m,Sub124060QcAnalyzer.Status.FOUND,note));
        }
    }

    static void pose(Sub124060QcAnalyzer.Result r,Random g){
        if(g.nextInt(10)==0)return;
        GmtMarkerPose.Result p=new GmtMarkerPose.Result();
        p.valid=g.nextInt(4)!=0;p.reason=p.valid?"":"fewer than five round markers";
        if(p.valid){p.tiltDeg=g.nextDouble()*12;p.tiltHighDeg=p.tiltDeg+1;p.markers=6;p.residual=0.003;}
        r.markerPose=p;
    }

    // ------------------------------------------------------------------------------------ free
    /** Every field drawn on its own: consistency is not assumed (one baton per position at most). */
    static Sub124060QcAnalyzer.Result free(Random g){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        Sub124060QcAnalyzer.DialSource[] ds=Sub124060QcAnalyzer.DialSource.values();
        r.dialSource=ds[g.nextInt(ds.length)];
        if(g.nextBoolean()){int i=g.nextInt(4);r.dialReason=new String[]{"watch image missing","analysis failed closed: X","no dial found in the photo (x)","dial location confidence is too low"}[i];
            code(r,"dialReasonCode",new String[]{"core.image_unreadable","core.analysis_exception","sub12.dial_not_found","sub12.dial_seed_low_confidence"}[i]);}
        double cx=500,cy=500,rad=300;
        if(g.nextInt(4)!=0)r.frame=g.nextInt(8)==0?GmtRoundMarkerAnalyzer.DialFrame.circle(cx,cy,rad):new GmtRoundMarkerAnalyzer.DialFrame(cx,cy,rad*1.01,rad*0.99,10);
        if(g.nextInt(3)!=0)r.edge=new DialEdgeEllipseFit.Fit(cx,cy,rad*1.01,rad*0.99,10,160,180,0.8);
        r.dialReproducible=g.nextInt(3)==0?null:g.nextBoolean();
        GmtRoundMarkerAnalyzer.DialFrame f=r.frame!=null?r.frame:new GmtRoundMarkerAnalyzer.DialFrame(cx,cy,rad,rad,0);
        if(g.nextInt(4)!=0){
            SubTwelveTriangle.Cand c=new SubTwelveTriangle.Cand();
            c.L=new double[]{490,250};c.R=new double[]{510,250};c.T=new double[]{500,270};c.cx=g.nextInt(20)==0?Double.NaN:500;c.cy=260;
            c.outline=g.nextBoolean()?"inner":"outer";c.tickAngle=g.nextBoolean()?0.2:Double.NaN;c.rotationDeg=g.nextBoolean()?0.5:Double.NaN;
            c.gapR=g.nextBoolean()?0.03:Double.NaN;c.centring=g.nextBoolean()?0.01:Double.NaN;c.widthR=0.07;c.heightR=0.07;
            r.triangle=c;r.lumeOutline=g.nextBoolean();
        }
        if(g.nextBoolean())r.tick60=new double[]{500,240};
        String[] w={null,"12 triangle not found","the 12 triangle is only 31 px wide in this photo (minimum 34)"};
        String[] wc={null,"sub12.triangle_not_found","sub12.triangle_too_small"};
        int i=g.nextInt(3);r.twelveWithheld=w[i];code(r,"twelveWithheldCode",wc[i]);
        i=g.nextInt(3);r.rotationWithheld=w[i];code(r,"rotationWithheldCode",wc[i]);
        i=g.nextInt(3);r.gapWithheld=w[i];code(r,"gapWithheldCode",wc[i]);
        i=g.nextInt(3);r.centringWithheld=w[i];code(r,"centringWithheldCode",wc[i]);
        r.rotationDeg=g.nextBoolean()?g.nextGaussian():Double.NaN;r.gapR=g.nextBoolean()?0.03:Double.NaN;r.centringW=g.nextBoolean()?0.01:Double.NaN;
        Boolean[] tri={null,Boolean.TRUE,Boolean.FALSE};
        r.rotationResizeStable=tri[g.nextInt(3)];r.gapResizeStable=tri[g.nextInt(3)];r.centringResizeStable=tri[g.nextInt(3)];
        List<GmtSixLandmarkAnalyzer.Position> ps=new ArrayList<>(java.util.Arrays.asList(Sub124060Layout.BATONS));
        Collections.shuffle(ps,g);
        Sub124060QcAnalyzer.Status[] st=Sub124060QcAnalyzer.Status.values();
        for(GmtSixLandmarkAnalyzer.Position p:ps){
            if(g.nextInt(6)==0)continue;
            Sub124060QcAnalyzer.Baton b=new Sub124060QcAnalyzer.Baton(p);
            b.status=st[g.nextInt(st.length)];b.note=g.nextBoolean()?"":"some note";
            if(g.nextInt(5)!=0){b.result=g.nextInt(6)==0?new GmtSixLandmarkAnalyzer.Result("x"):batonResult(f,p,g);if(b.result.valid)stability(b.result,g);}
            r.batons.add(b);
        }
        for(int h:Sub124060Layout.ROUND_HOURS){
            if(g.nextInt(8)==0)continue;
            GmtRoundMarkerAnalyzer.Marker m=g.nextInt(20)==0?null:new GmtRoundMarkerAnalyzer.Marker(h);
            Sub124060QcAnalyzer.Status s=st[g.nextInt(st.length)];
            if(m!=null){
                double[] q=f.at(-Math.PI/2+Math.toRadians(30.0*h),0.817);
                m.found=g.nextInt(4)!=0;m.x=g.nextInt(30)==0?Double.NaN:q[0];m.y=q[1];m.radiusPx=25;m.seedX=q[0];m.seedY=q[1];m.expectedRadiusPx=26;
                if(g.nextBoolean()){m.stabilityRun=true;m.stabilitySameEdge=g.nextBoolean();m.offMin=0;m.offMax=g.nextDouble()*0.05;}
            }
            if(m==null)continue;   // the overlay and summary dereference every marker; a missing one is not a valid analyser state
            r.rounds.add(new Sub124060QcAnalyzer.Round(m,s,g.nextBoolean()?"":"note"));
        }
        pose(r,g);
        return r;
    }

    /** Set an analyser reason-code field when this build has it (the v1 checkpoint does not). */
    static void code(Sub124060QcAnalyzer.Result r,String field,String value){
        try{
            Field f=Sub124060QcAnalyzer.Result.class.getDeclaredField(field);
            f.setAccessible(true);f.set(r,value);
        }catch(NoSuchFieldException e){
            // v1 checkpoint: no reason codes
        }catch(IllegalAccessException e){
            throw new IllegalStateException(e);
        }
    }
}
