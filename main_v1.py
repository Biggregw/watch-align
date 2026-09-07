"""Watch Align V1 FastAPI entry point for server/cloud launches."""
import main as backend
from v1_upgrade import install, perspective_diagnostics
from v1_full import install_full

install(backend)
install_full(backend, perspective_diagnostics)
app = backend.app
