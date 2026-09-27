# Video 33063 rehaut-perspective pilot — 2026-09-26

Source: user-supplied ~59.7 s Batgirl wrist video, 2160×3840, ~30 fps. Same physical watch throughout. This is not a controlled known-angle capture, but it provides a continuous natural pose sweep before a deliberate tilt series can be recorded.

## Sampling
Sampled one frame every 2 s (30 frames total). Frames were cropped to a broad watch region and downscaled before running the research-only rehaut perspective prototype. No production QC logic or thresholds were changed.

Raw measurements are in `video_33063_rehaut_pilot_2026-09-26.csv`.

## Result
The provisional rehaut detector returned a constrained result on 27/30 sampled frames.

Observed range across successful frames:

- vertical asymmetry V: -0.361 to +0.539
- horizontal asymmetry H: -0.330 to +0.292
- first-harmonic pose strength: 0.026 to 0.593

Representative near-frontal candidates:

- 36 s: V=-0.024, H=-0.010, strength=0.026, min/mean=0.929, fit residual=0.100
- 52 s: V=-0.047, H=+0.003, strength=0.047, min/mean=0.906, fit residual=0.062

Representative strong-angle candidates:

- 6 s: V=-0.361, H=+0.257, strength=0.443, min/mean=0.371
- 10 s: V=+0.325, H=+0.292, strength=0.437, min/mean=0.226
- 18 s: V=+0.539, H=+0.246, strength=0.593
- 58 s: V=-0.165, H=-0.330, strength=0.369, min/mean=0.637

The sign and magnitude change substantially through a single continuous same-watch video, including frames close to V=H=0 and frames with strong 2-D asymmetry. This is strong evidence that the rehaut signal is responding to capture pose rather than only fixed watch construction.

## Immediate implication
This video is useful enough to continue the experiment now. It can provide candidate baseline and oblique frames from the same physical watch. It cannot by itself calibrate degrees of camera pitch/yaw because the actual capture angles are unknown.

The next research step is to run the existing GMT12 raw geometry measurement on selected video frames, especially 36 s / 52 s as baseline candidates and 6 s / 10 s / 18 s / 38 s / 58 s as pose-diverse candidates, then test whether raw 12-clearance moves systematically with the rehaut V/H signal.

Do not derive GOOD/CORRECTABLE/REJECT thresholds from this video alone. The later controlled known-angle series remains necessary to turn these signals into a correction model and defensible reject boundary.
