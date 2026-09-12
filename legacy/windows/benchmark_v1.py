from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np

import main as backend
from v1_full import MODEL_GEOMETRY, bezel_top_measurement, marker_measurements
from v1_upgrade import perspective_diagnostics


def full_circle(image):
    try:
        return backend.detect_refined_circle_full(image)
    except Exception:
        return backend.detect_watch_circle(image)


def analyse_case(case_dir: Path):
    expected = json.loads((case_dir / "expected.json").read_text(encoding="utf-8"))
    candidate = cv2.imread(str(case_dir / "candidate.jpg"))
    if candidate is None:
        raise RuntimeError(f"Missing candidate.jpg in {case_dir}")
    model = MODEL_GEOMETRY[expected["model_ref"]]
    circle = full_circle(candidate)
    if circle is None:
        return {"case": case_dir.name, "pass": False, "failures": ["candidate circle not detected"]}
    markers, marker_summary = marker_measurements(candidate, circle, model)
    p_candidate = perspective_diagnostics(candidate, circle)
    failures = []
    if "marker_median_abs_error_max_deg" in expected and marker_summary.get("available"):
        if float(marker_summary.get("median_abs_marker_error_deg", 999)) > float(expected["marker_median_abs_error_max_deg"]):
            failures.append("marker median error above expected maximum")
    mismatch = None
    reference_path = case_dir / "reference.jpg"
    if reference_path.exists():
        reference = cv2.imread(str(reference_path))
        rc = full_circle(reference) if reference is not None else None
        p_reference = perspective_diagnostics(reference, rc) if reference is not None else {"available": False}
        if p_candidate.get("available") and p_reference.get("available"):
            mismatch = abs(float(p_candidate["tilt_deg"]) - float(p_reference["tilt_deg"]))
            if "max_perspective_mismatch_deg" in expected and mismatch > float(expected["max_perspective_mismatch_deg"]):
                failures.append("perspective mismatch above expected maximum")
    return {
        "case": case_dir.name,
        "pass": not failures,
        "failures": failures,
        "candidate_perspective": p_candidate,
        "perspective_mismatch_deg": round(mismatch, 3) if mismatch is not None else None,
        "marker_summary": marker_summary,
        "marker_count": len([m for m in markers if m.get("available")]),
        "bezel": bezel_top_measurement(candidate, circle, model),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--output", type=Path, default=Path("benchmark-results.json"))
    args = parser.parse_args()
    cases = [p for p in sorted(args.corpus.iterdir()) if p.is_dir() and (p / "expected.json").exists()]
    results = [analyse_case(p) for p in cases]
    summary = {"cases": len(results), "passed": sum(1 for r in results if r["pass"]), "failed": sum(1 for r in results if not r["pass"]), "results": results}
    args.output.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    raise SystemExit(1 if summary["failed"] else 0)


if __name__ == "__main__":
    main()
