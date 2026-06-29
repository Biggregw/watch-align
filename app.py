"""Watch Align - Windows tray launcher (local only, no internet tunnel).

Runs the existing FastAPI app (main.py, unchanged) in a background thread
and shows a system tray icon. Opens automatically in your browser at
http://localhost:8000 - reachable from this PC only.

This file is the entry point bundled into WatchAlign.exe by PyInstaller.
It does not change any of the alignment / image-processing logic in main.py.
"""

from __future__ import annotations

import multiprocessing
import os
import socket
import sys
import threading
import time
import webbrowser

import uvicorn
from PIL import Image, ImageDraw
from pystray import Icon, Menu, MenuItem

import main as backend  # the existing FastAPI app, untouched

PORT = 8000
URL = f"http://127.0.0.1:{PORT}"

# Single-instance locking is done by binding a dedicated lock socket to a
# fixed loopback port. Binding a TCP port is atomic at the OS level - only
# one process can hold a given port at a time - so this has none of the
# race condition a "check then start" approach would have, and unlike a
# Windows named mutex it works (and can be tested) identically on every
# platform. The lock port is deliberately different from the app's own
# PORT so the two never interfere. The socket is kept open for the life of
# the process; the OS releases it automatically when the process exits.
LOCK_PORT = 8765
_lock_socket = None


def acquire_single_instance_lock() -> bool:
    """Returns True if this process successfully claimed the single-instance
    lock (i.e. it's the primary instance), False if another instance already
    holds it.
    """
    global _lock_socket
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        # Note: intentionally NOT setting SO_REUSEADDR/SO_REUSEPORT here -
        # we WANT a second bind to the same port to fail, that's the lock.
        s.bind(("127.0.0.1", LOCK_PORT))
        s.listen(1)
        _lock_socket = s  # keep a reference so it isn't garbage-collected
        return True
    except OSError:
        # Port already bound => another instance holds the lock.
        s.close()
        return False



def make_icon_image() -> Image.Image:
    """Small watch-face style icon for the system tray."""
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((4, 4, size - 4, size - 4), fill=(30, 30, 30, 255), outline=(220, 220, 220, 255), width=3)
    cx, cy = size // 2, size // 2
    draw.line((cx, cy, cx, cy - 18), fill=(220, 220, 220, 255), width=3)
    draw.line((cx, cy, cx + 12, cy + 6), fill=(220, 220, 220, 255), width=3)
    draw.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=(220, 220, 220, 255))
    return img


def run_server(started_event: threading.Event) -> None:
    # PyInstaller windowed (console=False) builds set sys.stdout/stderr to None.
    # uvicorn's logging setup calls sys.stderr.isatty() which crashes on None.
    # Redirect to devnull so uvicorn can configure its log handlers safely.
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w")

    config = uvicorn.Config(backend.app, host="127.0.0.1", port=PORT, log_level="warning")
    server = uvicorn.Server(config)

    # uvicorn.Server exposes a startup hook we can use to know exactly
    # when it's actually bound and ready, rather than guessing with a
    # fixed time.sleep() and hoping it was long enough.
    original_startup = server.startup

    async def startup_and_signal(sockets=None):
        await original_startup(sockets=sockets)
        started_event.set()

    server.startup = startup_and_signal
    server.run()


def _open_app(icon, item) -> None:
    webbrowser.open(URL)


def _quit(icon, item) -> None:
    icon.stop()
    os._exit(0)


def wait_for_server(timeout: float = 20.0) -> bool:
    """Poll until something is actually listening on PORT, or give up.

    Used both by a fresh launch (waiting for our own server) and by a
    second launch that found another instance already starting up - in
    that case there's no event to wait on (it belongs to a different
    process), so we poll the port directly instead.
    """
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(0.5)
            if s.connect_ex(("127.0.0.1", PORT)) == 0:
                return True
        time.sleep(0.3)
    return False


def main() -> None:
    if not acquire_single_instance_lock():
        # Another copy is already running - or, just as likely, is in the
        # middle of starting up right now (PyInstaller onefile builds can
        # take a few seconds to unpack and initialise before the server
        # actually binds the port - e.g. the installer's "launch now" and
        # a manual double-click landing seconds apart). Either way, don't
        # start a second server that can never bind the port; instead
        # wait for whichever instance got there first to actually finish
        # starting, then open the browser.
        wait_for_server()
        webbrowser.open(URL)
        return

    started_event = threading.Event()
    threading.Thread(target=run_server, args=(started_event,), daemon=True).start()

    # Wait for the server to actually confirm it's bound and ready,
    # rather than sleeping a fixed guess. Falls back to opening anyway
    # after the timeout even if the signal never arrives, so a real
    # startup failure doesn't leave the user staring at nothing forever.
    started_event.wait(timeout=20)
    webbrowser.open(URL)

    menu = Menu(
        MenuItem("Open Watch Align", _open_app, default=True),
        Menu.SEPARATOR,
        MenuItem("Quit", _quit),
    )
    icon = Icon("WatchAlign", make_icon_image(), "Watch Align", menu)
    icon.run()


if __name__ == "__main__":
    # Required for PyInstaller --onefile builds: without this, certain
    # library code paths (commonly triggered via numpy/OpenCV/imageio
    # importing multiprocessing) cause the frozen exe to silently
    # re-launch itself as a "child" process, which re-runs this whole
    # file from scratch - including this main() call - resulting in
    # multiple WatchAlign.exe processes all fighting over the same port.
    # freeze_support() must be the very first thing called.
    multiprocessing.freeze_support()
    main()
