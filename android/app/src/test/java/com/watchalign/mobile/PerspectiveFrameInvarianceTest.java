package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Same-watch / different-camera-angle regression for the GMT planar perspective model.
 *
 * Each matrix below represents a different video frame of ONE unchanged planar dial. We project the
 * same canonical 126710 geometry through that frame's known homography, derive only the four dial-edge
 * cardinal anchors that the production app uses, then rectify the projected points with the production
 * PerspectiveRectifier. If the perspective calculation is correct, all frames recover the same dial
 * geometry to numerical precision.
 *
 * A second test intentionally places the 9 o'clock anchor on a larger concentric boundary. It proves
 * that a wrong physical edge can create false residuals around 8/10 even when the four-point
 * homography itself is mathematically exact. This mirrors the failure mode seen on phone testing.
 */
public class PerspectiveFrameInvarianceTest {
    private static final double MARKER_R = Gmt126710BlnrMeasured.MARKER_CENTER_R;

    // Canonical dial -> source-image homographies. These deliberately span face-on, left/right tilt,
    // vertical tilt and compound perspective/roll. Any invertible planar homography is a valid camera
    // view of a plane up to projective scale.
    private static final double[][][] VIEWS = {
            {{330,   0, 620}, {  0, 330, 520}, { 0.00,  0.00, 1}},
            {{330,  35, 540}, {-15, 300, 530}, { 0.18, -0.10, 1}},
            {{305, -28, 605}, { 22, 325, 495}, {-0.17,  0.08, 1}},
            {{310,  18, 590}, {-35, 300, 510}, { 0.06,  0.18, 1}},
            {{285,  65, 575}, {-55, 315, 515}, { 0.14, -0.15, 1}}
    };

    @Test public void samePhysicalDialIsInvariantAcrossPerspectiveFrames() {
        for (double[][] trueH : VIEWS) {
            PerspectiveMasterRenderer.Pose pose = poseFromTrueFrame(trueH);

            // The four exact hour axes plus every non-date marker centre.
            for (int hour = 1; hour <= 12; hour++) {
                if (hour == 3) continue;
                double[] p = radialPoint(hour, MARKER_R);
                assertRecovered(trueH, pose, p[0], p[1], 3e-6);
            }

            // Minute-track points around the complete dial provide a denser independent ring check.
            for (int i = 0; i < 60; i += 3) {
                double a = Math.toRadians(i * 6.0);
                double x = Gmt126710BlnrMaster.MINUTE_TRACK_R * Math.sin(a);
                double y = -Gmt126710BlnrMaster.MINUTE_TRACK_R * Math.cos(a);
                assertRecovered(trueH, pose, x, y, 3e-6);
            }

            // Date-window centre and the three 12-triangle vertices must also be invariant.
            assertRecovered(trueH, pose, Gmt126710BlnrMeasured.DATE_CENTER_R, 0, 3e-6);
            for (float[] local : Gmt126710BlnrMeasured.TRI_OUTER) {
                // At 12: tangential +x, radial outward points -y.
                double x = local[0];
                double y = -(Gmt126710BlnrMeasured.TRI_CENTER_R + local[1]);
                assertRecovered(trueH, pose, x, y, 3e-6);
            }
        }
    }

    @Test public void wrongNineBoundaryCreatesFalseEightAndTenResiduals() {
        double[][] trueH = VIEWS[1];
        PerspectiveMasterRenderer.Pose good = poseFromTrueFrame(trueH);
        double[] centre = project(trueH, 0, 0);

        // Move only the 9 o'clock anchor 6% farther from the true projective dial centre, simulating
        // selection of the larger/outer rehaut circle while 12/3/6 remain on the inner dial edge.
        PerspectiveMasterRenderer.Pose bad = copy(good);
        bad.anchor9X = (float) (centre[0] + 1.06 * (good.anchor9X - centre[0]));
        bad.anchor9Y = (float) (centre[1] + 1.06 * (good.anchor9Y - centre[1]));

        double radial8 = radialResidual(trueH, bad, 8, MARKER_R);
        double radial10 = radialResidual(trueH, bad, 10, MARKER_R);
        double radial1 = radialResidual(trueH, bad, 1, MARKER_R);

        // A single wrong edge is enough to manufacture roughly 2.5-3% dial-radius errors near 8/10,
        // while the opposite quadrant remains much less affected. These are false QC residuals.
        assertTrue("8 o'clock should show a false >2% DR radial residual", Math.abs(radial8) > 0.020);
        assertTrue("10 o'clock should show a false >2% DR radial residual", Math.abs(radial10) > 0.020);
        assertTrue("opposite quadrant should be much less affected", Math.abs(radial1) < 0.010);
        assertTrue(Math.abs(radial8) > 2.0 * Math.abs(radial1));
        assertTrue(Math.abs(radial10) > 2.0 * Math.abs(radial1));
    }

    @Test public void productionForwardAndInverseAgreeForEveryValidationView() {
        for (double[][] trueH : VIEWS) {
            PerspectiveMasterRenderer.Pose pose = poseFromTrueFrame(trueH);
            double[] built = PerspectiveMasterRenderer.buildH(pose);
            assertNotNull(built);

            for (double r : new double[]{0.25, 0.55, MARKER_R, Gmt126710BlnrMaster.MINUTE_TRACK_R}) {
                for (int deg = 0; deg < 360; deg += 15) {
                    double a = Math.toRadians(deg);
                    double x = r * Math.cos(a), y = r * Math.sin(a);
                    double[] image = PerspectiveRectifier.projectRaw(pose, x, y);
                    assertNotNull(image);
                    double[] recovered = PerspectiveRectifier.toDialRaw(pose, image[0], image[1]);
                    assertNotNull(recovered);
                    assertEquals(x, recovered[0], 1e-8);
                    assertEquals(y, recovered[1], 1e-8);
                }
            }
        }
    }

    private static double radialResidual(double[][] trueH, PerspectiveMasterRenderer.Pose rectifier,
                                         int hour, double radius) {
        double[] truePoint = radialPoint(hour, radius);
        double[] source = project(trueH, truePoint[0], truePoint[1]);
        double[] recovered = PerspectiveRectifier.toDialRaw(rectifier, source[0], source[1]);
        assertNotNull(recovered);
        double dx = recovered[0] - truePoint[0], dy = recovered[1] - truePoint[1];
        double a = Math.toRadians(hour * 30.0);
        double urx = Math.sin(a), ury = -Math.cos(a);
        return dx * urx + dy * ury;
    }

    private static void assertRecovered(double[][] trueH, PerspectiveMasterRenderer.Pose pose,
                                        double x, double y, double tolerance) {
        double[] source = project(trueH, x, y);
        double[] recovered = PerspectiveRectifier.toDialRaw(pose, source[0], source[1]);
        assertNotNull(recovered);
        assertEquals(x, recovered[0], tolerance);
        assertEquals(y, recovered[1], tolerance);
    }

    private static PerspectiveMasterRenderer.Pose poseFromTrueFrame(double[][] h) {
        double[] p12 = project(h, 0, -1), p3 = project(h, 1, 0),
                p6 = project(h, 0, 1), p9 = project(h, -1, 0);
        PerspectiveMasterRenderer.Pose p = new PerspectiveMasterRenderer.Pose();
        p.anchorMode = true;
        p.perspectiveMode = true;
        p.anchor12X = (float) p12[0]; p.anchor12Y = (float) p12[1];
        p.anchor3X = (float) p3[0]; p.anchor3Y = (float) p3[1];
        p.anchor6X = (float) p6[0]; p.anchor6Y = (float) p6[1];
        p.anchor9X = (float) p9[0]; p.anchor9Y = (float) p9[1];
        return p;
    }

    private static PerspectiveMasterRenderer.Pose copy(PerspectiveMasterRenderer.Pose p) {
        PerspectiveMasterRenderer.Pose q = new PerspectiveMasterRenderer.Pose();
        q.anchorMode = p.anchorMode; q.perspectiveMode = p.perspectiveMode;
        q.anchor12X = p.anchor12X; q.anchor12Y = p.anchor12Y;
        q.anchor3X = p.anchor3X; q.anchor3Y = p.anchor3Y;
        q.anchor6X = p.anchor6X; q.anchor6Y = p.anchor6Y;
        q.anchor9X = p.anchor9X; q.anchor9Y = p.anchor9Y;
        return q;
    }

    private static double[] radialPoint(int hour, double radius) {
        double a = Math.toRadians(hour * 30.0);
        return new double[]{radius * Math.sin(a), -radius * Math.cos(a)};
    }

    private static double[] project(double[][] h, double x, double y) {
        double d = h[2][0] * x + h[2][1] * y + h[2][2];
        return new double[]{
                (h[0][0] * x + h[0][1] * y + h[0][2]) / d,
                (h[1][0] * x + h[1][1] * y + h[1][2]) / d
        };
    }
}
