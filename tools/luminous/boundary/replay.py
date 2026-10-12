"""Replay existing original/processing evidence, without collecting or calibrating.

Pass the extracted Actions original and calibration artifacts plus the exact Batgirl
original; Java 17 must be on PATH. Results remain outside the repository by default.
"""
import argparse
import csv
import gzip
import hashlib
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[3]
FAMILIES = ('gmt_126710', 'submariner_124060', 'submariner_126610')


def csv_rows(path):
    with Path(path).open() as f:
        return list(csv.DictReader(f))


def write(path, rows):
    with path.open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('originals', 'saved', 'work'):
        parser.add_argument('--'+name, required=True, type=Path)
    parser.add_argument('--batgirl', type=Path, help='Required for the full audit; optional for focused circle regressions')
    parser.add_argument('--focused', action='store_true', help='Only the two documented failures and processing copy')
    args = parser.parse_args(); work=args.work.resolve(); work.mkdir(parents=True, exist_ok=True)
    if not args.focused and args.batgirl is None:parser.error('--batgirl is required for a full audit')
    saved=args.saved.resolve()
    subprocess.run(['python', str(ROOT/'tools/luminous/prepare_curated.py'), str(args.originals.resolve()), str(work), str(ROOT)], cwd=ROOT, check=True, stdout=(work/'hash-verification.log').open('w'))
    paths={r['photo_id']:r['path'] for r in csv_rows(work/'verified_inputs.csv')}
    if args.batgirl:assert hashlib.sha256(args.batgirl.read_bytes()).hexdigest() == 'c50e0845ddb6c50701a4ba8ab6b396092e67988a747206f77743c02e7c7e54eb'
    variants=[]
    for r in csv_rows(saved/'processing_manifest.csv'):
        source=Path(paths[r['original_photo_id']]); im=Image.open(source).convert('RGB'); method=r['processing']
        path=source if method=='identical_original_repeat' else work/'processing'/(r['photo_id']+('.jpg' if method.startswith('jpeg') else '.png')); path.parent.mkdir(exist_ok=True)
        if method.startswith('jpeg'): im.save(path, quality=int(method[-2:]), subsampling=0)
        elif method.startswith('resize'): im.resize((round(im.width*5/6),round(im.height*5/6)),Image.Resampling.LANCZOS).save(path)
        elif method.startswith('gain'): ImageEnhance.Brightness(im).enhance(.98 if '098' in method else 1.02).save(path)
        assert hashlib.sha256(path.read_bytes()).hexdigest()==r['sha256'],r['photo_id']
        r['path']=str(path); variants.append(r)
    manifests=[]
    for family in FAMILIES:
        rows=csv_rows(saved/f'{family}_extract.csv')
        for r in rows:r['path']=paths[r['photo_id']]
        if args.focused:rows=[r for r in rows if r['photo_id'] in ('80d288dcc8689345','0114716639829db3','c58f78ef6c1cd5b7')]
        if rows:
            write(work/f'{family}_all.csv',rows); manifests.append((family,'_all'))
        rows=[{k:r[k] for k in ('photo_id','group_id','split','path')} for r in variants if r['family']==family]
        if args.focused:rows=[r for r in rows if r['photo_id']=='0114716639829db3__resize_5of6_png' or r['photo_id'].startswith('c58f78ef6c1cd5b7__')]
        if rows:
            write(work/f'{family}_processing.csv',rows); manifests.append((family,'_processing'))
    if args.batgirl:
        write(work/'gmt_126710_batgirl.csv',[dict(photo_id='batgirl_37828',group_id='owner_batgirl',split='replica_control',path=str(args.batgirl.resolve()))]); manifests.append(('gmt_126710','_batgirl'))
    # Baseline geometry only, never an uploaded original or a new genuine reference.
    frozen=ROOT/'docs/research/luminous-boundary-fix-2026-10-12/batgirl_frozen_geometry.jsonl.gz'
    (work/'gmt_126710_batgirl_baseline.jsonl').write_bytes(gzip.decompress(frozen.read_bytes()))
    subprocess.run(['bash','tools/desktop-harness/run.sh','LuminousSyntheticChecks'],cwd=ROOT,check=True)
    cp=str(ROOT/'tools/desktop-harness/.cache/opencv-4.9.0-0.jar')+':'+str(ROOT/'tools/desktop-harness/.cache/classes')
    for family,suffix in manifests:
        manifest=work/f'{family}{suffix}.csv'
        raw=work/f'{family}{suffix}_corrected.jsonl'; scored=work/f'{family}{suffix}_corrected_scored.jsonl'
        cmd=['java','-Xmx2g','-Dwatchalign.model='+family,'-cp',cp,'com.watchalign.mobile.LuminousCalibrationStudy']
        subprocess.run(cmd+['extract',str(manifest),str(raw)],cwd=ROOT,check=True)
        subprocess.run(cmd+['score',str(raw),str(saved/'calibrated_references.json'),str(scored)],cwd=ROOT,check=True)
    if args.focused:
        cases=[('submariner_124060_all','80d288dcc8689345',2,.074790572436),('submariner_126610_all','0114716639829db3',10,.075535140664),('submariner_126610_processing','0114716639829db3__resize_5of6_png',10,.075985415646)]
        for stem,id,h,r in cases:
            p=next(json.loads(line) for line in (work/f'{stem}_corrected_scored.jsonl').read_text().splitlines() if json.loads(line)['photo_id']==id)
            m=next(m for m in p['markers'] if m['hour']==h)
            assert m['fit']['ideal'] and abs(m['fit']['radius_R']-r)<1e-9
        for stem in ('submariner_124060_all','submariner_124060_processing'):
            for line in (work/f'{stem}_corrected_scored.jsonl').read_text().splitlines():
                p=json.loads(line)
                if not p['photo_id'].startswith('c58f78ef6c1cd5b7'):continue
                m=next(m for m in p['markers'] if m['hour']==4)
                assert m['new_fitted'] is None and m['new_alignment'] is None
        print('Three reviewed circle outputs and seven contradictory-boundary cases passed')
    else:
        subprocess.run(['python',str(Path(__file__).with_name('compare.py')),str(saved),str(work),str(work/'report')],cwd=ROOT,check=True)


if __name__=='__main__':main()
