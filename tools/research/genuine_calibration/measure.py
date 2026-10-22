"""Measure the restored, masked acquisition root through the watch calibrator's own masked path.

    python -m tools.research.genuine_calibration.measure WORK --model submariner_124060

Runs tools/watch_calibrator/production_measure.measure with a research config (configs.config) that declares the
marker-mask-v1 policy and the marker_geometry_v1 adapter: GeometryMeasure (the app's Alpha99Pipeline read through
MarkerGeometry) writes values for eligible markers only, then marker_mask.apply verifies every photograph's bytes
against its SHA-bound archive mask and blanks every value whose marker (or, for ring metrics, any marker its ring fit
used) is not ELIGIBLE; summarise() re-checks every row before any statistic. A missing mask, a SHA mismatch or a value
of an ineligible marker stops the run: there is no unmasked fallback.

Writes WORK/geom/<model>_<partition>_gen_{photo,watch,repeatability,mask_exclusions}.csv and WORK/measure.json.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
for p in (str(ROOT), str(ROOT / 'tools/watch_calibrator')):
    if p not in sys.path:
        sys.path.insert(0, p)

import marker_mask  # noqa: E402
import production_measure  # noqa: E402
from tools.image_archive.integration import MASKS_FILE  # noqa: E402
import split as locked_split  # noqa: E402  (the calibrator's locked split)
from .configs import config  # noqa: E402


def measure(work, model):
    work = Path(work)
    cfg = config(model)
    acq = work / 'acq'
    if not marker_mask.required(cfg) or not (acq / MASKS_FILE).is_file():
        raise SystemExit('a masked research run needs the marker-mask-v1 policy and %s' % MASKS_FILE)
    out = {}
    for part in locked_split.PARTS:
        out[part] = production_measure.measure(cfg, acq, acq / 'locked_split.csv', work / 'geom', part, 'gen')
    (work / 'measure.json').write_text(json.dumps({'model': model, 'config': cfg, 'partitions': out}, indent=1) + '\n')
    print(json.dumps({p: {k: v for k, v in r.items() if k != 'metrics'} for p, r in out.items()}, indent=1))
    return out


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('work')
    ap.add_argument('--model', required=True)
    a = ap.parse_args()
    measure(a.work, a.model)
