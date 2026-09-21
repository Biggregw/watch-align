# Batgirl corpus fetch results

Latest successful GitHub Actions fetch:

- Workflow run: `35630037975`
- Head commit: `f621ac5952b138d0c776a7c5ea36050e89af8472`
- Artifact: `batgirl-126710blnr-research-corpus`
- Artifact ID: `10653237711`
- Artifact size: 89,499,507 bytes
- Artifact SHA-256 digest: `6621e09beab2e9bc397a451d22f4c4ce096466b4e2496e96962cd2f4f625c15d`
- Artifact retention: 1 day

## Empirical result

The GitHub-hosted runner successfully reached Rolex and Imgur even though the Claude Code sandbox could not.

- 35 sources attempted
- 31 sources populated
- 252 normalised images
- 1 first-party Rolex reference image
- 8 independent populated `gen_candidate` watches
- 22 independent populated `rep_labelled` watches
- 15 images flagged as possible perceptual duplicates for review

Image rows by split:

- genuine reference: 1
- genuine validation: 22
- genuine calibration: 22
- replica validation: 86
- replica calibration: 121

Replica image rows by factory:

- VSF: 99
- Clean: 81
- ARF: 17
- C+ Factory: 10

## Sources that failed to fetch

The following four source albums returned `gallery-dl` status 4 and produced no usable images in this run:

- `gen_wex_EZKpLku`
- `gen_wex_KWfXz8G`
- `gen_wex_I9FxPLn`
- `rep_cf_17D8FaS`

Do not count these as populated watches unless a future fetch succeeds or they are replaced with a separately documented source.

## Important statistical rule

The 252 image count is not 252 independent watches. Repeated photographs from one album are multiple views of the same physical watch. `physical_watch_id` remains the unit of independence. Aggregate repeated-image measurements per physical watch before comparing genuine-candidate and replica populations.

The first-party Rolex catalogue image is a reference, not an independent physical-watch sample. `gen_candidate` remains source-labelled genuine candidate rather than authenticated ground truth.

## For Claude Code

The Claude sandbox's block on Reddit/Imgur/Rolex does not prevent this research. GitHub Actions can fetch the corpus and package it as an Actions artifact.

While the artifact is still retained, a GitHub-authenticated environment can download it with GitHub CLI, for example:

```bash
gh run download 35630037975 \
  -n batgirl-126710blnr-research-corpus \
  -D .batgirl-corpus
```

The artifact contains the fetched `gen/` and `rep/` trees, `resolved_images.csv`, the source `manifest.csv`, and `fetch.log`.

After downloading, do not change QC thresholds immediately. First run the Python acquisition pipeline over the corpus and produce a per-image acquisition/measurement table, then aggregate by `physical_watch_id` and inspect repeatability before class comparisons.
