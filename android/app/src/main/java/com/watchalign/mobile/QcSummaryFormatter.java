package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a concise user-facing summary while leaving the detailed QC report intact.
 * It deliberately avoids inventing a defect when the detailed engine has not ranked one.
 */
final class QcSummaryFormatter {
    static String prependSummary(String report) {
        if (report == null || report.isEmpty()) return report;

        List<String> ranked = extractRanked(report);
        StringBuilder out = new StringBuilder("QC SUMMARY\n");

        if (ranked.isEmpty()) {
            out.append("No major defects detected.\n");
            String minor = bestMinorObservation(report);
            if (minor != null) out.append("Minor observation: ").append(minor).append("\n");
        } else {
            boolean major = false;
            for (String s : ranked) if (looksMajor(s)) { major = true; break; }
            out.append(major ? "Biggest detected issues\n" : "No major defects detected. Minor observations\n");
            for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                out.append(i + 1).append(". ").append(simplify(ranked.get(i))).append("\n");
            }
        }

        out.append("\nFull QC detail\n").append(report);
        return out.toString();
    }

    private static List<String> extractRanked(String report) {
        String[] markers = {"Top QC findings\n", "Top QC observations — advisory only\n"};
        for (String marker : markers) {
            int p = report.indexOf(marker);
            if (p < 0) continue;
            int start = p + marker.length();
            int end = report.indexOf("\n\n", start);
            if (end < 0) end = report.length();
            String[] lines = report.substring(start, end).split("\\n");
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                String s = line.replaceFirst("^\\d+\\.\\s*", "").trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        return new ArrayList<>();
    }

    private static boolean looksMajor(String s) {
        String x = s.toLowerCase();
        return x.contains("strong") || x.contains("merits inspection") || x.contains("off-centre") ||
                x.contains("offset") || x.contains("gap") || x.contains("magnification");
    }

    private static String bestMinorObservation(String report) {
        // Only surface a minor note when the engine itself reported a small 12-marker positional bias.
        // This is not a defect verdict; it is a convenience summary of an existing measurement.
        String needle = "12 o'clock angular ";
        int p = report.indexOf(needle);
        if (p < 0) return null;
        int e = report.indexOf('\n', p);
        if (e < 0) e = report.length();
        String line = report.substring(p, e);
        int r = line.indexOf("radial ");
        if (r < 0) return null;
        String v = line.substring(r + 7).replace("%", "").trim();
        try {
            double radial = Double.parseDouble(v);
            if (Math.abs(radial) < 0.20 || Math.abs(radial) > 1.50) return null;
            return radial > 0 ? "12 triangle is fractionally high/outward (" + fmt(radial) + "% radial)"
                              : "12 triangle is fractionally low/inward (" + fmt(radial) + "% radial)";
        } catch (Exception ignored) { return null; }
    }

    private static String simplify(String s) {
        return s.replace("12 marker body rotation", "12 triangle rotated")
                .replace("6 marker body rotation", "6 marker rotated")
                .replace("9 marker body rotation", "9 marker rotated")
                .replace("SEL gap area merits visual inspection", "SEL gap")
                .replace("Cyclops-to-aperture alignment merits inspection", "Cyclops alignment")
                .replace("Cyclops alignment merits inspection", "Cyclops alignment")
                .replace("Bezel/pip 12 offset", "Bezel/pip alignment");
    }

    private static String fmt(double v) { return String.format(java.util.Locale.US, "%+.2f", v); }
    private QcSummaryFormatter() {}
}
