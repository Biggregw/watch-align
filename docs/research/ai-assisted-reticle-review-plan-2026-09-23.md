# AI-assisted reticle review plan - 2026-09-23

Status: research plan only. Do not implement in Android or production yet.

Branch: `research/ai-assisted-reticle-review-plan`

## 1. Purpose

Test whether a vision-capable multimodal model becomes materially better at watch QC review when Watch Align first supplies trustworthy geometric context.

The proposed product architecture is:

1. Watch Align performs deterministic image alignment and renders a neutral geometric reticle.
2. A vision-capable AI reviews the original QC image together with the reticle version and, optionally, one or more genuine reference images.
3. The AI does not make an automatic GL/RL decision. It identifies visually supported areas worth human review.
4. The human remains the final decision-maker.

The research question is not whether an AI can solve watch geometry by itself. Earlier tests already showed that subtle defects can be missed when the model is asked to infer everything from a raw photo.

The research question is:

> Does external geometric guidance from Watch Align, plus carefully controlled genuine references, improve visual anomaly detection and localisation enough to be useful?

## 2. Why this is worth testing

### 2.1 Visual prompting is a real research direction

Research on multimodal large language models shows that visual prompts such as points, boxes, masks, free-form annotations, and externally generated spatial cues can improve fine-grained grounding and image understanding. This is directly relevant to a Watch Align reticle because the geometry can be encoded visually rather than described only in text.

Relevant work:

- Wu et al., *Visual Prompting in Multimodal Large Language Models: A Survey* (2024): https://arxiv.org/abs/2409.15310
- Lin et al., *Rethinking Visual Prompting for Multimodal Large Language Models with External Knowledge* (2024): https://arxiv.org/abs/2407.04681
- Lin et al., *Draw-and-Understand: Leveraging Visual Prompts to Enable MLLMs to Comprehend What You Want* (2024): https://arxiv.org/abs/2403.20271

The practical implication for Watch Align is that the reticle can act as external spatial knowledge supplied directly in the image.

### 2.2 Reference images can help, but must be controlled

Research on visual in-context learning and few-shot anomaly detection supports the idea that one or more visual references can improve specialised visual reasoning, although results are model-dependent and references must be selected carefully.

Relevant work:

- Zhou et al., *Visual In-Context Learning for Large Vision-Language Models* (2024): https://arxiv.org/abs/2402.11574
- Doveh et al., *Towards Multimodal In-Context Learning for Vision & Language Models* (2024): https://arxiv.org/abs/2403.12736
- Jiang et al., *Many-Shot In-Context Learning in Multimodal Foundation Models* (2024): https://arxiv.org/abs/2405.09798
- Li et al., *FADE: Few-shot/zero-shot Anomaly Detection Engine using Large Vision-Language Model* (2024): https://arxiv.org/abs/2409.00556

The implication is that a genuine watch reference may help the AI understand normal geometry, but a single random genuine photo can also mislead it because of angle, lighting, reflections, focus, crop, hands, and crystal differences.

### 2.3 Human-in-the-loop is appropriate for this product

Recent industrial defect-detection reviews continue to identify human-in-the-loop inspection, calibration, difficult imaging conditions, and false-positive control as important practical concerns.

Relevant work:

- Nahar et al., *AI-enabled defect detection in industrial products: A comprehensive survey, key insights and future research challenges* (2026): https://www.sciencedirect.com/science/article/pii/S1474034625009607
- Cheng et al., *A comprehensive survey for real-world industrial surface defect detection* (2026): https://www.sciencedirect.com/science/article/abs/pii/S0278612525002845

This matches the proposed product philosophy: geometry and AI should draw attention to anomalies, not pretend certainty where the image does not support it.

## 3. Core design principle

The AI must never be asked to reconstruct the watch geometry from scratch if Watch Align has already computed a trustworthy reticle.

Instead, the model should be given evidence in layers:

- raw dealer QC image
- the exact same image with the frozen neutral reticle
- optional genuine reference image or images
- optional genuine reference rendered with the same reticle
- optional local crops for regions of interest

The reticle must remain neutral. It must not pre-highlight a marker as defective during the main A/B experiment because that would leak the expected answer to the AI.

## 4. Image packet variants to test

Use the same underlying query image and the same AI model/prompt structure for all conditions.

### Condition A - Raw only

Input:
- raw dealer QC image

Purpose:
- baseline for what the AI can detect unaided

### Condition B - Raw + reticle

Input:
- raw dealer QC image
- same image with frozen Watch Align reticle

Purpose:
- test whether geometric visual prompting improves anomaly detection/localisation

### Condition C - Raw + genuine reference

Input:
- raw dealer QC image
- genuine reference image

Purpose:
- test value of a visual normal reference without the reticle

### Condition D - Raw + reticle + genuine reference

Input:
- raw dealer QC image
- reticle image
- genuine reference image
- genuine reference with equivalent reticle where possible

Purpose:
- test the combined architecture

### Optional Condition E - Matched-view genuine reference

If technically feasible, transform or select a genuine reference to approximately match the query's viewpoint, crop, scale, and orientation.

Do not fabricate model-specific geometry. The purpose is only to reduce irrelevant photographic differences before AI comparison.

### Optional Condition F - Multiple genuine references

Use 3 to 5 carefully selected genuine examples representing normal manufacturing and photographic variation.

The AI should compare against a normal range rather than treating one genuine photo as perfect ground truth.

## 5. Genuine-reference strategy

Do not use a random genuine image from the internet as the only reference.

Preferred reference set:

- confirmed correct model/reference
- clear dial visibility
- known provenance where possible
- minimal artistic perspective or heavy post-processing
- representative straight-on and moderate-angle examples
- no obvious QC anomaly

For a production architecture, maintain a curated reference library per supported model.

The AI prompt must explicitly state:

- genuine references are normal visual references, not proof of authenticity
- ignore colour, lighting, finishing, reflections, sharpness, crop, hand position, and photographic style unless the task specifically concerns them
- compare geometric relationships only when the reticle supports the comparison

## 6. Prompt design

Use a highly constrained prompt. Do not ask for an open-ended watch review.

Suggested structure:

### Role

You are reviewing a watch QC image using geometric guidance supplied by Watch Align.

### Evidence hierarchy

1. Visible watch pixels are primary evidence.
2. The Watch Align reticle supplies geometric reference lines only.
3. Genuine images show examples of normal geometry and natural variation.
4. Do not assume a deviation is a defect unless it is visibly supported.

### Required checks

Look specifically for:

- marker rotation relative to radial guide
- marker centre displaced clockwise/counter-clockwise from its hour axis
- marker unusually inward/outward relative to visible envelope geometry
- 12/6 inconsistency
- 3/9 inconsistency
- dial rotation relative to minute track
- bezel 12/pip alignment where visible
- obvious asymmetry or marker shape inconsistency

### Prohibitions

Do not:

- judge authenticity
- infer factory
- give GL/RL
- invent measurements
- report sub-pixel or percentage precision unless supplied by Watch Align
- treat glare, blur, crystal distortion, or hand occlusion as a defect without clear evidence
- use a genuine reference's colour/lighting/finish as a defect signal

### Output

For each supported observation return:

- location, preferably hour position
- short description
- confidence: low / moderate / high
- evidence source: reticle / genuine reference / both
- human review recommended: yes/no

If evidence is weak, return `no clear issue` for that location.

A structured JSON output should be tested in the experiment so results can be scored automatically.

## 7. Critical negative controls

The AI must be tested for blind trust in the overlay.

Include several deliberate controls:

### Slightly misaligned reticle control

Offset or rotate the reticle by a small known amount while keeping the watch image unchanged.

Question:
- does the AI incorrectly report the watch as defective because the overlay is wrong?

A robust reviewer should notice broad/global mismatch or lower confidence rather than accuse many markers individually.

### Wrong genuine reference control

Provide a visually similar but intentionally mismatched reference model in a clearly labelled research-only test.

Question:
- does the AI overfit to the reference rather than rely on Watch Align geometry?

### Overlay-only leakage control

Ensure the reticle itself contains no coloured defect highlights, anomaly labels, scores, or expected answer cues during the main experiment.

### Synthetic defect controls

Create known controlled changes such as:

- rotate one baton
- shift one round marker tangentially
- shift one marker outward/inward
- rotate dial relative to minute track

These provide exact ground truth for sensitivity testing.

## 8. Test dataset

Use three evidence classes.

### A. Synthetic controlled defects

Start from real usable images and create small known perturbations.

Advantages:
- exact ground truth
- lets us measure sensitivity versus defect magnitude
- useful for testing prompt/overlay effects

### B. Real independently labelled QC examples

Use examples where multiple human reviewers independently identified the same defect.

Do not use AI-generated labels as ground truth.

### C. Negative controls / apparently normal images

Include genuine and replica images with no independently verified placement defect.

This is essential for measuring false-positive rate.

Keep physical watches identifiable so repeated photographs are not counted as independent watches.

## 9. A/B evaluation design

For every test example, run Conditions A-D with:

- same vision model
- same model settings
- same core wording
- same image resolution where possible
- blinded filenames and metadata

Randomise condition order if the harness/model session could retain conversational context.

Prefer independent API calls per condition to avoid cross-condition leakage.

Primary metrics:

- defect-level recall
- defect-level precision
- false-positive rate on controls
- correct hour/location identification
- confidence calibration
- abstention rate
- repeatability on repeated photos of the same physical watch

Secondary metrics:

- does the reticle improve subtle defects more than obvious defects?
- does a genuine reference add value beyond the reticle?
- do multiple genuine references outperform one?
- does the model become over-dependent on the reference image?

The key result is not prose quality. It is whether the AI more reliably directs a human to the correct region without creating additional false alarms.

## 10. Proposed staged experiment

### Stage 0 - Finish and freeze reticle

Do not test AI while the overlay geometry/design is still changing.

Freeze one recommended reticle variant first.

### Stage 1 - Small proof of concept

Use approximately 20 to 30 examples:

- known good controls
- obvious defects
- subtle synthetic defects
- a few real labelled defects

Compare raw only versus raw + reticle.

Decision gate:
- continue only if the reticle materially improves localisation/recall without unacceptable false positives.

### Stage 2 - Genuine reference ablation

On the same frozen examples compare:

- raw + reticle
- raw + one genuine reference
- raw + reticle + one genuine reference
- raw + reticle + several genuine references

Decision gate:
- retain genuine references only if they improve results beyond reticle alone.

### Stage 3 - Larger blinded calibration study

Use a larger calibration-only dataset with physical-watch grouping and blinded labels.

Do not inspect final validation during prompt development.

### Stage 4 - Locked validation

Freeze:

- reticle
- reference-selection logic
- prompt
- output schema
- confidence interpretation

Then run validation once.

## 11. Product architecture if successful

Recommended product flow:

1. User uploads one or more dealer QC images.
2. Watch Align locates and aligns the dial.
3. Watch Align generates a neutral visual reticle.
4. Optional reference service selects appropriate genuine examples.
5. Vision AI receives raw + reticle + reference packet.
6. AI outputs candidate areas for attention with confidence and explanation.
7. Watch Align highlights only those areas as `worth a closer look` or similar.
8. User examines the actual image and makes the decision.

Do not present AI output as an authenticity decision or an automatic reject decision.

## 12. Multi-photo extension

Dealer QC usually contains several photographs of the same physical watch.

If single-photo AI review is useful, test multi-photo aggregation next:

- review each photo independently
- map findings to physical marker/hour positions
- increase confidence only when independent photos support the same anomaly
- downgrade isolated one-photo findings

This directly addresses the poor single-photo repeatability observed in the projective-marker-consensus research.

## 13. Risks

### Hallucinated precision

The model may invent exact measurements. Prevent with prompt constraints and avoid displaying unsupported numbers.

### Overlay anchoring

The model may trust a wrong overlay. Test deliberately misaligned-reticle controls.

### Reference anchoring

The model may treat ordinary photographic differences as defects. Test reference ablations and multiple genuine references.

### Resolution limits

Small marker errors may disappear after image resizing. Test full image plus model-generated or deterministic high-resolution crops.

### Occlusion/reflection

Hands, cyclops, glare, crystal reflections and highly reflective metal can confuse both CV and AI. Permit abstention.

### Data leakage / circular evaluation

Do not build prompt wording from validation examples. Keep calibration and validation separated.

## 14. Recommended first implementation

After the reticle is frozen, build a Python-only research harness that:

- takes query image + reticle image
- optionally takes 1 to N genuine references
- constructs the image packet and fixed prompt
- calls one selected vision-capable model
- saves structured JSON and raw response
- scores against blinded ground truth
- produces A/B tables for Conditions A-D

Do not build Android/API billing/UI yet.

## 15. Success criteria

Consider the architecture worth continuing only if at least one assisted condition clearly improves on raw-only review in blinded testing.

The desirable pattern is:

- higher defect recall
- equal or lower false-positive rate
- better localisation to the correct hour/region
- better consistency across repeated photos
- sensible abstention when evidence is weak

The ideal result would be that `raw + reticle + controlled genuine references` materially outperforms `raw only`, but the experiment must be allowed to show that genuine references add no value or even hurt performance.

## 16. Immediate next step

Finish the current generic-reticle prototype first.

Once one reticle is selected and frozen, run Stage 1 only:

> raw image versus raw image + neutral reticle

Do not add genuine references until the overlay's own contribution has been measured independently.

This isolates causality and prevents a four-part architecture from being adopted before we know which component actually helps.