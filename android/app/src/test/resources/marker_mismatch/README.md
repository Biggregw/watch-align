# Frozen marker outline evidence

These are numeric detector exports, **not calibration references or QC limits**.
No photographs are committed.

- `pepsi_alpha106.csv`: baseline and usable 9-baton variations from
  [run 38155286452](https://github.com/Biggregw/watch-align/actions/runs/38155286452),
  artifact `genuine-pepsi-9-yellow-template-sensitivity-numbers`.
  Original: Phillips CH080120 lot 75, 126710BLRO,
  SHA256 `3cf5442ab9c87b9c0b9481fd0eb2b92316a0287581635c49fc9cf6cdc4f31a00`.
  All variations derive from ONE photo of ONE physical watch. The original's
  physical-watch column in this historical export contains the variant ID; it
  must not be interpreted as multiple independent watches. Two failed poses
  have no marker rows and are recorded in the workflow log.
- `wos_alpha106.csv`: one independent physical watch, `wos_cpo_40616911`, from
  [run 38145439812](https://github.com/Biggregw/watch-align/actions/runs/38145439812),
  artifact `marker-mismatch-gmt_126710`.
  SHA256 `a5431af6379b78a5233303aeec379b30ef8a5c46ffa70be67a55a1126de8c138`.

Historical `*_pct` columns divide by union area. Tests recompute the owner's
filled-expected-area percentage from vertices/areas. Area and vertex exports
have eight decimal places; regression precision accounts for that rounding.
See `docs/research/marker-mismatch-overlay-consistency-2026-10-11.md` for the
full-precision local reruns and withholding rules.
