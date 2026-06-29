# PyInstaller spec for Watch Align tray app.
# Built by running:  pyinstaller WatchAlign.spec
# (build.bat does this for you - you shouldn't normally need to run this by hand.)
#
# This is a ONEDIR build (EXE + a folder of support files), NOT onefile.
# Onedir is used deliberately: the onefile bootloader extracts the whole
# app to a temp dir and re-launches itself on every run, which was causing
# duplicate WatchAlign.exe processes that fought over the server port.
# Onedir doesn't do that - it runs the exe directly with no self-respawn.
# The "it's a folder not a single file" downside doesn't matter here
# because the Inno Setup installer bundles the whole folder anyway, so the
# person sharing/installing still only deals with one WatchAlignSetup.exe.

from PyInstaller.utils.hooks import copy_metadata

block_cipher = None

# imageio looks up its own package metadata at runtime to find its plugins;
# under PyInstaller that metadata isn't there by default, so we copy it in
# explicitly. Without this, exporting GIF/MP4 from the frozen exe fails.
datas = []
datas += copy_metadata("imageio")
datas += copy_metadata("imageio-ffmpeg")

a = Analysis(
    ["app.py"],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=[
        "uvicorn.logging",
        "uvicorn.loops",
        "uvicorn.loops.auto",
        "uvicorn.protocols",
        "uvicorn.protocols.http",
        "uvicorn.protocols.http.auto",
        "uvicorn.protocols.websockets",
        "uvicorn.protocols.websockets.auto",
        "uvicorn.lifespan",
        "uvicorn.lifespan.on",
        "imageio_ffmpeg",
        "PIL._tkinter_finder",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

# ONEDIR: EXE() gets only the scripts; binaries/data go into COLLECT below.
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="WatchAlign",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="WatchAlign",
)
