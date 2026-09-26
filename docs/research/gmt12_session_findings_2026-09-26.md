# GMT12 detector session findings (resume point)

Branch: `feature/gmt-human-qc-auto-landmarks`. Pipeline: `tools/research/gmt12_auto_landmarks.py` + `human_qc_geometry.py` + `gmt12_qc_assessment.py`.

## Bugs found & fixed this session (all committed+pushed, tests added)
1. Dial-circle content re-score picked wrong Hough candidate near ties -> rank penalty added (`_pick_dial_circle`).
2. Bezel/rehaut ring washed out Otsu threshold in tick band -> `_trim_bezel_band`.
3. Hand crossing triangle edge broke 3-vertex assumption -> `_polygon_to_triangle_corners` (extremes-based, tolerant of extra vertices).
4. `_minute_ticks` fallback only tried best-scored candidate; a bad-geometry best candidate discarded a good runner-up -> `_first_regularized` retries in score order.
5. `_trim_bezel_band` used fixed pixel-row constants, not scale-invariant -> converted to fractions of band height.
6. Dial-circle rank penalty (coef 15) too weak for a larger real margin -> raised to 40.
7. **Triangle ROI top margin (`cy-.72r`) silently clipped the true top edge on ~3/13 photos**, producing wrong (inflated) clearance while looking like a clean pass. Fixed via margin widen to `.85r` then `.90r` + new `_touches_roi_edge` safety net (reject candidates still touching the ROI boundary -> UNASSESSABLE instead of a fabricated number).
8. `_sequence` scoring weighted "fewer inferred ticks" too heavily vs "agreement with independently-detected triangle centre" (axis) -> axis weight raised .25->.80.

All real photos re-verified visually after each fix (overlay vs source image), not just diffed. 45 unit tests pass (`pytest tools/research/tests/test_gmt12_*.py test_human_qc_geometry.py`).

## Open calibration problem (NOT resolved, needs more real data)
`GEN_TOP_CLEARANCE_LOW/HIGH = 0.149/0.169` in `gmt12_qc_assessment.py` traces to **2 undocumented anchor photos** (comment says "control anchors 33459/33461"), NOT the `datasets/gmt_phase_b_genuine` corpus (that used a different frozen detector + different landmark definitions -- confirmed incompatible by direct reconstruction attempt, produced physically-impossible ratios ~1.8 median).

Collected 7 "genuine"-claimed photos this session (mixed provenance: 1 user-supplied, 5 Google Images screenshots, 1 marketplace listing -- **none independently verified**), measured with current pipeline:
top_clearance = 0.114, 0.114, 0.146, 0.148, 0.160, 0.169, 0.169 (bimodal-ish split, not one tight cluster).

**Tested and ruled out: camera tilt/perspective is NOT the explanation.** Two independent geometric tests (dial-rim ellipse eccentricity via concentric-ring radial voting, and crystal-rim-vs-chapter-ring parallax center-offset) on all 8 photos (7 genuine + 1 replica) showed near-perfect circularity (ecc 0.987-1.000) and sub-2px center offsets in every case, uncorrelated with top_clearance. Method + visualizations in `/tmp` scratchpad (not committed, session-local) -- reusable approach: `_dial_circle` seed -> radial gradient voting per angle -> monotonic-shrink ring chain -> fitEllipse per ring.

**Conclusion so far:** clearance spread across genuine claims is likely either (a) real watch-to-watch manufacturing variation, or (b) the 2-anchor band just being wrong/too narrow. Need: more genuine photos with **verifiable** provenance (dealer/auction records, not search screenshots) before touching `GEN_TOP_CLEARANCE_LOW/HIGH`.

## Next steps
- Get verifiable-provenance genuine photos (not screenshots) to rebuild the band properly.
- 50-replica-watch external validation still blocked: this sandbox's egress proxy blocks reddit.com/imgur.com entirely; a GitHub Actions runner reaches Reddit but gets blocked by Reddit's own bot defense (confirmed via `.github/workflows/gmt12-fetch-rep001.yml` run). Unresolved.
- 6/9 baton markers: not started, would need own geometry + own genuine baseline (explicitly deferred earlier).
