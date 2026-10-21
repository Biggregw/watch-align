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
    from tools.image_archive.analyse_validation import unique
    E = unique(records())
    assert sum(decide(e)[0] == 'pass' for e in E) == 15
    assert Counter(e['model'] for e in E if decide(e)[0] == 'pass') == Counter({'submariner_126610': 9, 'submariner_124060': 6})
    bad = [e for e in E if (e.get('eligible') == 'no' or e.get('near_frontal') == 'no') and not e.get('error')]
    assert len(bad) == 35 and all(decide(e)[0] == 'fail' for e in bad)
    pilot = [e for e in records() if e['set'] == 'pilot']
    assert sum(1 for e in pilot if not e.get('error')) == 56 and not any(decide(e)[0] == 'pass' for e in pilot)


def test_the_dark_bar_false_accept_is_now_rejected_by_outline_coverage():
    from tools.image_archive.faceon import COVER_FAIL
    # 17484cbe61baaa1a hand_4 copy, CI run 39673334481: the bar left 51.6 % of the 4 marker's outline visible, above
    # the app's 50 % reconstruction bound and without a hand confirmation
    assert 0.516 < COVER_FAIL
