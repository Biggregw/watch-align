# Expanded RepTimeQC QC research — 2026-09-13

## Scope and method

This is a bounded, structured review of Reddit QC discussions used to guide Watch Align feature development. It is **not** a claim that every historical post in r/RepTimeQC was retrieved. Reddit/search indexing is incomplete, some albums disappear, and some posts are inaccessible to search. The objective is to capture recurring QC patterns with enough evidence to prioritise app features.

Research combined:

- the earlier structured 24-thread sample across GMT-Master II, Submariner and Datejust
- an additional broad pass across Rolex GMT/Submariner/Datejust plus Royal Oak, Nautilus and Omega examples
- a further multi-brand pass covering Tudor Black Bay, Omega Aqua Terra, IWC Mark XX, Cartier Santos/Tank, Breitling Navitimer and additional Patek Nautilus examples
- emphasis on posts where comments distinguished a genuine issue from a photo-angle/reflection false positive

The findings should be treated as empirical QC guidance, not factory tolerances.

## Strong recurring QC categories

### 1. Individual hour-marker geometry

Repeatedly discussed across Submariner, GMT and Datejust.

Common issues:

- angular cant / rotation
- radial position too far inward or outward
- tangential offset left/right from hour axis
- apparent misalignment caused by photo tilt

Representative evidence:

- VSF Submariner 124060: slight 9 o'clock clockwise tilt was noted but still considered acceptable.
  https://www.reddit.com/r/RepTimeQC/comments/1s3rrjf/qc_rolex_submariner_124060_vsf/
- VSF Submariner 124060: after straightening the source photo, experienced feedback described index alignment as good and 12 only a hair left of centre.
  https://www.reddit.com/r/RepTimeQC/comments/1tnlpqx/help_with_submariner_124060_vsf/
- Clean GMT 126710 example from earlier review: crooked 6 was a principal RL concern.
  https://www.reddit.com/r/RepTimeQC/comments/1jawk9m/
- Root Beer example reviewed in-chat: Reddit feedback called out 12-marker cant and a 5 o'clock index issue. Watch Align caught the 12 issue but missed the 5 index.

**Implication:** per-index geometry is the highest-priority missing visual feature.

## 2. Perspective/photo-angle false positives

This is one of the most consistent themes in QC discussions.

Examples:

- Submariner alignment that initially looked substantially off improved after rotating/straightening the image.
  https://www.reddit.com/r/RepTimeQC/comments/1inlax7
- A Submariner 126610 discussion concluded apparent 12/6 offset was largely due to light and angle; the rehaut was only very slightly off.
  https://www.reddit.com/r/RepTimeQC/comments/1cog5xd
- Datejust 36 discussions explicitly caution that off-axis photos can make small marker deviations look real.
  https://www.reddit.com/r/RepTimeQC/comments/1vwj6tm/qc_vsf_rolex_datejust_36/

**Implication:** fine QC claims must be gated by rectification/pose confidence. The app should downgrade or suppress sub-degree claims when perspective residuals are poor.

## 3. Date-wheel centring

Frequently a real QC concern.

Typical checks:

- horizontal centring
- vertical centring
- clipping/touching aperture edges
- consistency across multiple dates
- digit font/spacing only after position is judged

Examples:

- Datejust 41 ZF: date reported high and left; one commenter considered it RL-worthy and recommended asking for more dates.
  https://www.reddit.com/r/RepTimeQC/comments/1u1fvik/qc_first_rep_zf_rolex_datejust_41_white_dial_from/
- Omega Seamaster: date wheel visibly low in the aperture.
  https://www.reddit.com/r/RepTimeQC/comments/1s6r19f/qc_vsf_omega_seamaster/
- Datejust 41: apparent high date may have been photo angle/magnification rather than a true wheel issue.
  https://www.reddit.com/r/RepTimeQC/comments/t51vq0
- Nautilus 5711: date window itself was seriously crooked and widely considered RL-worthy.
  https://www.reddit.com/r/RepTimeQC/comments/1cruhm9

**Implication:** date aperture geometry and date glyph/group position should be measured separately.

## 4. Cyclops / crystal alignment

Cyclops appearance is often confused with date-wheel placement.

Evidence:

- GMT 126710 GRNR: owner thought cyclops was too low while also flagging rehaut alignment.
  https://www.reddit.com/r/RepTimeQC/comments/1kbd0fz
- GMT 126710 BLNR: additional photos were requested specifically because the cyclops looked off-centre while the watch face itself was rotated, making tool use difficult.
  https://www.reddit.com/r/RepTimeQC/comments/1kah0ug
- Older GMT example: cyclops looked slightly right, while the rest of the QC was broadly fine.
  https://www.reddit.com/r/RepTimeQC/comments/vfyy2f
- GMT C+F: apparent cyclops issue was explained as normal light reflection on the rounded lens.
  https://www.reddit.com/r/RepTimeQC/comments/zfwhj8

**Implication:** date-wheel position, cyclops centre, cyclops rotation, apparent magnification and lighting confidence should be separate outputs.

## 5. Bezel alignment vs bezel click/detent position

A recurring source of false positives.

Examples:

- Submariner 124060: owner thought the 30 bezel marker sat low, with commenters noting it might simply be photo angle or the bezel not being perfectly clicked into position.
  https://www.reddit.com/r/RepTimeQC/comments/1rq7mpf/vsf_124060_submariner_first_qc/
- Several earlier reviewed Submariner/GMT posts similarly distinguished insert alignment from a bezel that was one tick right or left.

**Implication:** the app should say "possible click offset" unless bezel centred-state is known. A single still image should not automatically label bezel insert misalignment.

## 6. Rehaut alignment

Commonly checked, but usually lower priority than a clearly crooked index/date issue.

Evidence:

- Submariner 126610: experienced reviewer explained that rehaut 'R' characters on the left side should align with hour markers; overall rehaut was only very slightly off.
  https://www.reddit.com/r/RepTimeQC/comments/1cog5xd
- GMT 126710 GRNR: owner flagged rehaut as noticeably off.
  https://www.reddit.com/r/RepTimeQC/comments/1kbd0fz
- Clean GMT example in prior sample had rehaut plus crooked 6 concerns.
  https://www.reddit.com/r/RepTimeQC/comments/1jawk9m/

**Implication:** rehaut should be measured against rectified dial axes, especially around 12 and 6, and reported separately from dial-index alignment.

## 7. SEL gap detection

Community reviewers repeatedly distinguish a true gap from a dark seam/shadow.

A real SEL gap is better supported when background/light/air is visibly passing through the joint. A black line alone is not enough.

**Implication:** any automated SEL feature should detect see-through/background evidence rather than simple dark-edge contrast.

## 8. Lume/marker-shape anomalies and reflections

These are visually tempting but high false-positive risk.

Prior reviewed Submariner examples showed marker/lume shape concerns that were later attributed to camera angle, glare or lighting.

**Implication:** use only as a low-confidence feature unless repeated views agree.

## 9. Hand alignment

Reddit comments repeatedly note that still photographs often cannot prove hand alignment.

Useful test convention seen in comments:

- minute hand exactly at 12
- hour hand should align exactly with an hour index

Example:

- VSF Submariner 124060 feedback explicitly said hand alignment could not be determined from the supplied photos and described the 12:00 check.
  https://www.reddit.com/r/RepTimeQC/comments/1ugbies/vsf_rolex_submariner_41_mm/

**Implication:** do not infer hand alignment from arbitrary static positions. Require a dedicated photo/video state.

## 10. Timegrapher interpretation

Frequently included in QC, but it is a separate mechanical/measurement category from image geometry.

Common values discussed:

- rate (s/day)
- amplitude
- beat error
- lift angle correctness

Important nuance: apparently low/high amplitude can be misinterpreted if lift angle is wrong or the watch is not fully wound.

Examples:

- VSF Submariner discussion: 233° amplitude accepted while noting it may not be fully wound.
  https://www.reddit.com/r/RepTimeQC/comments/1tnlpqx/help_with_submariner_124060_vsf/
- Datejust 36 discussion notes VS3235 lift-angle nuance when interpreting amplitude.
  https://www.reddit.com/r/RepTimeQC/comments/1s4dqax/qc_datejust_36mm_vsf/

**Implication:** if Watch Align adds timegrapher support, treat it as a separate module with movement-specific lift-angle context.

## 11. Model-specific geometry beyond Rolex

The broader pass strongly reinforces that Watch Align must use model/reference-specific QC profiles.

### Tudor Black Bay 58

Recurring pattern: small but systematic marker deviations are common enough that experienced reviewers judge them in a model-specific tolerance context.

Examples:

- ZF BB58: 3 marker slightly low and CW, 9 slightly CW, 6 slightly right of centre; still judged a decent GL for this model/factory.
  https://www.reddit.com/r/RepTimeQC/comments/1w1pkew/first_qc_tudor_black_bay_58/
- Earlier BB58 example specifically focused on whether the 3 marker was off.
  https://www.reddit.com/r/RepTimeQC/comments/sw2fk2

**Implication:** do not apply Rolex marker thresholds to Tudor. Add a Tudor-specific marker-position model and tolerance envelope.

### Omega Aqua Terra / Seamaster

Recurring checks:

- index alignment
- Omega symbol/logo centring and rotation
- date-window rotation and date-wheel vertical/horizontal position
- photo darkness/quality as a confidence limiter

Examples:

- Aqua Terra 150M: indices good, logo/symbol straight, but date window itself slightly CCW; reviewer said the single date was good and suggested more dates.
  https://www.reddit.com/r/RepTimeQC/comments/1v6b17p/omega_seamaster_aqua_terra_150m_qc/
- Aqua Terra: date slightly high was noted, but overall GL.
  https://www.reddit.com/r/RepTimeQC/comments/1vf2l4g/qc_omega_seamaster_aqua_terra_41mm/
- Aqua Terra: logo placement is a known concern, but a slight apparent offset can be invisible in normal wear and overblown zoom can exaggerate it.
  https://www.reddit.com/r/RepTimeQC/comments/1eqrkz4
- Aqua Terra: user concern over date-wheel centring led experienced commenters to request multiple dates rather than judge one frame.
  https://www.reddit.com/r/RepTimeQC/comments/18ptkzi

**Implication:** Omega profiles should include logo/symbol orientation and date-window rotation as first-class metrics.

### IWC Mark XX

A particularly useful example because it shows why feature selection must depend on dial construction.

- IWC Mark XX black dial: owner thought the 6 index, date and hand alignment were bad. Experienced reviewer pointed out that it is a printed dial, so the usual individual applied-index overlay logic does not apply; key checks were print bleeds, triangle straightness and date centring.
  https://www.reddit.com/r/RepTimeQC/comments/1jcbib9

**Implication:** model profiles need a `printed dial` vs `applied indices` distinction. The app should disable inappropriate index-placement overlays for printed-dial models.

### Cartier Santos

Recurring QC categories differ substantially from Rolex:

- overall dial rotation within case
- printed Roman numeral crispness
- Cartier logo and microprint at VII
- factory-specific historical quirks such as BVF floating-R discussion
- date-wheel centring on large models
- screw appearance/seat and crystal quality

Examples:

- BVF Santos 35: community repeatedly highlighted that the dial was unusually straight; printed dial/crystal/microprint were the meaningful checks and the index overlay was unnecessary.
  https://www.reddit.com/r/RepTimeQC/comments/1jqnsjc/bvf_cartier_santos_35mm_qc/
- BVF Santos 40: date-wheel position was the user's concern, while experienced feedback said printed dial, crystal and microprint were fine.
  https://www.reddit.com/r/RepTimeQC/comments/1pgkwar/bvf_cartier_santos_40mm/
- Santos 40: owner noted a possibly crooked overall dial and date 26 shifted left.
  https://www.reddit.com/r/RepTimeQC/comments/1k31y8e
- AF Santos: oversized/heavy-looking printed C was noted, but microprint is extremely small in normal wear and should not be overweighted.
  https://www.reddit.com/r/RepTimeQC/comments/1u6cyr4/cartier_santos_40mm_help_with_qc_please/
- K11 Tank: very little geometry QC applies beyond overall dial/print/crown because many standard template categories are N/A.
  https://www.reddit.com/r/RepTimeQC/comments/1eksa6y/k11_cartier_tank_small_black_template_in_comments/

**Implication:** Cartier needs dial-rotation/print quality/date centring logic, not Rolex-style marker geometry.

### Patek Nautilus

Recurring themes:

- date aperture/window rotation
- marker radial positioning
- small perimeter dot/track alignment on some references

Examples:

- 3KF Nautilus 5711 white dial: date window called seriously crooked and RL-worthy.
  https://www.reddit.com/r/RepTimeQC/comments/1cruhm9
- 3KF Nautilus 7118: concerns over 1/2 o'clock markers sitting high and perimeter dots around 10–2 not lining up evenly.
  https://www.reddit.com/r/RepTimeQC/comments/1evqutz

**Implication:** Nautilus should have a reference-specific date-window rotation metric plus marker/outer-track relationship checks.

### AP Royal Oak

Recurring concerns include:

- bezel screw angular alignment and seat depth
- AP logo lean
- double-12 symmetry
- date position

Examples:

- ZF Royal Oak 33: uneven 12 marker, leaning AP logo; previous attempts also had screw alignment problems.
  https://www.reddit.com/r/RepTimeQC/comments/1vk2a1l/qc_zf_royal_oak_33mm/
- ZF Royal Oak 15500: concern about screw recessing and date centring; commenters warned against over-relying on anecdotal gen comparison.
  https://www.reddit.com/r/RepTimeQC/comments/1jyaive

**Implication:** Royal Oak needs screw-vector/seat-depth checks and AP-logo geometry, which are irrelevant to Rolex models.

### Breitling Navitimer

The busy multi-scale dial introduces a different alignment problem.

- BLS Navitimer B01: owner identified misalignment between bezel/slide-rule/dial markings in multiple sectors, a date shifted right, and a high rate on the timegrapher.
  https://www.reddit.com/r/RepTimeQC/comments/14i7tgz

**Implication:** Navitimer requires concentric scale-registration checks rather than simple hour-marker-only analysis.

## 12. Model capability matrix should become a core architecture feature

Each model/reference profile should explicitly declare which checks are meaningful.

Suggested capability flags:

- applied_index_geometry
- printed_dial_rotation
- logo_orientation
- date_aperture_geometry
- date_wheel_centring
- cyclops_geometry
- bezel_detent_alignment
- rehaut_alignment
- sel_gap_detection
- screw_alignment
- outer_track_registration
- hand_alignment_test
- timegrapher_interpretation

The UI should hide or mark N/A for irrelevant checks instead of presenting the same generic checklist for every watch.

## Updated implementation priorities

### P0

- Per-index angular/radial/tangential geometry for applicable models
- Perspective/rectification confidence gate
- Keep raw metrics separate from classification
- Model/reference-specific calibration only
- Add model capability matrix so irrelevant checks are disabled

### P1

- Date aperture + date-wheel centring
- Cyclops/crystal centre/rotation/magnification confidence
- Bezel alignment with click/detent uncertainty
- Rehaut alignment
- Dedicated hand-alignment capture/test state
- Printed-dial overall rotation/registration
- Logo orientation/centering where model-relevant

### P2

- SEL true-gap detection
- Lume/marker-shape anomaly detection with reflection downgrade
- Timegrapher module with movement/lift-angle context
- AP screw vector/seat checks
- Navitimer concentric scale registration

## Product positioning

The evidence continues to support positioning Watch Align as:

> **Reference-based watch QC geometry analysis**

Do not present a single geometry metric as a definitive authenticity detector. A good replica can sit inside a genuine geometry cluster, while a poor genuine/rep watch can sit outside it.

## Data-quality rules for future research

For each Reddit QC example record:

- model/reference
- factory
- source URL
- issue category
- exact component/hour marker
- claimed direction of error
- severity
- GL/RL outcome
- number/agreement of experienced commenters where visible
- whether issue was dismissed as angle/reflection/click-position
- whether Watch Align currently checks it
- whether the image is straight enough to support geometry measurement

Avoid treating a single OP concern as confirmed evidence unless commenters or geometry independently support it.

## Research limitation

A five-minute-style bounded search improves breadth and confidence, but it cannot retrieve **every** r/RepTimeQC QC post because Reddit/search indexing is incomplete. This file is intended to be a persistent, expandable evidence base. Add new examples over time rather than treating it as a closed corpus.
