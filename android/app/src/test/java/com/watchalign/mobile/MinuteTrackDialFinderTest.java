package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;

public class MinuteTrackDialFinderTest {
    @Test public void convertsMinuteTrackRadiusToDialRadiusUsingMasterRatio(){
        double track=92.5;
        assertEquals(100.0,MinuteTrackDialFinder.expectedDialRadiusFromTrackRadius(track),1e-9);
    }

    @Test public void outwardBoundaryValidationDoesNotRescaleMinuteTrackEllipse(){
        RotatedRect track=new RotatedRect(new Point(120,140),new Size(220,200),17.0);
        RotatedRect result=MinuteTrackDialFinder.finalEllipseFromTrack(track,1.0325);

        assertEquals(track.center.x,result.center.x,1e-9);
        assertEquals(track.center.y,result.center.y,1e-9);
        assertEquals(track.size.width,result.size.width,1e-9);
        assertEquals(track.size.height,result.size.height,1e-9);
        assertEquals(track.angle,result.angle,1e-9);
    }

    @Test public void uprightQcRuleChoosesNearestSixDegreeBranchToImageUp(){
        RotatedRect circle=new RotatedRect(new Point(200,200),new Size(240,240),0.0);
        MinuteTrackDialFinder.TopPhaseResult result=MinuteTrackDialFinder.anchorToImageUp(circle,8.4);

        assertEquals(2.4,result.anchoredRollDeg,1e-6);
        assertEquals(2.4,result.errorDeg,1e-6);
        assertTrue(result.accepted);
    }

    @Test public void postAnchorFineRotationCannotJumpAnotherMinuteTick(){
        assertTrue(MinuteTrackPoseValidator.fineRotationLimitDeg()<3.0);
        assertTrue(MinuteTrackPoseValidator.fineRotationLimitDeg()<6.0);
    }
}
