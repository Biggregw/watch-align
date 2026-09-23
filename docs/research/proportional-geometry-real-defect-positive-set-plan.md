# Proportional Geometry - Real Defect-Positive Set Plan

Status: live supplemental research

Date: 2026-09-23

Purpose: define a future real-photo positive-control set from r/RepTimeQC without contaminating current genuine calibration work. The goal is to test whether frozen proportional rules recover defects that multiple human reviewers independently identified in real GMT QC photos.

This file is a planning/label document only. Do not use these examples while tuning the genuine baseline or selecting features.

## 1. Why a real positive set is needed

Synthetic displacements are useful because their ground truth is exact, but they do not fully represent:

- real dealer lighting,
- reflections,
- hand occlusion,
- imperfect focus,
- real marker segmentation failure,
- compound defects,
- camera-angle ambiguity.

Community QC can provide a second kind of evidence: real photographs with independently described geometric concerns.

However community comments are not manufacturing ground truth. They should therefore be treated as human-labelled positive candidates, not absolute truth.

## 2. Strict inclusion rule

Prefer a real-positive case when at least one of these is true:

1. poster and at least one independent experienced reviewer identify the same marker and same broad direction/type, or
2. two independent commenters identify the same marker issue, or
3. one detailed reviewer identifies a specific geometric issue and the image has a clear enough unobstructed view for later human adjudication.

Record disagreement explicitly.

Do not convert GL/RL preference into a geometry label. The label should be a concrete observation such as:

- `6 centre left`,
- `12 slight CW tilt`,
- `9 slight CW tilt`,
- `date high`,
- `12 centre right`.

## 3. Candidate cases already present in the Watch Align manifest

### A. `rep_vsf_7s6PyXJ`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1u1h4ix/`

Album:

`https://imgur.com/a/vsf-v3-gmt-master-ii-batgirl-7s6PyXJ`

Human-described geometry:

- poster: 6 marker visibly shifted left,
- reviewer: 6 bar really left of centre,
- reviewer: 12 triangle mini clockwise tilt and slightly left of centre,
- date described as slightly/high-ish but acceptable.

Research value:

- strong candidate for a real tangential-translation positive at 6,
- weaker secondary 12 rotation/translation case,
- useful test of whether common-mode pose correction preserves the 6 local residual.

Keep in the existing validation split if it is already there. Do not move it into calibration.

### B. `rep_vsf_gpZWOfy`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1ucww0c/`

Album:

`https://imgur.com/a/gpZWOfy`

Human-described geometry:

- 12 triangle a hair right of centre,
- 9 bar mini clockwise tilt,
- 6 bar tiny counter-clockwise cant,
- later follow-up comments call 9 clearly canted and mention another canted marker.

Research value:

- multi-marker compound case,
- strong test of global/common-mode versus truly local residuals,
- useful 9-baton rotation candidate.

Because several markers are implicated, do not use this case to derive pose/feature parameters.

### C. `rep_vsf_p3hHVMB`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1u91yef/`

Album:

`https://imgur.com/gallery/vsf-batgirl-qc-p3hHVMB`

Human-described geometry:

- poster describes 12 as slightly left/counter-clockwise,
- poster explicitly raises photo angle as a competing explanation.

Research value:

- borderline/ambiguous 12 case,
- especially useful for false-positive control and pose-angle guardrails,
- should not be treated as a strong positive unless independent comments support the same geometry.

### D. `rep_vsf_KfRKFwA`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1tf4uos/`

Album:

`https://imgur.com/a/KfRKFwA`

Human-described geometry:

- reviewer: 12 slightly left of centre,
- reviewer: date a tick left in the window,
- poster initially thought index alignment looked good and was more concerned about date/window perspective.

Research value:

- subtle tangential 12 case,
- date-window centring candidate for later local aperture work,
- useful example where the human defect signal is small rather than dramatic.

### E. `rep_vsf_oVwWMrC`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1u2z5mg/`

Album:

`https://imgur.com/a/vsf-batgirl-oVwWMrC`

Human-described geometry:

- poster suspects 6 misaligned to the right,
- one shown date (`19`) suspected left while other dates looked normal.

Research value:

- potential 6 tangential-placement candidate,
- illustrates why date-wheel assessment should use multiple dates rather than one numeral pair.

Do not promote to strong positive without independent confirmation.

## 4. Additional recent real-positive candidates not yet necessarily in manifest

### F. September 2026 VSF Batgirl, thread `1wiweof`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1wiweof/`

Independent review comments report:

- 12 slanted counter-clockwise,
- 6 bar left of centre,
- 9 bar small counter-clockwise tilt.

Research value:

- strong multi-marker geometry-positive candidate,
- valuable for testing common-mode pose decomposition because several markers are flagged in one photo set.

### G. September 2026 VSF Batgirl, thread `1w7ba8a`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1w7ba8a/`

Human-described geometry:

- reviewer: 12 slight clockwise tilt,
- reviewer: one shown date high in the window.

Research value:

- clean 12 orientation candidate,
- later date-centering candidate.

### H. August 2026 VSF Batgirl, thread `1vmpk0d`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1vmpk0d/`

Human-described geometry:

- 12 slight counter-clockwise tilt,
- 9 tiny counter-clockwise tilt,
- 6 micro counter-clockwise twist,
- date a tick low.

Research value:

- multi-marker small-defect case,
- tests sensitivity near the practical human-review threshold rather than only obvious errors.

### I. September 2026 VSF Batgirl, thread `1w9fsh3`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1w9fsh3/`

Human-described geometry:

- poster: 6 clearly tilted/left,
- concern remains visible after mentally/visually correcting image rotation.

Research value:

- strong 6 marker candidate,
- good test of incidence-first 6-baton logic.

### J. September 2026 VSF Batgirl, thread `1w7u8xu`

Source:

`https://www.reddit.com/r/RepTimeQC/comments/1w7u8xu/`

Human-described geometry:

- poster: 9 slight CCW, 12 and 6 slightly left,
- reviewer agrees 6/12 both left of centre and identifies 6 as the main decision point.

Research value:

- strong test of whether 6 remains locally abnormal after global common-mode correction,
- distinguishes one major marker issue from smaller correlated offsets.

## 5. Proposed label schema

For each real-positive candidate store one row per claimed marker/feature:

- `source_id`
- `physical_watch_id`
- `thread_url`
- `album_url`
- `marker_id`
- `feature_family` (`radial`, `tangential`, `orientation`, `size`, `date_centering`, etc.)
- `direction` (`inward`, `outward`, `CW`, `CCW`, `left`, `right`, `high`, `low`)
- `poster_claim`
- `independent_reviewer_count`
- `reviewer_agreement`
- `photo_angle_ambiguity`
- `hand_occlusion`
- `human_label_strength` (`strong`, `moderate`, `borderline`)
- `notes`

Do not store GL/RL as the target label.

## 6. Recommended strength levels

### Strong

- same concrete geometry issue independently identified by at least two people, or
- very obvious marker displacement with reviewer agreement.

### Moderate

- one detailed reviewer plus poster agreement,
- clear geometry but limited independent confirmation.

### Borderline

- slight issue,
- explicit angle ambiguity,
- disagreement,
- partly obscured marker.

Use borderline cases to test false positives and confidence calibration, not only recall.

## 7. How to use the set later

After genuine calibration features and thresholds are frozen:

1. run the pipeline blind on each candidate photo,
2. do not expose Reddit labels to the measurement pipeline,
3. compare predicted marker/location/type with the stored human label,
4. score exact marker localisation separately from broad anomaly detection,
5. record abstentions,
6. inspect whether common-mode pose correction removes false positives without erasing real local issues.

Suggested metrics:

- correct marker recall,
- correct broad defect-family recall,
- direction agreement,
- false findings on other markers,
- abstention rate,
- result repeatability across multiple views of the same watch.

## 8. Preserve validation independence

Several useful positive cases already belong to the current validation split.

Do not inspect their measurement results or tune rules against them while calibration work is active.

This document may record public human labels in advance, but the implementation must not use those labels to change feature definitions, geometry bands or thresholds.

The correct order is:

1. freeze genuine-derived rules,
2. freeze rule thresholds/interpretation using calibration-only or synthetic development data,
3. then open the real-positive holdout for sensitivity testing.

## 9. Important limitation

Reddit QC consensus is not objective manufacturing truth.

Its value is different:

- it identifies the kinds of visual geometry humans actually care about,
- it provides challenging real-photo examples,
- it provides an external target for whether Watch Align can surface the same region for human review.

The product goal should therefore be framed as `surface geometry worth checking`, not `prove Reddit was right`.
