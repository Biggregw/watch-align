"""Watch Align V1 Windows launcher."""
from __future__ import annotations
import multiprocessing
import app as legacy_launcher
from v1_upgrade import install, perspective_diagnostics
from v1_full import install_full

install(legacy_launcher.backend)
install_full(legacy_launcher.backend, perspective_diagnostics)

if __name__ == "__main__":
    multiprocessing.freeze_support()
    legacy_launcher.main()
