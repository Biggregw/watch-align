from copy import deepcopy
import json

import pytest
import numpy as np

from v1_full import gate_measurements, overall_confidence, _report_image, model_info


def measurements():
    return dict(overall_confidence='high',
        region_confidence=dict(markers='high', dial='high', bezel='high', perspective='high', **{'date/cyclops':'high'}),
        perspective=dict(candidate=dict(available=True, tilt_deg=2, confidence='high'), reference=dict(available=True, tilt_deg=2, confidence='high'), mismatch_deg=0),
        markers=[dict(hour=12, available=True, confidence='high', angular_error_deg=.948, radial_error_percent=1.23)],
        marker_summary=dict(available=True, overall_rotation_deg=.948),
        bezel=dict(available=True, confidence='high', offset_deg=.948),
        date_window=dict(available=True, confidence='high', x_offset_percent=1.23, y_offset_percent=2.34, bbox=[1,2,3,4]))


@pytest.mark.parametrize('confidence', ['low', None, 'unknown'])
def test_low_overall_suppresses_every_measurement(confidence):
    m=measurements(); m['overall_confidence']=confidence
    gate_measurements(m,'gen')
    assert m['bezel']['offset_deg'] is None
    assert m['markers'][0]['angular_error_deg'] is None
    assert m['date_window']['x_offset_percent'] is None
    assert m['marker_summary']['overall_rotation_deg'] is None
    assert 'unreliable' in m['measurement_warning']
    assert '.948' not in json.dumps(m)
    assert _report_image(np.zeros((720,720,3),dtype=np.uint8),m,model_info('126710BLNR')).shape == (720,1080,3)


@pytest.mark.parametrize('region,item,field', [('markers','markers','angular_error_deg'),('bezel','bezel','offset_deg'),('date/cyclops','date_window','x_offset_percent')])
def test_region_gate_preserves_other_reliable_regions(region,item,field):
    m=measurements(); m['region_confidence'][region]='low'
    gate_measurements(m,'gen')
    value=m[item][0] if item=='markers' else m[item]
    assert value[field] is None and value['reliable'] is False
    assert m['overall_confidence']=='high'
    if item!='bezel': assert m['bezel']['offset_deg']==.948


@pytest.mark.parametrize('available,confidence', [(False,'high'),(True,'low'),(True,None)])
def test_individual_feature_gate(available,confidence):
    m=measurements(); m['markers'][0].update(available=available,confidence=confidence)
    gate_measurements(m,'gen')
    assert m['markers'][0]['angular_error_deg'] is None


@pytest.mark.parametrize('tilt,mismatch', [(22.46,8.64),(2,6),(18,0)])
def test_perspective_hard_gate_even_with_high_estimate_confidence(tilt,mismatch):
    m=measurements(); m['perspective']['candidate']['tilt_deg']=tilt; m['perspective']['mismatch_deg']=mismatch
    gate_measurements(m,'gen')
    assert m['overall_confidence']=='low'
    assert m['bezel']['offset_deg'] is None


def test_high_and_medium_values_survive_and_no_date_supported():
    for confidence in ('high','medium'):
        m=measurements(); m['overall_confidence']=confidence; m['date_window']=None
        gate_measurements(m,'gen')
        assert m['measurement_warning'] is None
        assert m['bezel']['offset_deg']==.948
        assert m['markers'][0]['reliable']


def test_perspective_estimate_does_not_boost_geometry_confidence():
    assert overall_confidence(dict(dial='low',bezel='medium',perspective='high'),None)=='low'


def test_low_alignment_confidence_gates_gen():
    m=measurements(); m['base_alignment']={'confidence':'low'}
    gate_measurements(m,'gen')
    assert m['bezel']['offset_deg'] is None


def test_api_gates_before_report_and_saved_json(tmp_path,monkeypatch):
    from types import SimpleNamespace
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    import v1_full as full
    candidate=np.zeros((720,720,3),dtype=np.uint8)
    backend=SimpleNamespace(app=FastAPI(),STATIC_DIR=tmp_path,SESSIONS_DIR=tmp_path,BASE_DIR=tmp_path,
        resize_max=lambda x:x,read_upload=lambda x:candidate,detect_refined_circle_full=lambda x:(360,360,200),
        auto_align=lambda a,b:(np.eye(2,3),{'confidence':'high'}),RenderRequest=lambda **kw:kw,
        render_assets=lambda *args:{'urls':{}})
    m=measurements()
    monkeypatch.setattr(full,'marker_measurements',lambda *args:(deepcopy(m['markers']),{'count':12,'available':True}))
    monkeypatch.setattr(full,'bezel_top_measurement',lambda *args:deepcopy(m['bezel']))
    monkeypatch.setattr(full,'date_window_measurement',lambda *args:deepcopy(m['date_window']))
    perspectives=iter([dict(available=True,tilt_deg=22.46,confidence='high'),dict(available=True,tilt_deg=13.82,confidence='high')])
    full.install_full(backend,lambda *args:next(perspectives))
    result=TestClient(backend.app).post('/api/v1/analyse',data={'mode':'gen','model_ref':'126710BLNR'},files={'candidate':('qc.png',b'a'),'reference':('gen.png',b'b')})
    assert result.status_code==200, result.text
    body=result.json(); metrics=body['metrics']
    assert metrics['overall_confidence']=='low'
    assert metrics['perspective']['mismatch_deg']==8.64
    assert metrics['bezel']['offset_deg'] is None
    assert 'incompatible perspective' in metrics['measurement_warning']
    saved=json.loads((tmp_path/body['session_id']/'v1_metrics.json').read_text())
    assert saved==metrics
    assert (tmp_path/body['session_id']/'v1_report.png').is_file()
