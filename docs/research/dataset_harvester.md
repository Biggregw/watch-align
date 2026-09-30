# Unattended dataset harvester (2026-09-30)

Branch `feature/unattended-dataset-harvester` (from PR #32 head `2bb581c`). Code:
`tools/dataset_harvester/`. Workflow: **Dataset harvester**. Operating notes: `tools/dataset_harvester/README.md`.

## Why

The test set had been growing only through the phone (Collect screen), one photo at a time. The
harvester removes the phone from the loop: it runs on a server, a small VM or GitHub Actions, and
keeps improving the genuine/replica GMT set without supervision. Uncertain material goes to
quarantine instead of waiting for a person.

## Architecture

```
adapters (discover) ─► state/sources.jsonl ─► resolvers (normalise source) ─► download / local copy
   repo · phone · search · reddit                 local · direct · imgur album · reddit API · listing page
        │
        ▼
 group (one source = one physical watch) ─► dedupe (sha256 · dHash+pHash confirmed by pixels · dial-crop pHash)
        │
        ▼
 suitability: Suit driver = the app's GmtHumanQcAnalyzerV2 via tools/desktop-harness  +  dial pixel checks
        │
        ▼
 metadata (TitleTags rules with evidence/confidence) ─► Batch driver measurements (the regression harness)
        │
        ▼
 value (under-represented groups) ─► ACCEPT / QUARANTINE / REJECT ─► state + manifest.csv + watches.csv + reports
```

* **One authority for geometry.** Every dial, 12-marker, pose, marker and rehaut value comes from the
  app's own Java analysis, compiled for desktop by the existing harness. The only new Java is
  `tools/desktop-harness/drivers/Suit.java`, a read-only driver. It reports what the analyser already
  decides, and it parses the rehaut-sector and dial-ellipse diagnostics from the analyser's own
  report text, so no production class was changed. Measurements are the existing `Batch` driver,
  the one the 337-photo regression uses.
* **Adapters are separate from analysis.** A new source type only needs an adapter (discovery) and
  possibly a resolver (image enumeration).
* **State** is JSON lines, sorted and written atomically after every chunk of 8 sources. A killed run
  resumes. Downloaded images are content-addressed (`images/<sha[:2]>/<sha>.<ext>`), and a URL already
  mapped to a stored sha is never fetched again.

## Reused from PR #32

* `RedditClient` listing parsing was ported to `harvester/reddit.py`: gallery order, media_metadata,
  i.redd.it/i.imgur.com, Imgur albums and links in self-text, crossposts and the preview fallback.
  It is only used through the official API.
* `TitleTags` was ported to `harvester/metadata.py`: reference and suffix, nicknames, the
  factory list, and the "mentions a genuine" rule. It now attaches evidence and confidence, and it
  learns the factory vocabulary from the repository manifests.
* The `TestSetOps.checkPhoto` intent is kept: dial found, 12 found, pose not RETAKE, marker-layout
  tilt. It now runs headlessly in `Suit`.
* The `TestSetStore` manifest columns and the `physical_watch_id` convention are kept. Phone-collected
  watches keep their `collected_wNNN` ids.
* `tools/testset/ingest.py` output (`datasets/collected/manifest.csv`) and the `testset-inbox`
  upload branch are both read as sources.
* Exact-duplicate detection by sha256, and quarantine for "not sure" and genuine-in-title posts.

What remains Android-only: the Collect screen, share-to-Collect and the RepTimeQC screen. They are
optional manual routes. Nothing in the harvester needs them.

## Calibration notes (from the first full run over the repository's sources)

* **Studio photos defeat perceptual hashes.** Two Bob's Watches catalogue photos of different
  126710BLNRs (dates 26 and 29, different hand positions) were within dHash 6 / pHash 8 bits of each
  other. A hash match is therefore only a candidate. It is confirmed as "the same photograph" when at
  most 0.5% of pixels differ strongly after resampling both to 256 px. The measured values:
  resized/re-encoded copies 0.000, synthetic dial crops 0.000–0.002, the Bob's pair 0.054, and
  synthetic same-set-up photos 0.010–0.015. If the other photo is not on disk to compare, the match
  is "possible" and the watch is quarantined (`quarantine_possible_same_watch`), not rejected.
* **"No readable dial" is the app's verdict and is kept.** When the analyser finds dial-like
  outlines but too few clean markers and no stable minute frame, it discards the geometry and
  judges nothing ("No readable dial"). The harvester records `reject_no_dial` with that detail, and
  does not override it even where a person can see the watch. Two Elegant Swiss 126710BLRO listing
  photos are examples. On the 252 corpus photos that are also in the regression list, the harvester's
  analysis matches the regression harness exactly: 142 no readable dial, 60 with the 12 found,
  50 without.
* **Black dials.** App-usable photos in the corpus reach 73% crushed-black dial pixels, 10% clipped
  and 16% "glare" (bright, desaturated: mostly lume). The pixel limits (95% / 25% / 30%) only catch a
  dial that is all black or washed out. Sharpness (Laplacian variance, dial resampled to 512 px)
  ranges 52–5100 on app-usable photos, 5th percentile 257. The limit is 25.
* **Dial size.** The app read the 12 on dials of 153–256 px (0th–5th percentile). The harvester's
  floor is 200 px; the app's own "too small" flag handles the rest.

## Provenance handling

* Curated repository labels are taken as recorded, with the file named as evidence.
* `gen_candidate` (a seller says genuine) is **quarantined**, never accepted as genuine. One
  exception: a source curated conservatively as `gen_candidate` but hosted by an auction house,
  dealer or Rolex CPO programme gets that tier, and the change is written into the evidence.
* A replica must have an explicit replica source (a QC community, or a curated `rep_labelled`), a
  resolved supported reference and a named factory. Otherwise it is quarantined.
* Sotheby's image CDN disallows the harvester in robots.txt. Those eight phase B sources are
  recorded as `blocked_robots` (final) and are not worked around.

## Known limits

* Hosted runners can be refused by some image hosts. The same CLI runs on a VM
  (`--loop --interval 3600`, or cron).
* The Reddit adapter needs a Reddit-approved API app (`REDDIT_CLIENT_ID/SECRET`). Until then, Reddit
  posts found by other adapters wait as `deferred` and are processed automatically when
  credentials appear.
* Web search needs a Brave Search API key. The Google Custom Search JSON API is closed to new
  customers and scheduled to shut down on 2027-01-01, so it is not implemented. Providers sit behind
  `SearchProvider`.
* Imgur rate-limits unauthenticated album access from some IPs. `IMGUR_CLIENT_ID` uses the
  documented API instead.
* Discovery priorities rank the model/factory groups by need. They do not guarantee that a query
  finds usable photos of that group.

## First run (2026-09-30, repository sources only, no credentials)

The "repo" adapter found 82 sources (72 from the repository's source lists, 10 from the
phone-collected manifest). The phone inbox added nothing new, because every upload was already
ingested with reviewed tags. Each source was processed, then everything was re-processed with the
calibrated limits from stored copies (`--reprocess`, 0 downloads). A third run changed nothing
(0 sources examined, 0 downloads, decisions identical).

| | |
|---|---|
| Sources examined | 81 (+1 Reddit post deferred: needs API credentials) |
| Failed, final | 13: Sotheby's CDN robots.txt ×8, Imgur album gone ×4, listing 410 ×1 |
| Images | 262 local corpus copies + 49 downloads from dealer/auction/CPO pages and CDNs |
| Near duplicates | 21, all within one watch: listing pages serve the same photo at several sizes (srcset) |
| Measurement-quality images | 85 |
| Accepted physical watches | **49: genuine 19, replica 30** (78 usable images) |
| Quarantined | 5: seller-asserted genuine ×4, replica without factory ×1 |
| Rejected | 14: no readable dial / pose / landmarks on every photo |

Replica by factory: VSF 12, Clean 9, Rich 5, ARF 3, C+ 1. By model: replica 126710BLNR 24,
126710BLRO 3, 126710GRNR 2, 126720VTNR 1; genuine 126710BLNR 12, 126710BLRO 7.
The next priorities follow from that: genuine 126710GRNR and 126720VTNR, and replica BLRO, GRNR and
VTNR from VSF, Clean and ARF.
