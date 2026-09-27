# Phase B image selection and rejection log

Governing rule: `docs/architecture/QC_PRINCIPLES.md`'s second principle -- image
suitability is an input contract, not a problem for the measurement engine to
solve. Every candidate below was judged against the Phase B acceptance rule
(dial substantially frontal, full 12 o'clock region clearly visible, 60/minute-
track reference visible, complete triangle visible, coronet visible, no hand/
reflection materially obscuring the required landmarks, no extreme perspective
or artistic angle, sufficient resolution) BEFORE any measurement was attempted.

Source: `datasets/gmt_phase_b_genuine/manifest.csv` -- 25 independent
`established_dealer` / `auction_house` / `rolex_cpo` 126710BLNR listings.
Candidate images were downloaded per source via
`tools/research/phase_b_fetch_genuine.py` (direct dealer/auction page scrape,
no gallery-dl/Reddit), then reviewed as small previews retrieved from CI job
logs (direct artifact download is blocked in this sandbox -- see
`docs/research/gmt-phase-a-image-selection.md` for the same constraint).

## Source-level outcome

| source_id | provenance_class | candidate images | verdict | reason |
|---|---|---|---|---|
| `swe_79807` | established_dealer | 0 | **rejected** | listing page returns HTTP 410 Gone; no candidate images to review |
| `phillips_181407` | auction_house | 6 | **rejected** | all 6 candidate URLs are resolution variants of a single photograph: watch laid on a rough stone/concrete slab, dial visibly off-axis, non-plain background -- not a QC-style photograph, and no alternate frontal view exists on the page |
| `phillips_ch080423_22` | auction_house | 6 | **rejected** | all 6 candidate URLs are resolution variants of a single artistic photograph: watch draped across a brass instrument on a teal background, off-axis and non-plain background -- not a QC-style photograph, and no alternate frontal view exists on the page |
| all other 22 sources | mixed | 1-6 each | **accepted** | a substantially frontal, plain-background image was found (see per-image table below); see "not exhaustively reviewed" note |

## Accepted images (22 independent physical watches)

| source_id | image | physical_watch_id | provenance_class | note |
|---|---|---|---|---|
| `soth_f83f` | img0 | soth_f83f | auction_house | clean frontal studio product shot |
| `soth_a3e8` | img0 | soth_a3e8 | auction_house | clean frontal studio product shot |
| `soth_1a0f` | img0 | soth_1a0f | auction_house | clean frontal studio product shot |
| `soth_6ae6` | img0 | soth_6ae6 | auction_house | mild residual angle, best available on this listing; coronet/triangle/minute-track all clearly visible |
| `soth_8ccb` | img0 | soth_8ccb | auction_house | clean frontal studio product shot |
| `soth_2019_lot6` | img4 | soth_2019_lot6 | auction_house | img0-2 were an oblique ~20-25deg lifestyle photo (rejected, see below); img4 is a distinct, near-frontal catalogue crop |
| `soth_2021_lot224` | img0 | soth_2021_lot224 | auction_house | clean frontal studio product shot |
| `soth_6456` | img1 | soth_6456 | auction_house | largest/cleanest of several near-frontal crops on this listing |
| `wos_cpo_40616911` | img1 | wos_cpo_40616911 | rolex_cpo | frontal hero shot, Rolex CPO badge visible; strongest provenance in the corpus |
| `bobs_185749` | img0 | bobs_185749 | established_dealer | clean frontal studio product shot, plain white background |
| `bobs_175818` | img0 | bobs_175818 | established_dealer | clean frontal studio product shot, plain white background |
| `wf_441391` | img0 | wf_441391 | established_dealer | clean frontal studio product shot |
| `wf_435502` | img0 | wf_435502 | established_dealer | clean frontal studio product shot |
| `swe_75910` | img0 | swe_75910 | established_dealer | clean frontal studio product shot |
| `swe_68408` | img0 | swe_68408 | established_dealer | clean frontal studio product shot |
| `swe_60177` | img0 | swe_60177 | established_dealer | clean frontal studio product shot |
| `swe_59259` | img0 | swe_59259 | established_dealer | clean frontal studio product shot |
| `swe_68784` | img0 | swe_68784 | established_dealer | clean frontal studio product shot |
| `swe_68351` | img0 | swe_68351 | established_dealer | clean frontal studio product shot |
| `swe_42335` | img0 | swe_42335 | established_dealer | clean frontal studio product shot |
| `swe_77497` | img0 | swe_77497 | established_dealer | clean frontal studio product shot |
| `swe_74119` | img0 | swe_74119 | established_dealer | clean frontal studio product shot |

Exact `image_url` for every accepted image is recorded in
`datasets/gmt_phase_b_genuine/accepted_images.csv`, which
`tools/research/phase_b_measure.py` re-fetches by that precise URL (never a
fresh page re-scan) so the image measured is provably the one reviewed here.

## Individual candidate images explicitly reviewed and rejected

- `soth_6456`: img0 (redundant once img1 accepted -- smaller/lower quality
  crop of a similar frontal view), img2/img3 (oblique 3/4 profile, dial
  heavily foreshortened), img4 (mild tilt, redundant once img1 accepted),
  img5 (case-back close-up, no dial visible).
- `wos_cpo_40616911`: img0 (a different, also-acceptable frontal photo, but
  img1 has a cleaner/larger dial; not needed once img1 accepted), img2/img3/
  img4 (duplicate resolution variants of the same photo as accepted img1).
- `bobs_185749`: img1 (duplicate resolution variant of accepted img0),
  img2 (oblique 3/4 profile), img3 (case side-profile, no dial visible),
  img4 (box-and-papers photo, watch not visible).
- `soth_2019_lot6`: img0, img1, img2 (the same oblique ~20-25deg lifestyle
  photo at different crops/resolutions -- dial visibly foreshortened,
  rejected in favour of img4).

## Not exhaustively reviewed

Once a suitable frontal image was found for a physical watch, the remaining
candidate images of that SAME watch (typically img1-5 of the
`established_dealer`/`auction_house` sources) were not individually reviewed
and are not separately logged as accepted or rejected. One physical watch is
one independent statistical sample regardless of how many photographs exist
of it (per the Phase B "IMPORTANT SAMPLE RULE" and
`datasets/gmt_phase_b_genuine/README.md`'s independence rule), so additional
views of an already-accepted watch add no population-level value and were not
worth the extra review effort. This is consistent with the manifest's design:
one row per source_id/physical_watch_id.

## Result

22 of 25 manifest sources yielded a suitable image -- well above the Phase B
target of at least 10 independent genuine watches. 3 sources were rejected
outright (1 dead listing, 2 with only off-axis/artistic photography
available), each with a stated reason and no measurement effort spent on
them, per `docs/architecture/QC_PRINCIPLES.md` rules 12-13.
