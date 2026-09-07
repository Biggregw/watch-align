"""Watch Align V1 Windows launcher."""
from __future__ import annotations
import multiprocessing
import app as legacy_launcher
from v1_upgrade import install
install(legacy_launcher.backend)
if __name__ == "__main__":
    multiprocessing.freeze_support()
    legacy_launcher.main()
