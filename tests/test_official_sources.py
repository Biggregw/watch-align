from pathlib import Path

import cv2
import fitz
import numpy as np

import v1_official_sources as official


class Backend:
    def __init__(self, root: Path):
        self.PERSIST_DIR = root

    @staticmethod
    def resize_max(image):
        return image


def _make_pdf_with_image() -> bytes:
    image = np.full((900, 900, 3), 245, dtype=np.uint8)
    cv2.circle(image, (450, 450), 300, (40, 40, 40), 30)
    ok, encoded = cv2.imencode('.jpg', image)
    assert ok
    doc = fitz.open()
    page = doc.new_page(width=1000, height=1000)
    page.insert_image(fitz.Rect(50, 50, 950, 950), stream=encoded.tobytes())
    data = doc.tobytes()
    doc.close()
    return data


def test_sync_uses_official_brochure_when_product_page_has_no_asset_urls(tmp_path, monkeypatch):
    pdf = _make_pdf_with_image()

    def fake_fetch(url: str, timeout: int = 20) -> bytes:
        if url.endswith('.pdf'):
            return pdf
        return b'<html><body>JavaScript shell without direct image URLs</body></html>'

    monkeypatch.setattr(official, '_fetch', fake_fetch)
    backend = Backend(tmp_path)
    result = official.sync_official_references(backend, '126710BLNR')

    assert result['count'] >= 2
    cached, label = official._choose_cached_official(backend, '126710BLNR')
    assert cached is not None
    assert 'official Rolex source' in label
    assert 'official-brochure' in label


def test_batgirl_manifest_has_exact_model_brochures():
    sources = official.OFFICIAL_SOURCES['126710BLNR']
    assert {s['model_code'] for s in sources} == {'m126710blnr-0002', 'm126710blnr-0003'}
    assert all(s['brochure'].startswith('https://assets.rolex.com/api/brochure/') for s in sources)
