# Visibility thresholds: evidence and constraints, 2026-10-10

**Scope:** GMT 126710, Sub 124060, Sub Date 126610, all live alignment-reporting families.

**Status:** research/audit only. **No production tolerance change.**
Source data: android/app/src/main/assets/models/*/reference/ and model.json.
To reproduce: python3 tools/research/tolerance_audit/audit.py --out audit_results.

## Evidence from visual psychophysics

- Westheimer & Ley, *J Neurophysiol* 1997, doi:10.1152/jn.1997.77.5.2677
  (https://pubmed.ncbi.nlm.nih.gov/9163383/):
  Line-orientation sensitivity improves with apparent length and approaches optimal performance
  around **0.5 degrees of retinal extent**, not 0.5 degrees of line rotation.
  **Does not provide a watch-specific rotation threshold.**
- Westheimer & McKee, *Vision Research* 1977, doi:10.1016/0042-6989(77)90069-4
  (https://www.sciencedirect.com/science/article/pii/0042698977900694):
  Relative position and Vernier tasks with favourable foveal spatial arrangements reach
  **a few seconds of arc**. Such hyperacuity requires appropriate stimuli and cannot be used
  directly as the tolerance for an isolated polished watch index.
- Westheimer, *J Opt Soc Am* 1977, doi:10.1364/josa.67.000207
  (https://pubmed.ncbi.nlm.nih.gov/839301/):
  Classical visual-resolution acuity in that work ~**1 arcminute** while hyperacuity is
  **4–6 arcseconds**: different tasks with different neural cues.
- Morgan, *Vision Research* 2005, doi:10.1016/j.visres.2005.04.004
  (https://pubmed.ncbi.nlm.nih.gov/15904946/):
  For studied ellipses and rectangles, width/height discrimination thresholds **5–10%**
  and area **10–20%**. These tasks/stimuli are not identical to a lume-filled metal watch marker.
- *Efficiencies for the statistics of size discrimination*, 2011
  (https://pmc.ncbi.nlm.nih.gov/articles/PMC4135075/):
  Two tested observers' circle-diameter discrimination thresholds were **7–14%**
  for *large experimental circles*. This is not a justified Rolex round-dot-size limit.
- *The Clinical Use of Vernier Acuity*, 2021
  (https://pmc.ncbi.nlm.nih.gov/articles/PMC8523788/):
  Vernier sensitivity can be disrupted by visual crowding and context.

## What the actual current application does

From Alpha99Findings on the audited main baseline:

- **1.0°** visibility bar for 6/9/3 baton rotation, 12 triangle angular checks,
  ring rotation and GMT date-window tilt.
- **0.05 × marker full width** for baton position;
  **0.05 × round diameter** for round position and ring shift;
  **0.05 × triangle base width** for triangle lateral position.
- **0.05 × round radius**, i.e. **2.5% of round diameter**, for round size checks.
  Prior verbal descriptions of this as 5% of the diameter were incorrect.
- **0.20 × nominal triangle-to-minute-track gap** for 12 track clearance.
- All visibility bars are applied *after* reference comparison and reliability/interference
  checks. They are not genuine manufacturing tolerances. Existing position edge-consistency
  safeguards and low-resolution checks are separate.
- The 12 track clearance is capped at WORTH because its photo-to-photo allowance is not
  available; there is no justification to promote it to CLEAR.
- Sub 126610 date geometry is present but lacks a genuine date reference. It must not be
  assigned a numerical threshold from GMT images alone.

## Why a blanket update would be unsound

The cited experiments test psychophysical tasks (foveal lines, ideal intervals and large
shapes) that differ from casually viewing a small reflective wristwatch. There is no
single universal relationship between *laboratory minimum detectable displacement*,
*workmanlike inspection*, *manufacturing quality*, and *Reddit GL/RL decisions*.

To set numerical watch-specific visibility cutoffs responsibly, first establish actual
physical dial dimensions, a declared task (normal wear vs careful close inspection),
viewing distance, illumination, optical contrast and a validation procedure. Then
measure detectability across geometric deviations and fit a psychometric function
with uncertainty, ideally using multiple observers. Validate the chosen thresholds
against held-out genuine watches and positive controls.

The audit **does not justify changing 1°/5%/20% today**, nor does it justify automatically
setting thresholds for unsupported features. Existing genuine reference files must not
be overwritten without new verified independent genuine evidence and a reproducible
calibration/held-out regression.

## Current missing evidence

1. **Sub 126610 date window:** genuine reference absent; no measured date_tilt or centring
   threshold available.
2. **Sub 126610 photo repeatability:** borrows 124060 uncertainty until multiple
   verified photographs of the **same individual** 126610 watches are available.
3. **Baton width and length:** no production reference families for numerical QC;
   width-agreement exists to protect position reliability, not to call size defects.
4. **Real physical dial dimensions and psychometric data:** missing per model and
   viewing scenario.
5. **Model-specific known-defect holdouts:** needed before promoting any new visibility
   policy.

**Bottom line:** The right output is an audited, versioned candidate policy and
documentation of blocked features, not a falsely precise new number for every marker.
