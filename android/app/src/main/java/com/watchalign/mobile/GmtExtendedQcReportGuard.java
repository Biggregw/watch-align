package com.watchalign.mobile;

import com.watchalign.mobile.qc.PerspectiveConfidenceService;
import com.watchalign.mobile.qc.QcModuleResult;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safety layer for legacy photo-sensitive GMT diagnostics while they are migrated into reusable
 * modules. It prevents obviously contaminated component fits from being presented as useful QC.
 */
final class GmtExtendedQcReportGuard {
    private static final Pattern BODY_ROTATION = Pattern.compile("body rotation ([+-]?[0-9]+(?:\\.[0-9]+)?)°");
    private static final Pattern DATE_AXIS = Pattern.compile("Date aperture vs local ([0-9]{2})-minute marker: ([+-]?[0-9]+(?:\\.[0-9]+)?)°.*");

    private GmtExtendedQcReportGuard() {}

    static String sanitize(String report, PerspectiveConfidenceService.Assessment perspective) {
        if (report == null) return "Extended checks unavailable.";
        boolean fine = perspective != null && perspective.confidence() == QcModuleResult.Confidence.HIGH;
        String confidence = perspective == null ? "unverified" : perspective.confidence().name().toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        String[] lines = report.split("\\n", -1);
        boolean inTop = false;
        int keptTop = 0;

        for (String original : lines) {
            String line = original;
            if (line.startsWith("Top QC findings") || line.startsWith("Top QC observations")) {
                inTop = true;
                keptTop = 0;
                out.append(fine ? "Top QC findings" : "Top QC observations — geometry confidence gated").append('\n');
                continue;
            }
            if (inTop && line.trim().isEmpty()) {
                inTop = false;
                out.append('\n');
                continue;
            }
            if (inTop && line.matches("[1-3]\\. .*")) {
                if (!fine && (line.contains("Date aperture") || line.contains("SEL gap") || line.contains("body rotation"))) continue;
                keptTop++;
                out.append(keptTop).append(line.substring(1)).append('\n');
                continue;
            }

            Matcher body = BODY_ROTATION.matcher(line);
            if (body.find()) {
                double value = parse(body.group(1));
                if (!fine || !Double.isFinite(value) || Math.abs(value) > 12.0) {
                    line = line.substring(0, body.start()) + "body rotation withheld (component isolation not reliable)";
                }
            }

            Matcher date = DATE_AXIS.matcher(line);
            if (date.matches()) {
                double value = parse(date.group(2));
                if (!fine || !Double.isFinite(value) || Math.abs(value) > 12.0) {
                    line = "Date aperture axis vs local minute track: withheld because the local reference was not reliable enough.";
                }
            }

            if (line.startsWith("Fine QC is advisory because ")) {
                line = "Fine QC confidence follows corrected 12/3/6/9 anchors: " + confidence + ".";
            }
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private static double parse(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return Double.NaN; }
    }
}
