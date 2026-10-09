package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Alpha104: the hour markers vote on the minute branch of the pose (synthetic dial: bright markers on a dark dial). */
public class Alpha104MarkerBranchTest {
    static final double R=200,CX=300,CY=300;
    /** canonical -> image: scale R, centre (CX, CY) */
    static final double[] H={R,0,CX,0,R,CY,0,0,1};

    /** the model's markers painted bright (round discs / baton and triangle boxes approximated by their centre discs) */
    static Alpha99MarkerInterference.Sampler dial(ModelSpec m){
        return (px,py)->{
            double x=(px-CX)/R,y=(py-CY)/R;
            for(ModelSpec.Marker mk:m.markers){double[] c=mk.masterPoint();
                double rad=mk.shape==ModelSpec.Shape.ROUND?mk.outerR:mk.shape==ModelSpec.Shape.BATON?mk.tangentialHalf:0.6*mk.halfBase;
                if(Math.hypot(x-c[0],y-c[1])<=rad)return 200;}
            return 20;
        };
    }

    @Test public void theRightBranchWinsAndAOneMinuteLockIsFound(){
        ModelSpec m=TestModels.gmt();
        Alpha104MarkerBranch.Result ok=Alpha104MarkerBranch.analyse(dial(m),H,m);
        assertEquals(0,ok.best);assertTrue(ok.decisive);
        // pose locked one minute anticlockwise of the truth: turning it +1 minute puts the markers back
        double[] off=Alpha104MarkerBranch.turnPose(H,Math.toRadians(-6));
        Alpha104MarkerBranch.Result r=Alpha104MarkerBranch.analyse(dial(m),off,m);
        assertEquals(1,r.best);assertTrue(r.decisive);
        double[] back=Alpha104MarkerBranch.turnPose(off,Math.toRadians(6));
        for(int i=0;i<9;i++)assertEquals(H[i],back[i],1e-9);
    }

    @Test public void aFeaturelessDialDecidesNothing(){
        ModelSpec m=TestModels.gmt();
        Alpha104MarkerBranch.Result r=Alpha104MarkerBranch.analyse((px,py)->40,H,m);
        assertFalse(r.decisive);
    }
}
