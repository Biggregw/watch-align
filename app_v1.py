"""Watch Align V1 Windows launcher."""
from __future__ import annotations
import multiprocessing
import app as legacy_launcher
from v1_upgrade import install, perspective_diagnostics
import v1_full
from v1_confidence_v110 import install as install_confidence_v110
from v1_full import install_full
import v1_reference_library
from v1_reference_library import install_reference_library
import v1_official_sources
from v1_official_sources import install as install_official_sources
import v1_ux_v120
from v1_ux_v120 import install as install_v120
from v1_ux_v120_patch import install as install_v120_patch
from v1_bugfix_v121 import install as install_v121
from v1_bugfix_v122 import install as install_v122

legacy_launcher.PORT = 8001
legacy_launcher.LOCK_PORT = 8766
legacy_launcher.URL = f"http://127.0.0.1:{legacy_launcher.PORT}/v1"

install(legacy_launcher.backend)
install_confidence_v110(v1_full)
install_full(legacy_launcher.backend, perspective_diagnostics)
install_reference_library(legacy_launcher.backend, v1_full)
install_official_sources(legacy_launcher.backend, v1_full, v1_reference_library)
install_v120(legacy_launcher.backend, v1_full, v1_reference_library, v1_official_sources, perspective_diagnostics)
install_v120_patch(legacy_launcher.backend)
install_v121(legacy_launcher.backend, v1_full, v1_official_sources)
install_v122(legacy_launcher.backend, v1_full, v1_ux_v120, v1_official_sources, perspective_diagnostics)

if __name__ == "__main__":
    multiprocessing.freeze_support()
    import sys
    if "--smoke-test" in sys.argv:
        from smoke_v1 import run
        run(legacy_launcher.backend)
    else:
        legacy_launcher.main()
