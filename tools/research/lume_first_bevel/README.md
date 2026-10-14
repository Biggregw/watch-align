# Lume-first outer-metal reconstruction of the 12 o'clock triangle (research, 14 Oct 2026)

Research only. No app code, reference geometry, tolerance, verdict or UI was changed. Baseline: Alpha107,
`outer-metal/integration-20261013` @ `5e6d565` (verified as the branch head before this work).

## Hypothesis
Can the photographed triangle's actual OUTER metal boundary be reconstructed from the luminous fill plus a small amount
of directly observed bevel evidence, well enough to reveal manufacturing differences against the app's FIXED genuine
reference outline?

## Preserved baseline
`handoff_baseline/` holds the handoff's scripts and notes unmodified, and `ARCHIVE_MANIFEST.txt` lists every file of
the original archive with its sha256. The archive itself (zip sha256 `025cb5c8...b0b`) and its photographs are kept
outside the repository: they include private user photographs and are not published.

## Method (what is measured vs inferred)
1. **Registration**: `TriangleLumeExport.java` runs the unchanged pipeline. It takes the frozen pose H from the dial
   and minute track, and the ring model fitted without the 12 (`ModelSpec.ringMarkers` excludes triangles). It
   exports the exact yellow polygon (`ExpectedMarkerOutline.polygon`) and a 4x-native rectified crop in the same
   canonical coordinates (dial radius = 1). The 12 being judged never moves its own reference.
2. **Inner boundary (measured)**: the brightest connected fill inside a search corridor. The corridor is the reference
   dilated by 0.03 R and only limits where we look. The threshold is dial + 0.6 (lume - dial). If the fill grows as
   large as the whole metal outline (it has merged with a bright bevel), the photo is withheld.
3. **Outer metal (measured)**: along the normal of each lume side, middle 15-85 % only. The sequence is lume, dark
   separator, metal band, then dial. An observation is kept only where the metal band shows at least 15 grey levels of
   contrast and the dial stays dark beyond it.
4. **Thickness (measured)**: per side, the 85th percentile of the longest consistent run of observed widths.
5. **Hold-out**: the thickness from one side predicts the observed outer edge of each other side. A side is never
   scored against the evidence that built it.
6. **Actual boundary**: observed (solid cyan) where at least 40 % of a side is observed, otherwise inferred (dashed
   cyan) from that side's lume line plus the anchor thickness. Tips and corners are always withheld (grey).
7. **Comparison**: signed normal offsets from the fixed reference per side (+ = metal outside the reference), and side
   angles. From these: the rigid shift (tangential and radial), uniform growth, and rotation (the mean side-angle
   difference). Orange and magenta are drawn only on observed spans, from the same offsets. No complete-triangle area
   is computed: the tips and corners are not measured.

## Results
Inputs: the user's QC photo (private; local only); the user's two older-generation (116710LN) photos as qualitative
controls; and every sha256-verified genuine 126710-family photo in `alpha96_calibration/catalogue_provenance_strong.csv`:
- 126 photos, 96 retrievable with matching hashes (30 changed at source and were refused);
- 85 with a dial pose;
- 35 physical watches with a complete three-side comparison.

| Measure (one value per genuine watch) | Genuine median | Genuine p5 .. p95 | Genuine max \|x\| | QC 38097 | Genuine watches at or beyond QC |
|---|---:|---:|---:|---:|---:|
| Rotation (deg, + clockwise) | -0.16 | -0.81 .. +0.58 | 1.01 | **-0.95** | 2.9 % |
| Radial shift (% R, + towards track) | +0.07 | -0.26 .. +0.30 | 0.85 | **+0.94** | 0.0 % |
| Tangential shift (% R, + right) | +0.01 | -0.26 .. +0.16 | 0.62 | -0.21 | 8.6 % |
| Uniform outline growth (% R) | +0.54 | +0.47 .. +1.48 | 1.61 | +0.64 | 20 % |
| Left / right / top side shape angle (deg) | -0.13 / +0.16 / -0.10 | | 0.25 / 0.59 / 0.66 | -0.14 / -0.06 / +0.20 | 37 / 89 / 20 % |

- **QC vs the app's own readings**: rotation -0.95 deg, radial +0.94 % R, tangential -0.21 % R. The app reports a
  centreline 1.08 deg anticlockwise, the track 0.94 % R closer, and lateral 0.34 % R left. They agree.
- **QC stability**: 32 sensitivity variants (threshold 0.5/0.6/0.7, thickness p50/p85, corridor 0.025/0.035 R, each
  anchor side) give rotation -0.95 .. -1.04 deg and radial +2.85 .. +3.83 px. A whole-pipeline rerun on 85 % and 70 %
  rescaled copies and a quality-60 recompression gives rotation -0.96 .. -1.01 deg, radial +0.68 .. +1.02 % R and
  tangential -0.21 .. -0.58 % R.
- **Repeat photos (5 genuine watches)**: median spread is 0.26 deg rotation, 0.33 % R tangential and 0.25 % R radial;
  the worst spreads are 0.96 deg, 0.55 % R and 0.52 % R. Genuine watch `phillips_210072` reads -0.60 .. -0.92 deg in
  all six photos, so a genuine 126710 triangle can be turned about as much as the QC one. The radial reading looks
  resolution-dependent: two SWE watches read +0.52 % R in their ~720 px photos and +0.02 .. +0.28 % R in their ~250 px
  copies.
- **Hold-out thickness prediction** (median \|error\| per photo): about 0.11-0.16 % R (about 0.5-0.7 px), with p90 of
  0.37-0.48 % R. Left and right predict each other best. The top bar is often a different width from the sides (up to
  1.7 px on `cae9ed4ca889fc44`), so one constant thickness is wrong.
- **Coverage**:
  - Of the 85 posed genuine photos, 53 (62 %) give a complete comparison.
  - In 20 (24 %), the luminous fill merges with a brightly lit bevel and the photo is withheld; the user's 38116
    control fails the same way.
  - Only 45 photos never merge in any variant.
  - The rest lack observed edges.
- **Systematic growth**: on genuine watches the reconstructed outer edge sits a median 0.54 % R (about 2 px) outside
  the yellow reference on every side. Drawn raw, every genuine triangle would show an orange band. This is a definition
  offset between the reference outline and the traced outer edge, not a defect.

## Answers
- **Does it expose the QC tilt and displacement?** Yes. Both are visible and measured: rotation -0.95 deg, and the
  whole top bar 4.8 px (1.6 % R) above the reference. Both agree with the app's independent readings and are stable
  under the perturbations tested.
- **Does it avoid false reports on genuine 126710?** Not on rotation alone: one genuine watch reproducibly reaches
  0.6-0.9 deg. The radial shift sits above every genuine watch, but repeat-photo noise (up to 0.5 % R) is close to the
  margin. The uniform growth would be reported on every genuine watch unless it is accounted for.
- **Is lume tracing reliable?** Yes where a dark separator exists (stable across thresholds and corridors). It fails
  in about a quarter of genuine photos where the bevel is lit as brightly as the lume; those are withheld, not guessed.
- **Can observed thickness predict held-out edges?** Roughly, to about 0.15 % R typical and 0.45 % R p90. Inferred
  sides are therefore weaker evidence than observed sides.
- **What stays ambiguous?** Tips and corners (never measured). Sides or tops where the lume merges with the bevel. The
  top edge close to the minute track. Low-resolution photos.
- **Is it a better foundation than the fitted three-line polygon?** For position and rotation it reproduces the same
  rigid numbers, so it isn't more accurate there. It adds visible per-side evidence, explicit
  observed/inferred/withheld coverage, a validated thickness model and a measured systematic offset. Its coverage is
  lower: 62 % of posed photos.

## Minimum next steps (not done)
1. Decide the edge definition: either compare against a genuine-typical traced outline, or report growth separately
   and never colour it. Either way, measure it on genuine watches first; this is a calibration decision for the owner.
2. Merged lume and bevel: a gradient-based separator mode, or keep withholding.
3. More labelled QC triangles (only one exists) and repeat-photo pairs at the same resolution, to separate photo noise
   from manufacturing.

## Reproduce
```
JAVA_TOOL_OPTIONS=-Dwatchalign.model=gmt_126710 tools/desktop-harness/run.sh TriangleLumeExport photo.jpg out ID
python3 tools/research/lume_first_bevel/lume_first_compare.py out ID res --original photo.jpg
python3 tools/research/lume_first_bevel/genuine_summary.py res manifest.csv tools/research/lume_first_bevel/qc_metrics/qc38097.json
```
The CI workflow `.github/workflows/lume-first-bevel.yml` repeats the genuine 126710 run (job `genuine`, on push). The
`summary-only` job (on dispatch) re-summarises the stored results of run 38534404729.
`qc_metrics/qc38097.json` holds only the numbers measured on the private QC photo.
