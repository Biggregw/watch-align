"""Watch Align V1 FastAPI entry point for server/cloud launches."""
import main as backend
from v1_upgrade import install

install(backend)
app = backend.app
