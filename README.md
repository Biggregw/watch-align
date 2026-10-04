# Watch Align

Watch Align is an Android QC tool for analysing **uploaded dealer/QC watch photos** against a proven-genuine visual reference.

The product question is:

> **How does this particular watch differ from the genuine reference in the features that can be measured reliably from this photo?**

It is **not** an authenticity classifier. A high-quality replica may show no detectable deviation in the measured features, and that is a valid result.

## Current baseline

`main` is the clean restart baseline. The existing GMT implementation remains the known-working control and must not be changed until the new calibration method has been validated offline against it.

The active direction is documented in:

- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md)
- [`docs/architecture/QC_PRINCIPLES.md`](docs/architecture/QC_PRINCIPLES.md)
- [`docs/CALIBRATION_PROTOCOL.md`](docs/CALIBRATION_PROTOCOL.md)
- [`docs/HANDOFF.md`](docs/HANDOFF.md)

Historical research under `docs/research/` is evidence only. It does not define the current plan.

## New research order

1. **Nominal master first.** Start from the best available proven-genuine, near-frontal image for the feature being studied. A clean loose dial is ideal when available because the movement hole gives a direct centre and crystal/rehaut distortion is absent.
2. **Measure variation around the master.** Use independent genuine physical watches to estimate real variation. Multiple photos of the same watch estimate measurement/photo error, not manufacturing variation.
3. **Use relationships to detect perspective contamination.** Opposing markers should be checked together. If one side expands while the opposite side compresses in a coherent way, treat that first as a pose signal rather than widening the genuine manufacturing envelope.
4. **Freeze the genuine envelope.** Replica data never defines or widens the genuine reference range.
5. **Validate on real QC defects.** Use independent RepTimeQC cases whose defects were identified by reviewers. Measure blind to the stated defect where practical, then check whether the frozen genuine calibration reproduces it. Also test accepted/GL examples to control false positives.
6. **Choose implementation per feature.** A check may ultimately be deterministic code, a vision-AI call, a hybrid, or not reliable enough to ship. Do not decide this before validation.

## Research discipline

- Use existing project images and artifacts before searching for new data.
- Prefer a quick offline experiment over a GitHub Actions/calibrator run.
- Do not modify production code merely to answer a research question.
- Do not start large corpus expansion because a result is uncertain. First ask whether a small, well-chosen sample can answer the question.
- Version the reusable research prompt/protocol and improve it by checking whether it reproduces measurements we already know.
- Only move a proven research rule into production code after it has demonstrated useful sensitivity without unacceptable false flags on genuine/clean examples.

## First restart experiment

Recalibrate a **single known-working GMT marker feature** using the new protocol and compare the result with the existing GMT calibration. This is the control experiment for the new research method.

No broad Submariner expansion, new calibration platform, projective-pose programme, or classifier work should begin until that experiment succeeds.

## Build

The supported application is in `android/`.

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Use JDK 17 and the committed Gradle wrapper.
