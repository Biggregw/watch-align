import cv2
import numpy as np
from fastapi.testclient import TestClient

import v1_full
import v1_official_sources
import v1_ux_v120 as ux


def _png(width=900, height=900):
    img = np.zeros((height, width, 3), dtype=np.uint8)
    cv2.circle(img, (width // 2, height // 2), min(width, height) // 3, (220, 220, 220), 8)
    cv2.circle(img, (width // 2, height // 2), min(width, height) // 4, (80, 80, 80), -1)
    ok, encoded = cv2.imencode('.png', img)
    assert ok
    return encoded.tobytes()


def _runtime():
    import main_v1
    return main_v1


def _client():
    return TestClient(_runtime().app)


def test_runtime_version_and_core_pages_are_consistent():
    with _client() as c:
        home = c.get('/v1')
        assert home.status_code == 200
        assert 'V1 1.2.1' in home.text
        assert 'dashed oval' not in home.text.lower()
        js = c.get('/static/v1-full.js')
        assert js.status_code == 200
        assert 'waSyncOverlayLayer' in js.text
        assert "result:r" not in js.text
        assert c.get('/v1/references').status_code == 200
        models = c.get('/api/v1/models/full')
        assert models.status_code == 200
        body = models.json()
        assert body['version'] == v1_full.V1_FULL_VERSION == '1.2.1'
        assert {m['reference'] for m in body['models']} == {'126710BLNR', '124060'}


def test_preflight_accepts_image_and_rejects_non_image():
    with _client() as c:
        good = c.post('/api/v1/ux/preflight', files={'image': ('watch.png', _png(), 'image/png')})
        assert good.status_code == 200
        g = good.json()
        assert 'resolution' in g and 'sharpness' in g and 'perspective_suitability' in g
        bad = c.post('/api/v1/ux/preflight', files={'image': ('bad.txt', b'not an image', 'text/plain')})
        assert bad.status_code == 422


def test_reference_library_validates_models_and_uploads():
    with _client() as c:
        assert c.get('/api/v1/references/NOT-A-MODEL').status_code == 404
        bad = c.post('/api/v1/references/124060', files={'image': ('bad.bin', b'bad', 'application/octet-stream')})
        assert bad.status_code == 422
        good = c.post(
            '/api/v1/references/124060',
            files={'image': ('sub.png', _png(), 'image/png')},
            data={'source': 'functional audit', 'trusted': 'true'},
        )
        assert good.status_code == 200
        meta = good.json()
        assert meta['trusted'] is True
        listed = c.get('/api/v1/references/124060').json()['references']
        assert any(x['filename'] == meta['filename'] for x in listed)
        preview = c.get(f"/api/v1/ux/reference-image/124060/{meta['filename']}")
        assert preview.status_code == 200


def test_every_supported_model_has_official_source_manifest():
    assert set(v1_full.MODEL_GEOMETRY).issubset(set(v1_official_sources.OFFICIAL_SOURCES))
    for ref in v1_full.MODEL_GEOMETRY:
        sources = v1_official_sources.OFFICIAL_SOURCES[ref]
        assert sources
        assert all(s.get('page', '').startswith('https://www.rolex.com/') for s in sources)
        assert all(s.get('brochure', '').startswith('https://assets.rolex.com/') for s in sources)


def test_reference_selector_is_actually_bound_to_analyse_endpoint():
    route = next(r for r in _runtime().app.routes if getattr(r, 'path', None) == '/api/v1/analyse')
    names = {p.name for p in route.dependant.body_params}
    assert 'preferred_reference' in names


def test_reference_status_and_official_source_endpoints_cover_all_models():
    with _client() as c:
        for ref in ('126710BLNR', '124060'):
            status = c.get(f'/api/v1/ux/reference-status/{ref}')
            assert status.status_code == 200
            assert status.json()['model_ref'] == ref
            manifest = c.get(f'/api/v1/official-sources/{ref}')
            assert manifest.status_code == 200
            assert manifest.json()['sources']
        assert c.get('/api/v1/ux/reference-status/UNKNOWN').status_code == 404


def test_reference_matching_accounts_for_perspective_not_just_framing():
    class Backend:
        @staticmethod
        def detect_watch_circle(_img):
            return (450.0, 450.0, 250.0)

    candidate = np.zeros((900, 900, 3), dtype=np.uint8)
    front = candidate.copy()
    tilted = candidate.copy()
    cv2.circle(candidate, (450, 450), 250, (255, 255, 255), 5)
    cv2.circle(front, (450, 450), 250, (255, 255, 255), 5)
    cv2.ellipse(tilted, (450, 450), (250, 190), 0, 0, 360, (255, 255, 255), 5)
    assert ux._reference_score(Backend(), candidate, front) < ux._reference_score(Backend(), candidate, tilted)


def test_marker_disagreement_prevents_strong_consensus_claim():
    a = {'metrics': {'visual_alignment_confidence': 'high', 'markers': [{'hour': 12, 'reliable': True, 'angular_error_deg': 2.0}]}}
    b = {'metrics': {'visual_alignment_confidence': 'high', 'markers': [{'hour': 12, 'reliable': True, 'angular_error_deg': -2.0}]}}
    result = ux._consensus([a, b])
    assert result['level'] != 'high' or 'Strong match' not in result['summary']


def test_analyse_rejects_unknown_model_and_missing_candidate():
    with _client() as c:
        missing = c.post('/api/v1/analyse', data={'mode': 'qc', 'model_ref': '124060'})
        assert missing.status_code == 422
        unknown = c.post(
            '/api/v1/analyse',
            data={'mode': 'qc', 'model_ref': 'UNKNOWN'},
            files={'candidate': ('watch.png', _png(), 'image/png')},
        )
        assert unknown.status_code == 404


def test_report_route_rejects_unknown_session_cleanly():
    with _client() as c:
        response = c.get('/api/v1/report/definitely-not-a-session')
        assert response.status_code in (404, 422)
