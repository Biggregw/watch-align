package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Regression tests derived from known genuine/reference-image false positives. */
public class QcGenuineRegressionTest {
    @Test public void genuineTwelveMarkerOutliersAreNotAutoGraded() {
        assertEquals(0, QcExtendedMath.orientationSeverity(-4.06));
        assertEquals(0, QcExtendedMath.orientationSeverity(-48.53));
    }

    @Test public void cyclopsContourRotationOutlierIsNotAutoGraded() {
        assertEquals(0, QcExtendedMath.orientationSeverity(22.63));
        assertEquals(0, QcExtendedMath.cyclopsApertureOffsetSeverity(Math.hypot(0.033, 0.016)));
    }

    @Test public void smallLocalDateAxisBiasIsNotRankedAsAProblem() {
        assertEquals(0, QcExtendedMath.dateAxisSeverity(0.44 * 6.0));
        assertEquals(0, QcExtendedMath.dateAxisSeverity(-0.61 * 6.0));
    }

    @Test public void verticalGlyphCentroidCannotFailDateCenteringByItself() {
        assertEquals(0, QcExtendedMath.dateCenterSeverity(2.8, 21.8));
        assertEquals(0, QcExtendedMath.dateCenterSeverity(-0.2, -3.2));
    }

    @Test public void genuineScaleComparisonStaysNeutral() {
        assertEquals(0, QcExtendedMath.magnificationMatchSeverity(100.0));
        assertEquals(0, QcExtendedMath.magnificationMatchSeverity(96.6));
    }
}
