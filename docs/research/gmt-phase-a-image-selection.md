# Phase A image selection and rejection log

Governing rule: `docs/architecture/QC_PRINCIPLES.md`'s second principle -- image
suitability is an input contract, not a problem for the measurement engine to
solve. Every candidate below was judged against a realistic QC-photograph pose/
visibility envelope (reasonably frontal, complete dial clearly shown, coronet/
triangle/minute-track all visible) BEFORE any measurement was attempted.
Unsuitable images are rejected explicitly, with a stated reason, and were never
fed into the landmark harness.

Source: `datasets/126710BLNR/manifest.csv`'s `gen` class (1 first-party Rolex
catalogue reference + 8 independent source-labelled genuine marketplace
candidates -- `provenance=gen_candidate`, not independently authenticated by
this project, per `datasets/126710BLNR/README.md`'s own provenance rules).
Fetched via `gmt-phase-a-fetch-genuine.yml`; one representative image per
watch was previewed first, then the single strongest candidate's full album
was previewed to pick the best individual frame.

## Cross-watch comparison (one representative image per independent watch)

| physical_watch_id | provenance | verdict | reason |
|---|---|---|---|
| `official_catalogue_2026` | official (first-party Rolex catalogue) | **rejected** | 3/4 "hero shot" angled to show the case/bracelet profile; the dial itself is significantly foreshortened, not frontal, despite being the strongest-provenance source |
| `gen_wex_vmbUDwy` | gen_candidate | rejected | marketplace verification photo, ~30 degree 3/4 angle; dial foreshortened |
| `gen_wex_K4gqk6U` | gen_candidate | rejected | watch photographed inside its presentation box at an angle; small in frame, low effective resolution on the dial/12 region |
| `gen_wex_3KSuGhC` | gen_candidate | **selected** (see album breakdown below) | correct reference/bezel colourway confirmed visually (black/blue Batgirl); clean studio product photography, plain background; near-frontal in its best frame |
| `gen_wex_e99gXKb` | gen_candidate | rejected -- data-quality issue | representative image is a **green-dial** GMT-Master II, not a 126710BLNR Batgirl at all. This is a manifest/provenance mismatch (the source album does not show the reference the manifest row claims), not merely an unsuitable pose. Flagged for manifest correction; not used for any measurement. |
| `gen_wex_1TDYtpN` | gen_candidate | rejected | watch lying flat on a table, photographed from a steep downward angle; dial heavily foreshortened |
| `gen_wex_U2XNblj` | gen_candidate | rejected | representative image is a tray of multiple unrelated watches, not a single-watch QC-style photograph at all |
| `gen_wex_8Rg3vqJ` | gen_candidate | rejected | presentation-box shot; watch small in frame, dial at an angle |
| `gen_wex_bRffRhD` | gen_candidate | rejected | oblique angle, dial partly obscured by background clutter (photographed against a patterned magazine/comic backdrop); not a controlled QC-style photograph |

Eight of nine independent genuine-candidate sources were rejected outright at
this stage. This is the expected, correct outcome of applying the input
contract rather than a shortfall to fix -- these images were never valid
Watch Align QC inputs, and no measurement effort was spent on any of them.

## Within-album comparison (`gen_wex_3KSuGhC`, 10 images, all one physical watch)

| image | verdict | reason |
|---|---|---|
| `image_00.jpg` | rejected (runner-up) | near-frontal 3/4 studio shot, acceptable lighting, but a visible upward camera angle (case/crown edge partially visible on one side) makes it more oblique than image_04 |
| `image_01.jpg` | rejected | box-and-papers shot; no unobstructed dial view |
| `image_02.jpg` | rejected | angled top-down 3/4 view; dial foreshortened |
| `image_03.jpg` | rejected | side-rotated artistic composition (crown at top, 12 o'clock marker rotated to roughly the 9 o'clock screen position); not oriented as a QC photograph |
| `image_04.jpg` | **selected** | near-frontal: bezel numerals visible symmetrically on both left and right sides, minimal visible foreshortening; sharp focus and highest local contrast of the album (also the largest post-compression file size among the 10 images, consistent with retaining the most real detail); native resolution 1562x1800px, well above what the landmark search needs |
| `image_05.jpg` | rejected | case-back/profile close-up; dial not visible |
| `image_06.jpg` | rejected | crown close-up; dial not visible |
| `image_07.jpg` | rejected | bracelet close-up; dial not visible |
| `image_08.jpg` | rejected | clasp close-up; dial not visible |
| `image_09.jpg` | rejected | bracelet-focused composition; dial only partially in frame and at an angle |

## Selected image

- **source_id**: `gen_wex_3KSuGhC`
- **image**: `image_04.jpg` (`datasets/126710BLNR/gen/calibration/gen_wex_3KSuGhC/image_04.jpg` once fetched; never committed, per `datasets/126710BLNR/.gitignore`)
- **physical_watch_id**: `gen_wex_3KSuGhC`
- **provenance**: `gen_candidate` -- source-labelled genuine 126710BLNR (Jubilee bracelet), from a WatchExchange marketplace listing (`https://www.reddit.com/r/Watchexchange/comments/1p0o3bp/wtsrolex_gmt_master_ii_batgirl_jubilee_126710blnr/`), described by the seller as a 2021 full-set listing. **Not independently authenticated by Watch Align** -- treated strictly as a genuine candidate, per `datasets/126710BLNR/README.md`.
- **native resolution**: 1562x1800px
- **split**: `calibration` (per manifest; irrelevant to Phase A, which does not train or fit anything, but recorded for consistency with the dataset's own bookkeeping)
