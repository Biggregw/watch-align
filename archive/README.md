# Watch Align archive control folder

This folder drives the OneDrive photographic archive. It never contains photographs.

- `requests/` — commit a new `<name>.json` here to ask the archive workflow to do something
  (`verify`, `reconcile`, `recover`, `review_batch`, `keepalive`, `reauthorize`). Examples:
  `docs/image-archive/examples/`.
- `results/` — the workflow's answer to each request (same file name), committed automatically.
- `batches/` — one index per review batch: SHA-256, model, watch id, source URLs and the OneDrive folder
  holding its viewing copies.
- `decisions/` — review decisions (`<batch>-<reviewer>-<n>.json`), written by ChatGPT, Claude or the owner.
  See `CHATGPT-REVIEWER.md`.
- `approved-evidence.json` — rebuilt automatically; the only list of photos and markers allowed into calibration.

Full description: `docs/image-archive/AUTOMATION.md`.
