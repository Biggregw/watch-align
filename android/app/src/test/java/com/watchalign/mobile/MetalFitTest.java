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
}
