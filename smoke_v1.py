"""Exercise bundled routes and confidence gating without opening a tray/browser."""
import json
import os
from pathlib import Path


def run(backend):
    from fastapi.testclient import TestClient
    from v1_full import gate_measurements, V1_FULL_VERSION
    with TestClient(backend.app) as client:
        home = client.get('/v1')
        assert home.status_code == 200 and 'What do you want to check?' in home.text and 'V1 1.2.2' in home.text
        script=client.get('/static/v1-full.js')
        assert script.status_code == 200 and 'reference_consensus' in script.text and 'Recent comparisons' in home.text
        assert 'dashed oval' not in home.text
        assert '/api/v1/ux/reference-image/' in script.text
        models = client.get('/api/v1/models/full').json()
        assert models['version']==V1_FULL_VERSION == '1.2.2'
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
