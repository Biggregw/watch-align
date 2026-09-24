# Watch Align QC Product Specification

Status: product source of truth for QC development

## 1. Product purpose

Watch Align is an upload-first watch QC tool. A user uploads one existing QC photograph, typically a dealer-supplied image, and Watch Align objectively assesses geometry that has been validated against genuine reference watches.

Watch Align reports evidence. It does not make the user's GL/RL decision, provide an overall QC score, or claim authenticity.

## 2. Core principles

- Objective, validated geometry only. Do not report subjective visual guesses about printing, colour, finishing, lume appearance, etc.
- A measurable difference is not automatically a finding. A deviation must also be visually meaningful in normal QC photography.
- Confidence remains internal. If evidence is insufficient, do not show a low-confidence defect.
- Do not expose raw metrics/statistics in the normal UX.
- Do not use manual alignment to rescue an unreliable analysis.
- Findings should identify measured observations first. Likely causes may be shown only when strongly supported by geometry. Factory-specific knowledge may provide secondary context but must never create a finding.

## 3. Input and analysis flow

Primary flow:

1. User uploads one existing QC photo.
2. Processing starts immediately.
3. Watch Align checks image suitability.
4. If model recognition is demonstrably stable, Watch Align suggests the model and asks: `We think this is a <model>. Is that correct?` with `Yes` / `Choose another model`.
5. If model recognition confidence is insufficient, do not guess. Ask the user to choose the model.
6. If the confidently identified model is unsupported, show `QC for this model is coming soon` and stop.
7. Run validated QC for a supported model.
8. Present results.
9. `Analyse another watch` resets to the upload flow.

No camera-capture workflow is required. No QC history is stored by the product. No redundant Analyse button is required after upload.

During processing show a simple `Analysing your watch…` state. If processing takes longer, add a restrained message such as `Detailed QC can take a little longer.` Do not show invented progress percentages.

## 4. Choosing a suitable QC photo

Prompt the user to choose the clearest dial photo from the dealer QC set, with the watch face clearly visible and viewed as straight-on as reasonably possible. Avoid heavily angled shots, strong reflections, blur, or major obstruction of the dial.

A fundamentally unsuitable image must be rejected before QC with a clear reason and a request to choose another QC photo. Examples include excessive perspective, blur, or geometry that cannot be registered reliably.

A single obscured marker does not invalidate an otherwise suitable image. Analyse what can be assessed and place the obscured component under `Could not assess`.

One uploaded photo remains the product constraint. Do not request additional photographs for SEL, case, bracelet or other checks.

## 5. Supported models

The UI may list multiple Rolex models. Models without the required validated reference/detectors should be visibly unavailable for analysis and show `QC coming soon`.

The first reference under active development is Rolex GMT-Master II 126710BLNR.

Replica factory selection is not required. The watch is compared with the genuine reference regardless of VSF, Clean, ARF, etc.

A model must not be labelled `Full QC` until the complete agreed inspection set for that model has been validated. Until then use development/limited wording that does not imply completeness.

## 6. Result headline and coverage

If findings exist:

`2 areas worth reviewing`

and name the findings immediately underneath, ordered by relevance/visual noticeability.

If no findings exist:

`No obvious issues detected`

If important areas could not be assessed, make coverage explicit:

`Partial QC: 2 areas worth reviewing`

or

`Partial QC: No obvious issues detected`

Do not show a numeric coverage/completion percentage. Put unassessed components in a separate `Could not assess` section, never mixed with actual findings.

Any credible, visually meaningful defect counts as an area worth reviewing, including minor defects. Do not add Minor/Moderate/Significant labels.

## 7. Finding organisation

- Prefer one finding per physical/root issue rather than multiplying symptoms.
- Position and rotation problems on the same marker become one alignment finding, e.g. `6 marker alignment: appears slightly low and rotated clockwise`.
- Shape/proportion defects remain separate from alignment defects, e.g. `8 marker shape`.
- Several markers exhibiting the same underlying pattern should be consolidated, e.g. `Multiple marker alignment`, with affected markers highlighted together.
- If several deviations are explained by an overall dial alignment problem, report `Dial alignment` once rather than many derivative marker findings.
- Relationship defects such as abnormal 12↔6 symmetry may be reported as `Dial marker symmetry` even if neither individual marker independently crosses a finding threshold.
- Use plain-English directions where supported: high, low, left, right, rotated clockwise, rotated anticlockwise.
- Likely cause may be added when geometry strongly supports it. Otherwise do not speculate.

## 8. Geometric datum rules

### Minute track

The minute track is the primary local datum for hour-marker alignment and is itself a QC target.

At 12 o'clock, the 60-minute marker is king. It defines the reference axis. Independently assess:

- 12 marker against the 60-minute marker
- bezel triangle/pip against the 60-minute marker
- rehaut coronet against the 60-minute marker

Do not allow one misplaced component to redefine the axis and make another misplaced component appear correct.

Every hour marker should be assessed against its corresponding minute-track datum where visible and validated. Minute-track marker radial position, angular spacing and consistency are also in scope.

### Marker geometry

Validated checks may include:

- radial and tangential position
- rotation/orientation
- neighbour spacing and overall dial rhythm
- paired/opposed-marker symmetry
- marker width, height, shape and proportions
- 12-triangle apex/centre/base relationships
- round-marker geometry
- 6/9 baton geometry

### Bezel

Bezel alignment is in scope only when Watch Align can confidently establish that the bezel is at its 12-o'clock detent. Otherwise bezel alignment is not assessed.

### Rehaut

Rehaut/coronet alignment is in scope when objectively detectable. The coronet is assessed against the 60-minute-marker datum.

### Date and cyclops

Date/cyclops geometry is in scope, presented as a single `Date alignment` finding. It requires a specifically validated cyclops-aware method because magnification, refraction, optical distortion and viewing angle can change apparent geometry. Until such a method is validated, return `Could not assess` rather than infer misalignment from a simple projection.

### Hands

Hands are eventually in scope where reliable objective measurements can be derived. Hand-to-marker/minute-track relationships may be used when the displayed time makes the relationship unambiguous. Do not force hand QC from an unsuitable hand position.

### Case, SELs and bracelet

SEL fit/gaps, case geometry and bracelet relationships are ultimately in scope where they are objectively measurable from the single uploaded photo. The app must not request extra photos to complete these checks.

## 9. Visual result UX

The overview should use the natural whole-watch image. Keep the whole watch visible rather than cropping tightly to the dial. Highlight findings with simple numbered markers corresponding to the ordered findings list. Number 1 is the most relevant/noticeable finding, not a fixed clock position.

A clean result should still show the whole-watch image prominently.

Do not perspective-warp the entire watch merely to rectify the dial. The whole-watch presentation remains visually natural. Maintain a separate rectified dial/bezel coordinate space for geometric measurement and focused evidence.

Tapping a dial/bezel finding opens a focused rectified view. It should show:

- outline of the geometry detected on the uploaded watch
- expected genuine reference geometry
- opacity slider for the genuine reference overlay
- pinch-to-zoom and pan
- optional magnified-difference mode, clearly labelled as exaggerated for visibility

Normal evidence view must always show true measured positions. Magnified difference must never be confused with actual defect magnitude.

An overview may show all detected findings. Focused views show only the selected issue or consolidated issue group.

Do not expose the original-versus-corrected view as a user toggle in the focused dial workflow. The corrected view is the evidence view once the image has passed registration.

## 10. Information intentionally omitted from normal results

Do not show:

- raw geometry metrics
- genuine distribution statistics
- QC score / 100
- confidence labels
- GL/RL recommendation
- user `I disagree` / `Not an issue` voting in v1
- technical detector diagnostics

An optional `About this finding` section may state that the check has been validated against genuine reference watches. It should not expose raw metrics or add unnecessary statistical reasoning.

## 11. Evidence and validation requirements

A check may enter user-facing QC only after:

1. its genuine reference geometry is measured from independent genuine watches;
2. the measurement is stable enough under physical-watch-level sensitivity/repeatability testing;
3. detector behaviour is reliable on dealer-style QC photographs;
4. the finding threshold represents a visually meaningful defect, not merely a statistically measurable difference;
5. known replica/control examples demonstrate that the signal can identify the intended defect without unacceptable false positives on genuine controls.

Observed genuine p10/p90 or similar empirical ranges are descriptive evidence, not automatically pass/fail tolerances.

## 12. Full QC definition

`Full QC` is an earned label for a supported model. It requires validation of the complete agreed inspection set, including, where applicable:

- all hour markers: alignment, rotation, shape/proportions
- minute track: datum relationships, spacing and consistency
- marker spacing/rhythm and symmetry
- bezel alignment
- rehaut/coronet alignment
- date/cyclops alignment
- hands and usable hand relationships
- objectively measurable case geometry
- objectively measurable SEL/bracelet relationships

Because the product accepts only one photo, a particular analysis can still be `Partial QC` when validated checks cannot be observed in that image.

## 13. Current implementation direction

The 126710BLNR genuine proportional-geometry baseline is the first empirical reference. Research outputs must remain separate from production thresholds until stability validation and replica/control separation testing are complete.

The Android visual/projective alignment work is supporting infrastructure, not the product goal by itself. The product goal is reliable objective QC from one uploaded dealer-style image, with clear visual evidence and conservative handling of anything that cannot be assessed.
