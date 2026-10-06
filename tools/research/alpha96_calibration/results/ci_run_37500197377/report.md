# Alpha96 genuine calibration run (research only)

Measurement code: the production `AutomaticDialOverlay.build` and `Alpha94MarkerMeasurement.analyse`, run through the
desktop harness (`tools/desktop-harness/run.sh Alpha96Calib`). No measurement maths is duplicated. No thresholds are derived.

## Inventory of this run

| Group | photos listed | watches | photos present | accepted by Alpha96 pose |
|---|---:|---:|---:|---:|
| genuine_population | 213 | 120 | 156 | 144 |
| gen_candidate | 4 | 3 | 4 | 4 |
| rl_control | 4 | 4 | 4 | 4 |

## sha256-verified restore of catalogued photos

Only files whose sha256 equals the catalogued value are measured; changed or unreachable listings are not replaced.

| host | status | photos | watches |
|---|---|---:|---:|
| cdn.swisswatchexpo.com | hash_mismatch | 9 | 5 |
| cdn.swisswatchexpo.com | verified | 47 | 18 |
| content.thewosgroup.com | verified | 5 | 4 |
| dist.phillips.com | verified | 58 | 10 |
| www.bobswatches.com | hash_mismatch | 48 | 47 |
| www.bobswatches.com | verified | 42 | 40 |
| www.bobswatches.com | verified_cached | 3 | 3 |
| www.watchesofswitzerland.com | verified | 1 | 1 |

## Genuine population (provenance-strong: established dealer, auction house, Rolex CPO)

| Metric | unit | watches | photos usable | photos withheld | median | MAD | min | max | P10 | P90 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ring_shift | px | 67 | 137 | 7 | 0.696 | 0.224 | 0.028 | 1.802 | 0.223 | 1.280 |
| ring_shift_R | R | 67 | 137 | 7 | 0.0024 | 0.0011 | 0.0002 | 0.0060 | 0.0008 | 0.0044 |
| ring_rotation | deg | 67 | 137 | 7 | 0.023 | 0.038 | -0.172 | 0.116 | -0.057 | 0.087 |
| ring_scale | % | 67 | 137 | 7 | -0.042 | 0.050 | -0.214 | 0.242 | -0.154 | 0.077 |
| m6_rotation | deg | 62 | 135 | 9 | 0.021 | 0.188 | -0.617 | 0.708 | -0.360 | 0.587 |
| m6_local | px | 62 | 132 | 12 | 0.530 | 0.158 | 0.159 | 1.109 | 0.269 | 0.884 |
| m6_local_R | R | 62 | 132 | 12 | 0.0016 | 0.0004 | 0.0004 | 0.0034 | 0.0009 | 0.0026 |
| m6_raw | px | 62 | 135 | 9 | 0.800 | 0.318 | 0.059 | 1.957 | 0.341 | 1.383 |
| m6_raw_R | R | 62 | 135 | 9 | 0.0023 | 0.0008 | 0.0001 | 0.0072 | 0.0010 | 0.0049 |
| m6_local_tangential_R | R | 62 | 132 | 12 | 0.0003 | 0.0005 | -0.0023 | 0.0019 | -0.0005 | 0.0008 |
| m6_local_radial_R | R | 62 | 132 | 12 | 0.0014 | 0.0005 | -0.0014 | 0.0034 | 0.0001 | 0.0024 |
| m9_rotation | deg | 66 | 138 | 6 | 0.064 | 0.199 | -0.584 | 1.167 | -0.331 | 0.500 |
| m9_local | px | 65 | 134 | 10 | 0.620 | 0.221 | 0.175 | 1.420 | 0.318 | 1.125 |
| m9_local_R | R | 65 | 134 | 10 | 0.0020 | 0.0005 | 0.0006 | 0.0033 | 0.0011 | 0.0030 |
| m9_raw | px | 66 | 138 | 6 | 1.120 | 0.368 | 0.247 | 3.394 | 0.426 | 1.928 |
| m9_raw_R | R | 66 | 138 | 6 | 0.0037 | 0.0013 | 0.0008 | 0.0094 | 0.0017 | 0.0061 |
| m9_local_tangential_R | R | 65 | 134 | 10 | 0.0001 | 0.0006 | -0.0026 | 0.0032 | -0.0006 | 0.0010 |
| m9_local_radial_R | R | 65 | 134 | 10 | 0.0018 | 0.0005 | -0.0017 | 0.0033 | 0.0008 | 0.0028 |
| m12_centreline_rotation | deg | 66 | 139 | 5 | -0.094 | 0.240 | -1.027 | 1.703 | -0.707 | 0.488 |
| m12_raw | px | 66 | 139 | 5 | 1.089 | 0.317 | 0.174 | 12.890 | 0.582 | 1.779 |
| m12_raw_R | R | 66 | 139 | 5 | 0.0033 | 0.0014 | 0.0004 | 0.0357 | 0.0017 | 0.0064 |
| m12_local_R | R | 65 | 135 | 9 | 0.0039 | 0.0008 | 0.0008 | 0.0085 | 0.0026 | 0.0067 |
| m12_right_R | R | 66 | 139 | 5 | -0.0009 | 0.0010 | -0.0065 | 0.0189 | -0.0046 | 0.0007 |
| m12_down_R | R | 66 | 139 | 5 | 0.0027 | 0.0011 | -0.0058 | 0.0303 | 0.0006 | 0.0053 |
| m12_left_side_err | deg | 66 | 139 | 5 | -0.160 | 0.251 | -1.205 | 3.203 | -0.894 | 0.318 |
| m12_right_side_err | deg | 66 | 139 | 5 | 0.060 | 0.248 | -0.843 | 2.361 | -0.580 | 0.539 |
| m12_base_tilt | deg | 66 | 139 | 5 | -0.182 | 0.276 | -1.159 | 1.577 | -0.690 | 0.344 |
| rounds_max_local | px | 67 | 137 | 7 | 0.551 | 0.096 | 0.074 | 0.904 | 0.334 | 0.786 |
| rounds_max_local_R | R | 67 | 137 | 7 | 0.0016 | 0.0003 | 0.0006 | 0.0042 | 0.0012 | 0.0025 |
| rounds_usable | count | 70 | 144 | 0 | 7.000 | 1.000 | 0.000 | 8.000 | 5.000 | 8.000 |

## Marketplace genuine candidates (seller-asserted; descriptive only, NOT population evidence)

| Metric | unit | watches | photos usable | photos withheld | median | MAD | min | max | P10 | P90 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ring_shift | px | 3 | 4 | 0 | 0.410 | 0.071 | 0.338 | 0.708 | – | – |
| ring_shift_R | R | 3 | 4 | 0 | 0.0029 | 0.0005 | 0.0023 | 0.0035 | – | – |
| ring_rotation | deg | 3 | 4 | 0 | 0.013 | 0.017 | -0.026 | 0.030 | – | – |
| ring_scale | % | 3 | 4 | 0 | 0.173 | 0.042 | -0.017 | 0.215 | – | – |
| m6_rotation | deg | 3 | 4 | 0 | -0.275 | 0.174 | -0.449 | 0.475 | – | – |
| m6_local | px | 3 | 4 | 0 | 0.351 | 0.001 | 0.350 | 0.371 | – | – |
| m6_local_R | R | 3 | 4 | 0 | 0.0024 | 0.0001 | 0.0017 | 0.0025 | – | – |
| m6_raw | px | 3 | 4 | 0 | 0.366 | 0.082 | 0.284 | 0.529 | – | – |
| m6_raw_R | R | 3 | 4 | 0 | 0.0020 | 0.0002 | 0.0018 | 0.0036 | – | – |
| m6_local_tangential_R | R | 3 | 4 | 0 | 0.0006 | 0.0003 | -0.0007 | 0.0009 | – | – |
| m6_local_radial_R | R | 3 | 4 | 0 | 0.0022 | 0.0001 | 0.0016 | 0.0024 | – | – |
| m9_rotation | deg | 3 | 4 | 0 | 0.154 | 0.043 | -0.187 | 0.197 | – | – |
| m9_local | px | 3 | 4 | 0 | 0.364 | 0.082 | 0.281 | 0.589 | – | – |
| m9_local_R | R | 3 | 4 | 0 | 0.0025 | 0.0004 | 0.0020 | 0.0029 | – | – |
| m9_raw | px | 3 | 4 | 0 | 0.295 | 0.012 | 0.283 | 0.319 | – | – |
| m9_raw_R | R | 3 | 4 | 0 | 0.0019 | 0.0002 | 0.0016 | 0.0021 | – | – |
| m9_local_tangential_R | R | 3 | 4 | 0 | -0.0007 | 0.0001 | -0.0019 | -0.0007 | – | – |
| m9_local_radial_R | R | 3 | 4 | 0 | 0.0022 | 0.0001 | 0.0019 | 0.0023 | – | – |
| m12_centreline_rotation | deg | 2 | 2 | 2 | 0.085 | 0.517 | -0.432 | 0.603 | – | – |
| m12_raw | px | 2 | 2 | 2 | 0.238 | 0.180 | 0.058 | 0.418 | – | – |
| m12_raw_R | R | 2 | 2 | 2 | 0.0016 | 0.0013 | 0.0003 | 0.0029 | – | – |
| m12_local_R | R | 2 | 2 | 2 | 0.0048 | 0.0005 | 0.0043 | 0.0052 | – | – |
| m12_right_R | R | 2 | 2 | 2 | 0.0002 | 0.0004 | -0.0002 | 0.0006 | – | – |
| m12_down_R | R | 2 | 2 | 2 | 0.0013 | 0.0015 | -0.0002 | 0.0029 | – | – |
| m12_left_side_err | deg | 2 | 2 | 2 | 0.169 | 0.308 | -0.139 | 0.478 | – | – |
| m12_right_side_err | deg | 2 | 2 | 2 | 0.170 | 0.473 | -0.303 | 0.643 | – | – |
| m12_base_tilt | deg | 2 | 2 | 2 | 0.708 | 0.410 | 0.298 | 1.118 | – | – |
| rounds_max_local | px | 3 | 4 | 0 | 0.280 | 0.007 | 0.273 | 0.498 | – | – |
| rounds_max_local_R | R | 3 | 4 | 0 | 0.0019 | 0.0000 | 0.0019 | 0.0024 | – | – |
| rounds_usable | count | 3 | 4 | 0 | 7.000 | 1.000 | 6.000 | 8.000 | – | – |

## Within-watch repeatability (watches with more than one accepted photo)

- gen_candidate / ext_gen_blro_wex_listing_A / ring_rotation: n=2 median 0.01328 MAD 0.01069 range 0.00259..0.02397 deg
- gen_candidate / ext_gen_blro_wex_listing_A / m6_rotation: n=2 median -0.27474 MAD 0.16857 range -0.44331..-0.10616 deg
- gen_candidate / ext_gen_blro_wex_listing_A / m6_local_R: n=2 median 0.00250 MAD 0.00032 range 0.00218..0.00282 R
- gen_candidate / ext_gen_blro_wex_listing_A / m9_rotation: n=2 median -0.18709 MAD 0.21930 range -0.40639..0.03221 deg
- gen_candidate / ext_gen_blro_wex_listing_A / m9_local_R: n=2 median 0.00246 MAD 0.00007 range 0.00239..0.00252 R
- gen_candidate / ext_gen_blro_wex_listing_A / rounds_max_local_R: n=2 median 0.00190 MAD 0.00000 range 0.00190..0.00190 R
- genuine_population / gen_126710BLRO_phillips_146213 / ring_rotation: n=7 median 0.10030 MAD 0.01038 range 0.05284..0.11068 deg
- genuine_population / gen_126710BLRO_phillips_146213 / m6_rotation: n=7 median 0.16954 MAD 0.06584 range -0.35892..0.31651 deg
- genuine_population / gen_126710BLRO_phillips_146213 / m6_local_R: n=7 median 0.00284 MAD 0.00072 range 0.00213..0.00628 R
- genuine_population / gen_126710BLRO_phillips_146213 / m9_rotation: n=7 median -0.25497 MAD 0.11504 range -0.58209..-0.09677 deg
- genuine_population / gen_126710BLRO_phillips_146213 / m9_local_R: n=7 median 0.00296 MAD 0.00017 range 0.00258..0.00357 R
- genuine_population / gen_126710BLRO_phillips_146213 / m12_centreline_rotation: n=7 median 0.07011 MAD 0.14566 range -0.07555..0.46189 deg
- genuine_population / gen_126710BLRO_phillips_146213 / rounds_max_local_R: n=7 median 0.00171 MAD 0.00027 range 0.00127..0.00215 R
- genuine_population / gen_126710BLRO_phillips_147798 / ring_rotation: n=8 median 0.02901 MAD 0.00525 range 0.02307..0.04015 deg
- genuine_population / gen_126710BLRO_phillips_147798 / m6_rotation: n=8 median -0.13471 MAD 0.08337 range -0.22649..0.01740 deg
- genuine_population / gen_126710BLRO_phillips_147798 / m6_local_R: n=8 median 0.00144 MAD 0.00033 range 0.00110..0.00227 R
- genuine_population / gen_126710BLRO_phillips_147798 / m9_rotation: n=8 median 0.15415 MAD 0.09423 range -0.34029..0.55057 deg
- genuine_population / gen_126710BLRO_phillips_147798 / m9_local_R: n=8 median 0.00135 MAD 0.00025 range 0.00066..0.00219 R
- genuine_population / gen_126710BLRO_phillips_147798 / m12_centreline_rotation: n=8 median 0.42822 MAD 0.05272 range 0.20326..0.60062 deg
- genuine_population / gen_126710BLRO_phillips_147798 / rounds_max_local_R: n=8 median 0.00146 MAD 0.00003 range 0.00142..0.00194 R
- genuine_population / gen_126710BLRO_phillips_210072 / ring_rotation: n=8 median 0.08349 MAD 0.02191 range 0.00723..0.17246 deg
- genuine_population / gen_126710BLRO_phillips_210072 / m6_rotation: n=8 median 0.06714 MAD 0.10822 range -0.08269..0.28008 deg
- genuine_population / gen_126710BLRO_phillips_210072 / m6_local_R: n=8 median 0.00201 MAD 0.00079 range 0.00106..0.00679 R
- genuine_population / gen_126710BLRO_phillips_210072 / m9_rotation: n=8 median -0.46556 MAD 0.14193 range -0.72557..-0.31954 deg
- genuine_population / gen_126710BLRO_phillips_210072 / m9_local_R: n=8 median 0.00095 MAD 0.00043 range 0.00044..0.00517 R
- genuine_population / gen_126710BLRO_phillips_210072 / m12_centreline_rotation: n=8 median -0.66780 MAD 0.10490 range -0.77932..-0.21881 deg
- genuine_population / gen_126710BLRO_phillips_210072 / rounds_max_local_R: n=8 median 0.00214 MAD 0.00120 range 0.00076..0.00437 R
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / ring_rotation: n=6 median 0.03823 MAD 0.00206 range 0.02697..0.04555 deg
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / m6_rotation: n=6 median -0.06463 MAD 0.14125 range -0.23450..0.24879 deg
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / m6_local_R: n=6 median 0.00200 MAD 0.00016 range 0.00181..0.00417 R
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / m9_rotation: n=6 median -0.23041 MAD 0.08779 range -0.40927..0.13689 deg
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / m9_local_R: n=6 median 0.00176 MAD 0.00110 range 0.00065..0.00333 R
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / m12_centreline_rotation: n=6 median -0.30043 MAD 0.05620 range -0.43383..-0.16510 deg
- genuine_population / gen_126710BLRO_phillips_CH080120_75 / rounds_max_local_R: n=6 median 0.00255 MAD 0.00015 range 0.00111..0.00272 R
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / ring_rotation: n=3 median 0.06010 MAD 0.00296 range 0.05714..0.07073 deg
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / m6_rotation: n=3 median 0.59234 MAD 0.17435 range 0.41799..1.02833 deg
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / m6_local_R: n=3 median 0.00274 MAD 0.00087 range 0.00149..0.00361 R
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / m9_rotation: n=3 median 0.13778 MAD 0.09577 range -0.12832..0.23355 deg
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / m9_local_R: n=3 median 0.00201 MAD 0.00007 range 0.00194..0.00289 R
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / m12_centreline_rotation: n=3 median -1.02672 MAD 0.06131 range -1.15705..-0.96541 deg
- genuine_population / gen_126710BLRO_phillips_NY080121_35 / rounds_max_local_R: n=3 median 0.00262 MAD 0.00009 range 0.00252..0.00290 R
- genuine_population / hv_page_04d00923f7 / ring_rotation: n=3 median 0.02262 MAD 0.01169 range 0.01093..0.05417 deg
- genuine_population / hv_page_04d00923f7 / m6_rotation: n=3 median -0.09173 MAD 0.05357 range -0.28717..-0.03816 deg
- genuine_population / hv_page_04d00923f7 / m6_local_R: n=3 median 0.00230 MAD 0.00016 range 0.00159..0.00246 R
- genuine_population / hv_page_04d00923f7 / m9_rotation: n=3 median 0.19147 MAD 0.00098 range 0.13407..0.19245 deg
- genuine_population / hv_page_04d00923f7 / m9_local_R: n=3 median 0.00124 MAD 0.00030 range 0.00074..0.00154 R
- genuine_population / hv_page_04d00923f7 / m12_centreline_rotation: n=3 median 0.24121 MAD 0.02240 range 0.03919..0.26361 deg
- genuine_population / hv_page_04d00923f7 / rounds_max_local_R: n=3 median 0.00192 MAD 0.00006 range 0.00186..0.00268 R
- genuine_population / hv_page_176399af9b / ring_rotation: n=7 median -0.02188 MAD 0.00764 range -0.04356..-0.01228 deg
- genuine_population / hv_page_176399af9b / m6_rotation: n=7 median 0.43059 MAD 0.07306 range -0.00979..0.85100 deg
- genuine_population / hv_page_176399af9b / m6_local_R: n=7 median 0.00143 MAD 0.00039 range 0.00059..0.00218 R
- genuine_population / hv_page_176399af9b / m9_rotation: n=7 median -0.04870 MAD 0.11504 range -0.31462..0.60159 deg
- genuine_population / hv_page_176399af9b / m9_local_R: n=7 median 0.00266 MAD 0.00063 range 0.00136..0.00328 R
- genuine_population / hv_page_176399af9b / m12_centreline_rotation: n=7 median -0.18811 MAD 0.10824 range -0.53270..-0.05412 deg
- genuine_population / hv_page_176399af9b / rounds_max_local_R: n=7 median 0.00131 MAD 0.00012 range 0.00100..0.00261 R
- genuine_population / hv_page_302b41fe4e / ring_rotation: n=2 median -0.07070 MAD 0.00000 range -0.07070..-0.07070 deg
- genuine_population / hv_page_302b41fe4e / m6_rotation: n=2 median -0.38778 MAD 0.00000 range -0.38778..-0.38778 deg
- genuine_population / hv_page_302b41fe4e / m6_local_R: n=2 median 0.00118 MAD 0.00000 range 0.00118..0.00118 R
- genuine_population / hv_page_302b41fe4e / m9_rotation: n=2 median 0.01979 MAD 0.00000 range 0.01979..0.01979 deg
- genuine_population / hv_page_302b41fe4e / m9_local_R: n=2 median 0.00108 MAD 0.00000 range 0.00108..0.00108 R
- genuine_population / hv_page_302b41fe4e / m12_centreline_rotation: n=2 median 0.15949 MAD 0.00000 range 0.15949..0.15949 deg
- genuine_population / hv_page_302b41fe4e / rounds_max_local_R: n=2 median 0.00148 MAD 0.00000 range 0.00148..0.00148 R
- genuine_population / hv_page_382ce3b2b1 / ring_rotation: n=2 median 0.03710 MAD 0.00275 range 0.03435..0.03985 deg
- genuine_population / hv_page_382ce3b2b1 / m6_rotation: n=2 median -0.19304 MAD 0.03441 range -0.22744..-0.15863 deg
- genuine_population / hv_page_382ce3b2b1 / m6_local_R: n=2 median 0.00206 MAD 0.00061 range 0.00145..0.00267 R
- genuine_population / hv_page_382ce3b2b1 / m9_rotation: n=2 median 0.23087 MAD 0.03382 range 0.19705..0.26469 deg
- genuine_population / hv_page_382ce3b2b1 / m9_local_R: n=2 median 0.00261 MAD 0.00028 range 0.00233..0.00289 R
- genuine_population / hv_page_382ce3b2b1 / m12_centreline_rotation: n=2 median -0.72830 MAD 0.05036 range -0.77866..-0.67794 deg
- genuine_population / hv_page_382ce3b2b1 / rounds_max_local_R: n=2 median 0.00140 MAD 0.00017 range 0.00123..0.00158 R
- genuine_population / hv_page_4d76330046 / ring_rotation: n=2 median -0.00707 MAD 0.01284 range -0.01992..0.00577 deg
- genuine_population / hv_page_4d76330046 / m6_rotation: n=2 median 0.27521 MAD 0.13389 range 0.14133..0.40910 deg
- genuine_population / hv_page_4d76330046 / m6_local_R: n=2 median 0.00168 MAD 0.00061 range 0.00107..0.00230 R
- genuine_population / hv_page_4d76330046 / m9_rotation: n=2 median 0.44013 MAD 0.02582 range 0.41431..0.46596 deg
- genuine_population / hv_page_4d76330046 / m9_local_R: n=2 median 0.00283 MAD 0.00052 range 0.00231..0.00335 R
- genuine_population / hv_page_4d76330046 / m12_centreline_rotation: n=2 median -0.83732 MAD 0.06002 range -0.89735..-0.77730 deg
- genuine_population / hv_page_4d76330046 / rounds_max_local_R: n=2 median 0.00168 MAD 0.00042 range 0.00126..0.00210 R
- genuine_population / hv_page_4f2029765c / ring_rotation: n=2 median 0.06239 MAD 0.01429 range 0.04810..0.07668 deg
- genuine_population / hv_page_4f2029765c / m6_rotation: n=3 median -0.04402 MAD 0.20428 range -0.24830..0.64912 deg
- genuine_population / hv_page_4f2029765c / m6_local_R: n=2 median 0.00129 MAD 0.00051 range 0.00078..0.00180 R
- genuine_population / hv_page_4f2029765c / m9_rotation: n=3 median 0.01843 MAD 0.06680 range -0.04837..0.14111 deg
- genuine_population / hv_page_4f2029765c / m9_local_R: n=2 median 0.00317 MAD 0.00012 range 0.00306..0.00329 R
- genuine_population / hv_page_4f2029765c / m12_centreline_rotation: n=3 median -0.13419 MAD 0.00756 range -0.15808..-0.12663 deg
- genuine_population / hv_page_4f2029765c / rounds_max_local_R: n=2 median 0.00251 MAD 0.00015 range 0.00235..0.00266 R
- genuine_population / hv_page_54612422f7 / ring_rotation: n=2 median 0.05600 MAD 0.00476 range 0.05124..0.06076 deg
- genuine_population / hv_page_54612422f7 / m6_rotation: n=2 median 0.62352 MAD 0.12268 range 0.50084..0.74620 deg
- genuine_population / hv_page_54612422f7 / m6_local_R: n=2 median 0.00247 MAD 0.00011 range 0.00236..0.00258 R
- genuine_population / hv_page_54612422f7 / m9_rotation: n=2 median -0.04020 MAD 0.08787 range -0.12807..0.04767 deg
- genuine_population / hv_page_54612422f7 / m9_local_R: n=2 median 0.00158 MAD 0.00015 range 0.00142..0.00173 R
- genuine_population / hv_page_54612422f7 / m12_centreline_rotation: n=2 median -0.26370 MAD 0.00850 range -0.27220..-0.25520 deg
- genuine_population / hv_page_54612422f7 / rounds_max_local_R: n=2 median 0.00180 MAD 0.00044 range 0.00136..0.00225 R
- genuine_population / hv_page_6d3d310e79 / ring_rotation: n=8 median 0.03526 MAD 0.00584 range 0.02722..0.06505 deg
- genuine_population / hv_page_6d3d310e79 / m6_rotation: n=8 median 0.52251 MAD 0.13699 range 0.33606..0.83939 deg
- genuine_population / hv_page_6d3d310e79 / m6_local_R: n=8 median 0.00293 MAD 0.00016 range 0.00155..0.00316 R
- genuine_population / hv_page_6d3d310e79 / m9_rotation: n=8 median -0.12215 MAD 0.06592 range -0.29137..0.06666 deg
- genuine_population / hv_page_6d3d310e79 / m9_local_R: n=8 median 0.00111 MAD 0.00042 range 0.00044..0.00203 R
- genuine_population / hv_page_6d3d310e79 / m12_centreline_rotation: n=8 median -0.91080 MAD 0.07365 range -1.10074..-0.26200 deg
- genuine_population / hv_page_6d3d310e79 / rounds_max_local_R: n=8 median 0.00152 MAD 0.00006 range 0.00144..0.00467 R
- genuine_population / hv_page_701654bab7 / ring_rotation: n=3 median 0.06883 MAD 0.00594 range 0.03990..0.07477 deg
- genuine_population / hv_page_701654bab7 / m6_rotation: n=3 median 0.46480 MAD 0.00311 range 0.05708..0.46791 deg
- genuine_population / hv_page_701654bab7 / m6_local_R: n=3 median 0.00176 MAD 0.00063 range 0.00102..0.00239 R
- genuine_population / hv_page_701654bab7 / m9_rotation: n=3 median 0.12641 MAD 0.04124 range 0.08517..0.21964 deg
- genuine_population / hv_page_701654bab7 / m9_local_R: n=3 median 0.00223 MAD 0.00050 range 0.00131..0.00273 R
- genuine_population / hv_page_701654bab7 / m12_centreline_rotation: n=3 median -0.11838 MAD 0.03145 range -0.18457..-0.08693 deg
- genuine_population / hv_page_701654bab7 / rounds_max_local_R: n=3 median 0.00168 MAD 0.00001 range 0.00081..0.00169 R
- genuine_population / hv_page_7301586955 / ring_rotation: n=3 median -0.04398 MAD 0.00459 range -0.04857..-0.03571 deg
- genuine_population / hv_page_7301586955 / m6_rotation: n=3 median -0.01055 MAD 0.02026 range -0.03081..0.06491 deg
- genuine_population / hv_page_7301586955 / m6_local_R: n=3 median 0.00340 MAD 0.00007 range 0.00326..0.00347 R
- genuine_population / hv_page_7301586955 / m9_rotation: n=3 median 0.67633 MAD 0.10356 range 0.57277..1.32392 deg
- genuine_population / hv_page_7301586955 / m9_local_R: n=3 median 0.00149 MAD 0.00000 range 0.00062..0.00149 R
- genuine_population / hv_page_7301586955 / m12_centreline_rotation: n=3 median -0.62098 MAD 0.00530 range -0.62628..-0.60481 deg
- genuine_population / hv_page_7301586955 / rounds_max_local_R: n=3 median 0.00199 MAD 0.00039 range 0.00160..0.00317 R
- genuine_population / hv_page_9f89f27cf4 / ring_rotation: n=2 median 0.00373 MAD 0.00000 range 0.00373..0.00373 deg
- genuine_population / hv_page_9f89f27cf4 / m6_rotation: n=2 median -0.41379 MAD 0.00000 range -0.41379..-0.41379 deg
- genuine_population / hv_page_9f89f27cf4 / m6_local_R: n=2 median 0.00147 MAD 0.00000 range 0.00147..0.00147 R
- genuine_population / hv_page_9f89f27cf4 / m9_rotation: n=2 median 1.16650 MAD 0.00000 range 1.16650..1.16650 deg
- genuine_population / hv_page_9f89f27cf4 / m9_local_R: n=2 median 0.00167 MAD 0.00000 range 0.00167..0.00167 R
- genuine_population / hv_page_9f89f27cf4 / m12_centreline_rotation: n=2 median -0.68551 MAD 0.00000 range -0.68551..-0.68551 deg
- genuine_population / hv_page_9f89f27cf4 / rounds_max_local_R: n=2 median 0.00242 MAD 0.00000 range 0.00242..0.00242 R
- genuine_population / hv_page_a13e399a8f / ring_rotation: n=2 median 0.07862 MAD 0.00194 range 0.07668..0.08056 deg
- genuine_population / hv_page_a13e399a8f / m6_rotation: n=2 median 0.02014 MAD 0.08811 range -0.06796..0.10825 deg
- genuine_population / hv_page_a13e399a8f / m6_local_R: n=2 median 0.00097 MAD 0.00015 range 0.00082..0.00112 R
- genuine_population / hv_page_a13e399a8f / m12_centreline_rotation: n=2 median 0.46574 MAD 0.00290 range 0.46285..0.46864 deg
- genuine_population / hv_page_a13e399a8f / rounds_max_local_R: n=2 median 0.00146 MAD 0.00017 range 0.00129..0.00163 R
- genuine_population / hv_page_c5c577583b / ring_rotation: n=3 median 0.02548 MAD 0.00767 range 0.01781..0.06307 deg
- genuine_population / hv_page_c5c577583b / m6_rotation: n=3 median 0.20921 MAD 0.02840 range 0.05484..0.23761 deg
- genuine_population / hv_page_c5c577583b / m6_local_R: n=3 median 0.00199 MAD 0.00009 range 0.00170..0.00207 R
- genuine_population / hv_page_c5c577583b / m9_rotation: n=3 median -0.18885 MAD 0.04136 range -0.23021..0.08796 deg
- genuine_population / hv_page_c5c577583b / m9_local_R: n=3 median 0.00261 MAD 0.00050 range 0.00211..0.00384 R
- genuine_population / hv_page_c5c577583b / m12_centreline_rotation: n=3 median 0.40108 MAD 0.07591 range 0.30008..0.47699 deg
- genuine_population / hv_page_c5c577583b / rounds_max_local_R: n=3 median 0.00187 MAD 0.00002 range 0.00116..0.00188 R
- genuine_population / hv_page_ec154bc25b / ring_rotation: n=3 median -0.05743 MAD 0.00424 range -0.06504..-0.05319 deg
- genuine_population / hv_page_ec154bc25b / m6_rotation: n=3 median -0.61659 MAD 0.12340 range -0.74475..-0.49319 deg
- genuine_population / hv_page_ec154bc25b / m6_local_R: n=3 median 0.00265 MAD 0.00015 range 0.00250..0.00311 R
- genuine_population / hv_page_ec154bc25b / m9_rotation: n=3 median 0.15491 MAD 0.00851 range 0.05781..0.16342 deg
- genuine_population / hv_page_ec154bc25b / m9_local_R: n=3 median 0.00064 MAD 0.00012 range 0.00052..0.00111 R
- genuine_population / hv_page_ec154bc25b / m12_centreline_rotation: n=3 median -0.04701 MAD 0.00526 range -0.11963..-0.04175 deg
- genuine_population / hv_page_ec154bc25b / rounds_max_local_R: n=3 median 0.00214 MAD 0.00003 range 0.00211..0.00236 R
- genuine_population / hv_page_ed122bc9fa / ring_rotation: n=2 median -0.07182 MAD 0.01343 range -0.08525..-0.05840 deg
- genuine_population / hv_page_ed122bc9fa / m6_rotation: n=2 median -0.10806 MAD 0.05440 range -0.16245..-0.05366 deg
- genuine_population / hv_page_ed122bc9fa / m6_local_R: n=2 median 0.00181 MAD 0.00001 range 0.00180..0.00181 R
- genuine_population / hv_page_ed122bc9fa / m9_rotation: n=2 median 0.43652 MAD 0.03639 range 0.40013..0.47291 deg
- genuine_population / hv_page_ed122bc9fa / m9_local_R: n=2 median 0.00206 MAD 0.00034 range 0.00171..0.00240 R
- genuine_population / hv_page_ed122bc9fa / m12_centreline_rotation: n=2 median -0.14378 MAD 0.08150 range -0.22528..-0.06228 deg
- genuine_population / hv_page_ed122bc9fa / rounds_max_local_R: n=2 median 0.00228 MAD 0.00001 range 0.00227..0.00230 R
- genuine_population / swe_42335 / ring_rotation: n=2 median -0.00154 MAD 0.01677 range -0.01831..0.01523 deg
- genuine_population / swe_42335 / m6_rotation: n=3 median -0.42453 MAD 0.03778 range -0.75014..-0.38675 deg
- genuine_population / swe_42335 / m6_local_R: n=2 median 0.00335 MAD 0.00062 range 0.00273..0.00398 R
- genuine_population / swe_42335 / m9_rotation: n=3 median -0.07030 MAD 0.03880 range -0.22549..-0.03150 deg
- genuine_population / swe_42335 / m9_local_R: n=2 median 0.00333 MAD 0.00023 range 0.00310..0.00356 R
- genuine_population / swe_42335 / m12_centreline_rotation: n=3 median -0.73372 MAD 0.00693 range -0.74065..-0.65631 deg
- genuine_population / swe_42335 / rounds_max_local_R: n=2 median 0.00415 MAD 0.00003 range 0.00412..0.00419 R
- genuine_population / swe_59259 / ring_rotation: n=2 median 0.02895 MAD 0.02264 range 0.00631..0.05159 deg
- genuine_population / swe_59259 / m6_rotation: n=3 median 0.33244 MAD 0.00294 range 0.32321..0.33538 deg
- genuine_population / swe_59259 / m6_local_R: n=2 median 0.00191 MAD 0.00071 range 0.00120..0.00262 R
- genuine_population / swe_59259 / m9_rotation: n=3 median 0.28655 MAD 0.08055 range 0.03108..0.36710 deg
- genuine_population / swe_59259 / m9_local_R: n=2 median 0.00075 MAD 0.00020 range 0.00055..0.00096 R
- genuine_population / swe_59259 / m12_centreline_rotation: n=3 median -0.60317 MAD 0.03573 range -2.58445..-0.56744 deg
- genuine_population / swe_59259 / rounds_max_local_R: n=2 median 0.00307 MAD 0.00007 range 0.00300..0.00314 R
- genuine_population / swe_60177 / ring_rotation: n=3 median 0.11573 MAD 0.01997 range 0.08029..0.13570 deg
- genuine_population / swe_60177 / m6_rotation: n=3 median 0.12279 MAD 0.17776 range -0.07045..0.30055 deg
- genuine_population / swe_60177 / m6_local_R: n=3 median 0.00140 MAD 0.00009 range 0.00131..0.00203 R
- genuine_population / swe_60177 / m9_rotation: n=3 median 0.18996 MAD 0.14789 range 0.04207..0.40430 deg
- genuine_population / swe_60177 / m9_local_R: n=3 median 0.00108 MAD 0.00027 range 0.00081..0.00194 R
- genuine_population / swe_60177 / m12_centreline_rotation: n=3 median -0.12391 MAD 0.03006 range -0.15397..-0.06967 deg
- genuine_population / swe_60177 / rounds_max_local_R: n=3 median 0.00155 MAD 0.00060 range 0.00096..0.00342 R
- genuine_population / swe_68351 / ring_rotation: n=3 median -0.01510 MAD 0.00828 range -0.02338..0.01801 deg
- genuine_population / swe_68351 / m6_rotation: n=3 median 0.18252 MAD 0.01097 range 0.17155..0.20528 deg
- genuine_population / swe_68351 / m6_local_R: n=3 median 0.00230 MAD 0.00036 range 0.00122..0.00266 R
- genuine_population / swe_68351 / m9_rotation: n=3 median -0.36371 MAD 0.01064 range -0.39202..-0.35307 deg
- genuine_population / swe_68351 / m9_local_R: n=3 median 0.00214 MAD 0.00040 range 0.00174..0.00353 R
- genuine_population / swe_68351 / m12_centreline_rotation: n=3 median 0.05725 MAD 0.00830 range 0.00764..0.06555 deg
- genuine_population / swe_68351 / rounds_max_local_R: n=3 median 0.00180 MAD 0.00013 range 0.00095..0.00194 R
- genuine_population / swe_68408 / ring_rotation: n=3 median 0.06046 MAD 0.00335 range 0.04549..0.06381 deg
- genuine_population / swe_68408 / m6_rotation: n=3 median -0.11643 MAD 0.03605 range -0.22550..-0.08038 deg
- genuine_population / swe_68408 / m6_local_R: n=3 median 0.00241 MAD 0.00017 range 0.00204..0.00258 R
- genuine_population / swe_68408 / m9_rotation: n=3 median -0.10566 MAD 0.03136 range -0.37549..-0.07430 deg
- genuine_population / swe_68408 / m9_local_R: n=3 median 0.00191 MAD 0.00027 range 0.00115..0.00219 R
- genuine_population / swe_68408 / m12_centreline_rotation: n=3 median -0.22932 MAD 0.01245 range -0.25072..-0.21687 deg
- genuine_population / swe_68408 / rounds_max_local_R: n=3 median 0.00235 MAD 0.00007 range 0.00124..0.00243 R
- genuine_population / swe_68784 / ring_rotation: n=3 median 0.10465 MAD 0.00177 range 0.10288..0.10729 deg
- genuine_population / swe_68784 / m6_rotation: n=3 median -0.19783 MAD 0.13675 range -0.63274..-0.06108 deg
- genuine_population / swe_68784 / m6_local_R: n=3 median 0.00141 MAD 0.00018 range 0.00107..0.00159 R
- genuine_population / swe_68784 / m9_rotation: n=3 median 0.37671 MAD 0.20991 range 0.16680..0.61420 deg
- genuine_population / swe_68784 / m9_local_R: n=3 median 0.00176 MAD 0.00052 range 0.00120..0.00228 R
- genuine_population / swe_68784 / m12_centreline_rotation: n=3 median -0.01426 MAD 0.02876 range -0.04302..0.02326 deg
- genuine_population / swe_68784 / rounds_max_local_R: n=3 median 0.00093 MAD 0.00004 range 0.00089..0.00097 R
- genuine_population / swe_77497 / ring_rotation: n=3 median -0.00598 MAD 0.00834 range -0.02486..0.00236 deg
- genuine_population / swe_77497 / m6_rotation: n=3 median 0.28108 MAD 0.01675 range 0.26433..0.33620 deg
- genuine_population / swe_77497 / m6_local_R: n=3 median 0.00180 MAD 0.00004 range 0.00170..0.00184 R
- genuine_population / swe_77497 / m9_rotation: n=3 median 0.09097 MAD 0.08394 range 0.00703..0.24853 deg
- genuine_population / swe_77497 / m9_local_R: n=3 median 0.00154 MAD 0.00001 range 0.00153..0.00155 R
- genuine_population / swe_77497 / m12_centreline_rotation: n=3 median -0.63349 MAD 0.00563 range -0.63912..-0.60582 deg
- genuine_population / swe_77497 / rounds_max_local_R: n=3 median 0.00153 MAD 0.00006 range 0.00147..0.00215 R

## Replica regression controls (validation only; never used for genuine statistics)

All phone values reproduced: **yes**

| Control | field | phone (Alpha96) | runner | match (display rounding ±0.02) |
|---|---|---:|---:|---|
| RL_THEONE_BLNR | ring_shift_px | 0.53 | 0.52725 | yes |
| RL_THEONE_BLNR | ring_scale_pct | 0.19 | 0.18931 | yes |
| RL_THEONE_BLNR | ring_rotation_deg | 0.06 | 0.05586 | yes |
| RL_THEONE_BLNR | m12_right_px | -0.91 | -0.91407 | yes |
| RL_THEONE_BLNR | m12_down_px | 1.2 | 1.20240 | yes |
| RL_THEONE_BLNR | m12_rotation_deg | 0.96 | 0.96493 | yes |
| RL_THEONE_BLNR | m12_left_side_err_deg | 0.99 | 0.99001 | yes |
| RL_THEONE_BLNR | m12_right_side_err_deg | 0.89 | 0.88554 | yes |
| RL_THEONE_BLNR | m6_right_px | -1.24 | -1.24226 | yes |
| RL_THEONE_BLNR | m6_down_px | 0.83 | 0.83299 | yes |
| RL_THEONE_BLNR | m6_rotation_deg | 0.33 | 0.33127 | yes |
| RL_THEONE_BLNR | m6_local_px | 0.78 | 0.77680 | yes |
| RL_THEONE_BLNR | m9_right_px | -0.55 | -0.55082 | yes |
| RL_THEONE_BLNR | m9_down_px | -0.23 | -0.22942 | yes |
| RL_THEONE_BLNR | m9_rotation_deg | -0.85 | -0.84910 | yes |
| RL_THEONE_BLNR | m9_local_px | 0.28 | 0.27909 | yes |
| RL_THEONE_BLNR | rounds_usable | 8 | 8 | yes |
| RL_THEONE_BLNR | rounds_max_local_px | 0.66 | 0.65672 | yes |
| RL_USER_BATGIRL | ring_shift_px | 0.71 | 0.70509 | yes |
| RL_USER_BATGIRL | ring_scale_pct | 0.06 | 0.06356 | yes |
| RL_USER_BATGIRL | ring_rotation_deg | 0.11 | 0.11362 | yes |
| RL_USER_BATGIRL | m12_usable | false | false | yes |
| RL_USER_BATGIRL | m6_right_px | -0.8 | -0.79700 | yes |
| RL_USER_BATGIRL | m6_down_px | -0.43 | -0.43181 | yes |
| RL_USER_BATGIRL | m6_rotation_deg | 0.78 | 0.77606 | yes |
| RL_USER_BATGIRL | m6_local_px | 0.54 | 0.54423 | yes |
| RL_USER_BATGIRL | m9_right_px | 0.13 | 0.14534 | yes |
| RL_USER_BATGIRL | m9_down_px | -1.92 | -1.92277 | yes |
| RL_USER_BATGIRL | m9_rotation_deg | -1.52 | -1.51981 | yes |
| RL_USER_BATGIRL | m9_local_px | 0.93 | 0.93099 | yes |
| RL_USER_BATGIRL | rounds_usable | 8 | 8 | yes |
| RL_USER_BATGIRL | rounds_max_local_px | 0.9 | 0.90173 | yes |

