"""Watch Align V1 Windows launcher.

Runs the proven v0.9.4 tray launcher with the V1 geometry/perspective upgrade
installed before the FastAPI server starts. The original app.py and v0.9.4
engine remain untouched for safe comparison and rollback.
"""
from __future__ import annotations

import multiprocessing

import app as legacy_launcher
from v1_upgrade import install

install(legacy_launcher.backend)

if __name__ == "__main__":
    multiprocessing.freeze_support()
    legacy_launcher.main()
