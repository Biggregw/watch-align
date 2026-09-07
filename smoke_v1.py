"""Exercise bundled routes and confidence gating without opening a tray/browser."""
import json
import os
from pathlib import Path


def run(backend):
    from fastapi.testclient import TestClient
    from v1_full import gate_measurements, V1_FULL_VERSION
    with TestClient(backend.app) as client:
        assert client.get('/v1').status_code == 200
        script=client.get('/static/v1-full.js')
        assert script.status_code == 200 and 'measurementWarning' in script.text
        assert client.get('/api/v1/models/full').json()['version']==V1_FULL_VERSION
        assert client.get('/v1/references').status_code==200
    metrics=dict(overall_confidence='low',region_confidence={},perspective={},
                 bezel=dict(available=True,confidence='high',offset_deg=.948))
    gate_measurements(metrics,'gen')
    assert metrics['bezel']['offset_deg'] is None
    assert metrics['measurement_warning']
    Path(os.environ.get('WATCH_ALIGN_SMOKE_RESULT','smoke-result.json')).write_text(
        json.dumps(dict(ok=True,version=V1_FULL_VERSION)),encoding='utf-8')
