import json
import subprocess
import sys
import time

import cv2
import numpy as np
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from alignment_performance import affine_ecc
from comparison_jobs import ComparisonJobs


def wait_done(jobs, job_id):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        result = jobs.status(job_id)
        if result['state'] != 'running':
            return result
        time.sleep(.05)
    pytest.fail('Job did not finish')


def test_worker_reports_invalid_image_without_hanging(tmp_path):
    jobs = ComparisonJobs(tmp_path)
    try:
        job_id = jobs.submit({'mode': 'gen', 'model_ref': '124060', 'candidate': 'bad.png'}, {'candidate': b'bad'})['job_id']
        result = wait_done(jobs, job_id)
        assert result['state'] == 'failed'
        assert result['message']
        assert jobs.jobs[job_id]['process'].poll() is not None
    finally:
        jobs.close()


def sleeping_worker(monkeypatch):
    original = subprocess.Popen
    def launch(command, **kwargs):
        return original([sys.executable, '-c', 'import time; time.sleep(60)'], **kwargs)
    monkeypatch.setattr(subprocess, 'Popen', launch)


def test_cancel_kills_worker_and_rejects_overlapping_job(tmp_path, monkeypatch):
    sleeping_worker(monkeypatch)
    jobs = ComparisonJobs(tmp_path)
    try:
        job_id = jobs.submit({}, {})['job_id']
        with pytest.raises(HTTPException) as exc:
            jobs.submit({}, {})
        assert exc.value.status_code == 409
        process = jobs.jobs[job_id]['process']
        assert jobs.cancel(job_id)['state'] == 'cancelled'
        assert process.poll() is not None
        another = jobs.submit({}, {})['job_id']
        assert another != job_id
        assert jobs.cancel(another)['state'] == 'cancelled'
    finally:
        jobs.close()


def test_timeout_kills_native_worker(tmp_path, monkeypatch):
    sleeping_worker(monkeypatch)
    jobs = ComparisonJobs(tmp_path, timeout=.2)
    try:
        job_id = jobs.submit({}, {})['job_id']
        result = wait_done(jobs, job_id)
        assert result['state'] == 'failed'
        assert 'timed out' in result['message']
        assert jobs.jobs[job_id]['process'].poll() is not None
    finally:
        jobs.close()


def test_tray_quit_stops_comparisons_before_exiting(monkeypatch):
    import app
    from types import SimpleNamespace
    events = []
    monkeypatch.setattr(app.backend, 'comparison_jobs', SimpleNamespace(close=lambda: events.append('jobs')), raising=False)
    monkeypatch.setattr(app.os, '_exit', lambda code: events.append('exit'))
    app._quit(SimpleNamespace(stop=lambda: events.append('tray')), None)
    assert events == ['jobs', 'tray', 'exit']


def test_successful_result_and_stage_are_returned(tmp_path, monkeypatch):
    sleeping_worker(monkeypatch)
    jobs = ComparisonJobs(tmp_path)
    try:
        job_id = jobs.submit({}, {})['job_id']
        (tmp_path / job_id / 'result.json').write_text(json.dumps({'result': {'session_id': 'saved'}}))
        assert wait_done(jobs, job_id)['result']['session_id'] == 'saved'
        assert jobs.jobs[job_id]['process'].poll() is not None
    finally:
        jobs.close()


def test_job_api_validation_and_unknown_job():
    # The V1 installer patches legacy module globals; isolate it from tests of
    # the unpatched legacy confidence gate, regardless of test ordering.
    code = '''
import main_v1
from fastapi.testclient import TestClient
with TestClient(main_v1.app) as client:
    assert client.get('/api/v1/comparison-jobs/unknown').status_code == 404
    assert client.delete('/api/v1/comparison-jobs/unknown').status_code == 404
    result = client.post('/api/v1/comparison-jobs', data={'mode': 'gen', 'model_ref': 'UNKNOWN'},
                         files={'candidate': ('image.png', b'bad')})
    assert result.status_code == 404
'''
    subprocess.run([sys.executable, '-c', code], check=True, timeout=30, capture_output=True)


def test_affine_ecc_restores_full_resolution_coordinates(monkeypatch):
    shape = (1001, 1601)
    def fake(ref, aligned, warp, motion, criteria, inputMask, gaussFiltSize):
        assert max(ref.shape) <= 720
        assert inputMask.shape == ref.shape
        assert criteria[1] == 150
        return .9, np.array([[1, 0, 3], [0, 1, -2]], dtype=np.float32)
    monkeypatch.setattr(cv2, 'findTransformECC', fake)
    image = np.zeros(shape, np.float32)
    _, inverse = affine_ecc(image, image, np.ones(shape, np.uint8))
    assert inverse[0, 2] == pytest.approx(3 * 1601 / 720)
    assert inverse[1, 2] == pytest.approx(-2 * 1001 / round(1001 * 720 / 1601))


def test_affine_ecc_recovers_known_translation():
    random = np.random.default_rng(7)
    image = cv2.GaussianBlur(random.random((850, 1200), dtype=np.float32), (0, 0), 2)
    transform = np.float32([[1, 0, 5], [0, 1, -3]])
    moved = cv2.warpAffine(image, transform, (1200, 850))
    mask = np.zeros(image.shape, np.uint8)
    mask[40:-40, 40:-40] = 255
    score, inverse = affine_ecc(image, moved, mask)
    assert score > .9
    np.testing.assert_allclose(inverse[:, 2], [5, -3], atol=.5)
