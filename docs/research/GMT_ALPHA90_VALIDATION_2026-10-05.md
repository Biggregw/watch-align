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

## Next validation round

Before changing any Alpha90 code, collect and run at least:

- 5 additional clear RL GMT examples with independently documented visible alignment defects;
- 5 additional GL/borderline GMT examples, including clean controls and small accepted deviations.

Prioritise direct Reddit-hosted images or otherwise accessible full-resolution images. Avoid depending on Imgur-only albums where possible because availability has been unreliable during this session.

Desired RL defect mix:

- rotated 6 baton;
- rotated/off-centre 12 triangle;
- displaced 9 baton or individual round marker;
- whole-dial/marker-set rotational concern;
- bezel insertion/printing/alignment issue.

Desired GL mix:

- very clean GL;
- slight 6 or 12 deviation still accepted;
- perspective-related apparent defect that resolves when viewed correctly;
- visually busy photo that should either remain acceptable or fail closed;
- strong replica with near-genuine geometry.

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
