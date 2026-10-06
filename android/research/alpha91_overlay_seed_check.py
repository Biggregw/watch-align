#!/usr/bin/env python3
"""Research only. Integration check for alpha91_overlay_registration.py: can the frozen tick-lattice
refinement start from Alpha90's own dial ellipse (DIAL_ELLIPSES in alpha91_fairscan_pose_proof.py)
+ the 12-direction cue + the masked ECC basin step, i.e. without SIFT?
Compares the result with the frozen run's homographies in alpha91-overlay-registration-results/results.json.

Usage: python3 alpha91_overlay_seed_check.py --inputs <alpha91_claude_overlay_inputs>
"""
import argparse, json, os, sys
import cv2
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import alpha91_overlay_registration as A
import alpha91_fairscan_pose_proof as L

ap = argparse.ArgumentParser(); ap.add_argument('--inputs', required=True); args = ap.parse_args()
M, ref, SC = A.build_master(os.path.join(args.inputs, 'reference', 'bare_genuine_dial_reference_crop.png'))
tm = A.twelve_template(ref, SC, A.CUE_RS)
base = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                   'alpha91-overlay-registration-results', 'results.json')))['photos']
a = np.radians(np.arange(0, 360, 15)); G = 0.95 * np.c_[np.sin(a), -np.cos(a)]
for n in A.NAMES:
    g = cv2.cvtColor(cv2.imread(os.path.join(args.inputs, 'genuine_controls', n + '.png')), cv2.COLOR_BGR2GRAY).astype(np.float32)
    smp = A.Sampler(g)
    (cx, cy), (d1, d2), ang = L.DIAL_ELLIPSES[n]; t = np.radians(ang)
    Aff = np.array([[np.cos(t), -np.sin(t)], [np.sin(t), np.cos(t)]]) @ np.diag([d1 / 2, d2 / 2])
    H0 = np.array([[Aff[0, 0], Aff[0, 1], cx], [Aff[1, 0], Aff[1, 1], cy], [0, 0, 1.]])
    if np.linalg.det(H0[:2, :2]) < 0:
        H0 = H0 @ np.diag([-1, 1, 1.])
    Hb = np.array(base[n]['H'])
    for use_ecc in (False, True):
        H = H0 @ A.rot3(A.twelve_cue(smp, H0, M, tm, A.CUE_RS)[0])
        if use_ecc:
            H = A.coarse_ecc(ref, SC, g, H); H = H @ A.rot3(A.twelve_cue(smp, H, M, tm, A.CUE_RS)[0])
        d0 = np.hypot(*(A.proj(H, G) - A.proj(Hb, G)).T).max()
        H2, st, _ = A.fine_inner(smp, A.fine_centroid(smp, H, M), M)
        gate = A.lattice_fit_gate(st)
        d = np.hypot(*(A.proj(H2, G) - A.proj(Hb, G)).T).max()
        print(f"{n}: ellipse+12cue{'+ECC' if use_ecc else ''} seed {d0:.1f}px off -> after refinement {d:.3f}px "
              f"(phase change {A.relative_roll_deg(H, H2):+.2f}deg, tick median {st['tick_median_px']:.2f} rms {st['tick_rms_px']:.2f}px) "
              f"-> {'ACCEPT' if not gate else 'REJECT: ' + '; '.join(gate)}")
