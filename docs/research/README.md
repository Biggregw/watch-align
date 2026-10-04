# Historical research archive

Everything under `docs/research/` is preserved as **historical evidence** unless a current governing document explicitly promotes a finding.

These files contain useful measurements, failed experiments, prior thresholds, alpha-era next steps and implementation notes. They are deliberately retained so useful evidence is not lost, but their old roadmaps and `next step` sections are **not active instructions**.

Current direction is defined only by:

- `../PRODUCT_SCOPE.md`
- `../CALIBRATION_PROTOCOL.md`
- `../HANDOFF.md`
- `../architecture/QC_PRINCIPLES.md`
- `../../AGENTS.md`

## How to reuse historical evidence

- Reuse existing images/artifacts before acquiring more.
- Treat old numerical results as controls or evidence, not unquestioned production tolerances.
- Revalidate a historical feature under the current calibration protocol before changing production behaviour.
- Preserve negative findings; they help avoid repeating failed approaches.
- Do not restart an old research programme merely because a document says `next` or `GO`.

The first active control experiment is an offline recalibration of one already-working GMT feature using the current calibration protocol. Production GMT remains unchanged while that experiment runs.
