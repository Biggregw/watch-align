[DRAFT IN PROGRESS -- being filled in as CI audit/expansion runs complete.
Not a final deliverable yet. Placeholder headings only below this line.]

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

[per-source result once CI run 2 completes]

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
