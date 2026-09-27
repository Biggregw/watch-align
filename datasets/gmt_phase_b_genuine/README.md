# Phase B genuine control set (GMT 12-series, 126710BLNR family)

Source manifest and fetch workflow for `docs/research/GMT_12_TRIANGLE_FRONTAL_MILESTONE.md`
Phase B: establishing the genuine-reference distribution for the Phase A
dimensionless landmark ratios.

## Provenance tier

`datasets/126710BLNR/manifest.csv`'s `gen_candidate` class (marketplace listings,
seller-asserted genuine, not independently authenticated) is **not** used here.
Phase B's acceptance rule requires "provenance strong enough to classify the
watch as genuine" -- stronger than a marketplace seller's own claim. This
manifest instead uses:

- `established_dealer` -- a dealer that states the watch was inspected/authenticated
  (Watchfinder, SwissWatchExpo, Bob's Watches).
- `auction_house` -- a major auction house's authenticity guarantee (Sotheby's,
  Phillips).
- `rolex_cpo` -- Rolex Certified Pre-Owned, an official Rolex authentication
  programme.

Every row is still `provenance_class`, a source-level claim -- this project does
not independently re-authenticate watches. It is simply a materially stronger
claim than an unauthenticated marketplace listing, consistent with what the
Phase B task requires.

The manifest data (source URLs, physical-watch groupings, provenance notes) was
originally assembled on `research/proportional-geometry-knowledge-base` for
different (Stage 3 projective) measurement work. Only the manifest **data** is
reused here -- none of that branch's measurement/pose code. Phase B runs the
frozen Phase A detector (`tools/research/phase_a_landmarks.py`, unmodified)
against images fetched fresh for this branch.

## Scope: why 126710BLNR only

All 25 manifest rows are the 126710BLNR/126710BLRO reference family (Rolex calls
some of these listings "Batgirl", others "Batman" -- both colourways of the same
126710 case/dial/hand/marker geometry). "12-series Rolex GMT" is interpreted here
as this reference family, which already has clearly-fetchable listings from
strong-provenance sources sufficient to reach the Phase B target of >=10
independent genuine physical watches. Broadening to other GMT-Master II
references (116710, 116713, etc.) was not necessary to hit that target, and
holding the reference constant avoids introducing cross-reference marker-geometry
variation as a confound before the frontal single-reference case is itself
established.

## Independence rule

`physical_watch_id` is the unit of independence, one per manifest row (each row
is a distinct listing/serial from a distinct source). Multiple photographs of
the same listing are multiple views of the same physical watch, never counted
as multiple genuine samples.

## Fetching

Images are fetched fresh inside CI only and never committed -- see
`.github/workflows/gmt-phase-b-fetch-genuine.yml` and
`tools/research/phase_b_fetch_genuine.py`. Candidate images are downloaded per
source (dealer/auction page scrape, not gallery-dl -- these are direct product/
lot pages, not Reddit/Imgur galleries), written to `images/<source_id>/`, and
logged to `candidate_status.csv` (gitignored).

## Suitability review

Every downloaded candidate image is judged against
`docs/architecture/QC_PRINCIPLES.md`'s image-suitability input contract before
any measurement is attempted. Accepted/rejected images and reasons are recorded
in `docs/research/gmt-phase-b-image-selection.md`. Accepted images (exact
image URL, not just source page) are recorded in `accepted_images.csv` so the
measurement step can re-fetch precisely the same photograph.

## Measurement

`tools/research/phase_b_measure.py` re-fetches each accepted image by its exact
URL and runs the frozen, unmodified Phase A detector
(`tools/research/phase_a_landmarks.py`) on it -- no perspective correction, no
Stage 3 projective/canonical machinery. Results land in
`docs/research/gmt-phase-b-results/`.
