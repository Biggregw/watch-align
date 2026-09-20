package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Decision-logic tests for the primary-pose identity veto described in
 * MinuteTrackFirstOverlay. These test the pure boolean/enum decision functions only; the
 * Mat-based sampling in MinuteTrackIdentityGate.evaluate() requires native OpenCV and is
 * exercised by the Android instrumentation tests and the official-fixture acquisition check.
 */
public class MinuteTrackIdentityGateTest {

    @Test public void goodPrimaryNeverRequiresIdentityVerification(){
        // Clearly-valid primaries (small centre displacement) must see zero behaviour change:
        // no identity check is even evaluated, and acceptance is unaffected.
        assertFalse(MinuteTrackFirstOverlay.identityVerificationRequired(true,0.05));
        assertTrue(MinuteTrackFirstOverlay.finalAcceptance(true,0.05,null));
    }

    @Test public void rejectedPrimaryNeverRequiresIdentityVerification(){
        // An already-rejected primary must not evaluate or need identity evidence; rescue
        // already gets to run via the existing MinuteTrackRescueOverlay control flow.
        assertFalse(MinuteTrackFirstOverlay.identityVerificationRequired(false,0.50));
        assertFalse(MinuteTrackFirstOverlay.finalAcceptance(false,0.50,MinuteTrackIdentityGate.Verdict.FAIL));
    }

    @Test public void suspiciousCentreDisplacementRequiresIdentityVerification(){
        assertTrue(MinuteTrackFirstOverlay.geometricallySuspicious(0.30));
        assertTrue(MinuteTrackFirstOverlay.identityVerificationRequired(true,0.30));
        assertFalse(MinuteTrackFirstOverlay.geometricallySuspicious(0.22));
    }

    @Test public void regression_largeCentreDisplacementWithFailedIdentityMustNotStayAccepted(){
        // The exact real-world failure case: primary geometric checks all passed (topPhase,
        // final 12-axis, held-out minute-track validation all ACCEPTED), pose source reported
        // MINUTE TRACK FIRST, centre moved 42.79% of dial radius from the legacy seed, and the
        // pipeline still returned automaticAccepted=true at 75% confidence. Independent identity
        // evidence (dial darkness + 12/6/9 marker presence) must veto this.
        double centerDisplacement=0.4279;
        boolean primaryGeometricChecksPass=true;

        assertTrue(MinuteTrackFirstOverlay.identityVerificationRequired(
                primaryGeometricChecksPass,centerDisplacement));
        assertTrue(MinuteTrackFirstOverlay.primaryVetoed(
                primaryGeometricChecksPass,centerDisplacement,MinuteTrackIdentityGate.Verdict.FAIL));
        assertFalse(MinuteTrackFirstOverlay.finalAcceptance(
                primaryGeometricChecksPass,centerDisplacement,MinuteTrackIdentityGate.Verdict.FAIL));
    }

    @Test public void ambiguousIdentityAlsoVetoesASuspiciousPrimary(){
        // AMBIGUOUS (e.g. two of three markers found) is not strong enough to let a large,
        // unexplained centre displacement stand as an automatically-trusted pose.
        assertTrue(MinuteTrackFirstOverlay.primaryVetoed(
                true,0.35,MinuteTrackIdentityGate.Verdict.AMBIGUOUS));
        assertFalse(MinuteTrackFirstOverlay.finalAcceptance(
                true,0.35,MinuteTrackIdentityGate.Verdict.AMBIGUOUS));
    }

    @Test public void strongIdentityConfirmationLetsASuspiciousPrimaryStand(){
        // A large centre displacement can still be legitimate (e.g. genuine strong perspective).
        // If independent identity evidence strongly confirms the candidate (PASS), the primary
        // must not be vetoed, preserving intended behaviour for a truly valid pose.
        assertFalse(MinuteTrackFirstOverlay.primaryVetoed(
                true,0.35,MinuteTrackIdentityGate.Verdict.PASS));
        assertTrue(MinuteTrackFirstOverlay.finalAcceptance(
                true,0.35,MinuteTrackIdentityGate.Verdict.PASS));
    }
}
