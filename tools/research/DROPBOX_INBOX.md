# Research-only Dropbox inbox acquisition

This is a **manual pilot**, entirely separate from the existing Watch Align dataset harvester.
No production measurement, thresholds, photo quality filters, classifiers, references or
calibration datasets are changed.

The initial manifest contains ONE clearly reusable Creative Commons Commons photograph
labelled Rolex GMT-Master II 126713GRNR. It is **not a 126710BLNR/BLRO**, and its
genuineness is **unknown**: it is only an external photo transport trial. Do not count it
as genuine, and do not put it in the calibrated dataset.

The script queries Wikimedia Commons via its official MediaWiki API; checks the image
licence for CC0/CC BY/CC BY-SA/public domain, and excludes files explicitly described
as replicas. It downloads the original bytes, not previews; there is **no photographic
quality filtering**. Raw files are never recompressed. Each image and its metadata JSON
go under `/Watch Align/Incoming Photos/<model>/`, with filenames based on SHA-256.
The script downloads the saved image from Dropbox and compares bytes and SHA-256 before
marking the intake successful. Everything is labelled UNREVIEWED / AUTHENTICITY UNKNOWN.

### One-time prerequisite

For an initial one-off trial, configure a single GitHub Actions secret
`WATCHALIGN_DROPBOX_ACCESS_TOKEN` using a short-lived token from the Dropbox App Console.
For recurring use, the refresh-token configuration below avoids expiry.

GitHub cannot reuse the ChatGPT Dropbox connector's existing login.
Create a scoped **Full Dropbox** OAuth app with files.content.read / files.content.write;
authorise it with offline token access. Keep secrets out of chat and code. In the
GitHub repo's Actions secrets set:
- `WATCHALIGN_DROPBOX_REFRESH_TOKEN`
- `WATCHALIGN_DROPBOX_APP_KEY`
- `WATCHALIGN_DROPBOX_APP_SECRET`

To run it, the manual workflow must be on the default branch (currently this is an
unmerged research branch). Run with dry_run=true first, then a single dry_run=false
trial after authentication. No cron or automatic bulk collection.

Bob's Watches is **not** in the automated source manifest; its terms disallow scraping.
The uploader's Commons licence is distinct from independent Rolex authenticity.
Dealers with personal-use download licences alone are not automatic scraping approvals.
Only source classes whose automated retrieval and retention are allowed can be enabled.

Once a photo arrives, visually inspect and classify it in Dropbox; no code automatically
promotes anything into Suitable Photos. This is not a claim of a completed live Dropbox
upload until an actual run has succeeded and its readback has been checked.

## One-time isolated branch pilot
The two folders `/Watch Align/Incoming Photos` and
`/Watch Align/Incoming Photos/GMT-Master-II-126713GRNR` were created through
ChatGPT's connected Dropbox access. The GitHub uploader does not create folders,
so its access token only needs files.content.read/write. One branch-specific push
of `tools/research/dropbox_inbox_pilot.trigger` runs exactly one candidate;
no production/default-branch merge is required. Do not repeat the trigger after
one successful test. Remove this one-time push trigger before merging.
