# What moves the genuine 12-triangle offset? (per photo, offline)

Genuine accepted photos after removing shared photos (level dial): 134; with a usable 12 and round radius errors: 127. Sign: 12 radial + = outward, - = toward the dial centre ("down").

## By source (medians over photos)

| Source | photos | R px | ellipse ratio | edge (round radius err) R | ring scale % | 12 radial R | 12 tangential R | 12 local R | 6 radial R | 9 radial R |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Bob's | 34 | +434 | +0.996 | +0.0009 | -0.015 | -0.0036 | -0.0008 | +0.0037 | +0.0011 | +0.0021 |
| Phillips | 47 | +228 | +0.984 | +0.0005 | -0.068 | -0.0041 | -0.0008 | +0.0045 | +0.0019 | +0.0013 |
| SWE 900px | 32 | +179 | +0.995 | +0.0011 | -0.085 | -0.0054 | -0.0012 | +0.0056 | +0.0019 | +0.0014 |
| SWE full-size | 12 | +778 | +0.996 | +0.0005 | -0.131 | -0.0061 | -0.0014 | +0.0064 | +0.0016 | +0.0017 |
| other | 2 | +291 | +0.994 | +0.0010 | +0.079 | -0.0021 | -0.0006 | +0.0023 | +0.0014 | +0.0014 |

## Correlations with the 12 radial offset (all photos, then within sources with >= 8 photos)

| Predictor | Spearman (all) | Bob's | SWE full-size | Phillips |
|---|---:|---:|---:|---:|
| edge (round radius err / R) | +0.09 (n=127) | -0.27 (n=34) | +0.30 (n=12) | -0.12 (n=47) |
| ellipse ratio | -0.01 (n=127) | +0.04 (n=34) | +0.08 (n=12) | -0.04 (n=47) |
| dial radius px | +0.08 (n=127) | +0.11 (n=34) | +0.04 (n=12) | -0.07 (n=47) |
| ring scale % | +0.29 (n=127) | +0.08 (n=34) | +0.41 (n=12) | +0.05 (n=47) |
| tick RMS px | -0.25 (n=127) | -0.16 (n=34) | +0.10 (n=12) | -0.00 (n=47) |

## Linear model of the 12 radial offset (R units; baseline source = Bob's)

- source only: R² = 0.22; offsets vs Bob's: Phillips -0.0005, SWE 900px -0.0019, SWE full-size -0.0025, other +0.0012
- edge + ellipse only: R² = 0.00; edge coefficient +0.187 (pure outline-shift geometry predicts -0.23), ellipse coefficient -0.0128 per unit (ellipse ratio - 1)
- edge + ellipse + source: R² = 0.24; edge +0.127, ellipse +0.0396; source offsets left: Phillips +0.0000, SWE 900px -0.0019, SWE full-size -0.0025, other +0.0012

## Same photograph at two resolutions (harvester near/dial copy inside one watch)

| Watch | source | R big / small | edge R big / small | ellipse big / small | 12 radial R big / small | 12 tangential R big / small |
|---|---|---:|---:|---:|---:|---:|
| gen_126710BLRO_phillips_146213 | Phillips | 394 / 244 | +0.0006 / +0.0009 | +0.987 / +0.996 | -0.0063 / -0.0058 | -0.0005 / -0.0007 |
| gen_126710BLRO_phillips_146213 | Phillips | 394 / 244 | +0.0005 / +0.0009 | +0.987 / +0.996 | -0.0062 / -0.0058 | -0.0008 / -0.0007 |
| gen_126710BLRO_phillips_146213 | Phillips | 244 / 160 | +0.0009 / +0.0019 | +0.996 / +0.996 | -0.0058 / +0.0009 | -0.0007 / +0.0079 |
| gen_126710BLRO_phillips_147798 | Phillips | 399 / 247 | +0.0007 / +0.0009 | +0.991 / +0.982 | -0.0041 / -0.0040 | +0.0001 / +0.0003 |
| gen_126710BLRO_phillips_147798 | Phillips | 399 / 247 | +0.0010 / +0.0009 | +0.996 / +0.982 | -0.0042 / -0.0040 | -0.0001 / +0.0003 |
| gen_126710BLRO_phillips_147798 | Phillips | 247 / 160 | +0.0009 / +0.0011 | +0.982 / +0.984 | -0.0040 / -0.0053 | +0.0003 / +0.0015 |
| gen_126710BLRO_phillips_147798 | Phillips | 247 / 126 | +0.0009 / +0.0015 | +0.982 / +0.986 | -0.0040 / -0.0061 | +0.0003 / +0.0001 |
| gen_126710BLRO_phillips_210072 | Phillips | 380 / 235 | +0.0007 / +0.0006 | +0.985 / +0.983 | -0.0034 / -0.0053 | -0.0009 / -0.0015 |
| gen_126710BLRO_phillips_210072 | Phillips | 380 / 235 | +0.0006 / +0.0006 | +0.986 / +0.983 | -0.0036 / -0.0053 | -0.0008 / -0.0015 |
| gen_126710BLRO_phillips_210072 | Phillips | 235 / 130 | +0.0006 / -0.0034 | +0.983 / +0.969 | -0.0053 / -0.0071 | -0.0015 / +0.0053 |
| gen_126710BLRO_phillips_210072 | Phillips | 235 / 102 | +0.0006 / -0.0047 | +0.983 / +0.989 | -0.0053 / -0.0002 | -0.0015 / -0.0019 |
| gen_126710BLRO_phillips_CH080120_75 | Phillips | 379 / 235 | +0.0003 / -0.0002 | +0.983 / +0.983 | -0.0041 / -0.0040 | -0.0015 / -0.0011 |
| gen_126710BLRO_phillips_CH080120_75 | Phillips | 379 / 235 | +0.0002 / -0.0002 | +0.980 / +0.983 | -0.0041 / -0.0040 | -0.0015 / -0.0011 |
| gen_126710BLRO_phillips_CH080120_75 | Phillips | 235 / 119 | -0.0002 / -0.0000 | +0.983 / +0.981 | -0.0040 / -0.0047 | -0.0011 / -0.0011 |
| gen_126710BLRO_phillips_NY080121_35 | Phillips | 232 / 119 | -0.0002 / +0.0002 | +0.967 / +0.967 | -0.0036 / -0.0033 | -0.0007 / -0.0015 |
| hv_page_04d00923f7 | SWE full-size | 785 / 274 | +0.0008 / +0.0010 | +0.998 / +0.997 | -0.0058 / -0.0058 | -0.0015 / -0.0007 |
| hv_page_04d00923f7 | SWE 900px | 274 / 169 | +0.0010 / +0.0012 | +0.997 / +0.998 | -0.0058 / -0.0069 | -0.0007 / -0.0008 |
| hv_page_176399af9b | Phillips | 305 / 203 | +0.0003 / +0.0003 | +0.987 / +0.985 | -0.0034 / -0.0045 | -0.0010 / -0.0008 |
| hv_page_176399af9b | Phillips | 305 / 176 | +0.0003 / +0.0003 | +0.987 / +0.984 | -0.0034 / -0.0038 | -0.0010 / -0.0012 |
| hv_page_176399af9b | Phillips | 305 / 127 | +0.0003 / +0.0003 | +0.987 / +0.980 | -0.0034 / -0.0044 | -0.0010 / -0.0004 |
| hv_page_176399af9b | Phillips | 305 / 100 | +0.0003 / +0.0008 | +0.987 / +0.982 | -0.0034 / -0.0038 | -0.0010 / +0.0006 |
| hv_page_4f2029765c | SWE full-size | 641 / 272 | +0.0003 / +0.0001 | +0.996 / +0.995 | – / -0.0048 | – / -0.0006 |
| hv_page_4f2029765c | SWE 900px | 272 / 173 | +0.0001 / +0.0006 | +0.995 / +0.995 | -0.0048 / -0.0047 | -0.0006 / -0.0009 |
| hv_page_54612422f7 | SWE full-size | 620 / 266 | +0.0001 / +0.0001 | +0.997 / +0.986 | -0.0046 / -0.0048 | -0.0006 / -0.0008 |
| hv_page_6d3d310e79 | Phillips | 333 / 222 | +0.0000 / +0.0005 | +0.979 / +0.979 | -0.0040 / -0.0048 | -0.0021 / -0.0026 |
| hv_page_6d3d310e79 | Phillips | 333 / 192 | +0.0000 / +0.0006 | +0.979 / +0.979 | -0.0040 / -0.0046 | -0.0021 / -0.0024 |
| hv_page_6d3d310e79 | Phillips | 333 / 131 | +0.0000 / +0.0007 | +0.979 / +0.985 | -0.0040 / -0.0047 | -0.0021 / -0.0024 |
| hv_page_6d3d310e79 | Phillips | 333 / 103 | +0.0000 / +0.0006 | +0.979 / +0.983 | -0.0040 / +0.0057 | -0.0021 / -0.0038 |
| hv_page_701654bab7 | SWE full-size | 590 / 238 | +0.0005 / +0.0011 | +0.999 / +0.984 | -0.0063 / -0.0055 | -0.0020 / -0.0031 |
| hv_page_7301586955 | SWE full-size | 785 / 268 | +0.0007 / +0.0009 | +0.999 / +0.999 | -0.0060 / -0.0050 | -0.0018 / -0.0019 |
| hv_page_7301586955 | SWE full-size | 785 / 169 | +0.0007 / +0.0017 | +0.999 / +0.999 | -0.0060 / -0.0046 | -0.0018 / -0.0019 |
| hv_page_a13e399a8f | Bob's | 434 / 236 | +0.0012 / +0.0016 | +0.998 / +0.999 | -0.0038 / -0.0037 | -0.0014 / -0.0013 |
| hv_page_c5c577583b | SWE full-size | 818 / 250 | +0.0007 / +0.0012 | +0.995 / +0.996 | -0.0077 / -0.0062 | -0.0031 / -0.0020 |
| hv_page_ec154bc25b | SWE full-size | 758 / 274 | +0.0008 / +0.0011 | +0.996 / +0.998 | -0.0047 / -0.0045 | -0.0008 / -0.0012 |
| hv_page_ec154bc25b | SWE full-size | 758 / 167 | +0.0008 / +0.0019 | +0.996 / +0.999 | -0.0047 / -0.0037 | -0.0008 / -0.0014 |
| swe_42335 | SWE full-size | 727 / 249 | +0.0008 / -0.0009 | +0.990 / +0.986 | – / -0.0078 | – / -0.0018 |
| swe_59259 | SWE full-size | 713 / 271 | +0.0006 / +0.0006 | +0.990 / +0.991 | – / -0.0056 | – / +0.0009 |
| swe_59259 | SWE full-size | 713 / 172 | +0.0006 / -0.0002 | +0.990 / +0.991 | – / -0.0095 | – / -0.0032 |
| swe_60177 | SWE full-size | 782 / 275 | +0.0006 / +0.0010 | +0.997 / +0.995 | -0.0072 / -0.0059 | -0.0011 / -0.0018 |
| swe_60177 | SWE 900px | 275 / 174 | +0.0010 / +0.0016 | +0.995 / +0.994 | -0.0059 / -0.0073 | -0.0018 / -0.0003 |
| swe_68351 | SWE full-size | 834 / 261 | +0.0005 / +0.0009 | +0.995 / +0.993 | -0.0040 / -0.0045 | -0.0016 / -0.0009 |
| swe_68351 | SWE full-size | 834 / 160 | +0.0005 / +0.0012 | +0.995 / +0.993 | -0.0040 / -0.0063 | -0.0016 / -0.0006 |
| swe_68408 | SWE full-size | 774 / 259 | +0.0010 / +0.0008 | +0.988 / +0.988 | -0.0035 / -0.0028 | -0.0008 / -0.0012 |
| swe_68408 | SWE full-size | 774 / 166 | +0.0010 / +0.0014 | +0.988 / +0.988 | -0.0035 / -0.0036 | -0.0008 / -0.0011 |
| swe_68784 | SWE full-size | 803 / 268 | +0.0002 / +0.0006 | +0.996 / +0.997 | -0.0066 / -0.0064 | -0.0012 / -0.0010 |
| swe_77497 | SWE full-size | 585 / 271 | +0.0002 / +0.0010 | +0.993 / +0.993 | -0.0073 / -0.0059 | -0.0011 / -0.0011 |
| swe_77497 | SWE full-size | 585 / 177 | +0.0002 / +0.0016 | +0.993 / +0.994 | -0.0073 / -0.0054 | -0.0011 / -0.0016 |

Same-photo change in 12 radial (big - small): median -0.0001 R over 43 pairs (range -0.0096..+0.0022).


## 12 offset re-centred on the genuine nominal (research; the Alpha92 master is unchanged)

Genuine nominal over 59 watches: radial -0.0038 R (toward the centre), tangential -0.0009 R. 58/59 watches read the 12 toward the centre.

| Genuine per-watch 12 offset | median | P90 | max |
|---|---:|---:|---:|
| from the Alpha92 master | 0.0039 R | 0.0060 R | 0.0083 R |
| from the genuine nominal | 0.0012 R | 0.0028 R | 0.0046 R |

| Photo | group | R px | radial R | tangential R | offset from nominal R | genuine watches at least as far |
|---|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | gen_candidate | 203 | -0.0040 | -0.0019 | 0.0010 | 32/59 |
| GEN_CAND_HO_02 | gen_candidate | 143 | -0.0051 | -0.0015 | 0.0014 | 23/59 |
| RL_THEONE_BLNR | rl_control | 165 | -0.0083 | -0.0031 | 0.0050 | 0/59 |
| RL_LOCAL_BLNR | rl_control | 220 | -0.0008 | -0.0140 | 0.0134 | 0/59 |
| RL_ARF_BLRO_CROOKED6 | rl_control | 234 | +0.0042 | -0.0092 | 0.0116 | 0/59 |

Per source, offset from the pooled genuine nominal (median over watches): Bob's 0.0010 R (n=32), Phillips 0.0007 R (n=7), SWE 0.0022 R (n=18), other 0.0018 R (n=2)

