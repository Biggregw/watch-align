package com.watchalign.mobile;
import org.junit.Test;import static org.junit.Assert.*;
public class QcMeasurementFormatterTest {
 @Test public void reportsRangeAndSignedMedianDeviation(){String s=QcMeasurementFormatter.range("Base",.190f,.169f,.10f,.20f,"BW");assertTrue(s.contains("inside observed genuine range"));assertTrue(s.contains("+0.021"));assertTrue(s.contains("high vs median"));}
 @Test public void neverCallsObservedRangeFactoryTolerance(){assertFalse(QcMeasurementFormatter.range("Base",.2f,.17f,.1f,.2f,"BW").toLowerCase().contains("factory tolerance"));}
}
