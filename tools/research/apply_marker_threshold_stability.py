#!/usr/bin/env python3
"""Research-only fix for round-marker membership discontinuities.

The frozen marker segmenter derives its Otsu threshold from the pose-dependent
eligible pixels for each marker. Tiny pose changes therefore change the Otsu
histogram and can make a peer appear/disappear, abruptly changing the conic and
Stage 3 projective fit. Use one image-global Otsu threshold instead. The source
image is unchanged across the perturbation experiment, so the photometric
threshold is invariant while the existing geometric ROI/gates remain intact.
"""
from pathlib import Path

p = Path("tools/watch_align_py/marker_consensus.py")
s = p.read_text(encoding="utf-8")
old = '''    values = np.array([v for _x, _y, v in eligible], dtype=np.uint8)\n    threshold, _ = cv2.threshold(values.reshape(-1, 1), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)\n    mask = np.zeros((y1 - y0 + 1, x1 - x0 + 1), dtype=np.uint8)\n'''
new = '''    # The old Otsu histogram was built from `eligible`, whose membership is\n    # pose-derived. Sub-pixel pose jitter could therefore alter the threshold\n    # and make a round peer appear/disappear, causing a discrete conic/projective\n    # branch switch. Derive the photometric threshold from the immutable source\n    # image instead; keep the existing pose-derived geometric ROI and gates.\n    threshold, _ = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)\n    mask = np.zeros((y1 - y0 + 1, x1 - x0 + 1), dtype=np.uint8)\n'''
if old not in s:
    raise SystemExit("marker threshold patch anchor not found")
s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")
print("applied pose-invariant round-marker threshold")
