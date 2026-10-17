# Claude instructions for Watch Align

## The one hard rule (owner, 17 Oct 2026)

**The app works from a single photo of the watch** (an uploaded dealer/QC photo). Do not require multiple photos or
guided capture.

Everything else in this file and in the project docs is guidance. Follow it by default, but overrule it when the
evidence says so, and say why in the commit or PR.

## What we are building

When the genuine overlay is placed on the photo, find the hour markers that are **clearly off**: visibly misplaced
(sideways, or in or out towards the centre) or visibly tilted. Small, sub-pixel differences from genuine watches are not
the goal.

For each marker, the result is one of:

- **Clearly off**: unmistakably out of place, with a close-up showing it.
- **No visible issue**.
- **Can't check**: a hand, glare or the photo quality makes the reading unreliable.

Measured numbers belong in the details for anyone who wants them.

## Guidance (defaults, not rules)

- **Genuine baseline.** Use proven-genuine photos to place the overlay and as a sanity check: genuine markers should
  not come out "clearly off".
- **Unclear evidence.** Prefer "Can't check" to a guess when a hand, glare or blur affects a marker.
- **Show the evidence** for every "clearly off" marker.
- **Test before shipping.** Prove a change offline first, with ground truth where possible (markers moved by known
  amounts, genuine photos).
- **Protect existing behaviour.** Adding a model should not change existing models unless that is intended and tested.
- **Wording.** Describe what was measured. Avoid claiming a watch is genuine or fake.
- **Private photos.** Never commit or publish the owner's private photos.

## Background documents

These describe the project's history and earlier rules: `docs/QC-GUARDRAILS.md`, `docs/PRODUCT_SCOPE.md`,
`docs/CALIBRATION_PROTOCOL.md`, `docs/HANDOFF.md`, `AGENTS.md` and `docs/WATCH_ALIGN_ALIGNMENT_ONLY.md`.

They are useful context, but **where they conflict with this file, this file wins**. Their old task orders, "paused
work" lists and restart plans are historical and no longer apply.
