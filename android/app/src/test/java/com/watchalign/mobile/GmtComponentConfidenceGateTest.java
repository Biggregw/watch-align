package com.watchalign.mobile;
import static org.junit.Assert.*;import org.junit.Test;
public class GmtComponentConfidenceGateTest {
 @Test public void dateApertureRequiresPlausibleObservedGeometry(){assertTrue(QcExtendedAnalyzer.plausibleDateApertureGeometry(1.8,.25,.14));assertFalse(QcExtendedAnalyzer.plausibleDateApertureGeometry(.7,.25,.14));assertFalse(QcExtendedAnalyzer.plausibleDateApertureGeometry(1.8,.70,.14));}
 @Test public void implausibleNumeralCentroidIsSuppressed(){assertTrue(QcExtendedAnalyzer.numeralWithinAperture(4,-8));assertFalse(QcExtendedAnalyzer.numeralWithinAperture(80,0));assertFalse(QcExtendedAnalyzer.numeralWithinAperture(Double.NaN,0));}
 @Test public void selPolicyContainsNoNumericScoring(){assertEquals("SEL: visual inspection only; no automatic gap score",QcExtendedAnalyzer.SEL_VISUAL_ONLY);assertFalse(QcExtendedAnalyzer.SEL_VISUAL_ONLY.contains("dark"));}
}
