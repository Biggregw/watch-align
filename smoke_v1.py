"""Exercise bundled routes and confidence gating without opening a tray/browser."""
import json
import os
import time
from pathlib import Path


def run(backend):
    from fastapi.testclient import TestClient
    from v1_full import gate_measurements, V1_FULL_VERSION
    with TestClient(backend.app) as client:
        home = client.get('/v1')
        assert home.status_code == 200 and 'What do you want to check?' in home.text and 'V1 1.2.3' in home.text
        script=client.get('/static/v1-full.js')
        assert script.status_code == 200 and 'reference_consensus' in script.text and 'Recent comparisons' in home.text
        assert 'dashed oval' not in home.text
        assert '/api/v1/ux/reference-image/' in script.text
        assert '/api/v1/comparison-jobs' in script.text
        # Exercise the packaged worker dispatch, not only the parent routes.
        accepted = client.post('/api/v1/comparison-jobs', data={'mode': 'qc', 'model_ref': '124060'},
                               files={'candidate': ('invalid.png', b'not an image')})
        assert accepted.status_code == 202
        job_id = accepted.json()['job_id']
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            job = client.get('/api/v1/comparison-jobs/' + job_id).json()
            if job['state'] != 'running':
                break
            time.sleep(.1)
        assert job['state'] == 'failed', job
        assert 'stopped unexpectedly' not in job['message'], job
        assert 'timed out' not in job['message'], job
        models = client.get('/api/v1/models/full').json()
        assert models['version']==V1_FULL_VERSION == '1.2.3'
        assert client.get('/v1/references').status_code==200
        for ref in ('126710BLNR','124060'):
            status = client.get('/api/v1/ux/reference-status/'+ref)
            assert status.status_code == 200 and 'references' in status.json()
            sources = client.get('/api/v1/official-sources/'+ref)
            assert sources.status_code == 200 and sources.json().get('sources')
    metrics=dict(overall_confidence='low',region_confidence={},perspective={},
                 bezel=dict(available=True,confidence='high',offset_deg=.948))
    gate_measurements(metrics,'gen')
    assert metrics['bezel']['offset_deg'] is None
    assert metrics['measurement_warning']
    Path(os.environ.get('WATCH_ALIGN_SMOKE_RESULT','smoke-result.json')).write_text(
        json.dumps(dict(ok=True,version=V1_FULL_VERSION)),encoding='utf-8')
