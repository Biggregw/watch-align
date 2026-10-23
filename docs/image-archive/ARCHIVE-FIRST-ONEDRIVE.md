# Watch Align — archive originals first, review for calibration later

## Design boundary

This branch is based on draft PR #69; it is **not** an Android change and must not be merged into main as a shortcut.

- \`unreviewed/objects/<hash-prefix>/<SHA256>.<extension>\` contains original bytes, with no resizing or re-encoding.
- \`unreviewed/manifests/runs/*.json\` records source catalogue associations, original SHA-256, model and physical-watch identifiers where known, and any identity conflicts.
- Every record is explicitly \`review_status=unreviewed\` and \`calibration_approved=false\`. Unknown provenance stays unknown.
- Neither the archive admission rules nor calibrator inputs are changed. **Do not feed raw/unreviewed photos to the calibrator until reviewed.**
- No photograph, original or stored catalogue is deleted. Source-photo suitability is deferred to manual review.
- The same photo hash is backed up once per run; existing remote content must match exactly.
- No harvesting, quality rejection, access-scope escalation or autonomous workflow is performed.

## One-time recovery in the already authorised personal Codespace

The personal OneDrive app registration uses Microsoft Graph delegated \`Files.ReadWrite.AppFolder\`. The connection's synthetic upload/download/cleanup smoke test already passed; the credentials are held only in the owner's Codespace outside the git checkout.

\`\`\`bash
git fetch origin experiment/archive-first-onedrive-20261023
git switch experiment/archive-first-onedrive-20261023
python -m pytest tools/image_archive/tests/test_unreviewed.py -q

mkdir -p /tmp/watch-align-originals
gh run download 37194686346 --name watch-calibrator-124060-37194686346 --dir /tmp/watch-align-originals

# Read-only original-byte inventory across the repo and recovered artifact:
python -m tools.image_archive.unreviewed --scan /tmp/watch-align-originals
\`\`\`

This artifact was independently listed as still available on 23 October 2026. Its 471 members previously yielded 384 *catalogue-matched* exact originals, but these are historical recovery numbers. The actual local preflight count, hash checks and any later upload must be reported separately. Other historical artifact/cache photos have not yet been recovered.

## Explicit three-photo OneDrive pilot — no calibration

After checking the dry-run count, the owner can choose to run:

\`\`\`bash
python -m tools.image_archive.unreviewed --scan /tmp/watch-align-originals --limit 3 \
  --upload-unreviewed --authorized \
  --client-id 06a3bb10-a5d3-4dde-9654-1e61de7cd064 \
  --cache /tmp/watch-align-private/msal-cache.json
\`\`\`

This command uploads the first three catalogued originals in hash order (or uncatalogued images if none are catalogued) (not three validated genuine watches), verifies every remote original against its exact local bytes, and writes an immutable catalogue manifest.

Do **not** publish the MSAL cache, browser consent codes, or bearer/refresh tokens in chat, Git, build logs, or public Actions artifacts.

If a three-photo pilot passes and the owner approves a full migration, omit \`--limit 3\` from that same command. The dry run does not contact OneDrive. The upload requires explicit \`--upload-unreviewed --authorized\` flags and the existing credential cache.

The Codespace can stop and start, but deleting or rebuilding it can lose its local credential cache. Preserve credentials outside the repository in a private persistent store, or re-authorise when needed. Do not commit or upload a token cache as a research artifact.

## What the results do not establish

A successful upload does not establish:
- that a photo is genuine or front-on;
- that its markers are suitable for measuring;
- that duplicated watch IDs are verified unique watches;
- that ChatGPT currently has a direct OneDrive connector.

Review will require making selected originals accessible in a review session (or a separate secure application connector), then recording per-image and per-marker decisions. The source originals must remain unchanged. A future reviewed manifest may feed the exact production measurement engine for calibration; this command deliberately does not.
