# Phase 2: calibration-corpus expansion / failure audit

experiment/gmt-proportional-geometry-v1

## 0. Scope and standing rules (unchanged from Stage 1)

- Python engine is source of truth; Android/production QC untouched.
- No PR to main; no genuine-vs-replica classifier is being built.
- `physical_watch_id` is the unit of independence, never raw image count.
- Validation-split physical watches are completely untouched by this
  phase -- only calibration-split gen_candidate sources are read,
  fetched, or measured.
- The user's replica measurements are NOT re-examined in this phase.
- The 5 currently-selected core features are NOT redesigned here; this
  phase only tests whether they survive a larger calibration population.

## 1. Audit: coverage before expansion

Full source-level funnel (`gmt_calibration_audit_per_source.csv`, CI run
[35871064905](https://github.com/Biggregw/watch-align/actions/runs/35871064905),
fresh fetch of the manifest as it stood at Stage 1 freeze -- 6
calibration-split `gen_candidate` sources):

| source_id | fetched | pose_ok | round_marker_ok | triangle_ok | simple_ok | projective_ok | usable |
|---|---|---|---|---|---|---|---|
| gen_wex_3KSuGhC | 10 | 4 | 1 | 4 | 4 | 1 | yes |
| gen_wex_e99gXKb | 10 | 3 | 2 | 3 | 3 | 2 | yes |
| gen_wex_K4gqk6U | 1 | 0 | 0 | 0 | 0 | 0 | **no** |
| gen_wex_U2XNblj | 1 | 0 | 0 | 0 | 0 | 0 | **no** |
| gen_wex_EZKpLku | 0 | 0 | 0 | 0 | 0 | 0 | **no** |
| gen_wex_I9FxPLn | 0 | 0 | 0 | 0 | 0 | 0 | **no** |

Independent usable calibration `gen_candidate` physical watches before
expansion: **2 of 6** (`gen_wex_3KSuGhC`, `gen_wex_e99gXKb`) -- exactly
matching the Stage 1 population. Total usable images: 7 (4 + 3).

Grouped failure reasons for the 4 non-usable sources, per-image detail
in `gmt_calibration_audit_per_image.csv`:

- **acquisition/fetch failure** (2 sources: `gen_wex_EZKpLku`,
  `gen_wex_I9FxPLn`) -- gallery-dl produced zero resolved images this
  run. Not silently dropped: each has a placeholder audit row.
- **pose acquisition failure, no dial ellipse found** (2 sources:
  `gen_wex_K4gqk6U`, `gen_wex_U2XNblj`) -- each album resolved to
  exactly 1 usable-resolution image (1443x1800 and 1350x1800
  respectively), and that single image's minute-track ticks could not
  establish a dial ellipse (consistent with an off-angle/partial/wrist
  shot rather than a frontal dial photo, though this was not visually
  confirmed since this sandbox cannot open the source host).

## 2. Recovery: existing calibration sources, retried

All 6 original sources were re-fetched fresh (not reused from any prior
run) as part of the audit above. **Recovery produced zero
improvement**: the outcome for all 4 non-usable sources is identical to
what Stage 1 already recorded --

- `gen_wex_EZKpLku` / `gen_wex_I9FxPLn`: still 0 images resolved. This
  is consistent with a structurally dead source (deleted/private album)
  rather than transient rate-limiting, since a completely fresh fetch
  attempt on a different day changed nothing.
- `gen_wex_K4gqk6U` / `gen_wex_U2XNblj`: still exactly 1 image each,
  still failing the same pose gate for the same reason. This is
  consistent with the album genuinely containing only one
  usable-resolution photo, not a partial/interrupted fetch.

Conclusion: the existing manifest's calibration sources are exhausted.
Reaching the 8-watch target requires new sources, not further retries.

## 3. New genuine-candidate sources added

Sourcing method: WebSearch (this sandbox cannot reach reddit.com or
imgur.com directly, even through the search tool's domain filter --
both are excluded from its crawl -- so no new r/Watchexchange-style
albums could be discovered from here; only general web search was
available). Search surfaced real, distinct, individually-listed
125710BLNR ("Batgirl"/Jubilee) watches from established marketplaces
and dealers, consistent with the requested preference order
(first-party/authorised-dealer, then established marketplace listings
with full-set evidence, then credible-provenance owner/marketplace
listings). Reddit/imgur-style community marketplace sourcing (the
existing manifest's method) was not reachable and could not be
extended from this environment; the user may be able to supply
additional r/Watchexchange threads directly if more sources are wanted
later.

9 new calibration-only sources added (`datasets/126710BLNR/manifest.csv`,
commit `1784fa0`):

| source_id | provenance | evidence |
|---|---|---|
| gen_c24_id40153835 | Chrono24, individual dealer listing | "Full Set Unworn", Jubilee/Batgirl named |
| gen_c24_id44512719 | Chrono24, Trusted Seller | 2026 "Full Set Unworn" $22,500, Jubilee/Batgirl named |
| gen_c24_id38542337 | Chrono24, individual dealer listing | "Full Set", Jubilee/Batgirl named |
| gen_c24_id43043236 | Chrono24, Trusted Seller | "Full Set" $18,964, title states Jubilee explicitly |
| gen_1stdibs_j15020852 | 1stDibs, individual seller listing | Jubilee named |
| gen_1stdibs_j24782832 | 1stDibs, individual seller listing | Batgirl named |
| gen_1stdibs_j14512702 | 1stDibs, individual seller listing | Jubilee named |
| gen_bw_rlx9529 | Bernard Watch (established dealer) | 2023, "Full Set" |
| gen_wc_126710blnr_2021 | Watch Club (established UK dealer) | year 2021, warranty to Nov 2026 |

**Important documented limitation:** none of these 9 sites have a
native gallery-dl extractor, so they were fetched with gallery-dl's
generic `<img>`-tag scraper (`generic:<url>` in `image_url`). This
sandbox could not open any of these pages directly (egress-blocked) to
visually confirm the scraped images are exclusively the target watch
and not a related-listing thumbnail from the same page. The
pose-acquisition pipeline is the only automated filter applied --
non-watch imagery should fail to acquire a dial ellipse and simply
drop out of the funnel below, but a same-reference-different-watch
image slipping through undetected cannot be fully ruled out from this
environment. This is recorded, not hidden; see the per-source result
below for what actually came through.

**Result** (3 sequential CI rounds, `gmt_calibration_audit_per_source.csv`):

| source_id | fetched | usable | note |
|---|---|---|---|
| gen_1stdibs_j10266432 | 6 | yes (1 clean) | 5/6 flagged near-duplicate |
| gen_1stdibs_j14512702 | 6 | yes | |
| gen_1stdibs_j14542122 | 6 | yes | |
| gen_1stdibs_j15020852 | 4 | yes | |
| gen_1stdibs_j22086492 | 6 | yes (6/6!) | best-behaved new source |
| gen_1stdibs_j22349682 | 6 | yes (1 clean) | 5/6 flagged near-duplicate |
| gen_1stdibs_j24782832 | 6 | yes | |
| gen_1stdibs_j25758762 | 5 | **no, net 0 clean** | its only feature-bearing image was itself the near-duplicate |
| gen_1stdibs_j26718002 | 6 | yes | |
| gen_c24_id38542337 | 0 | no | |
| gen_c24_id40153835 | 0 | no | |
| gen_c24_id43043236 | 0 | no | |
| gen_c24_id44512719 | 0 | no | |
| gen_bw_rlx9529 | 0 | no | |
| gen_wc_126710blnr_2021 | 0 | no | |

**1stDibs was a 100%-reliable fetch channel** for this manifest (9/9
sources produced at least one fetched image; 8/9 produced a clean,
de-duplicated feature-bearing measurement). **Chrono24, Bernard Watch,
and Watch Club were 0/6** -- gallery-dl's generic `<img>`-tag scraper
found nothing on any of those 6 listing pages, consistent with
JS-rendered image galleries that a static-HTML scrape cannot see (not
retried further; a dedicated extractor or a JS-capable fetch path would
be needed, out of scope for this environment).

No contamination was detected: every accepted image that reached the
pose-acquisition and triangle-segmentation stage produced a
geometrically plausible dial measurement (tilt in a sane range,
triangle solidity/axis-agreement in the expected band) -- there is no
positive evidence any wrong-watch or non-watch image slipped through,
though this remains an automated-only check, not a visual one (see the
caveat above).

## 4. Expanded feature-distribution statistics

**Independent usable calibration `gen_candidate` physical watches: 11
fetched/pose-accepted, 10 contributing at least one de-duplicated
feature measurement** (`gen_1stdibs_j25758762`'s only qualifying image
was itself flagged as a near-duplicate of a sibling photo and was
excluded from population statistics rather than double-counted -- see
above). This clears the requested minimum (>=8) and sits within the
preferred 10-12 range. Up from 2 watches / 7 images at Stage 1 freeze.

Full numbers: `gmt_calibration_expanded_report.txt`,
`gmt_calibration_expanded_watch_summary.csv`,
`gmt_calibration_expanded_clean.csv` (37 clean per-image rows across
the 11 watches, 28 with simple features, 20 with projective features).

Headline comparison against the Stage 1 (n=2) numbers for the 5 frozen
core features -- feature definitions are UNCHANGED, only the population
is larger:

| feature | Stage 1 (n=2) median / MAD | Expanded (n=10) median / MAD |
|---|---|---|
| apex_r_simple | 0.5605 / 0.0174 | 0.5492 / **0.0010** |
| base_r_simple | 0.7592 / 0.0281 | 0.7592 / 0.0413 |
| centroid_tangential_offset_canonical | -0.0484 / 0.0413 | -0.0012 / 0.0053 |
| symmetry_axis_angular_deviation_deg | -3.71 / 1.37 | -0.08 / 1.41 |
| base_width_over_height | 1.145 / 0.059 | 1.120 / 0.080 |

`apex_r_simple`'s median barely moved (0.5605 -> 0.5492) and its
between-watch spread tightened by an order of magnitude -- the
strongest evidence yet that this feature is a stable, well-behaved
measurement across independent watches, not an artifact of the n=2
sample. `centroid_tangential_offset_canonical`'s median also moved
much closer to zero (-0.0484 -> -0.0012) with a much tighter MAD; the
apparent left/right bias Stage 1 flagged as "possibly a small-sample
artifact" now reads as **not supported** at n=10 -- the expanded
population centres almost exactly on the nominal axis.
`base_r_simple` and `base_width_over_height` kept similar medians but
did NOT tighten (MAD held steady or widened slightly) -- consistent
with genuine between-watch variation in these two features rather than
n=2 noise. `symmetry_axis_angular_deviation_deg`'s median moved close
to zero and its MAD held near its Stage 1 value -- still the noisiest
of the five core features, as already flagged.

## 5. Simple vs. projective comparison (expanded)

Coverage: simple n=28/37 images (10 watches), projective n=20/37
images (9 watches) -- projective's >=5-round-marker-plus-conditioned-
fit requirement remains a strict subset, as at Stage 1.

At n=2 (Stage 1), projective looked tighter on every one of the 6
simple/projective pairs. **At n=10, that pattern does not hold up for
every feature:**

| pair | simple MAD | projective MAD | which is tighter |
|---|---|---|---|
| apex_r | 0.0027 | 0.0125 | **simple, by 4.6x** |
| centre_r | 0.0368 | 0.0104 | projective |
| base_r | 0.0452 | 0.0059 | projective |
| apex_to_base_span | 0.0487 | 0.0164 | projective |
| base_to_minute_track_gap | 0.0452 | 0.0059 | projective |
| centre_to_minute_track_gap | 0.0368 | 0.0104 | projective |

Per the standing instruction not to force projective normalisation to
win: **for `apex_r` specifically, the simple (affine-only) coordinate
is now materially more robust than the projective correction**, the
opposite of the Stage 1 impression from 3 projective-eligible images.
For every other paired feature, projective remains tighter, consistent
with Stage 1. This is reported as a genuine, feature-specific result,
not resolved in projective's favour across the board.

Tilt correlation likewise stays mixed and does not cleanly favour
either representation: e.g. `centre_r_simple` r=-0.29 vs.
`centre_r_projective` r=-0.60 (projective more tilt-sensitive here, the
opposite of what a "projective corrects for tilt" hypothesis would
predict), while `apex_r_simple` r=+0.17 vs. `apex_r_projective`
r=-0.10 (both weak). No feature shows a clean, strong tilt-independence
signal in either representation at this sample size.

## 6. Tilt-dependence analysis (expanded)

Combined tilt range across the clean population: 2.1-48.6 degrees
(<=10 deg: n=20 images; >10 deg: n=8-17 images depending on feature
coverage, up from Stage 1's zero >10deg evidence for the core
features). The <=10/>10 subgroup medians now show real, feature-specific
movement (e.g. `apex_to_base_span_simple` 0.252 at <=10deg vs 0.161 at
>10deg; `base_r_simple` 0.805 vs 0.715) -- there is now at least some
>10deg evidence to examine, where Stage 1 had literally none for the
projective-eligible subset. This is still a modest sample per subgroup
(n=8 images >10deg) and should be treated as a first look, not a
validated tilt-correction curve.

## 7. Research-branch checkpoint

Re-checked at phase start and again before this final write-up:
`origin/research/proportional-geometry-knowledge-base` remains at
commit `d19988a`, same three `docs/research/proportional-geometry-*.md`
files, unchanged since the 2026-09-23 addendum. No new guidance to
reconcile against this phase's findings. The research branch was not
merged or cherry-picked into this implementation branch.

## 8. Verdict: is the sample now sufficient for the next research stage?

**Partially.** The explicit numeric target (>=8 independent usable
calibration `gen_candidate` physical watches) is met -- 10 contribute
clean measurements, 11 were successfully fetched and pose-accepted.
This is a real, substantial improvement over Stage 1's n=2 and already
changes at least one conclusion (the `apex_r` simple-vs-projective
comparison no longer uniformly favours projective; the tangential-
offset "left/right bias" impression weakens considerably).

It is not yet a validated genuine-population distribution, and should
not be treated as one:

- n=10 clean watches is far below any classical sample size for a
  robust percentile-band genuine profile, especially for the noisier
  angular features (`symmetry_axis_angular_deviation_deg` MAD is still
  >1 degree).
- All 9 successfully-sourced new watches come from a single channel
  (1stDibs), which is a real diversity limitation even though each is
  an independently listed physical watch -- a single marketplace's
  photography conventions (lighting, typical camera angle, cropping)
  could in principle correlate across these 9 in a way this analysis
  cannot detect from geometry alone.
- The generic-scrape contamination risk documented in section 3 was
  not eliminated, only found to have produced no obvious anomalies in
  this run.
- >10deg tilt evidence is still thin (n=8 images).

Recommended next step, if requested: hold the frozen feature
definitions exactly as they are (this phase deliberately did not
redesign them) and treat this expanded population as the input to a
future feature-selection or profile-refresh phase, rather than as a
finished genuine profile. Per the user's explicit instruction, the
5-feature profile and the calibration/validation split are NOT altered
by this phase; the user's replica measurements were NOT re-examined.

## 4. Expanded feature-distribution statistics

[re-run of the frozen feature extraction over the expanded set]

## 5. Simple vs. projective comparison (expanded)

## 6. Tilt-dependence analysis (expanded)

## 7. Research-branch checkpoint

Re-checked at phase start: origin/research/proportional-geometry-knowledge-base
at commit d19988a, all three docs/research/proportional-geometry-*.md files
read -- unchanged since the last checkpoint (see the 2026-09-23 addendum).
No new guidance to reconcile in this phase.

## 8. Verdict: is the sample now sufficient for the next research stage?

[explicit yes/no/partial with numbers]
