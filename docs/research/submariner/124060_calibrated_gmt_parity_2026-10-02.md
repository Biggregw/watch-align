# 124060 calibrated GMT-parity checkpoint — 2026-10-02

## Canonical product baseline

The production baseline for the Rolex Submariner 124060 is the GMT-style parity implementation originally frozen at:

- branch: `feature/android-sub124060-gmt-ui-parity`
- commit: `5ae65fb9c52a94877f5af75cdaf53315a8f0b738`

That checkpoint deliberately copied the mature GMT Watch Align product contract first: whole-dial verdict badges, every hour position represented, missing/obscured reasons, detailed close-up evidence and the same summary hierarchy. Before calibration its reliable measurements were shown as neutral `M` / measured-not-judged states.

This checkpoint preserves that exact product direction and supplies family-specific calibration. It does not replace the GMT-style UI with a separate Submariner research presentation.

## Calibration provenance

The reusable watch-family calibrator is retained in the repository at `tools/watch_calibrator`, with model policy in `calibration/models/124060.json` and workflow `.github/workflows/watch-family-calibrator.yml`.

Frozen 124060 evidence:

- workflow: `Watch family calibrator`
- run: `36927036008`
- calibration source SHA: `8337fd9344a5cf2f3ff4952f973fd9e74801be52`
- artifact: `watch-calibrator-124060-36927036008`
- artifact SHA-256: `5a23dcf54486d99aa6a0b50cbd7b7027c0a190a777030ff67586706b88d311da`

Policy: development genuine watches fix the limits. Validation and holdout can reject them but cannot move them. Replica evidence is a stress test only and never moves a limit.

## Frozen 124060 bands

| App metric | Clear band | Check band | Product use |
| --- | --- | --- | --- |
| `twelve.rotation_deg` | -13.88038488935 to 15.22676818935 deg | -21.157173159025 to 22.503556459025 deg | 12 alignment, after existing repeatability gate |
| `twelve.gap_r` | none | none | **Measured only. Not judged.** Calibration marked it insufficient because it was pose/scale sensitive |
| `twelve.centring_w` | -0.147616360146 to 0.136472106146 widths | -0.233275680438 to 0.222131426438 widths | 12 alignment, after existing repeatability gate |
| `round.ring_rho` | 0.7826128775166086 to 0.8526717081695576 R | 0.7650981698533712 to 0.870186415832795 R | Aggregate round-marker position |
| `round.spacing_rms_deg` | -5.587231836527973 to 6.370183430972006 deg | -9.573036925694634 to 10.355988520138666 deg | Aggregate round-marker spacing |
| `baton.3_9_line_offset_r` | -0.08170984795356724 to 0.09378104194951688 R | -0.14020681125459528 to 0.1522780052505449 R | 3/9 shared axis judgement |
| `axis.12_6_line_offset_r` | -0.1343132175899482 to 0.1396427365740998 R | -0.21825249276636563 to 0.22358201175051726 R | 12/6 shared axis judgement |

Outside the check band maps to the mature GMT `STRONG` / `check closely` visual state. Missing, withheld or unreliable evidence maps to `UNASSESSABLE` / grey dash.

## Product mapping

The mature GMT visual contract is retained:

- green tick: nothing flagged by the provisional 124060 calibration;
- amber `!`: worth a look;
- red `!!`: check closely;
- grey dash: not judged on this photo.

The 124060 model adapter maps the calibrated relations onto the existing layout:

- 12: rotation + centring + 12-to-6 axis, worst supported result wins;
- 3 and 9: shared 3-to-9 axis result;
- 6: 12-to-6 axis result;
- round markers: aggregate ring-radius + spacing result;
- 12 gap: displayed as measurement evidence only, never used to colour or score the marker.

The existing alpha70/alpha71 reliability gates remain authoritative. A calibration band is not applied when the underlying measurement is unavailable or unstable.

## Explicit non-goals

- No GMT threshold is copied into the 124060.
- No GMT detector or GMT QC behaviour is changed by this integration.
- The frozen bands are not an authenticity classifier.
- The frozen bands are not an overall watch-quality score.
- The calibrator output is provisional until wider phone testing exposes and resolves any concrete false-positive or false-negative cases.

## Version

The integrated Android checkpoint is `1.3.0-alpha72` (`versionCode 13072`).

After this checkpoint is merged, `main` is the canonical source of truth for both the GMT-style 124060 product implementation and the reusable calibration tooling/provenance. Future 124060 work should branch from `main`, not from the earlier alpha70 research-style path.
