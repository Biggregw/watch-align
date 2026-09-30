# Dataset harvester

Unattended, headless builder of the Watch Align genuine/replica GMT test set. It runs on any Linux
machine, a small VM, or GitHub Actions, with no phone involved:

```
DISCOVER -> NORMALISE SOURCE -> DOWNLOAD -> GROUP BY PHYSICAL WATCH -> DEDUPLICATE -> CHECK IMAGE QUALITY
-> CLASSIFY METADATA -> RUN WATCH ALIGN MEASUREMENTS -> ASSESS DATASET VALUE -> ACCEPT / QUARANTINE / REJECT
-> SAVE STATE -> CONTINUE
```

## Run it

Needs Python 3.11+ and JDK 17 (the photo check and measurements are the app's own Java analysis,
run through `tools/desktop-harness`; the OpenCV jar is downloaded from Maven Central on first use).

```bash
python3 -m pip install -r tools/dataset_harvester/requirements.txt
python3 tools/dataset_harvester/harvest.py --dry-run          # plan: what would be fetched, no writes
python3 tools/dataset_harvester/harvest.py                    # one bounded run (25 sources)
python3 tools/dataset_harvester/harvest.py --max-sources 200  # a longer run
python3 tools/dataset_harvester/harvest.py --report           # rewrite report/manifest from state
python3 tools/dataset_harvester/harvest.py --reprocess        # re-evaluate processed sources from stored copies
python3 tools/dataset_harvester/harvest.py --loop --interval 3600   # keep going on a VM (Ctrl-C / SIGTERM stops cleanly)
python3 -m unittest discover -s tools/dataset_harvester/tests -v      # tests (no network, no Java)
```

Options: `--discover`, `--process` (either alone runs only that stage), `--once` (default),
`--source-adapter repo|phone|search|reddit` (repeatable), `--data-dir PATH` (default
`datasets/harvest`, or `$HARVEST_DATA_DIR`), `--shards N` (parallel analysis JVMs).

A cron line for a VM: `17 * * * * cd /srv/watch-align && python3 tools/dataset_harvester/harvest.py --max-sources 40 >> harvest.log 2>&1`.
GitHub Actions: workflow **Dataset harvester** (`.github/workflows/dataset-harvester.yml`), daily
schedule plus manual `workflow_dispatch`, one run at a time (`concurrency: dataset-harvester`).
State is kept on the `data/harvest` branch; images stay in the Actions cache and are never committed.

## Discovery adapters

| Adapter | Needs | What it finds |
|---|---|---|
| `repo` | nothing | every source already recorded in this repository: `datasets/126710BLNR` corpus, `datasets/gmt_phase_b_genuine`, `tools/research/validation/gmt12` lists, the phone-collected manifest (`datasets/collected/manifest.csv` or `$HARVEST_COLLECTED_MANIFEST`), and anything placed in `datasets/inbox/<gen\|rep\|unknown>/` (image folders = one watch each; `*.txt` = URL lists) |
| `phone` | `HARVEST_PHONE_INBOX` (a checkout of the `testset-inbox` branch) | photos uploaded from the app's optional Collect screen; photos already ingested with reviewed tags are skipped |
| `search` | `BRAVE_SEARCH_API_KEY` | web results from the Brave Search API for the most under-represented model/factory groups |
| `reddit` | `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET` (Reddit-approved app, official API) | RepTimeQC posts for the most under-represented replica groups |

Album resolution: Imgur albums use the Imgur API when `IMGUR_CLIENT_ID` is set, otherwise
`gallery-dl` (as the 126710BLNR corpus fetcher already does). Reddit posts found by any adapter are
only opened through the official API; without credentials they wait in state as `deferred`
(`needs_reddit_api`) and are picked up automatically once credentials exist. No search engine or
Reddit HTML is scraped, robots.txt is respected for page and image fetches, requests to one host are
at least one second apart, and a refusal (401/403/robots) is recorded, never worked around.

Missing credentials never stop a run: that adapter reports `disabled` and the rest carry on.

## Rules it keeps

* **One post/album/listing = one physical watch**, unless a curated manifest maps several sources to
  one `physical_watch_id`. Reports always give independent-watch counts and image counts separately.
* **Deduplication**: sha256 (exact), whole-image dHash + pHash (resized/re-encoded copies), and a pHash
  of the dial region (crops of the same photograph). The first copy held is the original; later copies
  never count again, and a source whose photos all belong to another watch is rejected as a duplicate.
* **Labels carry evidence and confidence.** Curated repository labels are taken as recorded. Otherwise
  class comes from the source (replica QC community, dealer/auction/CPO, marketplace), reference and
  factory from the title (the app's TitleTags rules, factory vocabulary learned from the manifests).
  Anything low-confidence, conflicting, or a title that mentions a genuine beside a replica goes to
  QUARANTINE. Seller-asserted genuine (`gen_candidate`) is quarantined, not accepted as genuine.
* **Photo suitability** uses the app's own analysis (dial found, 12 marker found, pose label from
  GmtHumanPosePolicy, marker-layout tilt, hand at 12, round markers) plus pixel checks on the dial
  (resolution, completeness, sharpness, crushed/blown pixels, glare). Thresholds and their reasons are
  in `harvester/config.py`. Reason codes: `reject_no_dial`, `reject_low_resolution`, `reject_pose`,
  `reject_blur`, `reject_occlusion`, `reject_duplicate`, `reject_incomplete_dial`,
  `reject_landmarks_unusable`, `reject_underexposed`, `reject_overexposed`, `reject_glare`,
  `reject_unreadable`, `inconclusive_*`.
* **Perspective diagnostics** (recorded, not used to correct anything): rehaut widths at 12/3/6/9 and
  their top/bottom and left/right ratios with coverage as confidence, the dial-ellipse tilt and which
  axis it foreshortens, and the marker-layout tilt.
* **Measurements**: accepted-quality images go through the existing `Batch` driver (the 337-photo
  regression measurement); the raw rows are stored with each image. If the harness cannot analyse
  an image it is inconclusive; nothing is estimated in its place.
* **Decisions**: ACCEPT needs confident labels and at least one measured, non-duplicate image.
  QUARANTINE holds anything potentially useful but uncertain. REJECT is clearly unusable, duplicate,
  or an unsupported reference. Every decision records its reasons.
* **Dataset value**: discovery works on the (class, model, factory) groups with the fewest accepted
  independent watches first; each accepted watch gets a value score (new independent watch, group and
  model coverage). A group with 40 or more accepted watches quarantines further ones as low value
  rather than growing without purpose.

## Outputs (in the data directory)

* `state/sources.jsonl`, `state/images.jsonl`: resumable state (canonical URL, adapter, ids, times,
  status, failure reason, retries, image URLs, sha256, hashes, watch id, labels, confidence, decision).
* `manifest.csv`: one row per image, starting with the corpus columns `local_path, class_label,
  physical_watch_id, model, factory, source`, then provenance, hashes, suitability, measurement and decision.
* `watches.csv`: one row per physical watch.
* `reports/<run>.json|md` and `reports/latest.*`.
* `measurements/<run>/chunk_NNN/r*.csv`: raw Batch measurement tables.

Existing manifests and collected data are read, never rewritten.
