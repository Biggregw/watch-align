"""The documented face-on-v3 validation counts are reproduced from the committed evidence (numbers only)."""
import json
from collections import Counter
from pathlib import Path

from tools.image_archive.analyse_validation import decide

ROOT = Path(__file__).resolve().parents[3]


def records():
    return [json.loads(l) for l in (ROOT / 'docs/image-archive/face-on-evidence.jsonl').open()]


def test_evidence_holds_numbers_and_labels_only():
    for e in records():
        assert 'path' not in e
        assert len(e['sha256']) == 64


def test_every_reviewer_ineligible_photo_with_bytes_is_rejected_and_no_reviewed_oblique_photo_is_admitted():
    for e in records():
        if not e['set'].startswith('reviewed') or e.get('error'):
            continue
        status = decide(e)[0]
        if e['eligible'] == 'no' or e['near_frontal'] == 'no':
            assert status == 'fail', e['photo_id']


def test_documented_counts():
    E = records()
    elig = Counter(decide(e)[0] for e in E if e['set'].startswith('reviewed') and e['eligible'] == 'yes' and not e.get('error'))
    assert elig == Counter({'pass': 204, 'fail': 63, 'inconclusive': 6})
    hist = {}
    for e in E:
        if 'historical' in e['set'] or e['set'] == 'committed':
            hist.setdefault(e['sha256'], e)
    assert len(hist) == 384
    assert Counter(decide(e)[0] for e in hist.values()) == Counter({'pass': 56, 'fail': 118, 'inconclusive': 210})
