# Alpha91 2D homography research — 2026-10-05

Research only. Alpha90 production code remains unchanged.

## Established premise

A single 3x3 planar homography is capable of mapping the canonical GMT dial to a difficult genuine QC photograph with sub-pixel to ~1 px non-triangle holdout error when the homography itself is correct. Therefore the remaining problem is not the projection model; it is recovering the correct homography automatically without allowing the QC targets (applied markers, text, date, etc.) to pull the fit.

## Research findings

### 1. Same-plane concentric circles are useful, but not sufficient alone

Classical projective-geometry work shows that two projected coplanar circles can recover metric rectification information from a single image. Work specifically on projected concentric circles shows that their shared-center/projective constraints simplify recovery of the plane's affine/Euclidean structure and compensate ellipse-center eccentricity.

However, two concentric circles alone have a rotational ambiguity around their common center. In Watch Align that is acceptable because the minute-track system supplies radial structure and a coarse external/structural cue only has to identify the correct 6-degree phase basin; it does not have to determine the final precise rotation.

Practical implication: use concentric annular geometry as a strong coarse rectification/conditioning constraint, not as the complete final pose solver.

### 2. The minute track is stronger than a single ellipse

The frozen GMT master already contains two useful same-plane radii:

- minute-track inner end: ~0.925 R
- minute-track outer annulus: ~0.972 R

Each minute mark is a radial segment joining these two radii. This provides many observations on two concentric rings plus radial orientation, rather than a single ring of points.

A robust coarse homography should therefore use the complete minute-track system where possible:

- inner and outer endpoints / sampled radial segments
- all available minute positions
- hour-position outer stubs only where applied markers occlude the inner end
- no applied-marker geometry

The cyclic correspondence ambiguity is only 6 degrees. A coarse cue such as bezel/case orientation or date side can choose the correct sector without contributing a precision measurement. Fine phase is then determined entirely by the dial-plane minute-track evidence.

### 3. Robust point fitting: use a modern homography estimator

OpenCV's USAC framework supports robust homography estimation with local optimisation, symmetric reprojection error and MAGSAC/PROSAC variants. This is preferable to letting a small number of opposing pairs dominate the solution.

Recommended coarse solve:

1. detect candidate minute-track observations and assign confidence;
2. establish cyclic indexing / correct 6-degree phase basin;
3. estimate H from all reliable dial-plane correspondences using USAC/MAGSAC or equivalent robust optimisation;
4. reject if inlier coverage is not distributed around the dial or if holdout annular evidence does not improve.

### 4. Direct refinement is likely the important final step

ECC (Enhanced Correlation Coefficient) registration directly optimises an image warp and supports a 3x3 homography. It is designed to tolerate photometric changes and OpenCV provides mask-aware and multiscale forms. It requires a reasonably good initial warp, making it suitable after the coarse geometric solve rather than as the initial detector.

For Watch Align, however, raw intensity ECC is not the ideal primary objective because the canonical master is geometric while candidate QC photographs contain lighting, reflections, hands, text and other appearance differences.

A better primary fine objective is edge/distance-transform (chamfer-style) registration:

- build an edge/distance field from the candidate in a tightly controlled dial-plane annulus;
- project the canonical minute-track line geometry through H;
- minimise robust distance from those projected line samples to candidate edge evidence;
- use bilinear interpolation of the distance field for sub-pixel optimisation;
- truncate/robustify the loss so hands/reflections/outliers cannot dominate;
- keep all QC targets masked from the objective.

Classical chamfer registration is specifically intended for matching a drawing/outline to image features and supports sub-pixel refinement through interpolation of the distance field, which matches this project better than raw pixel correlation.

ECC can still be retained as a comparator or secondary refinement on a heavily masked/preprocessed annulus.

### 5. Relevant recent gauge work supports homography, not polar unwrapping

Recent industrial analog-gauge research treats perspective correction as a planar homography problem and explicitly reports that polar unwrapping of an ellipse causes nonlinear angular errors. A 2026 gauge system uses structural keypoints / virtual correspondences to build a homography and restores the dial to a frontal metric space before measurement. That is consistent with the Watch Align oracle result.

## Recommended Alpha91 architecture

### Stage A — coarse localisation

- locate dial/minute annulus approximately;
- fit broad conic/ellipse only as a seed, not as the final mapping;
- estimate coarse clock phase only well enough to select the correct 6-degree minute-track indexing basin.

### Stage B — robust minute-track homography

- use both inner and outer minute-track radii / radial mark segments;
- assign canonical minute identities after phase-basin selection;
- fit a full 8-DoF homography using all reliable correspondences with robust estimation;
- require spatial coverage around the dial.

### Stage C — masked sub-pixel refinement

- candidate edge map + precise L2 distance transform;
- canonical minute-track geometry projected through current H;
- robust nonlinear optimisation of all 8 homography parameters against distance-transform samples;
- fit region restricted to trusted dial-plane structures;
- applied markers, hands, date/cyclops, dial text/coronet and rehaut/bezel excluded from the refinement objective.

### Stage D — independent validation

Before using the homography for QC:

- evaluate withheld minute-track samples / sectors;
- verify the transformed dial annulus remains physically coherent;
- reject photographs with insufficient coverage or excessive residual rather than forcing a pose.

Only after acceptance should the frozen genuine master marker outlines be projected for human/QC inspection.

## What not to pursue now

Do not add:

- bezel-to-dial depth
- 3D camera reconstruction
- sapphire refraction
- lens-distortion modelling
- marker-driven local fitting

unless a correctly recovered dial-plane homography leaves a repeatable residual pattern across genuine controls that specifically requires one of them.

## Smallest next experiment

On `POOL_GEN_HO_02`:

1. start from its existing approximate dial ellipse;
2. manually or semi-automatically extract only the minute-track annulus;
3. recover H from minute-track geometry alone;
4. apply a distance-transform refinement;
5. compare the unchanged 11 marker holdouts with the oracle homography result (~0.39 px mean non-triangle error).

Decision:

- if the automatic dial-plane-only solver approaches ~1 px holdout error, proceed to 3–5 genuine controls;
- if the coarse solve is good but fine error remains, test ECC vs edge-distance refinement;
- if it cannot recover the oracle geometry from the minute track on this one image, research the observation/indexing stage rather than adding more physical camera complexity.
