@echo off
rem Watch Align - one-time build script.
rem Double-click this once. It sets up Python packages and builds
rem WatchAlign.exe.

cd /d "%~dp0"

echo ============================================
echo  Watch Align - build setup
echo ============================================
echo.

rem --- Find a working Python (avoid Microsoft Store stub that satisfies
rem     "where" but fails when actually run) ---
set PYLAUNCH=
py --version >nul 2>&1
if %errorlevel%==0 (
    set PYLAUNCH=py
) else (
    python --version >nul 2>&1
    if %errorlevel%==0 (
        set PYLAUNCH=python
    ) else (
        echo [ERROR] Python was not found.
        echo Please install Python 3.11 or later from https://www.python.org/downloads/
        echo During install, tick "Add python.exe to PATH".
        echo.
        pause
        exit /b 1
    )
)

echo Using Python launcher: %PYLAUNCH%
echo.

rem --- Create virtual environment if needed ---
if not exist .venv (
    echo Creating Python virtual environment...
    %PYLAUNCH% -m venv .venv
    if not exist .venv (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
)

call .venv\Scripts\activate.bat

echo.
echo Installing Python packages - this can take a few minutes...
python -m pip install --upgrade pip >nul
pip install -r "%~dp0requirements.txt"
if %errorlevel% neq 0 (
    echo [ERROR] Failed to install requirements.txt packages.
    pause
    exit /b 1
)

pip install pyinstaller pystray
if %errorlevel% neq 0 (
    echo [ERROR] Failed to install build-only packages.
    pause
    exit /b 1
)

echo.
echo Building WatchAlign app - this can take a few minutes...
pyinstaller --noconfirm WatchAlign.spec
if not exist dist\WatchAlign\WatchAlign.exe (
    echo [ERROR] Build failed - WatchAlign.exe was not produced.
    echo Scroll up to see the error from PyInstaller above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  App build complete!
echo ============================================
echo.

rem --- Try to build the installer too, if Inno Setup is available ---
rem Inno Setup's own installer can land in several different places
rem depending on version and whether "install for all users" was ticked:
rem   - Program Files\Inno Setup 6           (system-wide, 64-bit)
rem   - Program Files (x86)\Inno Setup 6     (system-wide, older/32-bit)
rem   - %LocalAppData%\Programs\Inno Setup 6 (current user only - common
rem                                            default when not run as admin)
rem The registry lookup below is the most reliable check since Inno Setup
rem always records its real install path there regardless of location, so
rem it's tried first; the folder checks are just a fallback if that lookup
rem ever fails for some reason.
rem
rem (Path existence is checked via separate "if exist" statements rather
rem than combined one-liners, to avoid a known batch-parsing trap: the
rem parentheses in %ProgramFiles(x86)% are themselves treated as block
rem delimiters if they end up inside an if(...) body.)
set "ISCC="

for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1" /v "InstallLocation" 2^>nul ^| findstr "InstallLocation"') do set "ISCC_REGDIR=%%B"
if not defined ISCC_REGDIR (
    for /f "tokens=2,*" %%A in ('reg query "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1" /v "InstallLocation" 2^>nul ^| findstr "InstallLocation"') do set "ISCC_REGDIR=%%B"
)
if defined ISCC_REGDIR (
    if exist "%ISCC_REGDIR%\ISCC.exe" set "ISCC=%ISCC_REGDIR%\ISCC.exe"
)

set "ISCC_CANDIDATE_1=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
set "ISCC_CANDIDATE_2=%ProgramFiles%\Inno Setup 6\ISCC.exe"
set "ISCC_CANDIDATE_3=%LocalAppData%\Programs\Inno Setup 6\ISCC.exe"

if not defined ISCC (
    if exist "%ISCC_CANDIDATE_1%" set "ISCC=%ISCC_CANDIDATE_1%"
)
if not defined ISCC (
    if exist "%ISCC_CANDIDATE_2%" set "ISCC=%ISCC_CANDIDATE_2%"
)
if not defined ISCC (
    if exist "%ISCC_CANDIDATE_3%" set "ISCC=%ISCC_CANDIDATE_3%"
)

if defined ISCC (
    echo Building WatchAlignSetup.exe installer...
    "%ISCC%" WatchAlign.iss
    if exist installer_output\WatchAlignSetup.exe (
        echo.
        echo ============================================
        echo  Installer build complete!
        echo ============================================
        echo.
        echo  Share this ONE file with anyone you want to use the app:
        echo      installer_output\WatchAlignSetup.exe
        echo.
        echo  They just double-click it and follow the install wizard -
        echo  no Python, no build.bat, nothing else needed on their end.
        echo.
    ) else (
        echo.
        echo [WARNING] Inno Setup was found but the installer build failed.
        echo Scroll up to see the error from ISCC above.
        echo You can still share dist\WatchAlign.exe directly instead - see
        echo SETUP-NOTES.txt for the difference between the two options.
        echo.
    )
) else (
    echo  NOTE: To also build a one-click installer ^(WatchAlignSetup.exe^)
    echo  that anyone can run without needing Python or this build.bat
    echo  step, install Inno Setup ^(free^) from:
    echo      https://jrsoftware.org/isdl.php
    echo  then run build.bat again. It will be detected automatically.
    echo.
    echo  Until then, you have a working app right here:
    echo      dist\WatchAlign\WatchAlign.exe
    echo  That works fine for using it yourself - run it from inside that
    echo  folder (the folder contains support files it needs, so keep them
    echo  together). To share it with other people as a proper installer,
    echo  install Inno Setup and run build.bat again.
    echo.
)

echo  See SETUP-NOTES.txt for first-run tips (SmartScreen) and the
echo  difference between WatchAlign.exe and WatchAlignSetup.exe.
echo.
pause
