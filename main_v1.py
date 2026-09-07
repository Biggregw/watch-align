"""Watch Align V1 FastAPI entry point for server/cloud launches."""
import main as backend
from v1_upgrade import install, perspective_diagnostics
import v1_full
from v1_full import install_full
from v1_reference_library import install_reference_library

install(backend)
install_full(backend, perspective_diagnostics)
install_reference_library(backend, v1_full)
app = backend.app
