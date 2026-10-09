#!/usr/bin/env python3
"""Checks the end-to-end runs pulled from the emulator: per run, the funnel from the app's own log and decision CSV,
every ZIP (strictly below 30 MB, opens, CRC-valid, images decode, manifest rows match images, traceable SKU + URL +
sha256 that matches the file), and prints one log-only contact sheet of the accepted images."""
import base64, csv, glob, hashlib, io, os, sys, zipfile
import cv2, numpy as np

out = sys.argv[1]; ok = True; sheet = []
for run in sorted(os.listdir(out)):
    d = os.path.join(out, run)
    dec = list(csv.DictReader(open(os.path.join(d, 'image_decisions.csv')))) if os.path.exists(os.path.join(d, 'image_decisions.csv')) else []
    acc = list(csv.DictReader(open(os.path.join(d, 'accepted.csv')))) if os.path.exists(os.path.join(d, 'accepted.csv')) else []
    from collections import Counter
    print(f"\n##### RUN {run}: image decisions {dict(Counter((r['stage'], r['outcome']) for r in dec))}; accepted rows {len(acc)}")
    for r in dec:
        if r['outcome'] != 'ACCEPTED': print('   ', r['outcome'], r['sku'], r['image_url'].split('/')[-1], '|', r['reason'])
    log = open(os.path.join(d, 'run_log.txt')).read() if os.path.exists(os.path.join(d, 'run_log.txt')) else ''
    if 'Export summary' not in log and 'ZIP export: nothing to export' not in log:
        ok = False; print('  INCOMPLETE: the run did not reach the ZIP export within the polling limit')
    zips = sorted(glob.glob(os.path.join(d, 'zips', '*.zip')))
    print(f"  ZIPs: {len(zips)}")
    nimg = 0
    for z in zips:
        size = os.path.getsize(z); good = size < 30 * 1024 * 1024
        with zipfile.ZipFile(z) as zf:
            bad = zf.testzip(); names = zf.namelist(); imgs = [n for n in names if n.startswith('images/')]
            H = 'reference,sku,title,product_url,image_url,file,sha256,width,height,acquisition,dial_radius_px,axis_ratio,ring_support,sharpness'.split(',')
            txt = zf.read('manifest.csv').decode() if 'manifest.csv' in names else ''
            # 1.4 wrote per-reference manifests without a header row (fixed in 1.4.1)
            man = list(csv.DictReader(io.StringIO(txt))) if txt.startswith('reference,') else [dict(zip(H, r)) for r in csv.reader(io.StringIO(txt))]
            decoded = 0; traced = 0
            for n in imgs:
                b = zf.read(n); im = cv2.imdecode(np.frombuffer(b, np.uint8), cv2.IMREAD_COLOR)
                decoded += im is not None
                row = next((m for m in man if m['file'] == n.split('/')[-1]), None)
                if row and row['sku'] and row['product_url'].startswith('https://www.bobswatches.com/') and row['sha256'] == hashlib.sha256(b).hexdigest(): traced += 1
                if im is not None and len(sheet) < 30:
                    s = 200 / max(im.shape[:2]); t = np.full((230, 200, 3), 255, np.uint8); im2 = cv2.resize(im, (int(im.shape[1] * s), int(im.shape[0] * s)))
                    t[:im2.shape[0], :im2.shape[1]] = im2; cv2.putText(t, (row or {}).get('sku', '?') + ' ' + (row or {}).get('reference', ''), (2, 225), cv2.FONT_HERSHEY_SIMPLEX, .4, (0, 0, 255), 1); sheet.append(t)
            nimg += len(imgs)
            fine = good and bad is None and decoded == len(imgs) and traced == len(imgs) and len(imgs) > 0
            ok &= fine
            print(f"   {'OK ' if fine else 'BAD'} {os.path.basename(z)}: {size/1048576:.2f} MB, {len(imgs)} images, decoded {decoded}, traceable (SKU+URL+sha256) {traced}, crc {'ok' if bad is None else bad}")
    files = glob.glob(os.path.join(d, 'files', 'accepted_images', '*', '*.jp*g'))
    print(f"  accepted image files on the device: {len(files)}; images inside ZIPs: {nimg}")
    if len(files) != nimg: ok = False; print('  MISMATCH between saved images and ZIP contents')
if sheet:
    while len(sheet) % 6: sheet.append(np.full((230, 200, 3), 255, np.uint8))
    img = np.vstack([np.hstack(sheet[i:i + 6]) for i in range(0, len(sheet), 6)])
    b = cv2.imencode('.jpg', img, [cv2.IMWRITE_JPEG_QUALITY, 70])[1]
    print('=== ACCEPTED CONTACT SHEET BASE64 JPEG BEGIN'); print(base64.b64encode(b.tobytes()).decode()); print('=== END')
print('\nALL ZIP CHECKS PASSED' if ok else '\nSOME CHECKS FAILED'); sys.exit(0 if ok else 1)
