# GMT replica marker control-set expansion — 2026-09-23

Purpose: queue additional independent 126710-series replica/QC examples for the proportional-geometry validation stage. These examples are not numerical results. Human QC observations are recorded before Watch Align measurements are examined.

## Strong multi-defect positive

### VSF GMT-Master II 126710 V3 DD3285 — July 2026 second attempt
Source: https://www.reddit.com/r/RepTimeQC/comments/1ur0ic4/qc_rolex_gmtmaster_ii_126710_vsf_v3_dd3285_second/

Independent reviewer described multiple index defects and stated they were also visible in the supplied video:
- 12 right of centre and clockwise rotated.
- 9 high and counter-clockwise rotated.
- 6 leaning left and left of centre.
- some round markers off centre.

Predeclared expected signals:
- 12 lateral displacement: `h12.centre_t`.
- 12 rotation: `h12.axis_residual_deg` / triangle orientation signal.
- 9 radial displacement: `h09.centre_r`.
- 9 rotation: `h09.axis_residual_deg`.
- 6 lateral displacement: `h06.centre_t`.
- 6 rotation/lean: `h06.axis_residual_deg`.
- round-marker displacement: corresponding `centre_r` / `centre_t`, supported by adjacent spacing residuals.

This is a high-value positive because several independently described geometric faults coexist in one watch and were reported as visible in video, reducing the chance that every observation is solely a still-photo perspective artefact.

## Useful 9-marker positive

### VSF GMT-Master II 126710 Bruce Wayne — June 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1u4prie/2nd_round_gmt_master_ii_126710_bruce_wayne_oyster/

Owner reports a persistent slight upward shift of the 9 marker after comparison with more than 50 QC examples. 12 is described as acceptable; 6 has only a very minor left bias.

Predeclared primary signal: `h09.centre_r`.
Supporting signal: local adjacent-marker spacing / left-side symmetry.

Treat as medium-strength evidence until an independent reviewer explicitly confirms the 9 displacement.

## Strong/clean negative control

### VSF GMT-Master II 126710 Batgirl — March 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1rmqk68/vsf_gmt_126710_batgirl_qc/

Multiple commenters describe alignment as clean/good and the watch as an easy GL. This is useful as a negative control because the validation should not manufacture marker faults merely because the input is a replica.

Expected result: primary marker-placement metrics should remain within or close to the empirical genuine reference once photographic uncertainty is accounted for. A systematic failure here would indicate detector/domain bias rather than useful defect sensitivity.

## Very-clean negative / subtle-9 challenge

### VSF GMT-Master II 126710 Bruce Wayne — September 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1wcjesf/qc_vsf_gmt_master_ii_126710_bruce_wayne_jubilee/

Several reviewers call this example very clean; one notes a slight clockwise tilt of the 9 marker. This is a valuable boundary case.

Predeclared test:
- `h09.axis_residual_deg` may show a small displacement.
- The system should not turn a subtle/borderline observation into a severe QC finding without separation beyond genuine and repeatability noise.

## 6-marker challenge

### VSF GMT-Master II 126710 Pepsi — June 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1ttphnt/first_qc_vsf_v3_pepsi_gmt_2_126710_please_help/

Owner flags 12/6/9. Reviewer response says 12 and 9 are subtle enough not to worry about, while the 6 is biased enough to contribute to getting another watch.

Predeclared primary signal: `h06.centre_t` and/or `h06.centre_r` according to the direction resolved from the image.
Supporting signal: `h06.axis_residual_deg` if the segmentation shows actual rotation rather than translation.

This is particularly useful for testing whether the model can distinguish a material 6-marker issue from minor 12/9 apparent offsets.

## Perspective false-positive control

### VSF GMT-Master II 126710 Pepsi — January 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1q4ongb/1st_ever_order_vsf_gmt_please_help/

Owner believed 6 and 12 were shifted left. Reviewer said removing the small tilt from the original photograph made the alignments fine.

Expected result: after pose correction, `h06.centre_t` and `h12.centre_t` should not show defect-level separation. If they do, this becomes evidence that residual pose error still contaminates the proposed metric.

## 12 lateral vs rotation discriminator

### VSF GMT-Master II 126710 — April 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1sun8dh/qc_gmt_master_ii_126710/

Owner suspected a crooked 12 triangle. Reviewer adjusted image angle and judged the index alignment acceptable, describing the 12 as more left of centre rather than noticeably clockwise tilted.

Predeclared test:
- `h12.centre_t` may move.
- `h12.axis_residual_deg` should not indicate a strong rotation if the reviewer's interpretation is correct.

This is a useful feature-disentanglement example: translation and rotation should not collapse into one generic "misaligned" score.

## Additional 6/12 challenge

### VSF V3 GMT-Master II 126710 Pepsi — August 2026
Source: https://www.reddit.com/r/RepTimeQC/comments/1vp3pbs/first_rep_qc_rolex_gmt_master_ll_126710_pepsi_gub/

Reviewer describes the 12 triangle as a fraction left of centre and the 6 bar as skewed left of centre.

Predeclared signals:
- 12: `h12.centre_t`.
- 6: `h06.centre_t`, with `h06.axis_residual_deg` used to distinguish skew/rotation from pure translation.

## Control-set principles

1. Do not use owner concern alone as strong ground truth. Store it separately from independent reviewer confirmation.
2. Keep GL/clean examples, perspective false positives, subtle boundary cases and obvious positives. A detector validated only on obvious defects is not enough.
3. Preserve physical-watch independence. Multiple images/video frames from the same QC album are repeated observations, not independent watches.
4. Do not alter the predeclared expected metric after seeing Watch Align measurements.
5. Evaluate feature-level detection first. Do not derive a global watch score from this experiment.
6. Where the human description is ambiguous between translation and rotation, test both candidate metrics but record the ambiguity in advance.
7. The strongest candidate features are those that remain stable on genuine/clean controls, reject perspective false positives, and move beyond repeatability noise on independently confirmed defects.

## Next action after genuine baseline completes

Acquire/identify the most frontal usable image for each control watch, assign stable physical-watch IDs, run the exact same pose/marker measurement path used for the genuine baseline, and produce a joined blind table containing human label, predeclared expected signal, genuine-reference deviation, repeatability margin and whether the expected signal responded.
