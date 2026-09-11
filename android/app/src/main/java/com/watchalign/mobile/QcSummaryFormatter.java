package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;

/** Builds a concise summary while leaving full diagnostic detail intact. */
final class QcSummaryFormatter {
    static String prependSummary(String report) {
        if (report == null || report.isEmpty()) return report;

        boolean canonicalGmt = report.contains("CANONICAL GMT GEOMETRY");
        List<String> ranked = canonicalGmt ? extractCanonicalGmt(report) : extractRanked(report);
        if (canonicalGmt) {
            // Keep non-marker findings such as date/SEL issues, but do not let the older
            // marker-ring-relative 12/6/9 observations override canonical GMT placement.
            for (String s : extractRanked(report)) {
                String x=s.toLowerCase();
                if (x.contains("marker local position") || x.contains("marker body rotation")) continue;
                ranked.add(s);
            }
        }

        StringBuilder out = new StringBuilder("QC SUMMARY\n");
        if (ranked.isEmpty()) {
            out.append("No major defects detected.\n");
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

    private static List<String> extractCanonicalGmt(String report) {
        List<String> out=new ArrayList<>();
        int p=report.indexOf("CANONICAL GMT GEOMETRY\n");
        if(p<0)return out;
        int end=report.indexOf("\nInterpretation:",p);
        if(end<0)end=report.length();
        String[] lines=report.substring(p,end).split("\\n");
        for(String line:lines){
            String s=line.trim();
            if(!(s.contains("o'clock:")))continue;
            if(s.contains("OUTSIDE GMT RANGE"))out.add(s.replace("[OUTSIDE GMT RANGE", "[OUTSIDE GMT RANGE"));
            else if(s.contains("[CHECK;"))out.add(s);
            else if(s.contains("ANGULAR CHECK"))out.add(s);
        }
        return out;
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
        return x.contains("outside gmt range") || x.contains("strong") || x.contains("merits inspection") ||
                x.contains("off-centre") || x.contains("offset") || x.contains("gap") || x.contains("magnification");
    }

    private static String simplify(String s) {
        if(s.contains("o'clock:") && s.contains("radial Δ")) {
            return s.replace(" of dial radius", "")
                    .replace("[CHECK;", "[CHECK;")
                    .replace("[OUTSIDE GMT RANGE;", "[OUTSIDE GMT RANGE;");
        }
        return s.replace("12 marker body rotation", "12 triangle rotated")
                .replace("6 marker body rotation", "6 marker rotated")
                .replace("9 marker body rotation", "9 marker rotated")
                .replace("SEL gap area merits visual inspection", "SEL gap")
                .replace("Cyclops-to-aperture alignment merits inspection", "Cyclops alignment")
                .replace("Cyclops alignment merits inspection", "Cyclops alignment")
                .replace("Bezel/pip 12 offset", "Bezel/pip alignment");
    }

    private QcSummaryFormatter() {}
}
