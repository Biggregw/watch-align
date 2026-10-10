# Bob's Rolex Harvester photo lists

Each CSV is a harvester "Photo list" (1.4.2+) or a list rebuilt from the owner's ZIP packs: listing, image URL and
sha256 of every accepted photo. No images. Adding a list here runs `.github/workflows/harvest-lists.yml`, which
re-downloads the originals (kept only when byte-identical), measures them and prints the per-family geometry report
(`tools/research/generic_sub/`). Rows are genuine dealer photos used for research; nothing here sets an app limit until
it is promoted into a model's catalogue with the usual held-out checks.
