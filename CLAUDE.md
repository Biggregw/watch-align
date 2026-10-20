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

## Two-tier alignment classification (owner, 20 Oct 2026)

Alongside the existing individual-marker visual-QC objective, the owner explicitly wants an **experimental whole-watch
alignment-based classification** from the same measurements on the same single uploaded photo. This does **not** replace
the individual-marker results or their close-up evidence.

Apply two independent tiers:

- **Strict genuine-alignment standard:** all required hour markers must meet the separately maintained strict pooled
  genuine measurement tolerances. The owner deliberately accepts that some authentic Rolex watches will fail this
  demanding standard. Do not relax genuine limits merely to make every known genuine watch pass.
- **Acceptable replica-QC standard:** keep the existing, separately adjustable visual acceptability limits and the
  existing "clearly off" marker checks. Do not silently replace them with the strict genuine limits.

Whole-watch display labels, explicitly approved by the owner:

- **Genuine:** all required hour markers are reliably assessed and all meet the strict genuine-alignment standard.
- **Replica:** at least one credible measurement falls outside the strict genuine standard, and no credible measurement
  fails the acceptable replica-QC standard. One marker is enough.
- **Replica — QC Issues:** at least one credible measurement fails acceptable replica QC; show the specific
  marker(s) and magnified evidence.
- **Can't Determine:** no confirmed failure and a required marker cannot be assessed reliably (hand obstruction, blur,
  glare, ambiguous metal edges, missing measurements), or a photo-wide pose or internal analysis failure.

Decision order (owner, 20 Oct 2026: a confirmed failure takes precedence over unrelated unreadable markers):

1. A photo-wide pose or analysis failure is always **Can't Determine**.
2. Any credible measurement over the replica-QC limits → **Replica — QC Issues**, even if another marker is
   obscured or uncertain.
3. Otherwise, any credible measurement outside the strict genuine limits → **Replica**, even if another marker
   cannot be assessed.
4. Otherwise, any required marker missing or unreliable → **Can't Determine**.
5. **Genuine** only when every required marker is reliably measured and passes every strict genuine limit.

**Credible measurement (owner, 21 Oct 2026):** a modest edge-fit imperfection (slightly rough, partly inconsistent,
loosely fixed, low resolution) does not discard a usable, out-of-range reading. Not credible, and never a failure:
missing or non-finite values, anything withheld (Can't check, hand gate), an outline mostly reconstructed, a
geometrically corrupted fit (RMS over 1 px, or more of the edge inconsistent than consistent), or an uncertain fit
beside a confirmed hand (the validated refit decides those). A genuine pass still needs every marker reliable. The
genuine and replica-QC tolerances are frozen; do not recalibrate them. When a Replica or Replica — QC Issues result
rests on partial marker coverage, show a warning that some markers could not be assessed and additional QC issues may
exist.

Require 12 physical hour markers for No-Date models and 11 for models with a date aperture. Never average individual
defects away, count an unmeasured marker as a pass, or classify a watch as a replica from a missing or corrupted
measurement.
Check photo quality first; invite a new, straight-on photo when the result is unexpected.

**Interpretation is mandatory:** "Genuine" and "Replica" are owner-approved *alignment-based screening predictions*,
not verification of authenticity, manufacturing origin or movement. Display a visible, concise disclaimer that real
Rolex watches can fail these strict limits and well-aligned replicas can pass. Never present the label as a conclusive
authentication or invent a confidence percentage.

**Frozen calibration baseline:** `docs/CALIBRATION_BASELINE_2026-10-20.md` records every genuine and replica-QC
tolerance, the resolution allowance, the reliability and credibility rules and the classification order, with their
source constants. Any change to a tolerance or rule there needs the owner's explicit approval and must be documented
with its old and new values, date and commit.

Keep strict-genuine and adjustable-replica calibration settings separate, preserve the old individual-marker QC
behaviour, and do not alter source/reference data merely to improve classification rates. Implement using the existing
marker measurements; do not introduce a second detection engine.


## Guidance (defaults, not rules)

- **Genuine baseline.** Use proven-genuine photos to place the overlay and as a sanity check: genuine markers should
  not come out "clearly off".
- **Unclear evidence.** Prefer "Can't check" to a guess when a hand, glare or blur affects a marker.
- **Show the evidence** for every "clearly off" marker.
- **Test before shipping.** Prove a change offline first, with ground truth where possible (markers moved by known
  amounts, genuine photos).
- **Protect existing behaviour.** Adding a model should not change existing models unless that is intended and tested.
- **Wording.** Describe what was measured. Whole-watch **Genuine** / **Replica** labels are explicitly permitted as
  non-conclusive alignment-based predictions with a visible disclaimer; never claim verified authenticity.
- **Private photos.** Never commit or publish the owner's private photos.

## Background documents

These describe the project's history and earlier rules: `docs/QC-GUARDRAILS.md`, `docs/PRODUCT_SCOPE.md`,
`docs/CALIBRATION_PROTOCOL.md`, `docs/HANDOFF.md`, `AGENTS.md` and `docs/WATCH_ALIGN_ALIGNMENT_ONLY.md`.

They are useful context, but **where they conflict with this file, this file wins**. Their old task orders, "paused
work" lists and restart plans are historical and no longer apply.
