"""Watch Align V1 Windows launcher."""
from __future__ import annotations
import multiprocessing
import app as legacy_launcher
from v1_upgrade import install, perspective_diagnostics
import v1_full
from v1_full import install_full
from v1_reference_library import install_reference_library

# Run the V1 beta independently from an installed legacy Watch Align copy.
# This prevents the legacy single-instance lock / port 8000 server from
# intercepting a V1 launch and serving the old app at /v1.
legacy_launcher.PORT = 8001
legacy_launcher.LOCK_PORT = 8766
legacy_launcher.URL = f"http://127.0.0.1:{legacy_launcher.PORT}/v1"

install(legacy_launcher.backend)
install_full(legacy_launcher.backend, perspective_diagnostics)
install_reference_library(legacy_launcher.backend, v1_full)

if __name__ == "__main__":
    multiprocessing.freeze_support()
    legacy_launcher.main()
