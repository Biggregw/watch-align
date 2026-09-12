# Watch Align

Compare watch photographs locally on Windows. Align a QC photo with a genuine reference, inspect blink/overlay/edge views, and export a comparison report. Model profiles cover Rolex **126710BLNR** and **124060**.

[Download the latest Windows installer](https://github.com/Biggregw/watch-align/releases/latest/download/WatchAlignSetup.exe) · [User guide](V1-README.md) · [Release history](docs/releases/README.md)

## Use

Install `WatchAlignSetup.exe` and launch Watch Align from the Start menu. The tray app opens [the local interface](http://127.0.0.1:8001/v1).

- **Check my watch:** analyse a single QC photo.
- **Compare with genuine:** use a selected or automatically chosen reference and check agreement across up to three references.
- **Manual overlay:** align two supplied photos with fine controls.

Comparisons show their current stage and elapsed time. Cancel stops the comparison process; jobs that exceed three minutes stop with an error. Only one comparison runs at a time per app instance. The legacy `/api/v1/analyse` endpoint remains synchronous for existing clients; the desktop interface uses `/api/v1/comparison-jobs`.

Automatic rotation searches a full 360° to match the candidate to the reference, including sideways and upside-down photos. It displays the applied angle and opens the aligned overlay. The annotated photo is rotated with an expanded canvas so its edges are preserved. If repeating markers or weak detail make the angle ambiguous, the app flags the result for manual review and withholds confident measurements.

Photos and results stay on your PC. Fetching official references connects to the manufacturer; it does not upload your QC photos. Runtime data lives next to the installed executable in `runtime/`.

Perspective correction and reliability checks help with angled photos, but straighter, sharper photographs work best. Measurements describe image geometry, not calibrated physical dimensions or proof of authenticity.

## Run from source

Use Python 3.11 or newer:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt pystray
python app_v1.py
```

For a local server without the tray app, run `python -m uvicorn main_v1:app --host 127.0.0.1 --port 8001`.

## Test and build

```powershell
python -m pip install pytest httpx
python -m pytest -q tests
node tests/ui_comparison_jobs.cjs
build.bat
```

`build.bat` builds `dist/WatchAlign/WatchAlign.exe`; Inno Setup is needed to produce the installer. Keep the complete portable folder together.

Pull requests and pushes to `main` run regression tests and build the Windows artifact. Publishing is an explicit **Run workflow → publish** action on `main`, after the tests, executable smoke check, and installer build pass. Existing release tags are never overwritten.

## Repository map

- `main.py`, `v1_upgrade.py`: alignment engine and geometry checks.
- `v1_full.py`, `v1_ux_v120.py`, `v1_bugfix_*.py`: analysis, UI, and compatibility layers.
- `comparison_jobs.py`, `comparison_progress.py`: isolated workers, progress, cancellation, and timeout.
- `alignment_performance.py`: bounded residual alignment search.
- `rotation_alignment.py`: full-turn orientation matching and rigidly rotated analysis views.
- `static/comparison-jobs.js`: comparison interaction; other V1 assets are generated at startup.
- `tests/`, `benchmarks/`: regression tests and optional private-image benchmarking.
- `docs/releases/`: historical release and rollback notes.

Report problems through [GitHub issues](https://github.com/Biggregw/watch-align/issues). [Support development](https://buymeacoffee.com/biggregw).
