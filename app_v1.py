"""Watch Align V1 Windows launcher."""
from __future__ import annotations
import multiprocessing
import app as legacy_launcher
from v1_upgrade import install, perspective_diagnostics
import v1_full
from v1_full import install_full
from v1_reference_library import install_reference_library

install(legacy_launcher.backend)
install_full(legacy_launcher.backend, perspective_diagnostics)
install_reference_library(legacy_launcher.backend, v1_full)
legacy_launcher.URL = f"http://127.0.0.1:{legacy_launcher.PORT}/v1"

if __name__ == "__main__":
    multiprocessing.freeze_support()
    legacy_launcher.main()
