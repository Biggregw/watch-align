"""Build the owner's visual QC review pack: one self-contained HTML file for a phone (research only).

    python -m tools.research.genuine_calibration.review_pack --photos DIR --records REC.jsonl [...] --compare CMP.txt [...]
                                                              out.html

The pack is NEVER committed or published: it embeds private and third-party photographs, and is handed to the owner
directly. This module holds only the case definitions and the layout.

For each case it shows the photograph(s) first, then plain enlarged crops of each disputed marker (close and in context,
cut from the original pixels, no overlay), then a verdict control per marker (1 visually unacceptable manufacturing
defect / 2 acceptable imperfection / 3 uncertain, photograph insufficient). Nothing is pre-labelled. The measurements
and both engines' findings sit in a collapsed section to open after judging. The verdicts can be copied as text.
"""
from __future__ import annotations

import argparse
import base64
import html
import io
import json
import re
from pathlib import Path

import cv2
import numpy as np

# photo kinds: how a photograph relates to the others of the same watch
SAME_SITTING = 'separate exposure, same sitting (same camera position and light; second hand moved): not a re-encoding, not fully independent'
OTHER_VIEW = 'independent viewpoint (steep close-up from another angle; no dial registration, so no crops)'
ONLY = 'only photograph of this watch available'

CASES = [
    {'id': 'A', 'title': 'Submariner 124060 replica, QC thread 1w848xb (reviewer accepted the 12)', 'layout': 'submariner_124060',
     'markers': [12, 2, 11], 'private': False,
     'why': 'The two engines disagree here: the existing app passes these markers, the new engine flags them in both photographs.',
     'photos': [('SUB_accepted12_1w848xb', 'reddit/submariner_124060/SUB_accepted12_1w848xb.jpg', SAME_SITTING),
                ('SUB_accepted12_1w848xb_photo5', 'reddit/submariner_124060/SUB_accepted12_1w848xb_photo5.jpg', SAME_SITTING)]},
    {'id': 'B', 'title': 'Submariner 124060 replica (Clean factory), RepTimeQC 1lnn2kz', 'layout': 'submariner_124060',
     'markers': [6, 9], 'private': False,
     'why': 'The engines disagree: the new engine flags the 6 and 9 in all three straight-on photographs, the existing app does not.',
     'photos': [('a699939a5839d8b0', 'clean_1lnn2kz/a699939a5839d8b09c13d988aa30a9729451e3baddc8ba2b65cabcb1fe780646', SAME_SITTING),
                ('531bf26df63f8e28', 'clean_1lnn2kz/531bf26df63f8e2821d555abb5aca9efaeb2390f318cf954acbf5411e7da0e66', SAME_SITTING),
                ('7924b3438d027820', 'clean_1lnn2kz/7924b3438d027820115a26dc8e4ce5178f50da034cbee53d5d1e1c721bca7c3b', SAME_SITTING),
                ('143fd10ba12988a1', 'clean_1lnn2kz/143fd10ba12988a1823584c5d748435f0c09c08a7df3c1a3d23625d3d0c780dd', OTHER_VIEW),
                ('53316f31797c69a7', 'clean_1lnn2kz/53316f31797c69a727283eeaf82ace9d7cbe066f4456bebd58d0c02951c48781', OTHER_VIEW)]},
    {'id': 'C', 'title': 'GMT-Master II 126710 BLRO replica (ARF), "reported crooked 6" - PRIVATE local control', 'layout': 'gmt_126710',
     'markers': [6, 8, 12, 4, 7, 9, 11], 'private': True,
     'why': 'A crooked 6 was reported. The existing app flags the 8 and 12; the new engine flags the 6 and also 4, 7, 8, 9, 11 and 12. '
            'The 6 is the claimed marker; the others are listed because an engine flagged them.',
     'photos': [('RL_ARF_BLRO_CROOKED6', 'local/RL_ARF_BLRO_CROOKED6.png', ONLY)]},
    {'id': 'D', 'title': 'GMT-Master II 126710 replica, QC thread 1s4cszq (reviewer rejected for a "crooked 6")', 'layout': 'gmt_126710',
     'markers': [6], 'private': False,
     'why': 'Claimed defect at the 6. The existing app flags it against the strict genuine standard; the new engine could not assess it.',
     'photos': [('GMT_crooked6_1s4cszq', 'reddit/gmt_126710/GMT_crooked6_1s4cszq.jpg', ONLY)]},
    {'id': 'E', 'title': 'Submariner 124060 replica, QC thread 1wgfy7k (reviewer rejected the 12)', 'layout': 'submariner_124060',
     'markers': [12], 'private': False,
     'why': 'Claimed defect at the 12. The existing app flags it; the new engine could not assess it.',
     'photos': [('SUB_rejected12_1wgfy7k', 'reddit/submariner_124060/SUB_rejected12_1wgfy7k.jpg', ONLY)]},
    {'id': 'F', 'title': 'GMT-Master II 126710 BLNR replica (VSF), note "6 left; 12 slightly tilted" - PRIVATE local control', 'layout': 'gmt_126710',
     'markers': [6, 12], 'private': True,
     'why': 'Claimed: the 6 to the left and the 12 slightly turned. The existing app flags the 6 as Clearly off; the new engine could not assess either.',
     'photos': [('RL_LOCAL_BLNR', 'local/RL_LOCAL_BLNR.png', ONLY)]},
    {'id': 'G', 'title': 'GMT-Master II 126710 BLNR replica, user-supplied phone photo "Batgirl" - PRIVATE (coverage control)', 'layout': 'gmt_126710',
     'markers': [9], 'private': True,
     'why': 'Coverage control: the 9 o\'clock baton. The existing app flags the 9 against the strict genuine standard; the new engine did not assess it '
            '(not eligible). No claim exists either way.',
     'photos': [('RL_USER_BATGIRL', 'local/USER_BATGIRL_GMT.jpg', ONLY)]},
]

VERDICTS = ('1 Visually unacceptable manufacturing defect', '2 Acceptable imperfection', '3 Uncertain / photograph insufficient')


def records(paths):
    out = {}
    for p in paths:
        for line in open(p):
            line = line.strip()
            if line:
                r = json.loads(line[6:] if line.startswith('QCREC ') else line)
                if r.get('photo_id'):
                    out[r['photo_id']] = r
    return out


def compares(paths):
    """photo_id -> {'result': existing result, hour/'ring': (existing, new, agreement)} from ResearchCompare output"""
    out = {}
    for p in paths:
        t = open(p, encoding='utf-8', errors='replace').read()
        for pid, body in re.findall(r'CMP-BEGIN (\S+)\n(.*?)CMP-END', t, re.S):
            d = {}
            first = body.strip().splitlines()[0] if body.strip() else ''
            d['result'] = first.replace('existing result: ', '')
            for item in re.split(r'\n(?=\d+ (?:round|baton|triangle)\n|whole ring\n)', body):
                lines = [x.strip() for x in item.strip().splitlines()]
                m = re.match(r'^(\d+) (round|baton|triangle)$', lines[0]) if lines else None
                key = int(m.group(1)) if m else ('ring' if lines and lines[0] == 'whole ring' else None)
                if key is not None and len(lines) >= 4:
                    d[key] = (lines[1].replace('existing: ', ''), lines[2].replace('new: ', ''), lines[3])
            out[pid] = d
    return out


def jpeg(img, max_side, q=82):
    h, w = img.shape[:2]
    k = min(1.0, max_side / max(h, w))
    if k < 1:
        img = cv2.resize(img, (int(w * k), int(h * k)), interpolation=cv2.INTER_AREA)
    ok, buf = cv2.imencode('.jpg', img, [cv2.IMWRITE_JPEG_QUALITY, q])
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.tobytes()).decode()


def analysis_scale(img):
    """original pixels per analysis pixel: the harness loads large photos reduced (Alpha96Calib.loadAlpha96: power-of-two
    reduction while the half-size is still >= 3200 px, then a resize to at most 3200 px)"""
    mx = max(img.shape[:2])
    s = 1
    while mx / (s * 2) >= 3200:
        s *= 2
    cur = -(-mx // s)
    k = 3200.0 / cur if cur > 3200 else 1.0
    return s / k


def marker_px(rec, hour, f=1.0):
    H = np.array(rec['H']).reshape(3, 3) if f == 1.0 else np.diag([f, f, 1.0]) @ np.array(rec['H']).reshape(3, 3)
    for m in rec.get('markers') or []:
        if int(m['h']) == hour and m.get('fix'):
            v = H @ np.array([m['fix'][0], m['fix'][1], 1.0])
            return v[0] / v[2], v[1] / v[2], float(np.hypot(H[0, 0], H[1, 0]))   # pixels per R
    return None


def crop(img, x, y, half, out_px):
    h, w = img.shape[:2]
    x0, y0, x1, y1 = int(round(x - half)), int(round(y - half)), int(round(x + half)), int(round(y + half))
    pad = cv2.copyMakeBorder(img, half, half, half, half, cv2.BORDER_CONSTANT, value=(30, 30, 30))
    c = pad[y0 + half:y1 + half, x0 + half:x1 + half]
    return cv2.resize(c, (out_px, out_px), interpolation=cv2.INTER_LANCZOS4 if out_px > c.shape[0] else cv2.INTER_AREA)


CSS = """
:root{--bg:#0d1420;--card:#16202f;--fg:#e9eef5;--muted:#9fb0c6;--acc:#3fd0ef;--line:#2a3a52;--warn:#ffbe3c}
:root:not([data-theme="light"]){color-scheme:dark}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 -apple-system,system-ui,Segoe UI,Roboto,sans-serif}
main{max-width:760px;margin:0 auto;padding:16px}
h1{font-size:22px;margin:4px 0 8px}h2{font-size:18px;margin:0 0 6px}h3{font-size:15px;margin:14px 0 6px;color:var(--acc)}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;margin:18px 0}
img{max-width:100%;height:auto;border-radius:8px;display:block}
.muted{color:var(--muted);font-size:13px}.priv{color:var(--warn);font-weight:600;font-size:13px}
.pair{display:flex;gap:8px;flex-wrap:wrap}.pair figure{flex:1 1 300px;margin:0}
figcaption{color:var(--muted);font-size:12px;margin:3px 0 10px}
fieldset{border:1px solid var(--line);border-radius:10px;margin:10px 0;padding:8px 10px}
legend{font-weight:600;padding:0 4px}
label.v{display:block;padding:9px 6px;border-radius:8px;margin:2px 0}
label.v:has(input:checked){background:#24405a}
input[type=radio]{transform:scale(1.25);margin-right:10px}
textarea{width:100%;box-sizing:border-box;background:#0b111b;color:var(--fg);border:1px solid var(--line);border-radius:8px;padding:8px;font:13px monospace}
details{margin-top:10px;border-top:1px dashed var(--line);padding-top:8px}summary{cursor:pointer;color:var(--acc)}
table{border-collapse:collapse;width:100%;font-size:12.5px}td,th{border-bottom:1px solid var(--line);padding:5px 4px;vertical-align:top;text-align:left}
button{background:var(--acc);color:#05202a;border:0;border-radius:8px;padding:10px 14px;font-weight:700;font-size:15px}
.toc a{color:var(--acc);display:block;padding:3px 0}
"""

JS = """
const KEY='watchalign-review-pack-v1';
function collect(){const out=[];document.querySelectorAll('fieldset[data-m]').forEach(f=>{const c=f.querySelector('input:checked');
 const n=f.querySelector('input[type=text]');out.push(f.dataset.m+': '+(c?c.value:'(not judged)')+(n&&n.value?' - '+n.value:''));});
 const t=document.getElementById('summary');t.value=out.join('\\n');try{const s={};document.querySelectorAll('input').forEach(i=>{if(i.type=='radio'&&i.checked)s[i.name]=i.value;if(i.type=='text')s[i.name]=i.value;});localStorage.setItem(KEY,JSON.stringify(s));}catch(e){}}
function restore(){try{const s=JSON.parse(localStorage.getItem(KEY)||'{}');document.querySelectorAll('input').forEach(i=>{if(i.type=='radio'&&s[i.name]===i.value)i.checked=true;if(i.type=='text'&&s[i.name])i.value=s[i.name];});}catch(e){}collect();}
function copyAll(){const t=document.getElementById('summary');t.select();try{navigator.clipboard.writeText(t.value);}catch(e){document.execCommand('copy');}}
document.addEventListener('change',collect);document.addEventListener('input',collect);window.addEventListener('load',restore);
"""


def build(photos_dir, recs, cmps):
    P = Path(photos_dir)
    E = html.escape
    parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
             '<title>QC Review Pack</title><style>%s</style></head><body><main>' % CSS,
             '<h1>Visual QC review pack</h1>',
             '<p>Seven watches, %d markers. For each marker, look at the photographs and crops first and choose one verdict. '
             'Nothing here is pre-labelled. The engines\' findings and the measurements are in a closed section under each case: open it '
             'only after judging, if at all. Your choices are summarised at the end to copy back.</p>' % sum(len(c['markers']) for c in CASES),
             '<p class="muted">Crops are cut from the original pixels and enlarged; there are no overlays. "Close" shows the marker alone, '
             '"context" shows it with its neighbours and the minute track. PRIVATE photographs are local/user-supplied: keep this file private.</p>',
             '<div class="card toc"><b>Cases</b>' + ''.join('<a href="#case%s">%s. %s</a>' % (c['id'], c['id'], E(c['title'])) for c in CASES) +
             '<a href="#verdicts">Your verdicts (copy)</a></div>']
    for c in CASES:
        parts.append('<section class="card" id="case%s"><h2>%s. %s</h2>' % (c['id'], c['id'], E(c['title'])))
        if c['private']:
            parts.append('<p class="priv">PRIVATE photograph: not published or committed.</p>')
        parts.append('<p><b>Markers needing your verdict:</b> %s</p>' % ', '.join('%d o\'clock' % h for h in c['markers']))
        imgs = {}
        for pid, rel, kind in c['photos']:
            img = cv2.imread(str(P / rel), cv2.IMREAD_COLOR)
            if img is None:
                parts.append('<p class="muted">%s: photograph not available locally.</p>' % E(pid))
                continue
            imgs[pid] = img
            r = recs.get(pid)
            rpx = r.get('rpx') if r and r.get('ok') else None
            parts.append('<h3>Photograph %s</h3><img src="%s" alt="%s"><figcaption>%s · %dx%d px%s<br>%s</figcaption>' % (
                E(pid), jpeg(img, 1400), E(pid), E(pid), img.shape[1], img.shape[0],
                ' · dial radius %.0f px' % rpx if rpx else ' · dial not registered', E(kind)))
        for h in c['markers']:
            parts.append('<h3>%d o\'clock</h3><div class="pair">' % h)
            shown = 0
            for pid, rel, kind in c['photos']:
                r, img = recs.get(pid), imgs.get(pid)
                if img is None or not r or not r.get('ok') or not r.get('H'):
                    continue
                loc = marker_px(r, h, analysis_scale(img))
                if not loc:
                    continue
                x, y, ppr = loc
                parts.append('<figure><img src="%s"><figcaption>%s · close</figcaption></figure>' % (
                    jpeg(crop(img, x, y, max(14, int(0.21 * ppr)), 360), 400, 88), E(pid)))
                parts.append('<figure><img src="%s"><figcaption>%s · context</figcaption></figure>' % (
                    jpeg(crop(img, x, y, max(30, int(0.42 * ppr)), 360), 400, 88), E(pid)))
                shown += 1
            if not shown:
                parts.append('<p class="muted">No registered straight-on photograph to crop from; judge from the full photograph(s) above.</p>')
            parts.append('</div>')
            name = 'v_%s_%d' % (c['id'], h)
            parts.append('<fieldset data-m="%s %d o\'clock"><legend>Your verdict: %s, %d o\'clock</legend>' % (c['id'], h, c['id'], h) +
                         ''.join('<label class="v"><input type="radio" name="%s" value="%s">%s</label>' % (name, E(v), E(v)) for v in VERDICTS) +
                         '<input type="text" name="n_%s" placeholder="optional note" style="width:100%%;margin-top:6px;padding:8px;border-radius:8px;border:1px solid #2a3a52;background:#0b111b;color:#e9eef5"></fieldset>' % name)
        # engine findings, closed
        rows = []
        for pid, rel, kind in c['photos']:
            d = cmps.get(pid)
            if not d:
                continue
            rows.append('<tr><th colspan="3">%s · existing app result: %s</th></tr>' % (E(pid), E(d.get('result', ''))))
            for h in c['markers'] + ['ring']:
                if h in d:
                    ex, new, agree = d[h]
                    rows.append('<tr><td>%s</td><td>existing: %s</td><td>new: %s<br><span class="muted">%s</span></td></tr>' % (
                        '%d o\'clock' % h if h != 'ring' else 'whole ring', E(ex), E(new), E(agree)))
        parts.append('<details><summary>Engine findings and measurements (open after judging)</summary><p class="muted">%s</p>'
                     '<table>%s</table></details></section>' % (E(c['why']), ''.join(rows) or '<tr><td>none recorded</td></tr>'))
    parts.append('<section class="card" id="verdicts"><h2>Your verdicts</h2><p class="muted">Filled in as you choose; copy and send back. '
                 'Saved in this browser if it allows.</p><textarea id="summary" rows="16" readonly></textarea><p><button onclick="copyAll()">Copy verdicts</button></p></section>')
    parts.append('<script>%s</script></main></body></html>' % JS)
    return '\n'.join(parts)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--photos', required=True)
    ap.add_argument('--records', nargs='+', required=True)
    ap.add_argument('--compare', nargs='+', required=True)
    ap.add_argument('out')
    a = ap.parse_args(argv)
    Path(a.out).write_text(build(a.photos, records(a.records), compares(a.compare)), encoding='utf-8')
    print('wrote', a.out, Path(a.out).stat().st_size // 1024, 'KB')


if __name__ == '__main__':
    main()
