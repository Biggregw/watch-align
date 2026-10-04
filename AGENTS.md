# Watch Align agent instructions

Read these before substantial work:

1. `docs/PRODUCT_SCOPE.md`
2. `docs/CALIBRATION_PROTOCOL.md`
3. `docs/HANDOFF.md`
4. `docs/architecture/QC_PRINCIPLES.md`

## Product guardrails

- Watch Align analyses uploaded dealer/QC photos. Do not redesign it around guided capture or multiple runtime photos.
- The product reports **deviation from a proven-genuine reference**. It is not a genuine/fake classifier.
- A replica may legitimately show no detectable deviation in the features assessed from a photo.
- Genuine data defines the reference envelope. Replica data is validation/stress evidence only and must never move a genuine-derived boundary.
- Preserve the existing working GMT behaviour until a replacement calibration method has been proven offline.

## Research-first rule

Before changing production code, answer the research question outside production code if possible.

Preferred order:

1. reuse existing project images/artifacts;
2. run a small offline measurement experiment;
3. compare against a known result or held-out reference;
4. tighten the measurement prompt/protocol;
5. repeat on fresh/held-out images;
6. only then implement a proven rule in the app.

Do **not** use GitHub Actions, a full calibrator run, an APK build, or a new architectural layer merely to answer a question that can be tested with a few images and a local script/notebook.

## Calibration rules

- Start with the best available proven-genuine near-frontal image as the nominal master for the feature.
- A clean loose dial is preferred when it materially improves geometry, but do not delay work searching for one if existing images already answer the question.
- Separate nominal design geometry, genuine watch-to-watch variation, and photo/detector uncertainty.
- Treat multiple photos of one physical watch as repeatability evidence, not independent watches.
- Check opposing or related markers for coherent pose distortion before allowing an outlier to widen the genuine envelope.
- If one side expands while the opposite side compresses in a pattern consistent with perspective, first classify it as photo contamination. Correct, pair, or exclude the affected raw value instead of enlarging tolerance.
- Keep marker position separate from marker size. Do not let uncertain lume/white-gold thresholding contaminate a centre-position measurement.
- Normalize to a clearly defined dial boundary; do not silently switch between dial/rehaut, crystal or bezel edges.
- Report exclusions and uncertainty rather than guessing.

## Golden prompt development

`docs/CALIBRATION_PROTOCOL.md` is versioned research infrastructure.

When the protocol is tested against a feature whose answer is already known:

- record the independent result;
- compare it quantitatively with the established result;
- diagnose the error source;
- improve the protocol;
- retest on held-out images rather than tuning only to the original sample.

A protocol version is promoted only when it repeatedly reproduces known measurements without hidden manual fitting to the answer.

## Validation against replica QC

After a genuine envelope is frozen:

- use independent RepTimeQC examples with a clearly stated visible defect;
- keep the defect label separate while measuring where practical;
- test whether the frozen genuine-reference comparison finds the same issue;
- also run accepted/GL examples to measure false positives;
- classify the eventual implementation per feature as deterministic code, vision AI, hybrid, or not reliable enough.

The success criterion is useful specimen-specific QC, not universal replica-vs-genuine separation.

## Current task order

1. Recalibrate one known-working GMT feature with the new protocol.
2. Compare it with the existing GMT result.
3. Improve and freeze the reusable prompt/protocol.
4. Expand to other GMT features only if the control succeeds.
5. Validate against real RepTimeQC defects and accepted controls.
6. Only then revisit Submariner calibration/production changes.

Do not restart the old measurement-contract, coverage-funnel, large-corpus, or projective-refinement programmes unless a new validated feature proves they are necessary.

## Engineering/build rules

- Supported app: `android/`.
- Use JDK 17 and the committed Gradle wrapper.
- Do not upgrade dependencies/toolchains during unrelated work.
- Diagnose the first underlying failure; do not disable tests or weaken checks to get green CI.
- Prefer small, reversible changes.
- Do not create long stacked PR chains. One focused proven change at a time.
- Do not commit/push/build/run CI unless the user asked for it or it is genuinely required by the approved implementation step.
- Historical branches and `docs/research/` are evidence, not active instructions.
