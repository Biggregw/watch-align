#!/usr/bin/env python3
"""Unattended Watch Align dataset harvester.

    python3 tools/dataset_harvester/harvest.py                  # one bounded discover+process run
    python3 tools/dataset_harvester/harvest.py --dry-run        # plan only: no downloads, no state writes
    python3 tools/dataset_harvester/harvest.py --report         # rewrite the report from saved state
    python3 tools/dataset_harvester/harvest.py --loop --interval 3600   # keep running (small VM)

See tools/dataset_harvester/README.md. Credentials only ever come from the environment.
"""
from __future__ import annotations

import argparse
import signal
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from harvester.adapters import all_adapters  # noqa: E402
from harvester.config import LIMITS, Paths  # noqa: E402
from harvester.outputs import markdown  # noqa: E402
from harvester.pipeline import Pipeline  # noqa: E402

_stop = False


def _on_signal(*_):
    global _stop
    _stop = True
    print("stop requested: finishing the current chunk, then saving", flush=True)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--discover", action="store_true", help="run discovery (default: discover and process)")
    ap.add_argument("--process", action="store_true", help="process pending sources (default: discover and process)")
    ap.add_argument("--once", action="store_true", help="one cycle then exit (the default unless --loop)")
    ap.add_argument("--loop", action="store_true", help="repeat cycles until stopped (SIGINT/SIGTERM)")
    ap.add_argument("--interval", type=int, default=3600, help="seconds between cycles with --loop")
    ap.add_argument("--max-sources", type=int, default=LIMITS.default_max_sources, help="sources processed per cycle (bounded by default)")
    ap.add_argument("--dry-run", action="store_true", help="plan only: no network, no downloads, no state written")
    ap.add_argument("--reprocess", action="store_true", help="re-evaluate sources already processed (uses local copies)")
    ap.add_argument("--source-adapter", action="append", choices=sorted(all_adapters()), help="limit discovery to this adapter (repeatable)")
    ap.add_argument("--report", action="store_true", help="only write the report/manifest from saved state")
    ap.add_argument("--data-dir", type=Path, help="state/images/reports location (default datasets/harvest or $HARVEST_DATA_DIR)")
    ap.add_argument("--shards", type=int, help="parallel analysis JVMs (default: CPUs/2, max 4)")
    a = ap.parse_args(argv)

    both = not a.discover and not a.process and not a.report
    signal.signal(signal.SIGINT, _on_signal)
    signal.signal(signal.SIGTERM, _on_signal)
    paths = Paths(a.data_dir) if a.data_dir else Paths()

    while True:
        from harvester.harness import Harness
        p = Pipeline(paths=paths, dry_run=a.dry_run, reprocess=a.reprocess, max_sources=a.max_sources,
                     harness=Harness(shards=a.shards), should_stop=lambda: _stop)
        if a.report:
            rep = p.report()
            print(markdown(rep))
            return 0
        if a.discover or both:
            p.discover(a.source_adapter)
        if (a.process or both) and not _stop:
            p.process()
        rep = p.report()
        print(markdown(rep), flush=True)
        if a.dry_run and rep.get("plan"):
            print("## Plan (first sources)\n")
            for x in rep["plan"][:30]:
                print(f"- {x['watch'] or '(new)'} [{x['class'] or '?'}] {x['how']} — {x['source']}")
        if not a.loop or _stop:
            return 0
        for _ in range(a.interval):
            if _stop:
                return 0
            time.sleep(1)


if __name__ == "__main__":
    raise SystemExit(main())
