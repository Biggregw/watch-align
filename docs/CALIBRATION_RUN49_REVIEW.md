# Calibration run 49 review

This note records the post-run inspection of PR #45 workflow run 49 and the changes it makes to the calibration-platform plan.

## Run inspected

- workflow run: `37111597441`
- artifact: `watch-calibrator-124060-37111597441`
- artifact id: `11270124737`
- artifact digest: `sha256:72921a6e8527275226dfdceacdf334f7c8ece871f454859c6d3c30ff8e29340f`
- PR head: `9ba8b896efff9ed6c13ac83cd71761ab77697073`
- result: success
- calibration state: `GENUINE_ENVELOPE_READY`

The first hardening slice therefore passed end-to-end with the new model/config/measurement boundary checks, frozen config and run manifest in place.

## What remained stable

The discovery candidate rows and locked watch-level split were identical between run 44 and run 49. Source diversity also remained identical: 41 acquired genuine watches, five qualifying genuine sources, and 29.2683% dominant-source share.

Most importantly, all seven final genuine-envelope calibration bands were numerically unchanged between run 44 and run 49. The hardening code therefore did not alter the production calibration limits.

The new run manifest also correctly hashes the frozen config and `acquired_images.csv`; independently recomputing both SHA-256 values from the artifact matched the recorded values.

## Critical finding: the evidence bytes were not actually frozen

Although the discovery rows and watch split were identical, the image bytes were not.

Both artifacts contain 385 acquisition rows, but 115 corresponding image slots had a different SHA-256 in run 49 than in run 44 while retaining the same source/image slot. The drift was concentrated in public dealer/CDN sources:

- Bob's Watches: 16 of 82 image rows changed bytes
- SwissWatchExpo: 47 of 60 changed bytes
- DavidSW: 52 of 70 changed bytes
- European Watch Company: 0 of 56
- Watches of Switzerland Rolex CPO: 0 of 60
- Phillips: 0 of 2
- replica seed images: 0 of 55

For the changed rows, source URLs and dimensions were generally unchanged while byte sizes and SHA-256 values changed, consistent with CDN re-encoding or rendition changes rather than a new discovered watch population.

The exact-duplicate count consequently changed from 11 to 12, and the usable non-duplicate genuine-photo count changed from 319 to 318.

This is direct evidence that a manifest of URLs plus an Actions image cache is not an immutable calibration evidence snapshot.

## Resulting diagnostic drift

The final calibration bands remained unchanged, but the evidence drift changed diagnostics and coverage. Examples include:

- development photos: 194 -> 192
- development photos with any gated value: 44 -> 42
- `round.ring_rho`: 61 photos / 29 watches -> 60 photos / 28 watches
- `round.spacing_rms_deg`: 61 / 29 -> 60 / 28
- `twelve.centring_w`: 60 / 30 -> 58 / 29
- `baton.3_9_line_offset_r`: 29 photos -> 30 photos
- `twelve.gap_r` gained a development pose-sensitive flag
- `axis.12_6_line_offset_r` gained a development pose-sensitive flag
- several within-watch repeatability diagnostics changed

There were 40 scalar/list differences in `calibration.json` even though the final clear/check bands were unchanged.

This proves that live reacquisition cannot be used as the replay-quality gate for detector or architecture changes.

## Run-manifest provenance finding

The run manifest records `repo_commit` from `GITHUB_SHA`. For a pull-request workflow this is the GitHub-generated merge commit. In run 49 it recorded `52fc8dca3605f1bf150ef6e8c3c5653d93143868`, whereas the PR head tested was `9ba8b896efff9ed6c13ac83cd71761ab77697073`.

The merge SHA is useful because it identifies the exact checkout that executed, but it is not enough by itself for long-term provenance. Future manifests should record separately:

- exact checkout/merge SHA
- PR/head SHA
- base SHA
- workflow run id and event
- relevant source-tree or measurement-source fingerprint

Paths inside the manifest should also be artifact/repository-relative rather than only runner-absolute paths.

## Plan change

The broad architecture remains correct, but the sequence should change.

Run 44 remains the **numerical baseline**, not a true deterministic evidence-replay baseline, because its artifact did not preserve the original image bytes. Run 49 has the same limitation.

Before further measurement-adapter refactoring, 126610 work, baton reliability changes, or the fast geometric preflight becomes a hard gate, the next foundation slice should create a real immutable evidence snapshot and deterministic replay path.

### Revised immediate sequence

1. Keep run 44 as the accepted numerical baseline and run 49 as proof that the first hardening slice preserves final bands.
2. Extend the manifest to record checkout SHA, head SHA, base SHA, workflow/run identity, portable paths, and a source/measurement fingerprint.
3. Create a versioned immutable evidence snapshot containing the exact acquired image bytes in a content-addressed store plus a canonical manifest of physical watch, model, source, image SHA, duplicate-cluster state and relative path.
4. Compute one snapshot identity from the canonical manifest and content hashes. Verify every byte before replay.
5. Add a replay mode that performs **no discovery and no network acquisition** and reads only the frozen snapshot.
6. Create a new `124060 evidence baseline v1` snapshot. It does not need to reproduce historical run-44 image bytes, which are no longer recoverable from the artifact; instead its calibration must be compared with the accepted run-44/run-49 numerical bands and any differences explained before it becomes the deterministic replay baseline.
7. Run the same frozen snapshot twice and require byte-identical measurement inputs and deterministic calibration output apart from explicitly non-semantic metadata such as timestamps.
8. Only after replay is proven, refactor to the measured-only versioned adapter contract and require equivalence on the frozen snapshot.
9. Then implement the measurement coverage/rejection-reason contract, per-metric sufficiency rules, acquisition adapter generalisation and perceptual duplicate clustering.
10. Benchmark and then enable the fast geometric preflight against that same frozen snapshot with the protected-positive rule.
11. Fix the 124060 relational baton reliability path and recalibrate against the frozen evidence.
12. Only then expand to 126610LN/LV and family-comparison work.

## Cache rule

Actions caches may improve acquisition speed, but they must never define production evidence identity. A replay must not restore a mutable 'latest image cache' and then treat it as the frozen corpus.

Acquisition/refresh may use caches opportunistically. Promotion/replay must consume only the immutable content-addressed evidence snapshot, with hash verification and no network fallback.

## Consequence for the fast geometric preflight

This review strengthens the earlier preflight plan. The preflight should be benchmarked against the exact frozen image bytes, not a later download of the same URLs. This is necessary because the dealer/CDN evidence has now demonstrated that the same URLs can return materially different encoded bytes between runs.

The previously observed `<300 px shortest side` zero-loss filter remains useful evidence, but the actual circle/ellipse/symmetry detector should not become a hard rejection stage until the immutable snapshot/replay layer exists.

## Conclusion

No change is required to the conservative genuine-envelope calibration philosophy or the final run-44 bands as a result of run 49. The major change is sequencing: immutable evidence capture and offline deterministic replay move ahead of all further measurement architecture changes. The run-49 result demonstrates why that protection is necessary rather than merely desirable.
