"""Watch Align V1 FastAPI entry point for server/cloud launches."""
import main as backend
from v1_upgrade import install, perspective_diagnostics
import v1_full
from v1_confidence_v110 import install as install_confidence_v110
from v1_full import install_full
import v1_reference_library
from v1_reference_library import install_reference_library
import v1_official_sources
from v1_official_sources import install as install_official_sources
from v1_ux_v120 import install as install_v120

install(backend)
install_confidence_v110(v1_full)
install_full(backend, perspective_diagnostics)
install_reference_library(backend, v1_full)
install_official_sources(backend, v1_full, v1_reference_library)
install_v120(backend, v1_full, v1_reference_library, v1_official_sources, perspective_diagnostics)
app = backend.app
