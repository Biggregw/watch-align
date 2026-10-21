"""Visual review pack of the photographs face-on-v3 admits (for the owner's approval).

    python -m tools.image_archive.review_pack EVIDENCE.jsonl OUT_DIR --items items.csv [--items ...] [--fixtures DIR]

Uses only original photographs whose bytes hash to the catalogued SHA-256 (each
file is re-hashed here). Writes, per Rolex model (app dial family), PDF contact
sheets of the admitted photographs (12 per page, aspect ratio preserved) and of
their dial close-ups (16 per page), plus a comparison set of borderline rejected
and inconclusive photographs, an index CSV that identifies every full-resolution
original (SHA-256, image URL, listing, watch), and a README. Output is for a
private CI artifact only: never commit it (third-party photographs).
"""
from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter, defaultdict
from hashlib import sha256
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

from .analyse_validation import decide, primary

PAGE = (3508, 2480)            # A4 landscape at 300 dpi
MARGIN, HEADER, LABEL = 70, 150, 150
NAMES = {'gmt_126710': 'GMT-Master II 126710', 'submariner_126610': 'Submariner Date 126610 family',
         'submariner_124060': 'Submariner No-Date 124060'}
RES = Path(__file__).resolve().parents[2] / 'tools/research/marker_audit/results'
MANIFESTS = ('date-verified.csv', 'gmt-verified.csv', 'nodate-verified.csv', 'checkpoint/date-expanded-verified.csv')


def font(size, bold=False):
    for name in (('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'),
                 '/usr/share/fonts/truetype/dejavu/' + ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf')):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def sources():
    """sha256 -> catalogue row (image URL, listing, watch) from the committed verified manifests."""
    out = {}
    for m in MANIFESTS:
        for r in csv.DictReader(open(RES / m)):
            out.setdefault(r['sha256'], r)
    return out


def locate(items, fixtures):
    """sha256 -> verified local path (re-hashed; a mismatching file is never used)."""
    paths, rejected = {}, 0
    cands = []
    for f in items:
        cands += [(r['sha256'], r['path']) for r in csv.DictReader(open(f)) if r.get('path')]
    if fixtures:
        cands += [(None, str(p)) for p in Path(fixtures).rglob('*') if p.is_file()]
    for want, p in cands:
        try:
            got = sha256(Path(p).read_bytes()).hexdigest()
        except OSError:
            continue
        if want and got != want:
            rejected += 1
            continue
        paths.setdefault(got, p)
    return paths, rejected


def load(path):
    with Image.open(path) as im:
        return ImageOps.exif_transpose(im).convert('RGB')


def dial_crop(img, e):
    """Square crop around the registered dial (1.15 R), from the evidence's centre and scale."""
    try:
        k = img.size[0] / float(e['work_w'])
        cx, cy, r = e['centre_x'] * k, e['centre_y'] * k, e['scale_px'] * k * 1.15
    except (KeyError, TypeError, ZeroDivisionError):
        return img
    return img.crop((int(cx - r), int(cy - r), int(cx + r), int(cy + r)))


def label_lines(e, ref, cell_id):
    tilt = e.get('tilt_deg')
    rpx = e.get('orig_rpx') or e.get('rpx')
    first = '%s  ·  %s' % (cell_id, e['photo_id'])
    second = '%s  ·  tilt %s  ·  dial radius %s px (%s px across)' % (
        ref, '%.1f°' % tilt if isinstance(tilt, (int, float)) else 'n/a',
        '%.0f' % rpx if rpx else 'n/a', '%.0f' % (2 * rpx) if rpx else 'n/a')
    return first, second


def sheets(entries, title, out_pdf, cols, rows, crop=False):
    """entries: [(evidence, image path, reference, cell id, note)] -> multi-page PDF (+ PNG pages)."""
    per = cols * rows
    pages = []
    cw = (PAGE[0] - 2 * MARGIN) // cols
    ch = (PAGE[1] - 2 * MARGIN - HEADER) // rows
    f_title, f_lab, f_small = font(54, True), font(30, True), font(26)
    for start in range(0, len(entries), per):
        page = Image.new('RGB', PAGE, 'white')
        d = ImageDraw.Draw(page)
        n_pages = (len(entries) + per - 1) // per
        d.text((MARGIN, MARGIN), '%s  —  page %d of %d' % (title, start // per + 1, n_pages), fill='black', font=f_title)
        for i, (e, path, ref, cell, note) in enumerate(entries[start:start + per]):
            x0 = MARGIN + (i % cols) * cw
            y0 = MARGIN + HEADER + (i // cols) * ch
            img = load(path)
            if crop:
                img = dial_crop(img, e)
            box = (cw - 30, ch - LABEL - 20)
            img.thumbnail(box, Image.Resampling.LANCZOS)        # aspect ratio preserved
            page.paste(img, (x0 + (cw - img.size[0]) // 2, y0 + (box[1] - img.size[1]) // 2))
            d.rectangle([x0 + 5, y0 - 5, x0 + cw - 10, y0 + ch - 15], outline=(190, 190, 190), width=2)
            a, b = label_lines(e, ref, cell)
            ty = y0 + box[1] + 8
            d.text((x0 + 15, ty), a, fill='black', font=f_lab)
            d.text((x0 + 15, ty + 38), b, fill=(40, 40, 40), font=f_small)
            if note:
                d.text((x0 + 15, ty + 72), note[:95], fill=(170, 30, 30), font=f_small)
        pages.append(page)
    if pages:
        pages[0].save(out_pdf, save_all=True, append_images=pages[1:], resolution=300)
    return len(pages)


BORDERLINE = (  # (primary reason, how many per model, what it shows)
    ('fail_face_on_ellipse', 6, 'tilt >= 13.29 deg: genuine readings fall outside the strict limits'),
    ('inconclusive_face_on_ellipse', 4, 'tilt between the validated 10.60 and the failure 13.29 deg'),
    ('inconclusive_face_on_rehaut_vertical', 3, 'perspective gradient outside the tested envelope'),
    ('inconclusive_face_on_rehaut_horizontal', 3, 'perspective gradient outside the tested envelope'),
    ('inconclusive_exposure', 2, 'exposure outside the tested envelope'),
    ('inconclusive_sharp_boundaries', 3, 'sharpness below the tested envelope'),
    ('fail_marker', 8, 'a marker obstructed (app hand check, < 80 % outline) or unreliable (rough edge)'),
    ('inconclusive_marker', 3, 'a marker with 80-90 % of its outline visible (untested band)'),
    ('fail_original_resolution', 4, 'dial below the archive 250 px radius floor'),
    ('inconclusive_dial_not_registered', 4, 'no readable dial found by the production analysis'),
)


def note_for(e, reasons):
    tags = sorted({re.sub(r'^(fail|inconclusive)_', '', re.sub(r'_marker_(\d+)', r' marker \1', x))
                   for x in reasons if not x.startswith('inconclusive_marker_')})
    hidden = e.get('partly_hidden') or []
    withheld = list((e.get('withheld') or {}).keys())
    extra = (' · hidden/withheld markers %s' % sorted(set(map(int, hidden + withheld)))) if hidden or withheld else ''
    return ', '.join(tags)[:70] + extra


def run(evidence, out, items, fixtures):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    best = {}
    for line in open(evidence):
        e = json.loads(line)
        # one record per photograph: the one with evidence beats an entry whose bytes were not fetched
        if e['sha256'] not in best or (best[e['sha256']].get('error') and not e.get('error')):
            best[e['sha256']] = e
    E = list(best.values())
    for e in E:
        e['_status'], e['_reasons'] = decide(e)
        e['_primary'] = primary(e['_status'], e['_reasons'])
    paths, mismatched = locate(items, fixtures)
    src = sources()
    admitted = [e for e in E if e['_status'] == 'pass']
    index, missing = [], []
    summary = Counter()

    def ref(e):
        return (src.get(e['sha256']) or {}).get('model') or e.get('family') or e.get('model')

    by_model = defaultdict(list)
    for e in admitted:
        (by_model[e['model']] if e['sha256'] in paths else missing).append(e)
    for model, es in sorted(by_model.items()):
        es.sort(key=lambda e: (ref(e), e['photo_id']))
        tag = {'gmt_126710': 'G', 'submariner_126610': 'D', 'submariner_124060': 'N'}[model]
        entries = []
        for i, e in enumerate(es, 1):
            cell = '%s%03d' % (tag, i)
            entries.append((e, paths[e['sha256']], ref(e), cell, ''))
            s = src.get(e['sha256']) or {}
            index.append({'cell': cell, 'decision': 'pass', 'photo_id': e['photo_id'], 'sha256': e['sha256'],
                          'reference': ref(e), 'app_model': model, 'tilt_deg': e.get('tilt_deg'),
                          'dial_radius_px': e.get('orig_rpx') or e.get('rpx'), 'width': e.get('orig_w'),
                          'height': e.get('orig_h'), 'physical_watch_id': s.get('physical_watch_id') or e.get('physical_watch_id', ''),
                          'source': s.get('source', ''), 'image_url': s.get('image_url', ''), 'listing_url': s.get('listing_url', ''),
                          'origin': e['set'], 'reasons': ''})
        name = re.sub(r'[^A-Za-z0-9]+', '_', NAMES[model])
        summary[model] = len(es)
        sheets(entries, 'ACCEPTED %s (%d photographs)' % (NAMES[model], len(es)), out / ('accepted_%s.pdf' % name), 4, 3)
        sheets(entries, 'ACCEPTED %s - dial close-ups' % NAMES[model], out / ('accepted_%s_dials.pdf' % name), 4, 4, crop=True)
    # comparison set: borderline rejected / inconclusive, deterministic sample per model and reason
    comp = []
    for reason, n, why in BORDERLINE:
        for model in sorted(by_model):
            pool = sorted((e for e in E if e['_primary'] == reason and e.get('model') == model and e['sha256'] in paths),
                          key=lambda e: e['photo_id'])
            comp += [(e, why) for e in pool[:n]]
    entries = []
    for i, (e, why) in enumerate(comp, 1):
        cell = 'X%03d' % i
        entries.append((e, paths[e['sha256']], ref(e), cell, e['_status'].upper() + ': ' + note_for(e, e['_reasons'])))
        s = src.get(e['sha256']) or {}
        index.append({'cell': cell, 'decision': e['_status'], 'photo_id': e['photo_id'], 'sha256': e['sha256'],
                      'reference': ref(e), 'app_model': e.get('model'), 'tilt_deg': e.get('tilt_deg'),
                      'dial_radius_px': e.get('orig_rpx') or e.get('rpx'), 'width': e.get('orig_w'), 'height': e.get('orig_h'),
                      'physical_watch_id': s.get('physical_watch_id') or e.get('physical_watch_id', ''), 'source': s.get('source', ''),
                      'image_url': s.get('image_url', ''), 'listing_url': s.get('listing_url', ''), 'origin': e['set'],
                      'reasons': '; '.join(e['_reasons'])})
    sheets(entries, 'COMPARISON: borderline rejected and inconclusive (%d)' % len(entries), out / 'comparison_rejected_inconclusive.pdf', 4, 3)
    with open(out / 'index.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(index[0]))
        w.writeheader()
        w.writerows(index)
    (out / 'README.txt').write_text(
        'Watch Align image archive - visual review pack (profile face-on-v3)\n\n'
        'accepted_*.pdf        every photograph face-on-v3 admits, by model, 12 per page (whole photo, aspect kept)\n'
        'accepted_*_dials.pdf  the same photographs, dial close-ups, 16 per page\n'
        'comparison_*.pdf      a sample of borderline rejected / inconclusive photographs, with the reason\n'
        'index.csv             cell id -> photo id, full SHA-256, reference, tilt, dial radius, image size,\n'
        '                      watch, image URL and listing: identifies each full-resolution original\n\n'
        'Labels: cell id, photo id; reference, perspective tilt of the dial (deg) and dial radius in original pixels.\n'
        'Every photograph is an exact original (re-hashed against its catalogued SHA-256 before use).\n'
        'Admitted: %d unique; shown: %s; not shown (original bytes not available on this runner): %d.\n'
        'Files rejected because their bytes did not match the catalogue: %d.\n'
        'Third-party photographs: keep private, do not commit or redistribute.\n'
        % (len(admitted), dict(summary), len(missing), mismatched))
    print('REVIEW admitted %d; on sheets %s; missing bytes %d %s; comparison %d; sha mismatches %d' % (
        len(admitted), dict(summary), len(missing), [e['photo_id'] for e in missing][:20], len(entries), mismatched))
    return index


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    ap.add_argument('out')
    ap.add_argument('--items', action='append', default=[])
    ap.add_argument('--fixtures')
    a = ap.parse_args()
    run(a.evidence, a.out, a.items, a.fixtures)
