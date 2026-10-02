package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Image-backed golden-regression driver for the production Rolex Submariner 124060 route.
 *
 * Input:  list.tsv lines of physical_watch_id TAB image_path
 * Output: one CSV row per image containing the reliability-gated production measurements and
 *         the user-facing calibration labels currently produced by Sub124060Calibration.
 *
 * This is intentionally separate from CalibMeasure: calibration can measure without judging,
 * while a golden regression must freeze the behaviour a phone build would actually expose.
 */
public final class Sub124060Golden {
    private static final String[] KEYS = {
            "twelve.rotation_deg",
            "twelve.gap_r",
            "twelve.centring_w",
            "round.ring_rho",
            "round.spacing_rms_deg",
            "baton.3_9_line_offset_r",
            "axis.12_6_line_offset_r"
    };

    private Sub124060Golden() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: Sub124060Golden <list.tsv> <out.csv>");
            System.exit(2);
        }
        nu.pattern.OpenCV.loadLocally();
        try (PrintWriter w = new PrintWriter(new FileWriter(args[1]))) {
            StringBuilder header = new StringBuilder("physical_watch_id,path,dial_source,dial_reproducible,pose_tilt_deg");
            for (String key : KEYS) header.append(',').append(key).append(',').append(key).append(".label");
            header.append(",twelve.overall,rounds.overall,baton.3_9.overall,axis.12_6.overall");
            w.println(header);

            for (String line : Files.readAllLines(Path.of(args[0]))) {
                String[] fields = line.split("\\t", 2);
                if (fields.length < 2 || fields[1].trim().isEmpty()) continue;
                String watchId = fields[0].trim();
                String imagePath = fields[1].trim();
                emit(w, watchId, imagePath);
                w.flush();
            }
        }
    }

    private static void emit(PrintWriter w, String watchId, String imagePath) {
        String dialSource = "unreadable";
        String dialRepro = "";
        double poseTilt = Double.NaN;
        double[] values = new double[KEYS.length];
        Arrays.fill(values, Double.NaN);
        String[] labels = new String[KEYS.length];
        Arrays.fill(labels, "NOT JUDGED");
        String twelveOverall = "NOT JUDGED";
        String roundsOverall = "NOT JUDGED";
        String baton39Overall = "NOT JUDGED";
        String axis126Overall = "NOT JUDGED";

        try {
            Bitmap bitmap = Load.photo(imagePath);
            if (bitmap != null) {
                FullResSource full = Load.fullSource(imagePath);
                WatchAlignCoreV13.AnalysisResult result = WatchAlignCoreV13.analyse(
                        bitmap, Collections.<Bitmap>emptyList(), "124060", full);
                Sub124060QcAnalyzer.Result sub = result.sub124060;
                if (sub != null) {
                    dialSource = String.valueOf(sub.dialSource);
                    dialRepro = String.valueOf(sub.dialReproducible);
                    if (sub.markerPose != null && sub.markerPose.valid) poseTilt = sub.markerPose.tiltDeg;

                    Sub124060Calibration.Assessment a = Sub124060Calibration.assess(sub);
                    if (a.rotationMeasured) values[0] = sub.rotationDeg;
                    if (a.gapMeasured) values[1] = sub.gapR;
                    if (a.centringMeasured) values[2] = sub.centringW;
                    if (a.roundRingMeasured) values[3] = a.roundRingRho;
                    if (a.roundSpacingMeasured) values[4] = a.roundSpacingRmsDeg;
                    if (a.baton39Measured) values[5] = a.baton39LineOffsetR;
                    if (a.axis126Measured) values[6] = a.axis126LineOffsetR;

                    labels[0] = Sub124060Calibration.label(a.rotationMeasured, a.rotation);
                    labels[1] = Sub124060Calibration.label(a.gapMeasured, a.gap);
                    labels[2] = Sub124060Calibration.label(a.centringMeasured, a.centring);
                    labels[3] = Sub124060Calibration.label(a.roundRingMeasured, a.roundRing);
                    labels[4] = Sub124060Calibration.label(a.roundSpacingMeasured, a.roundSpacing);
                    labels[5] = Sub124060Calibration.label(a.baton39Measured, a.baton39);
                    labels[6] = Sub124060Calibration.label(a.axis126Measured, a.axis126);

                    twelveOverall = Sub124060Calibration.words(a.twelve());
                    roundsOverall = Sub124060Calibration.words(a.rounds());
                    baton39Overall = Sub124060Calibration.words(a.baton("3"));
                    axis126Overall = Sub124060Calibration.words(a.baton("6"));
                }
            }
        } catch (Throwable t) {
            dialSource = "error:" + t.getClass().getSimpleName();
        }

        StringBuilder out = new StringBuilder();
        out.append(watchId).append(',')
                .append(imagePath.replace(',', ';')).append(',')
                .append(dialSource).append(',')
                .append(dialRepro).append(',')
                .append(fmt(poseTilt));
        for (int i = 0; i < KEYS.length; i++) {
            out.append(',').append(fmt(values[i])).append(',').append(labels[i]);
        }
        out.append(',').append(twelveOverall)
                .append(',').append(roundsOverall)
                .append(',').append(baton39Overall)
                .append(',').append(axis126Overall);
        w.println(out);
    }

    private static String fmt(double x) {
        return Double.isFinite(x) ? String.format(Locale.US, "%.6f", x) : "";
    }
}
