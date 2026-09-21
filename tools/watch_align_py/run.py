"""CLI test runner: load a real photo, apply MainActivity.readBitmap's exact 1600px
longest-side cap, run the ported pipeline, print its report plus marker diagnostics."""
import math
import sys

import marker_qc
import pipeline
from image_io import decode_capped


def describe_markers(markers):
    if markers is None:
        return "GmtMarkerQcRepair.measure() returned None (pose acquisition failed)"
    lines = []
    for hour in range(1, 13):
        d = markers[hour]
        if d is None:
            continue
        if not d.measured:
            lines.append(f"{hour} marker: not confidently isolated")
            continue
        body = f"{d.body_rotation_deg:+.2f}°" if math.isfinite(d.body_rotation_deg) else "unavailable"
        tri = (f", base-to-minute-track {d.triangle_outward_delta_pct_r:+.2f}% R"
               if math.isfinite(d.triangle_outward_delta_pct_r) else "")
        lines.append(f"{hour} marker: angular offset {d.angular_deg:+.2f}°, "
                      f"radial {d.radial_pct_r:+.2f}% R, body rotation {body}{tri}")
    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print("usage: run.py <image-path> [image-path...]")
        sys.exit(1)
    for path in sys.argv[1:]:
        print(f"=== {path} ===")
        bgr = decode_capped(path)
        result = pipeline.build(bgr)
        if result.reason:
            print(f"REJECTED (fallback path): {result.reason}")
        else:
            print(result.report)
        print("--- marker diagnostics ---")
        print(describe_markers(marker_qc.measure_from_bgr(bgr)))
        print()


if __name__ == "__main__":
    main()
