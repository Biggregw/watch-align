# Watch Align same-watch video perspective analysis

## Scope

Source: the user's own replica GMT watch, recorded continuously while the camera moves around the same physical watch.

This is useful specifically because the watch geometry cannot change during the video. Any change in measured marker position is therefore measurement/viewpoint error, not a real change in the watch. It is not authenticity evidence.

The analysis sampled 20 keyframes from the first ~42 seconds of the video. Nineteen produced a usable minute-track ellipse. Across them, 124 individual marker measurements passed the detector's existing sanity gates.

The low-tilt reference for each marker is the median of five well-measured frames at approximately 4.7 to 7.7 degrees apparent tilt, with 10-11 markers measurable per frame.

## Main result

The video confirms that radial position changes with viewing angle on the same watch.

Examples relative to the low-tilt reference:

- 16.3 degree frame: marker 8 moved +6.57 %R and marker 12 moved -7.96 %R.
- 20.2 degree frame: markers 1, 4, 5 and 8 moved approximately +3.82, +2.86, +4.00 and +5.06 %R.
- 25.5 degree frame: marker 1 moved +2.92 %R while markers 4 and 5 moved approximately -3.34 and -3.49 %R.

The sign and affected sector change with viewpoint. This is not a simple global radial scale error that can be fixed by applying one correction based only on tilt magnitude.

## Where the error enters

The final radial value and the marker detector's local radial displacement are almost the same quantity in this video:

- correlation of their frame-to-frame residuals: 0.9997
- mean absolute difference between them: 0.027 %R

So the radial drift is already present when the detected marker centroid is compared with the ellipse-projected expected marker centre. The later ellipse 'undo distortion' calculation adds almost none of the drift.

This narrows the likely cause to the local marker measurement path and/or the ellipse-derived ROI basis, rather than the final radial conversion itself.

At high tilt, marker measurability also drops and the detected bright-component area often changes materially. However, area change does not explain everything: at 16.3 degrees marker 8 shifted +6.57 %R while its detected area changed by only about +2.5%.

## Global pose versus local marker measurement

Two high-tilt frames still had respectable minute-track ellipse fits:

- 16.3 degrees: fit median about 1.38 px, but only 2 markers remained measurable and one moved by +6.57 %R.
- 25.5 degrees: fit median about 1.15 px, but only 5 markers remained measurable and several moved by about 3 %R.

So a plausible-looking global minute-track pose does not guarantee stable marker centroids.

## Projective homography check on the same watch

The selected-frame test repeated the earlier corpus A/B test using this single physical watch.

Across 17 matched high-tilt marker observations:

- baseline ellipse method median absolute radial error: 1.27 %R
- final-homography coordinate method median absolute radial error: 3.01 %R
- baseline median absolute angular error: 0.72 degrees
- final-homography coordinate method median absolute angular error: 1.40 degrees

Simply mapping the already-detected marker centroid through the final projective homography makes this controlled same-watch sample worse. This independently agrees with the larger corpus experiment.

## What this suggests next

The strongest next experiment is to rectify each marker's image patch into canonical/frontal space first, then detect/segment the marker inside that rectified patch.

That is materially different from the failed experiment. The failed experiment detected the centroid in the skewed image first and only then transformed that already-biased point.

A local rectified-patch experiment should remain Python-only and calibration-only until it proves improved within-watch repeatability. Production Android code and master geometry should remain unchanged.

## Limitations

This video was handheld and the watch was on-wrist, so illumination, focus, distance and small wrist movement also change. Those are useful real-world stresses but make this a real-world stress test rather than a laboratory calibration.

A follow-up video with the watch fixed flat, diffuse lighting, locked focus/exposure and camera-only movement would be an even cleaner calibration source.
