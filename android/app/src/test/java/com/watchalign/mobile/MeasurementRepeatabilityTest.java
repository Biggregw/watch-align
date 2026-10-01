package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MeasurementRepeatabilityTest {
    @Test public void normalizedSpreadIsConvertedToPixels(){
        assertEquals(0.8,MeasurementRepeatability.widthShiftPx(-0.01,0.03,20),1e-9);
        assertEquals(1.2,MeasurementRepeatability.radiusShiftPx(0.020,0.023,400),1e-9);
    }

    @Test public void onePixelIsTheGenericRepeatabilityBoundary(){
        assertTrue(MeasurementRepeatability.stable(1.0));
        assertFalse(MeasurementRepeatability.stable(1.01));
        assertFalse(MeasurementRepeatability.stable(Double.NaN));
    }

    @Test public void angularSpreadUsesVisibleEndTravel(){
        double px=MeasurementRepeatability.angleShiftPx(0,0.5,60);
        assertTrue(px>0.5&&px<0.6);
        assertTrue(MeasurementRepeatability.stable(px));
        assertFalse(MeasurementRepeatability.stable(MeasurementRepeatability.angleShiftPx(0,1.5,60)));
    }
}
