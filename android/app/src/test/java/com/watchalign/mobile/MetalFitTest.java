package com.watchalign.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** 1.6.3 outer-metal fit (MetalFit) on synthetic dials: a known move and turn are recovered from the fixed genuine
 *  outline, a hand-like bar only removes the edge it covers, too little edge is Can't check, and an uncertain edge
 *  over a limit is "Possible misalignment - inspect", never a pass. */
public class MetalFitTest {
    static final double RPX=400,S=900;
    /** canonical -> px: centre (450, 450), radius RPX */
    static final double[] H={RPX,0,S/2, 0,RPX,S/2, 0,0,1};

    static double[][] circle(double cx,double cy,double r){double[][] p=new double[96][];for(int i=0;i<96;i++){double a=2*Math.PI*i/96;p[i]=new double[]{cx+r*Math.cos(a),cy+r*Math.sin(a)};}return p;}
    static double[][] rect(double cx,double cy,double hw,double hh){return new double[][]{{cx-hw,cy-hh},{cx+hw,cy-hh},{cx+hw,cy+hh},{cx-hw,cy+hh}};}
    static double[][] moved(double[][] p,double tx,double ty,double th){double[] c=ClearlyOff.mean(p);return MetalFit.pose(p,c,th,tx,ty);}

    /** a synthetic photo (pure Java): dark dial (15), polygons painted in order, 4x4 supersampled (a soft edge) */
    static final class Pic {
        final int w=(int)S,h=(int)S;final float[] px=new float[w*h];final List<double[][]> polys=new ArrayList<>();final List<Double> vals=new ArrayList<>();
        Pic add(double[][] p,double v){polys.add(p);vals.add(v);return this;}
        ClearlyOff.Img img(){
            java.util.Arrays.fill(px,15f);
            double x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;
            for(double[][] p:polys)for(double[] q:p){x0=Math.min(x0,q[0]);y0=Math.min(y0,q[1]);x1=Math.max(x1,q[0]);y1=Math.max(y1,q[1]);}
            int i0=(int)Math.max(0,H[0]*x0+H[2]-4),i1=(int)Math.min(w-1,H[0]*x1+H[2]+4),j0=(int)Math.max(0,H[4]*y0+H[5]-4),j1=(int)Math.min(h-1,H[4]*y1+H[5]+4);
            for(int j=j0;j<=j1;j++)for(int i=i0;i<=i1;i++){double sum=0;
                for(int a=0;a<4;a++)for(int b=0;b<4;b++){double x=((i+(a+0.5)/4-0.5)-H[2])/H[0],y=((j+(b+0.5)/4-0.5)-H[5])/H[4];double v=15;
                    for(int k=0;k<polys.size();k++)if(inside(polys.get(k),x,y))v=vals.get(k);sum+=v;}
                px[j*w+i]=(float)(sum/16);}
            return new ClearlyOff.Img(px,w,h,H);
        }
    }
    static boolean inside(double[][] p,double x,double y){boolean in=false;
        for(int i=0,j=p.length-1;i<p.length;j=i++)if((p[i][1]>y)!=(p[j][1]>y)&&x<(p[j][0]-p[i][0])*(y-p[i][1])/(p[j][1]-p[i][1])+p[i][0])in=!in;return in;}
    /** the marker: bright metal rim (180, 0.008 R) around lume (230) */
    static Pic dial(double[][] metal){return new Pic().add(metal,180).add(shrink(metal,0.008),230);}
    static double[][] shrink(double[][] p,double d){double[] c=ClearlyOff.mean(p);double[][] o=new double[p.length][];
        for(int i=0;i<p.length;i++){double dx=p[i][0]-c[0],dy=p[i][1]-c[1],n=Math.hypot(dx,dy);o[i]=new double[]{c[0]+dx*(1-d/n),c[1]+dy*(1-d/n)};}return o;}

    @Test public void recoversAKnownMoveFromTheFixedOutline(){
        double[][] gen=circle(0.40,-0.69,0.05);
        MetalFit.Fit f=MetalFit.fit(dial(moved(gen,0.006,-0.004,0)).img(),gen,ModelSpec.Shape.ROUND,RPX,null,0);
        assertTrue(f.ok());assertTrue(f.uncertain,f.uncertain.isEmpty());
        assertEquals(0.40+0.006,f.cObs[0],0.0006);assertEquals(-0.69-0.004,f.cObs[1],0.0006);
    }

    @Test public void recoversATurnOfABaton(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        MetalFit.Fit f=MetalFit.fit(dial(moved(gen,0,0,Math.toRadians(3))).img(),gen,ModelSpec.Shape.BATON,RPX,null,0);
        assertTrue(f.ok());assertEquals(3.0,f.tiltDeg,0.15);
    }

    @Test public void aHandOnlyRemovesTheEdgeItCovers(){
        double[][] gen=circle(0.69,0.40,0.05);
        // a polished hand (bright) across a third of the marker
        Pic g=dial(moved(gen,0.005,0,0)).add(new double[][]{{0.60,0.415},{0.80,0.415},{0.80,0.455},{0.60,0.455}},200);
        MetalFit.Fit f=MetalFit.fit(g.img(),gen,ModelSpec.Shape.ROUND,RPX,null,0);
        assertTrue(f.note,f.ok());assertTrue(f.cover<0.95);
        // within 0.3 % R (about 1 px here; the position limit is 1 %): the bar's own edge pulls slightly where it meets the rim
        assertEquals(0.69+0.005,f.cObs[0],0.003);assertEquals(0.40,f.cObs[1],0.003);
        boolean hidden=false;for(byte s:f.status)if(s!=MetalFit.SEEN)hidden=true;
        assertTrue("the covered part is drawn as not seen",hidden);
    }

    /** Owner, 20 Oct 2026 (genuine Yacht-Master, seconds hand along the 6 baton): a hand lying along a marker hides most
     *  of its outline; the marker is Can't check, not reconstructed from what is left and judged. */
    @Test public void aHandAlongAMarkerIsCantCheck(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        List<double[]> along=new ArrayList<>();along.add(new double[]{180,10,0.95});   // a hand down the 6 o'clock line, wider than the baton
        MetalFit.Fit f=MetalFit.fit(dial(gen).img(),gen,ModelSpec.Shape.BATON,RPX,along,0);
        assertFalse(f.ok());
        assertTrue(f.note,f.note.equals(MetalFit.HAND_COVERS)||f.note.equals("too little of its outer edge is visible"));
        // the same hand passing beside it hides nothing of it: measured as before
        List<double[]> beside=new ArrayList<>();beside.add(new double[]{200,4,0.95});
        MetalFit.Fit g=MetalFit.fit(dial(gen).img(),gen,ModelSpec.Shape.BATON,RPX,beside,0);
        assertTrue(g.note,g.ok());assertTrue(g.handFrac<MetalFit.HAND_HIDDEN_CANT);
    }

    /** Owner's Submariner Date (20 Oct 2026): the radial hand finder followed the seconds tail out to the 5 marker, where
     *  the independent per-marker check found no hand. Without that confirmation the marker is measured, not withheld. */
    @Test public void aHandOnlyTheRadialFinderSeesDoesNotWithholdTheMarker(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        List<double[]> along=new ArrayList<>();along.add(new double[]{180,10,0.95});
        MetalFit.Fit f=MetalFit.fit(dial(gen).img(),gen,ModelSpec.Shape.BATON,RPX,along,0,Double.NaN,false);
        assertNotEquals(MetalFit.HAND_COVERS,f.note);
        assertTrue(MetalFit.handConfirmed(new ClearlyOff.Input(6,ModelSpec.Shape.BATON,gen,false,"hand crosses or touches this marker")));
        assertFalse(MetalFit.handConfirmed(new ClearlyOff.Input(6,ModelSpec.Shape.BATON,gen,true,"")));
        assertFalse(MetalFit.handConfirmed(new ClearlyOff.Input(6,ModelSpec.Shape.BATON,gen,null,null)));
    }

    @Test public void aHandOverLessThanHalfOfAMarkerLeavesItMeasured(){
        double[] c=ClearlyOff.cpt(0.81,Math.toRadians(60));double[][] gen=circle(c[0],c[1],0.045);
        // a narrow hand past the round marker's edge: it hides a strip of the outline, not most of it
        List<double[]> side=new ArrayList<>();side.add(new double[]{62.5,1,1.2});   // reaching past it (a hand tip over it is widened)
        MetalFit.Fit f=MetalFit.fit(dial(gen).img(),gen,ModelSpec.Shape.ROUND,RPX,side,0);
        assertTrue(f.note,f.ok());assertTrue("hand share "+f.handFrac,f.handFrac>0&&f.handFrac<MetalFit.HAND_HIDDEN_CANT);
    }

    @Test public void tooLittleEdgeIsCantCheck(){
        double[][] gen=circle(0.69,0.40,0.05);
        MetalFit.Fit f=MetalFit.fit(new Pic().add(circle(0,0,0.001),15).img(),gen,   // nothing there
                ModelSpec.Shape.ROUND,RPX,null,0);
        assertFalse(f.ok());assertFalse(f.note.isEmpty());
    }

    @Test public void handMaskIsTheHandsWidthNotItsAngle(){
        // a hand 10 deg wide near the pivot is about 0.03 R wide; out at 0.8 R it covers about +-2.2 deg plus the margin,
        // not the whole 10 deg sector
        List<double[]> hands=new ArrayList<>();hands.add(new double[]{60,10,0.95});
        assertTrue(MetalFit.underHand(ClearlyOff.cpt(0.8,Math.toRadians(60)),hands));
        assertTrue(MetalFit.underHand(ClearlyOff.cpt(0.8,Math.toRadians(62)),hands));
        assertFalse(MetalFit.underHand(ClearlyOff.cpt(0.8,Math.toRadians(64.5)),hands));
        assertFalse("beyond its reach",MetalFit.underHand(ClearlyOff.cpt(0.99,Math.toRadians(60)),hands));
    }

    @Test public void uncertainEdgeOverALimitIsInspectNeverAPass(){
        ClearlyOff.Marker r=new ClearlyOff.Marker(4,ModelSpec.Shape.ROUND);r.sidePct=1.6;r.inoutPct=0.2;
        r.uncertain="its visible edge is rough (1.0 px)";
        ClearlyOff.decide(r,1e9,null);
        assertEquals(ClearlyOff.State.POSSIBLE,r.state);assertTrue(r.reason.contains("rough"));
        r.uncertain="";ClearlyOff.decide(r,1e9,null);assertEquals(ClearlyOff.State.CLEARLY_OFF,r.state);
        r.sidePct=0.3;r.uncertain="only 40% of its outline is visible";ClearlyOff.decide(r,1e9,null);
        assertEquals("under the limits an uncertain edge is still no visible issue",ClearlyOff.State.NO_VISIBLE_ISSUE,r.state);
        // a saved photo keeps it
        Calibration.Record rec=new Calibration.Record();rec.id="1";rec.model="gmt_126710";rec.rpx=1e9;
        Calibration.Mark k=new Calibration.Mark();k.hour=4;k.shape=ModelSpec.Shape.ROUND;k.side=1.6;k.inout=0.2;k.uncertain="its visible edge is rough (1.0 px)";rec.marks.add(k);
        Calibration.Record back=Calibration.recordFromJson(Calibration.toJson(rec));
        assertEquals(ClearlyOff.State.POSSIBLE,Calibration.state(back,back.at(4),ClearlyOff.Limits.defaults()));
        assertTrue(Calibration.flagged(ClearlyOff.State.POSSIBLE));
    }

    // ---- 1.6.6: the one-side refit beside a confirmed hand (owner's genuine Submariner Date, 6 baton) ----------------

    /** a steel seconds hand (120: brighter than the dial, darker than the rim) from the pivot converging on the 6 baton's right edge, gapPx outside it at the baton's outer end;
     *  the baton moved dx sideways */
    static Pic secondsBeside(double dx,double gapPx){
        double xe=0.025+dx+gapPx/RPX+0.002,ang=Math.atan2(xe,0.83),w=0.0025;
        return dial(moved(rect(0,0.75,0.025,0.08),dx,0,0))
                .add(new double[][]{{-w,0},{w,0},{xe+w,0.83},{xe-w,0.83}},120)
                .add(new double[][]{{xe-w,0.83},{xe+w,0.83},{Math.tan(ang)*0.97+w,0.97},{Math.tan(ang)*0.97-w,0.97}},120);
    }
    /** the detected seconds line ~1.5 deg inside the hand, as on the owner's photo (its mask then misses the edge) */
    static double detected(double dx,double gapPx){return 180-Math.toDegrees(Math.atan2(0.025+dx+gapPx/RPX+0.002,0.83))+1.5;}

    @Test public void aHandBesideABatonPullsTheFitAndTheRefitWithoutThatSideRecoversIt(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        MetalFit.Fit f=MetalFit.fit(secondsBeside(0,2.5).img(),gen,ModelSpec.Shape.BATON,RPX,null,0,detected(0,2.5),true);
        assertTrue(f.ok());assertFalse("the pulled fit is flagged",f.uncertain.isEmpty());
        assertTrue("pulled towards the hand",f.cObs[0]>0.003);
        assertNotNull(f.alt);assertEquals(1,f.altSide);                                  // rect side 1 = its right side
        assertTrue(f.alt.uncertain,f.alt.uncertain.isEmpty());
        assertEquals(0.0,f.alt.cObs[0],0.001);assertEquals(0.75,f.alt.cObs[1],0.001);assertEquals(0.0,f.alt.tiltDeg,0.1);
    }

    @Test public void aRealMoveBesideAHandIsStillMeasuredByTheRefit(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        MetalFit.Fit f=MetalFit.fit(secondsBeside(0.012,2.5).img(),gen,ModelSpec.Shape.BATON,RPX,null,0,detected(0.012,2.5),true);
        assertNotNull(f.alt);
        assertEquals("a 1.2 % R move is not hidden by leaving out the hand's side",0.012,f.alt.cObs[0],0.001);
    }

    @Test public void noRefitWithoutTheIndependentHandCheck(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        MetalFit.Fit f=MetalFit.fit(secondsBeside(0,2.5).img(),gen,ModelSpec.Shape.BATON,RPX,null,0,detected(0,2.5),false);
        assertNull(f.alt);
        // nor for a clean fit, nor for a round marker
        MetalFit.Fit c=MetalFit.fit(dial(gen).img(),gen,ModelSpec.Shape.BATON,RPX,null,0,detected(0,2.5),true);
        assertTrue(c.uncertain.isEmpty());assertNull(c.alt);
    }

    @Test public void aSideIsBesideAHandOnlyWhenTheHandRunsAlongIt(){
        double[][] p=rect(0,0.75,0.025,0.08);
        assertEquals(-1,MetalFit.sideBesideHand(p,null,90));                 // crossing at right angles
        assertEquals(-1,MetalFit.sideBesideHand(p,null,180));                // down its middle: both long sides alike
        List<double[]> hands=new ArrayList<>();hands.add(new double[]{178.5,2,0.95});
        assertEquals(1,MetalFit.sideBesideHand(p,hands,Double.NaN));          // an hour hand along its right side
        hands.clear();hands.add(new double[]{178.5,2,0.40});
        assertEquals("a hand that does not reach it",-1,MetalFit.sideBesideHand(p,hands,Double.NaN));
    }

    /** the trap: a bright reflection evenly along the OTHER long side moves the clean-looking refit (a uniform offset
     *  of the one remaining long side is indistinguishable from a move). The independent luminous-centre cross-check
     *  that catches it needs OpenCV: tools/desktop-harness/drivers/RefitTraps.java (three-options CI). */
    @Test public void aMisleadingEdgeOnTheRemainingSideFoolsTheRefitAlone(){
        double[][] gen=rect(0,0.75,0.025,0.08);
        double xl=-0.025-2.0/RPX-0.002;
        Pic p=secondsBeside(0,2.5).add(new double[][]{{xl-0.0025,0.66},{xl+0.0025,0.66},{xl+0.0025,0.84},{xl-0.0025,0.84}},200);
        MetalFit.Fit f=MetalFit.fit(p.img(),gen,ModelSpec.Shape.BATON,RPX,null,0,detected(0,2.5),true);
        assertNotNull(f.alt);assertTrue(f.alt.uncertain.isEmpty());
        assertTrue("the refit alone is fooled",Math.abs(f.alt.cObs[0])>0.005);
    }
}
