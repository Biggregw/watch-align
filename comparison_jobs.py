"""Run comparisons in killable processes instead of tying up HTTP requests.

The desktop app runs one job at a time. Polling does not start more work;
cancel and timeout terminate the worker, including native OpenCV calls.
"""
from __future__ import annotations

import atexit
import json
import shutil
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path

from fastapi import File, Form, HTTPException, UploadFile

from comparison_progress import write_json

TIMEOUT_SECONDS = 180


def run_worker(folder: Path) -> None:
    import comparison_progress
    comparison_progress._folder = folder
    try:
        comparison_progress.report('Preparing comparison…')
        import main_v1
        import cv2
        # Multiple native thread pools can make a desktop unresponsive.
        cv2.setNumThreads(2)
        backend = main_v1.backend
        manifest = json.loads((folder / 'request.json').read_text(encoding='utf-8'))
        route = next(r for r in backend.app.routes if getattr(r, 'path', '') == '/api/v1/analyse')
        from contextlib import ExitStack
        with ExitStack() as stack:
            kwargs = {'mode': manifest['mode'], 'model_ref': manifest['model_ref'], 'reference': None}
            for key in ('candidate', 'reference'):
                if manifest.get(key):
                    stream = stack.enter_context((folder / key).open('rb'))
                    kwargs[key] = UploadFile(filename=manifest[key], file=stream)
            result = route.dependant.call(**kwargs)
        write_json(folder / 'result.json', {'result': result})
    except Exception as exc:
        detail = exc.detail if isinstance(exc, HTTPException) else f'Comparison failed: {type(exc).__name__}: {exc}'
        write_json(folder / 'result.json', {'error': str(detail)})


class ComparisonJobs:
    def __init__(self, root: Path, timeout: float = TIMEOUT_SECONDS):
        self.root = root
        self.timeout = timeout
        self.lock = threading.RLock()
        self.jobs = {}

    def submit(self, manifest, uploads):
        with self.lock:
            if any(job['state'] == 'running' for job in self.jobs.values()):
                raise HTTPException(409, 'A comparison is already running. Wait for it or cancel it first.')
            # Retain only recent status/results in memory and remove job uploads.
            for old_id in list(self.jobs)[:-9]:
                shutil.rmtree(self.root / old_id, ignore_errors=True)
                del self.jobs[old_id]
            job_id = uuid.uuid4().hex
            folder = self.root / job_id
            folder.mkdir(parents=True)
            try:
                for key, raw in uploads.items():
                    (folder / key).write_bytes(raw)
                write_json(folder / 'request.json', manifest)
                command = ([sys.executable, '--comparison-worker', str(folder)] if getattr(sys, 'frozen', False)
                           else [sys.executable, str(Path(__file__).resolve()), '--worker', str(folder)])
                process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                           stderr=subprocess.DEVNULL,
                                           creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            except Exception:
                shutil.rmtree(folder, ignore_errors=True)
                raise
            self.jobs[job_id] = {'state': 'running', 'message': 'Preparing comparison…',
                                 'started': time.monotonic(), 'process': process}
            threading.Thread(target=self._monitor, args=(job_id,), daemon=True).start()
            return {'job_id': job_id}

    @staticmethod
    def _stop(process):
        if process.poll() is None:
            process.kill()
        process.wait(timeout=10)

    def _monitor(self, job_id):
        folder = self.root / job_id
        job = self.jobs[job_id]
        try:
            while True:
                with self.lock:
                    if job['state'] != 'running':
                        return
                    elapsed = time.monotonic() - job['started']
                    if elapsed >= self.timeout:
                        self._stop(job['process'])
                        job.update(state='failed', message='Comparison timed out after three minutes. Try a smaller, straight-on photo or another reference.')
                        return
                    result_path = folder / 'result.json'
                    if result_path.exists():
                        result = json.loads(result_path.read_text(encoding='utf-8'))
                        # Reap the worker before accepting another comparison.
                        self._stop(job['process'])
                        if 'error' in result:
                            job.update(state='failed', message=result['error'])
                        else:
                            job.update(state='complete', message='Analysis complete.', result=result['result'])
                        return
                    progress_path = folder / 'progress.json'
                    if progress_path.exists():
                        job['message'] = json.loads(progress_path.read_text(encoding='utf-8'))['message']
                    if job['process'].poll() is not None:
                        job.update(state='failed', message='Comparison worker stopped unexpectedly. Please try again.')
                        return
                time.sleep(0.2)
        except Exception:
            with self.lock:
                self._stop(job['process'])
                job.update(state='failed', message='Could not finish the comparison. Please try again.')
        finally:
            # The completed image session is separate; remove temporary uploads.
            for name in ('candidate', 'reference'):
                (folder / name).unlink(missing_ok=True)

    def status(self, job_id):
        with self.lock:
            job = self.jobs.get(job_id)
            if job is None:
                raise HTTPException(404, 'Comparison job not found. Start a new comparison.')
            return {key: value for key, value in job.items() if key not in ('process', 'started')} | {
                'elapsed_seconds': round(time.monotonic() - job['started'])}

    def cancel(self, job_id):
        with self.lock:
            self.status(job_id)
            job = self.jobs[job_id]
            if job['state'] == 'running':
                self._stop(job['process'])
                job.update(state='cancelled', message='Comparison cancelled.')
            return self.status(job_id)

    def close(self):
        with self.lock:
            for job_id in self.jobs:
                self.cancel(job_id)


def install(backend):
    if getattr(backend, '_comparison_jobs_installed', False):
        return
    backend._comparison_jobs_installed = True
    import v1_full
    from version import VERSION
    backend.app.version = v1_full.V1_FULL_VERSION = VERSION
    html_path = backend.STATIC_DIR / 'v1.html'
    html_path.write_text(html_path.read_text(encoding='utf-8').replace('1.2.2', VERSION), encoding='utf-8')
    jobs = ComparisonJobs(backend.PERSIST_DIR / 'runtime' / 'comparison-jobs')
    backend.comparison_jobs = jobs
    atexit.register(jobs.close)
    backend.app.add_event_handler('shutdown', jobs.close)

    @backend.app.post('/api/v1/comparison-jobs', status_code=202)
    def start(mode: str = Form(...), model_ref: str = Form(...), candidate: UploadFile = File(...),
              reference: UploadFile | None = File(None)):
        import v1_full
        v1_full.model_info(model_ref)
        if mode not in ('gen', 'qc'):
            raise HTTPException(422, 'Choose a valid comparison mode.')
        manifest = {'mode': mode, 'model_ref': model_ref}
        uploads = {}
        for key, upload in (('candidate', candidate), ('reference', reference)):
            if upload is not None and upload.filename:
                raw = upload.file.read(backend.MAX_UPLOAD_BYTES + 1)
                if not raw or len(raw) > backend.MAX_UPLOAD_BYTES:
                    raise HTTPException(422, 'Each photo must be between 1 byte and 20 MB.')
                manifest[key] = Path(upload.filename).name
                uploads[key] = raw
        if 'candidate' not in uploads:
            raise HTTPException(422, 'Choose your watch photo first.')
        return jobs.submit(manifest, uploads)

    @backend.app.get('/api/v1/comparison-jobs/{job_id}')
    def status(job_id: str):
        return jobs.status(job_id)

    @backend.app.delete('/api/v1/comparison-jobs/{job_id}')
    def cancel(job_id: str):
        return jobs.cancel(job_id)

    js_path = backend.STATIC_DIR / 'v1-full.js'
    js_path.write_text(js_path.read_text(encoding='utf-8') + '\n' +
                       (Path(__file__).parent / 'static' / 'comparison-jobs.js').read_text(encoding='utf-8'), encoding='utf-8')


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--worker':
        run_worker(Path(sys.argv[2]).resolve())
