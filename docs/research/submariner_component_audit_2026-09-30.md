# Submariner component audit, 2026-09-30

This is the first implementation step for issue #34. It records what the completed GMT corpus research supports before any production Submariner QC is added.

## Safely generic now

- source discovery, provenance tracking, physical-watch identity, exact/near duplicate handling and ACCEPT/QUARANTINE/REJECT state
- basic image decoding and storage
- generic pixel-level suitability checks such as minimum image size, dial completeness, blur, exposure and glare
- watch-level dataset accounting and watch-level train/validation/holdout splitting
- page/gallery resolution for dealer, auction and image-host sources

These can be reused for Submariner without importing GMT geometry thresholds.

## Generic only with family parameters

- dial localisation and dial-normalised coordinates
- indexed-marker localisation
- ellipse/affine rectification
- marker-layout confidence
- pose gating based on dial/rehaut evidence
- 12 marker detection and stability checks
- rehaut measurements
- bezel-to-dial relationships

The GMT research showed that detector centres can be stable while rotation/size and pose labels still have heavy tails. Rehaut-sector thresholds were especially sensitive to small image translations. Therefore these components may be shared as code, but not as fixed GMT thresholds or baselines.

## GMT-only until revalidated

- `GmtHumanPosePolicy` decision thresholds
- `GmtRehautSectorAnalyzer` thresholds and pose boundaries
- `GmtMarkerPose` calibration
- GMT 12-triangle clearance/rotation ranges
- GMT minute-track offsets and any GMT-specific marker correspondence
- any QC verdict calibrated from the existing GMT genuine distribution

These must not be copied into Submariner calibration.

## Submariner phase-1 scope

- 124060 Submariner No-Date
- 126610LN Submariner Date
- 126610LV Submariner Date

The 116610LN remains outside the calibrated phase-1 population.

## Data rules

- one physical watch is one independent sample regardless of photo count
- multiple usable views of the same watch are repeatability data
- genuine watches establish normal geometry
- replicas are stress cases, not an authenticity-classifier training set
- development, validation and locked holdout splits are by `physical_watch_id`
- the existing 11-photo 124060 controlled series is repeatability-only

## First engineering sequence

1. introduce explicit watch-family configuration while preserving current GMT behaviour byte-for-byte where possible
2. ingest the existing Submariner candidate source pool into a Sub-specific acquisition path
3. gather at least 15 independent genuine and 20 independent replica phase-1 Submariners before tuning geometry
4. build a research measurement harness for Sub marker centres, rehaut, pose and repeatability
5. decide from genuine controls whether 124060 and 126610 can share one family geometry profile
6. only then promote validated Sub-specific QC into Android

No production Android QC change is part of this step.