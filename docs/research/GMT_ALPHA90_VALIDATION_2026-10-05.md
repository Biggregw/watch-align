# GMT Alpha90 fixed-master validation

Updated: 2026-10-05

## Frozen build

Validation build: `1.3.0-alpha90-bezel-ray-proof-arm64`

Frozen source commit: `f66acee665a5afb4450fa08f61396c344be43627`

Immutable validation branch: `freeze/alpha90-validation`

Do not change the master geometry, minute-pair pose solver, acceptance thresholds, line widths, colours, bezel rays, or post-fit behaviour while this validation round is in progress. If a defect or limitation is discovered, record it here first. Code changes happen only after the validation round is reviewed.

## Validation question

Does one fixed genuine GMT master, projected using candidate camera perspective inferred from minute-track evidence, expose known real QC defects while remaining close on independently GL/acceptable watches?

The validation is specifically testing the separation:

`fixed genuine geometry + candidate camera pose -> projected reference`

The candidate must never reshape the genuine master. Applied hour markers, 12 triangle, 6/9 batons, text, date/cyclops, hands and bezel geometry are not permitted to steer the fit.

## Outcome labels

- `PASS-RL`: known independently identified defect remains visibly different from the fixed master.
- `PASS-GL`: independently accepted/GL watch does not acquire a significant false defect from the overlay.
- `UNASSESSABLE`: Alpha90 correctly refuses the photo because there are too few clean, well-spread opposing minute pairs or other pose evidence is insufficient.
- `FAIL-FOLLOW`: known defect is visually followed/absorbed by the projected master.
- `FAIL-FALSE`: a clean/GL control receives a material false mismatch.

Do not alter Alpha90 to improve an individual example during this round.

## Initial validation set

### A. Clear RL control

Source: r/RepTimeQC, GMT-Master II 126710 Bruce Wayne, thread `1ua5hfh`.

Independent QC concern: 6 marker/baton left/leaning and 12 marker/triangle displaced/rotated sufficiently for RL discussion.

Alpha90 observation from user-run screenshot:

- 6 baton visibly departs from the yellow genuine outline/white radial reference rather than being perfectly hugged by it;
- 12 shows a smaller but visible mismatch;
- most round markers remain close to the fixed master;
- overall minute-track projection remains coherent.

Status: **PASS-RL, provisional**.

Reason: the local known defect remains visible while the rest of the dial is broadly coherent, which is the expected behaviour if the fixed master is not being reshaped onto the defect.

### B. Borderline / acceptable GL control

Source: r/RepTimeQC, VSF Pepsi 126710, thread `1u6is5g`.

Independent QC context: small alignment concerns around 6/9/12 were discussed but considered acceptable/GL-level rather than an obvious RL.

First candidate photo: Alpha90 refused the image with `candidate photo has too few clean, well-spread opposing pairs`. The photo had substantial glare/obstruction and limited usable minute-track evidence.

Status for first photo: **UNASSESSABLE, correct fail-closed behaviour**.

A second clearer photo from the same watch was accepted by Alpha90. Observation:

- 6 baton remains close to the yellow genuine outline and white radial axis;
- 12 triangle remains close to the fixed master;
- 9 and round markers are broadly coherent;
- minute-track projection is consistent around the dial;
- any residuals are substantially smaller than the clear RL control.

Status for clearer photo: **PASS-GL, provisional**.

### C. Clean GL control

Source: r/RepTimeQC, Clean Pepsi V3, thread `1iud77l`.

Independent QC outcome: clean/easy GL with no significant alignment concern.

Alpha90 observation from user-run screenshot:

- 6 and 9 batons closely coincide with the fixed genuine outlines;
- 12 triangle is centred closely on the projected master;
- round-marker centres are consistently close;
- minute-track projection is coherent;
- major bezel rays do not indicate an obvious gross rotational error.

Status: **PASS-GL, provisional**.

## What the first three cases suggest

The initial pattern is encouraging but not yet enough to change production logic:

1. a known RL-level marker problem produces a visibly larger local mismatch;
2. an acceptable/borderline watch produces only small residuals when the photo is good enough;
3. a clean GL control aligns very closely;
4. Alpha90 can correctly refuse a poor photo rather than drawing a fallback overlay.

This is evidence for the concept, not proof. Continue validation before changing the algorithm.

## Locked ten-case validation queue

These cases were selected before running them through Alpha90. Do not replace a case because Alpha90 gives an inconvenient result. A case may be replaced only if the source image is unavailable or too poor for Alpha90, and that failed attempt should be recorded as `UNASSESSABLE` first.

### Additional clear-RL cases

**RL-1 — Clean Pepsi 126710BLRO, obvious CCW 6 baton**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1ktlzo1  
Ground truth: multiple commenters independently call the 6 badly canted and recommend RL.  
Target: 6 baton rotation.

**RL-2 — Clean Pepsi 126710BLRO, rotated-left 6 baton**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1jawk9m  
Ground truth: OP and replies identify the crooked 6; OP explicitly RLs for the 6 marker.  
Target: second independent 6-baton rotation case.

**RL-3 — Clean GMT 126710GRNR, tilted 12 triangle**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1gme7m2  
Ground truth: commenters identify the 12 triangle as tilted and recommend RL, although one comment notes the source picture itself is not perfectly straight.  
Target: 12 triangle rotation plus perspective robustness.

**RL-4 — VSF Pepsi 126710, 12 and 6 not centred**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1vxcmp9  
Ground truth: replies call for RL, specifically citing 12 not centred and 6 not centred.  
Target: positional rather than purely angular error.

**RL-5 — VSF Batgirl 126710BLNR, 6 CCW/left and 12 left**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1us2fqk  
Ground truth: multiple replies say the 6 is visibly wrong/awful and recommend RL; OP then RLs.  
Target: strong 6 defect with a smaller 12 deviation.

### Additional GL / borderline cases

**GL-1 — VSF Pepsi 126710BLRO, very clean easy GL**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1rb69zb  
Ground truth: photos attached directly to the post; multiple independent comments call it easy GL with nothing improperly done.  
Target: strong clean negative control.

**GL-2 — VSF Pepsi 126710, clean direct-post GL**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1rle36t  
Ground truth: commenter calls it an excellent VSF V3 Pepsi and GL; OP had wondered whether 6 was slightly off.  
Target: apparently slight 6 concern that should not become an RL-level mismatch.

**GL-3 — VSF Sprite 126720, tiny 3 tilt but GL**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1vqizfq  
Ground truth: experienced reply straightens the photo, finds 12 acceptable and only a tiny 3 clockwise tilt, then recommends GL.  
Target: perspective correction plus small accepted local deviation. Note that the Sprite is left-crown architecture, so use only if the current Alpha90 canonical orientation/model route accepts the image without silently assuming right-crown GMT geometry.

**GL-4 — VSF Batman 126710BLNR, claimed off-centre triangle but consensus GL**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1wldksl  
Ground truth: OP worried about 8 and 12, but multiple replies say the triangle is acceptable and recommend GL.  
Target: false-positive control for a user-perceived 12 issue.

**GL-5 — VF Bruce Wayne 126710GRNR, 6 concern judged acceptable**  
Thread: https://www.reddit.com/r/RepTimeQC/comments/1t5717n  
Ground truth: OP worried about 6/angle; experienced reviewer says indices are acceptably aligned and recommends GL.  
Target: borderline 6-control from a different factory.

## Rules for running the ten cases

For each case:

1. Use the clearest reasonably face-on full-watch photo available in the post.
2. Do not crop so tightly that the solver loses the full minute track; ordinary phone screenshot UI is acceptable only if the watch retains enough resolution.
3. Run the exact frozen Alpha90 APK.
4. If Alpha90 refuses the photo, record `UNASSESSABLE` rather than selecting a worse fallback image simply to force an overlay.
5. Capture one screenshot at the default bold overlay opacity.
6. Judge the overlay before rereading the detailed Reddit comments when practical.
7. Record only visible differences supported by the fixed master. Do not infer authenticity.

## Rules for reading the overlay

- Compare local candidate geometry with the projected genuine master, not with screen vertical/horizontal.
- Perspective can legitimately make a perfect projected baton non-vertical in image coordinates.
- White radial rays are visual construction references created in the canonical master before the same perspective warp.
- Yellow outlines are fixed master geometry, not candidate contours.
- Bold 4 px rendering is useful for gross inspection but may visually hide 1-2 px differences. Do not change stroke width during this validation round.
- Bezel rays are visual aids only. Bezel sits on a different physical plane from the dial, so oblique photos can show real parallax. Do not interpret small bezel-ray residuals as an automatic defect.

## Decision gate after 10 more cases

Only after the additional 5 RL + 5 GL/borderline cases are run should we decide whether the next problem is primarily:

- pose contamination (`FAIL-FOLLOW`);
- master geometry/calibration (`FAIL-FALSE` recurring at the same canonical feature);
- photo-quality gating;
- visualisation sensitivity;
- bezel-specific parallax/reference modelling;
- or whether the fixed-master principle is sufficiently validated to proceed to quantitative residual measurements.
