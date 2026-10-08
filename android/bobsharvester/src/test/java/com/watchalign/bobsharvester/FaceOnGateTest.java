package com.watchalign.bobsharvester;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Synthetic product photos in Bob's framing (white background, watch head filling the width, black bezel insert with
 * numerals, dial with indices), face-on and tilted. On 48 real, manually reviewed face-on Bob's 124060 photos the gate
 * accepts 48 (v1.3: 5); simulated 30-45 deg tilts of them are all rejected (desktop check, images not committed).
 */
public class FaceOnGateTest {
    /** grey image of a watch head; tiltDeg squeezes it horizontally by cos(tilt) about the centre */
    static int[] watch(int w,int h,double tiltDeg){
        int[] g=new int[w*h];double cx=w/2.0,cy=h/2.0,c=Math.cos(Math.toRadians(tiltDeg)),R=w*0.47;
        java.util.Random rnd=new java.util.Random(7);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-cx)/c,dy=y-cy,r=Math.hypot(dx,dy),a=Math.atan2(dy,dx);int v=250;
            if(Math.abs(dx)<R*0.62&&Math.abs(dy)>R*0.9)v=185;                       // bracelet
            if(r<R)v=170;                                                           // steel bezel edge
            if(r<R*0.95)v=25;                                                       // black bezel insert
            if(r<R*0.95&&r>R*0.80&&(int)Math.floor((a+Math.PI)/(2*Math.PI)*60)%5==0&&r<R*0.9)v=230;   // bezel markings
            if(r<R*0.74)v=120;                                                      // crystal / rehaut
            if(r<R*0.70)v=18;                                                       // black dial
            if(r<R*0.62&&r>R*0.50&&(int)Math.floor((a+Math.PI)/(2*Math.PI)*12*4)%4==0)v=235;        // indices
            g[y*w+x]=Math.max(0,Math.min(255,v+(int)(rnd.nextGaussian()*3)));
        }
        return g;
    }
    static FaceOnGate.Result run(double tilt){int w=556,h=850;return FaceOnGate.assess(watch(w,h,tilt),w,h,1412.0/w,1412,2160);}

    @Test public void faceOnIsAccepted(){FaceOnGate.Result r=run(0);assertTrue(r.summary(),r.ok);assertTrue(r.axis>0.97);}
    @Test public void mildTiltIsAccepted(){assertTrue(run(12).summary(),run(12).ok);}
    @Test public void thirtyDegreeTiltIsRejectedWithAReason(){FaceOnGate.Result r=run(30);assertFalse(r.summary(),r.ok);assertTrue(r.reason,r.reason.contains("not face-on"));}
    @Test public void fortyFiveDegreeTiltIsRejected(){assertFalse(run(45).ok);}
    @Test public void smallImageIsRejectedWithAReason(){int[] g=new int[300*400];FaceOnGate.Result r=FaceOnGate.assess(g,300,400,1,300,400);assertFalse(r.ok);assertTrue(r.reason.contains("too small"));}
    @Test public void blankImageIsRejectedWithAReason(){int w=556,h=850;int[] g=new int[w*h];java.util.Arrays.fill(g,240);FaceOnGate.Result r=FaceOnGate.assess(g,w,h,2.5,1412,2160);assertFalse(r.ok);assertFalse(r.reason.isEmpty());}
}
