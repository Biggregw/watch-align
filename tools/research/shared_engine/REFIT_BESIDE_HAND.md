# 1.6.6: a baton measured without the edge beside a confirmed hand

Owner, 20 Oct 2026. A known-genuine Submariner Date (dealer photo, dial 477 px across) came out **Can't Determine**:
ten markers met the strict genuine standard, but the 6 baton's outer-metal fit was uncertain (rough 1.44 px edge, 66 %
of it inconsistent). It read 0.60 % R sideways (limit 0.96 % R including the 0.5 px allowance) with a standard error of
±0.07 % R. The owner's photo is not committed; it was used locally only.

## What was wrong with the 6 baton

The seconds hand runs just outside the baton's right-hand metal edge. The independent per-marker check found the hand
(`handClean` false), and the seconds line was detected at 175.85°. That is about 1.5° inside the real hand, so the
seconds-hand mask (`MetalFit.onSecondsHand`) removed only 8 points of the right edge. Per side of the fitted outline:

| side | points used | mean residual | note |
|---|---|---|---|
| right (beside the hand) | 102 | +2.0 px | ramps 0.55 → 4.5 px down its length, following the hand closing in |
| left | 133 | +1.3 px | constant: the whole fit shifted ~1.3 px towards the hand |
| ends | 44 / 36 | −0.2 / −0.3 px | clean |

The rigid fit split the hand's pull between both long sides. Almost all of the 0.60 % R is hand bias, and the ±0.07 %
R is a purely statistical figure that cannot see it. **The uncertain flag was right.** The same photo's 9 baton, with no
hand, fits the same outline to 0.14 px.

On 19 perturbations of the photo (crops, resizes 0.85–1.35, brightness, contrast, JPEG q70/85), the main fit flips
between a clean −0.05 % R and hand-pulled readings of −0.53…−0.73 % R, with tilts up to 1.18° (over the strict 1.10°).

## The correction

`MetalFit.fit`: when a baton's fit is uncertain **and** the per-marker check confirmed a hand, it is fitted again
without the one side the detected hand runs along. That means the hand line is within 15° of parallel to the side and
within 0.02 R of its midpoint, and exactly one side qualifies. The refit (`Fit.alt`) is kept only when what remains is
clean by the reliable-fit standards (RMS ≤ 0.5 px, ≤ 15 % inconsistent, ≥ 50 % seen, standard errors within limits).

`ClearlyOff` measures the refit like any reading (`Marker.altSidePct/altInoutPct/altTiltDeg`). It also measures the
**independently** found luminous centre's offset from the refit across the side left out (`altLumeDiffPct`): that is
the coordinate the left-out side would have fixed.

`WatchClassification.alt` uses the refit **only for the strict genuine standard**, and only when all of these hold: the
marker is a baton; its own reading is uncertain but within replica QC (No visible issue); nothing is withheld; the dial
is at least 400 px across; and the lume agrees within `ALT_LUME_AGREE_R`. With the validated one-side uncertainty U:

- **PASS** when |position| + U<sub>pos</sub> and |rotation| + U<sub>rot</sub> are both inside the strict limits;
- **FAIL** (Replica) when |position| − U<sub>pos</sub> or |rotation| − U<sub>rot</sub> is outside a limit;
- otherwise **undecided** (Can't Determine).

The marker's own reading, its replica-QC answer (green tick), its close-up, every tolerance, the references and the
overlays are unchanged (TrackDump main columns byte-identical).

## Validation of U (verified genuine photos, CI `three-options.yml`, `drivers/SideDrop.java`)

Every reliably fitted genuine baton refitted with each side left out in turn:

| set | batons | long side: max Δposition | long side: max Δtilt (p99) | end: max Δposition |
|---|---|---|---|---|
| GMT 126710 (66 photos) | 96 | 0.135 % R | 0.77° (0.57°) | 0.30 % R |
| Submariner Date 126610 (99) | 185 | 0.127 % R | 0.24° (0.21°) | 0.29 % R |
| Submariner 124060 (132) | 316 | 0.128 % R | 0.46° (0.29°) | 0.95 % R |

Hands are radial lines, so the parallel test only ever leaves out a long side. **U<sub>pos</sub> = 0.20 % R** (≈1.5 × the
largest of about 1,200 long-side refits) and **U<sub>rot</sub> = 0.80°** (above the largest, 0.77°).

Lume vs the reliable metal fit, in total: p99 0.48–0.86 % R (max 1.04). That is too loose to catch a misleading edge, but
it is almost all along the baton. Across it: being measured (three-options CI); the tolerance is set from it.

## Traps (`drivers/RefitTraps.java`, CI; `MetalFitTest`)

| case | refit | lume across | result |
|---|---|---|---|
| hand beside the right edge, baton in place / moved 0.6 / 1.2 % R | error 0.005–0.047 % R | 0.001–0.021 % R | accepted, move measured |
| plus a reflection evenly along the left edge | error 0.92–0.98 % R (looks clean) | 0.93–1.0 % R | refused |
| hand not confirmed by the per-marker check | none | | |
| hand crossing the baton, or down its middle | none (no single side) | | |

## The owner's photo

The refit leaves out the right edge and reads −0.04 % R sideways, −0.03 % R in/out, −0.24° (lume across 0.038 % R). That
is stable over all perturbations where it ran (−0.036…−0.073 % R; −0.28…+0.24°), and it matches the other markers
(−0.27…+0.10 % R). Position 0.05 + 0.20 = 0.25 ≤ 0.96 % R; rotation 0.24 + 0.80 = 1.04 ≤ 1.10°: **Genuine**, with a
narrow rotation margin.

## Known limitations

- **A hand very close to a baton edge can pull a fit that is not flagged.** On the synthetic dial, a hand 0.5 px outside
  the edge pulled the fit to 1.17° of tilt with no uncertain flag. That risks a false *Replica*, never a false Genuine.
  It is a pre-existing issue that this change does not alter.
- **Not covered:** round markers and the 12 triangle are not refitted, and an uncertain reading over a replica-QC limit
  (Possible) is never overruled.
- **A very bright hand can merge into the lume region** and pull the lume centre. Honest refits are then refused (Can't
  Determine), which is the safe direction.
