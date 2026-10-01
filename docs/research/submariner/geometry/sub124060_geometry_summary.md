# Genuine 124060 geometry calibration (development only)

Development genuine 124060 only. Descriptive repeatability; no thresholds, no replica comparison.

- Scope: {'family': 'submariner_12', 'model': '124060', 'class_tag': 'gen', 'partition': 'development'}
- Photos in scope: 36; usable: 19 from 9 physical watches
- Usable photos by watch: {'bobs_124060_151200': 2, 'bobs_124060_174149': 3, 'bobs_124060_182386pl': 1, 'bobs_124060_182455': 3, 'bobs_124060_182482': 3, 'bobs_124060_187439': 2, 'bobs_124060_187502pl': 1, 'bobs_124060_190837': 2, 'bobs_124060_191110': 2}
- Excluded photos: {'no_dial': 10, 'review:watch upside down (12 at the bottom) and small in frame; detections fall on the bezel': 3, 'dial_not_edge_fitted_or_fallback': 4}
- Landmark exclusions from visual review: {'06166e7935': ['r11'], '211999e931': ['r8'], '31edfb66b6': ['r4'], '32736f25fb': ['r1'], '4bf250413f': ['r2'], '50418ff952': ['r2'], '5aefb6a02a': ['r2', 'r8'], '6a4ce9c64f': ['12', 'r5'], '8c94e8fafc': ['r8'], '98dd3ab2d8': ['b3', 'r7'], 'a205de17a5': ['12', 'r2', 'r4'], 'ab5aeaf2e1': ['r2', 'r4'], 'b316d6b31f': ['r1', 'r2'], 'c14f8b3abb': ['r1', 'r2'], 'c84ee1426d': ['12', 'r5'], 'cbf87c6069': ['r1'], 'cec3df9313': ['r2', 'r8'], 'ece0886178': ['r4'], 'fa86078e31': ['r7']}
- Usable photos not covered by the visual review: none

Ratio = between-watch MAD of watch medians / median within-watch MAD. Descriptive only; the class labels are not pass/fail and no threshold is derived.

## between-watch variation clearly exceeds measurement noise (5)

| metric | watches | 2+ photo watches | photos | perturbation range med | within MAD | between MAD | ratio | pose-sensitive |
|---|---|---|---|---|---|---|---|---|
| r1_inset | 7 | 4 | 12 | 0.0295 | 0.0045 | 0.0247 | 5.51 | image_scale(88%);inplane_rotation(+-5deg) |
| r4_fitted_edge_radius_r | 8 | 4 | 14 | 0.0052 | 0.0011 | 0.0038 | 3.61 |  |
| r10_fitted_edge_radius_r | 9 | 4 | 16 | 0.0008 | 0.0029 | 0.0066 | 2.29 | dial_r_px |
| r10_dtheta_deg | 9 | 3 | 14 | 0.2362 | 0.1165 | 0.2452 | 2.10 | inplane_rotation(+-5deg) |
| track_spread_r | 9 | 7 | 19 | 0.0044 | 0.0221 | 0.0454 | 2.05 | dial_axis_ratio |

## similar scale (23)

| metric | watches | 2+ photo watches | photos | perturbation range med | within MAD | between MAD | ratio | pose-sensitive |
|---|---|---|---|---|---|---|---|---|
| ring_round_circle_centre_offset_r | 9 | 4 | 16 | 0.0027 | 0.0008 | 0.0015 | 1.80 | dial_axis_ratio;mpose_tilt_deg;inplane_rotation(+-5deg) |
| t12_surround_rotation_deg | 9 | 4 | 15 | 0.2995 | 0.5648 | 0.9167 | 1.62 | inplane_rotation_deg |
| r1_dtheta_deg | 6 | 3 | 10 | 0.1356 | 0.0358 | 0.0567 | 1.58 | image_scale(88%);inplane_rotation(+-5deg) |
| dial_r_px | 9 | 7 | 19 | 0.3131 | 28.4253 | 44.4701 | 1.56 | mpose_tilt_deg;inplane_rotation_deg |
| r1_fitted_edge_radius_r | 7 | 4 | 12 | 0.0004 | 0.0041 | 0.0059 | 1.46 |  |
| r11_inset | 9 | 5 | 16 | 0.0147 | 0.0038 | 0.0053 | 1.42 | inplane_rotation(+-5deg) |
| r8_dtheta_deg | 8 | 2 | 11 | 0.3928 | 0.2480 | 0.3508 | 1.41 |  |
| rehaut_rh_w3_r | 9 | 7 | 19 | 0.0070 | 0.0066 | 0.0093 | 1.41 |  |
| t12_surround_gap_to_track_r | 9 | 4 | 15 | 0.0043 | 0.0024 | 0.0032 | 1.34 | image_scale(88%);inplane_rotation(+-5deg) |
| b6_rho | 8 | 6 | 15 | 0.0037 | 0.0045 | 0.0058 | 1.31 | mpose_tilt_deg;inplane_rotation_deg;dial_r_px |
| r7_rho | 9 | 3 | 14 | 0.0047 | 0.0028 | 0.0036 | 1.29 |  |
| r8_fitted_edge_radius_r | 8 | 3 | 13 | 0.0011 | 0.0021 | 0.0027 | 1.26 |  |
| ring_round_rho_mad | 9 | 4 | 16 | 0.0017 | 0.0009 | 0.0011 | 1.26 | mpose_tilt_deg |
| r5_gap_to_track_r | 9 | 3 | 14 | 0.0022 | 0.0011 | 0.0013 | 1.19 | inplane_rotation(+-5deg) |
| r1_gap_to_track_r | 7 | 4 | 12 | 0.0061 | 0.0044 | 0.0052 | 1.18 |  |
| r4_inset | 8 | 4 | 14 | 0.0333 | 0.0068 | 0.0080 | 1.17 | mpose_tilt_deg;inplane_rotation(+-5deg) |
| r5_fitted_edge_radius_r | 9 | 3 | 14 | 0.0006 | 0.0035 | 0.0041 | 1.16 |  |
| r10_rho | 9 | 4 | 16 | 0.0016 | 0.0021 | 0.0024 | 1.15 | dial_axis_ratio |
| opp_r4_r10_rho_diff | 8 | 4 | 14 | 0.0061 | 0.0033 | 0.0038 | 1.15 | mpose_tilt_deg |
| r1_rho | 7 | 4 | 12 | 0.0019 | 0.0018 | 0.0020 | 1.14 | inplane_rotation(+-5deg) |
| r11_gap_to_track_r | 9 | 5 | 16 | 0.0020 | 0.0054 | 0.0059 | 1.10 | mpose_tilt_deg;dial_r_px |
| dial_fit_rms_over_r | 9 | 7 | 19 | 0.0010 | 0.0012 | 0.0013 | 1.07 | inplane_rotation_deg;dial_r_px |
| ring_round_mean_angle_offset_deg | 9 | 3 | 14 | 0.1765 | 0.1122 | 0.1126 | 1.00 | inplane_rotation(+-5deg) |

## measurement noise dominates (70)

| metric | watches | 2+ photo watches | photos | perturbation range med | within MAD | between MAD | ratio | pose-sensitive |
|---|---|---|---|---|---|---|---|---|
| track_minus_round_ring_r | 9 | 4 | 16 | 0.0039 | 0.0028 | 0.0028 | 0.98 | mpose_tilt_deg |
| rehaut_rh_w6_r | 9 | 7 | 19 | 0.0322 | 0.0108 | 0.0104 | 0.96 | dial_axis_ratio |
| b3_rotation_deg | 9 | 3 | 12 | 0.9356 | 0.3221 | 0.3096 | 0.96 | inplane_rotation(+-5deg) |
| ring_round_rho_median | 9 | 4 | 16 | 0.0018 | 0.0018 | 0.0017 | 0.94 | mpose_tilt_deg;dial_r_px |
| r5_inset | 9 | 3 | 14 | 0.0098 | 0.0215 | 0.0201 | 0.93 |  |
| ring_round_spacing_rms_deg | 9 | 3 | 14 | 0.1324 | 0.1234 | 0.1143 | 0.93 |  |
| r11_fitted_edge_radius_r | 9 | 5 | 16 | 0.0005 | 0.0061 | 0.0056 | 0.93 | dial_r_px |
| rehaut_rh_w12_r | 9 | 7 | 19 | 0.0123 | 0.0081 | 0.0070 | 0.86 | mpose_tilt_deg;dial_r_px;inplane_rotation(+-5deg) |
| opp_r5_r11_rho_diff | 9 | 3 | 13 | 0.0045 | 0.0062 | 0.0050 | 0.81 |  |
| inplane_rotation_deg | 9 | 7 | 19 | 0.1334 | 1.6131 | 1.2917 | 0.80 | dial_r_px |
| r7_inset | 9 | 3 | 14 | 0.0179 | 0.0171 | 0.0131 | 0.77 |  |
| rehaut_min_over_mean | 9 | 4 | 16 | 0.3088 | 0.1338 | 0.1014 | 0.76 | mpose_tilt_deg;dial_r_px;inplane_rotation(+-5deg) |
| r11_rho | 9 | 5 | 16 | 0.0021 | 0.0018 | 0.0014 | 0.76 | dial_axis_ratio;inplane_rotation_deg |
| b9_rotation_deg | 9 | 4 | 14 | 0.8794 | 0.4355 | 0.3238 | 0.74 | inplane_rotation(+-5deg) |
| r11_dtheta_deg | 9 | 3 | 13 | 0.1456 | 0.0960 | 0.0685 | 0.71 |  |
| ring_round_circle_radius_r | 9 | 4 | 16 | 0.0018 | 0.0027 | 0.0019 | 0.71 | mpose_tilt_deg;dial_r_px |
| b6_inset | 8 | 6 | 15 | 0.0164 | 0.0545 | 0.0370 | 0.68 |  |
| baton_minus_round_rho | 9 | 4 | 16 | 0.0060 | 0.0051 | 0.0034 | 0.66 |  |
| mirror_r1_r11_rho_diff | 7 | 3 | 11 | 0.0023 | 0.0027 | 0.0018 | 0.65 |  |
| dial_axis_ratio | 9 | 7 | 19 | 0.0029 | 0.0024 | 0.0016 | 0.65 |  |
| b9_width_r | 9 | 4 | 14 | 0.0046 | 0.0049 | 0.0031 | 0.64 |  |
| r5_dtheta_deg | 9 | 3 | 14 | 0.2104 | 0.2863 | 0.1804 | 0.63 |  |
| r7_dtheta_deg | 9 | 2 | 12 | 0.3131 | 0.2315 | 0.1432 | 0.62 |  |
| r4_gap_to_track_r | 8 | 4 | 14 | 0.0089 | 0.0061 | 0.0038 | 0.62 |  |
| r7_fitted_edge_radius_r | 9 | 3 | 14 | 0.0011 | 0.0018 | 0.0011 | 0.61 |  |
| t12_surround_lateral_offset_from_60tick_w | 9 | 4 | 15 | 0.0064 | 0.0224 | 0.0124 | 0.56 | inplane_rotation_deg |
| r10_gap_to_track_r | 9 | 4 | 16 | 0.0026 | 0.0084 | 0.0046 | 0.55 | mpose_tilt_deg |
| b6_length_r | 8 | 6 | 15 | 0.0016 | 0.0089 | 0.0046 | 0.52 | mpose_tilt_deg;inplane_rotation_deg;dial_r_px |
| t12_surround_height_r | 9 | 4 | 15 | 0.0051 | 0.0095 | 0.0048 | 0.51 |  |
| t12_surround_rho | 9 | 4 | 15 | 0.0033 | 0.0069 | 0.0035 | 0.51 | inplane_rotation_deg;dial_r_px |
| r5_rho | 9 | 3 | 14 | 0.0028 | 0.0069 | 0.0035 | 0.51 |  |
| track_pitch_deg | 9 | 7 | 19 | 0.0500 | 0.1000 | 0.0500 | 0.50 | dial_axis_ratio |
| b6_rotation_deg | 8 | 6 | 15 | 0.6915 | 3.1224 | 1.4642 | 0.47 |  |
| track_minus_baton_rho_r | 9 | 7 | 19 | 0.0043 | 0.0118 | 0.0055 | 0.46 |  |
| opp_r5_r11_angle_dev_deg | 9 | 3 | 13 | 0.2526 | 0.3823 | 0.1675 | 0.44 |  |
| r4_rho | 8 | 4 | 14 | 0.0050 | 0.0037 | 0.0016 | 0.42 | dial_axis_ratio;mpose_tilt_deg |
| r7_gap_to_track_r | 9 | 3 | 14 | 0.0038 | 0.0061 | 0.0024 | 0.40 |  |
| rehaut_rh_w9_r | 9 | 7 | 19 | 0.0061 | 0.0076 | 0.0030 | 0.39 | mpose_tilt_deg |
| opp_r1_r7_angle_dev_deg | 7 | 3 | 10 | 0.3202 | 0.6381 | 0.2512 | 0.39 |  |
| b9_length_r | 9 | 4 | 14 | 0.0013 | 0.0266 | 0.0103 | 0.39 |  |
| t12_surround_width_r | 9 | 4 | 15 | 0.0039 | 0.0190 | 0.0071 | 0.37 | inplane_rotation_deg |
| b6_gap_to_track_r | 8 | 6 | 15 | 0.0016 | 0.0039 | 0.0015 | 0.37 |  |
| opp_r4_r10_angle_dev_deg | 8 | 4 | 14 | 0.4669 | 0.9985 | 0.3551 | 0.36 | dial_axis_ratio |
| b3_dtheta_deg | 9 | 2 | 11 | 0.2247 | 0.3894 | 0.1324 | 0.34 |  |
| t12_surround_dtheta_from_60tick_deg | 9 | 4 | 15 | 0.1005 | 0.4087 | 0.1366 | 0.33 | inplane_rotation_deg |
| track_radius_r | 9 | 7 | 19 | 0.0022 | 0.0033 | 0.0011 | 0.33 | inplane_rotation_deg |
| r8_gap_to_track_r | 8 | 3 | 13 | 0.0055 | 0.0060 | 0.0020 | 0.33 |  |
| b3_inset | 9 | 3 | 12 | 0.0103 | 0.0182 | 0.0060 | 0.33 |  |
| b6_width_r | 8 | 6 | 15 | 0.0010 | 0.0063 | 0.0019 | 0.31 | inplane_rotation_deg;dial_r_px |
| b3_width_r | 9 | 3 | 12 | 0.0032 | 0.0062 | 0.0019 | 0.31 |  |
| r10_inset | 9 | 4 | 16 | 0.0148 | 0.0137 | 0.0042 | 0.31 | dial_axis_ratio |
| mpose_residual_r | 9 | 4 | 16 | 0.0013 | 0.0015 | 0.0004 | 0.30 |  |
| b3_length_r | 9 | 3 | 12 | 0.0010 | 0.0087 | 0.0026 | 0.30 |  |
| b3_rho | 9 | 3 | 12 | 0.0014 | 0.0116 | 0.0027 | 0.24 |  |
| mirror_r5_r7_rho_diff | 9 | 2 | 12 | 0.0029 | 0.0046 | 0.0009 | 0.21 |  |
| r4_dtheta_deg | 8 | 3 | 12 | 0.5504 | 0.9102 | 0.1862 | 0.20 |  |
| mpose_tilt_deg | 9 | 4 | 16 | 1.4220 | 2.2646 | 0.4574 | 0.20 |  |
| b3_gap_to_track_r | 9 | 3 | 12 | 0.0018 | 0.0083 | 0.0016 | 0.20 |  |
| b9_inset | 9 | 4 | 14 | 0.0074 | 0.0361 | 0.0063 | 0.17 |  |
| opp_r1_r7_rho_diff | 7 | 3 | 10 | 0.0056 | 0.0057 | 0.0009 | 0.16 |  |
| r8_inset | 8 | 3 | 13 | 0.0313 | 0.0340 | 0.0049 | 0.14 |  |
| b9_rho | 9 | 4 | 14 | 0.0011 | 0.0235 | 0.0033 | 0.14 |  |
| r8_rho | 8 | 3 | 13 | 0.0021 | 0.0066 | 0.0009 | 0.14 |  |
| b6_dtheta_deg | 8 | 4 | 13 | 0.1168 | 1.8861 | 0.2514 | 0.13 |  |
| mirror_r4_r8_rho_diff | 7 | 3 | 11 | 0.0052 | 0.0181 | 0.0023 | 0.13 |  |
| t12_surround_apex_deg | 9 | 4 | 15 | 0.2470 | 2.1499 | 0.2611 | 0.12 | inplane_rotation_deg;dial_r_px |
| b9_dtheta_deg | 9 | 4 | 14 | 0.2675 | 2.9556 | 0.3354 | 0.11 |  |
| t12_b6_line_centre_offset_r | 8 | 4 | 13 | 0.0007 | 0.0138 | 0.0013 | 0.09 |  |
| t12_b6_angle_dev_deg | 8 | 4 | 13 | 0.1076 | 2.2732 | 0.2035 | 0.09 |  |
| b9_gap_to_track_r | 9 | 4 | 14 | 0.0018 | 0.0180 | 0.0016 | 0.09 |  |

## insufficient data (36)

| metric | watches | 2+ photo watches | photos | perturbation range med | within MAD | between MAD | ratio | pose-sensitive |
|---|---|---|---|---|---|---|---|---|
| b3_b9_line_centre_offset_r | 9 | 1 | 10 | 0.0022 | 0.0000 | 0.0040 | 82.09 | image_scale(88%);inplane_rotation(+-5deg) |
| b3_b9_angle_dev_deg | 9 | 1 | 10 | 0.5314 | 0.1421 | 0.5988 | 4.21 | image_scale(88%);inplane_rotation(+-5deg) |
| r2_rho | 4 | 3 | 9 | 0.0010 | 0.0020 | 0.0018 | 0.90 |  |
| r2_fitted_edge_radius_r | 4 | 3 | 9 | 0.0005 | 0.0006 | 0.0003 | 0.51 |  |
| r2_inset | 4 | 3 | 9 | 0.0312 | 0.0108 | 0.0031 | 0.29 | inplane_rotation(+-5deg) |
| mirror_r2_r10_rho_diff | 4 | 3 | 9 | 0.0035 | 0.0058 | 0.0016 | 0.28 |  |
| opp_r2_r8_rho_diff | 3 | 3 | 8 | 0.0021 | 0.0114 | 0.0022 | 0.20 |  |
| b3_b9_rho_diff | 9 | 1 | 10 | 0.0014 | 0.0122 | 0.0024 | 0.19 |  |
| r2_gap_to_track_r | 4 | 3 | 9 | 0.0060 | 0.0077 | 0.0012 | 0.16 |  |
| r2_dtheta_deg | 4 | 2 | 7 | 0.2466 | 0.1684 | 0.0234 | 0.14 |  |
| opp_r2_r8_angle_dev_deg | 3 | 3 | 8 | 0.1507 | 0.3354 | 0.0310 | 0.09 |  |
| line12_6_vs_line3_9_orthogonality_deg | 8 | 0 | 8 | 0.2010 | – | 0.0555 | – |  |
| r10_lume_radius_r | 7 | 0 | 7 | 0.0003 | – | 0.0010 | – |  |
| r10_surround_radius_r | 7 | 0 | 7 | 0.0004 | – | 0.0018 | – |  |
| r11_lume_radius_r | 7 | 0 | 7 | 0.0004 | – | 0.0005 | – |  |
| r11_surround_radius_r | 7 | 0 | 7 | 0.0005 | – | 0.0008 | – |  |
| r1_lume_radius_r | 3 | 0 | 3 | 0.0043 | – | 0.0006 | – |  |
| r1_surround_radius_r | 3 | 0 | 3 | 0.0012 | – | 0.0001 | – |  |
| r2_lume_radius_r | 1 | 0 | 1 | 0.0044 | – | 0.0000 | – |  |
| r2_surround_radius_r | 1 | 0 | 1 | 0.0040 | – | 0.0000 | – |  |
| r4_lume_radius_r | 4 | 0 | 4 | 0.0068 | – | 0.0009 | – |  |
| r4_surround_radius_r | 4 | 0 | 4 | 0.0059 | – | 0.0003 | – |  |
| r5_lume_radius_r | 4 | 0 | 4 | 0.0007 | – | 0.0006 | – |  |
| r5_surround_radius_r | 2 | 0 | 2 | 0.0015 | – | 0.0013 | – |  |
| r7_lume_radius_r | 5 | 0 | 5 | 0.0014 | – | 0.0028 | – |  |
| r7_surround_radius_r | 5 | 0 | 5 | 0.0009 | – | 0.0018 | – |  |
| r8_lume_radius_r | 3 | 0 | 3 | 0.0074 | – | 0.0007 | – |  |
| r8_surround_radius_r | 3 | 0 | 3 | 0.0058 | – | 0.0005 | – |  |
| t12_lume_apex_deg | 1 | 0 | 1 | 4.5265 | – | 0.0000 | – |  |
| t12_lume_dtheta_from_60tick_deg | 1 | 0 | 1 | 0.1597 | – | 0.0000 | – |  |
| t12_lume_gap_to_track_r | 1 | 0 | 1 | 0.0167 | – | 0.0000 | – |  |
| t12_lume_height_r | 1 | 0 | 1 | 0.0470 | – | 0.0000 | – |  |
| t12_lume_lateral_offset_from_60tick_w | 1 | 0 | 1 | 0.0437 | – | 0.0000 | – |  |
| t12_lume_rho | 1 | 0 | 1 | 0.0014 | – | 0.0000 | – |  |
| t12_lume_rotation_deg | 1 | 0 | 1 | 4.1321 | – | 0.0000 | – |  |
| t12_lume_width_r | 1 | 0 | 1 | 0.0160 | – | 0.0000 | – |  |

## descriptive only (image frame) (5)

| metric | watches | 2+ photo watches | photos | perturbation range med | within MAD | between MAD | ratio | pose-sensitive |
|---|---|---|---|---|---|---|---|---|
| dial_cx_px | 9 | 7 | 19 | 0.4404 | 46.5668 | 129.8809 | 2.79 |  |
| dial_semi_major_px | 9 | 7 | 19 | 0.5818 | 27.9553 | 43.8832 | 1.57 |  |
| dial_semi_minor_px | 9 | 7 | 19 | 0.4525 | 28.8954 | 45.0421 | 1.56 |  |
| dial_cy_px | 9 | 7 | 19 | 0.3150 | 16.6095 | 19.5115 | 1.17 |  |
| tick60_image_clock_angle_deg | 9 | 5 | 16 | 0.0992 | 0.7531 | 0.6774 | 0.90 |  |

