package com.watchalign.mobile;

import com.watchalign.mobile.profile.Triangle12Calibration;

/** Builds explanatory assessment text from raw production measurements plus versioned calibration data. */
final class GmtTriangle12Assessment {
    private GmtTriangle12Assessment() {}

    static String summary(Triangle12RelationalMetric.Result m, Triangle12Calibration c) {
        if (m == null) return "QC SUMMARY\n12 triangle measurements unavailable";
        if (c == null) return "QC SUMMARY\n12 triangle calibration unavailable";

        double legacyBaseMin = c.baseTo60.legacyClassifierMin;
        double legacyBaseMax = c.baseTo60.legacyClassifierMax;
        double pilotBaseMin = c.baseTo60.pilotMin();
        double pilotBaseMax = c.baseTo60.pilotMax();
        double crownMin = c.apexToCrown.legacyClassifierMin;
        double crownMax = c.apexToCrown.legacyClassifierMax;
        double rotMax = c.rotation.observedEnvelopeAbsMaxDeg;

        boolean legacyBaseOk = m.baseGapRatio >= legacyBaseMin && m.baseGapRatio <= legacyBaseMax;
        boolean pilotBaseOk = m.baseGapRatio >= pilotBaseMin && m.baseGapRatio <= pilotBaseMax;
        boolean crownOk = m.apexGapRatio >= crownMin && m.apexGapRatio <= crownMax;
        boolean rotOk = Math.abs(m.rotationDeg) <= rotMax;

        String headline;
        if (m.baseGapRatio < pilotBaseMin) headline = "track gap below current genuine pilot observations";
        else if (m.baseGapRatio > pilotBaseMax) headline = "track gap above current genuine pilot observations";
        else headline = "track gap inside current genuine pilot observations";
        if (!rotOk) headline += " · rotation outside current angular envelope";

        double trackDelta = m.baseGapRatio - c.baseTo60.pilotMedian;
        String trackDir = trackDelta < 0 ? "closer to minute track" : "farther from minute track";
        String centre = Math.abs(m.lateralPx) < .5f ? "centred" : String.format("%+.2f px lateral", m.lateralPx);
        String rot = rotOk
                ? String.format("%+.2f° · inside current ±%.2f° envelope", m.rotationDeg, rotMax)
                : String.format("%+.2f° · %.2f° beyond current ±%.2f° envelope", m.rotationDeg, Math.abs(m.rotationDeg) - rotMax, rotMax);

        String baseText = String.format(
                "Track gap: %.3f BW · %.3f %s than genuine pilot median %.3f · pilot %.3f–%.3f (n=%d)",
                m.baseGapRatio, Math.abs(trackDelta), trackDir, c.baseTo60.pilotMedian,
                pilotBaseMin, pilotBaseMax, c.genuineControlCount);

        String pilotPosition;
        if (m.baseGapRatio < pilotBaseMin)
            pilotPosition = String.format("Pilot position: %.3f below lowest genuine control", pilotBaseMin - m.baseGapRatio);
        else if (m.baseGapRatio > pilotBaseMax)
            pilotPosition = String.format("Pilot position: %.3f above highest genuine control", m.baseGapRatio - pilotBaseMax);
        else
            pilotPosition = String.format("Pilot position: inside observations · nearest edge %.3f",
                    Math.min(m.baseGapRatio - pilotBaseMin, pilotBaseMax - m.baseGapRatio));

        String legacyText = String.format("Legacy classifier: %s frozen %.3f–%.3f band",
                legacyBaseOk ? "inside" : "outside", legacyBaseMin, legacyBaseMax);
        String crownText = String.format(
                "Apex-to-crown: %.3f BW · pilot median %.3f · %s legacy %.3f–%.3f band",
                m.apexGapRatio, c.apexToCrown.pilotMedian, crownOk ? "inside" : "outside", crownMin, crownMax);

        String confidence;
        if (!pilotBaseOk && legacyBaseOk)
            confidence = "Assessment: outside current genuine pilot observations, while still inside the intentionally wider legacy classifier.";
        else if (pilotBaseOk)
            confidence = "Assessment: track gap sits inside the current genuine pilot observations.";
        else
            confidence = "Assessment: track gap is outside both the current genuine pilot observations and the frozen legacy classifier.";

        return "QC SUMMARY\n12 triangle: " + headline + " · " + centre + "\n"
                + baseText + "\n" + pilotPosition + " · " + legacyText + "\n"
                + crownText + "\nRotation: " + rot + "\n" + confidence;
    }
}
