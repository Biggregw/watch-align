#!/usr/bin/env python3
"""RESEARCH ONLY. Build the Alpha96 calibration manifests from evidence already in the repository.

Outputs (in this directory):
  catalogue_provenance_strong.csv  every provenance-strong genuine photo already catalogued by the dataset
                                   harvester (origin/data/harvest manifest.csv): established dealer, auction
                                   house, Rolex CPO; class gen; suitable=yes; same-layout black-dial 40 mm GMT
                                   references. local_path points at the harvester's image store
                                   (datasets/harvest/images/<sha>.jpg); the images themselves were never
                                   committed, so the runner reports them as missing unless they are restored.
  manifest_local.csv               photos actually present for this run: marketplace genuine candidates
                                   (descriptive only, never population) and replica regression controls.

No image is downloaded here.
"""
import csv
import io
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
STRONG = {'established_dealer', 'auction_house', 'rolex_cpo'}
# Same dial layout as the Alpha92 bare-dial master (black dial, date at 3, right-hand crown).
PRIMARY = {'126710BLNR', '126710BLRO', '126710GRNR'}
GOLD_SURROUND = {'126711CHNR', '126713GRNR', '126715CHNR', '126718GRNR'}
EXCLUDED = {'126720VTNR': 'left-hand crown: mirrored layout (date at 9)',
            '126729VTNR': 'left-hand crown: mirrored layout (date at 9)',
            '126719BLRO': 'white-gold reference with blue / meteorite dial variants'}
COLS = ['photo_id', 'local_path', 'class_label', 'group', 'physical_watch_id', 'model', 'layout_group',
        'provenance', 'source', 'split', 'sha256', 'image_url', 'notes']


def harvest_catalogue(out):
    raw = subprocess.check_output(['git', 'show', 'origin/data/harvest:manifest.csv'], cwd=HERE).decode()
    rows, excluded = [], {}
    for r in csv.DictReader(io.StringIO(raw)):
        if r['class_label'] != 'gen' or r['provenance'] not in STRONG or r['suitable'] != 'yes':
            continue
        m = r['model']
        if m in EXCLUDED or m not in PRIMARY | GOLD_SURROUND:
            excluded[m or '(blank)'] = excluded.get(m or '(blank)', 0) + 1
            continue
        rows.append(dict(photo_id=r['sha256'][:16], local_path=r['local_path'].replace('datasets/harvest/', '', 1),
                         class_label='gen', group='genuine_population', physical_watch_id=r['physical_watch_id'],
                         model=m, layout_group='primary_steel' if m in PRIMARY else 'gold_surround_variant',
                         provenance=r['provenance'], source=r['source'], split='', sha256=r['sha256'],
                         image_url=r['image_url'], notes=f"harvest pose={r['pose']} decision={r['dataset_decision']}"))
    with open(out, 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=COLS); w.writeheader(); w.writerows(rows)
    return rows, excluded


LOCAL = [
    # Marketplace seller-asserted genuine candidates (r/Watchexchange). Descriptive only; not population.
    ('GEN_CAND_WEX_BLRO_01', 'gen_candidate/EXT_EXT_GEN_BLRO_WEX_01.png', 'gen', 'gen_candidate', 'ext_gen_blro_wex_listing_A',
     '126710BLRO', 'marketplace gen_candidate (r/Watchexchange)', 'Alpha91 overlay control; listing id not recorded; WEX_01/02 assumed same watch (same listing, unverified)'),
    ('GEN_CAND_WEX_BLRO_02', 'gen_candidate/EXT_EXT_GEN_BLRO_WEX_02.png', 'gen', 'gen_candidate', 'ext_gen_blro_wex_listing_A',
     '126710BLRO', 'marketplace gen_candidate (r/Watchexchange)', 'see WEX_01'),
    ('GEN_CAND_HO_01', 'gen_candidate/POOL_GEN_HO_01.png', 'gen', 'gen_candidate', 'gen_wex_1TDYtpN',
     '126710BLNR', 'marketplace gen_candidate (r/Watchexchange)', 'Alpha90 pool GEN_HO_01'),
    ('GEN_CAND_HO_02', 'gen_candidate/POOL_GEN_HO_02.png', 'gen', 'gen_candidate', 'gen_wex_vmbUDwy',
     '126710BLNR', 'marketplace gen_candidate (r/Watchexchange)', 'Alpha90 pool GEN_HO_02'),
    # Replica regression controls: validation only, never used for genuine limits.
    ('RL_THEONE_BLNR', 'rl_controls/RL_THEONE_BLNR.png', 'rep', 'rl_control', 'rep_theonewatches_blnr_user', '126710BLNR',
     'rep (user-supplied, Theonewatches)', 'Alpha96 phone regression reference'),
    ('RL_USER_BATGIRL', 'rl_controls/USER_BATGIRL_GMT.jpg', 'rep', 'rl_control', 'rep_user_batgirl', '126710BLNR',
     'rep (user-supplied)', 'Alpha96 phone regression reference; chat-compressed copy (960x1280)'),
    ('RL_LOCAL_BLNR', 'rl_controls/RL_LOCAL_BLNR.png', 'rep', 'rl_control', 'rep_vsf_7s6PyXJ', '126710BLNR',
     'rep_labelled', 'Alpha90 pool RL_LOCAL_01: 6 left; 12 slightly tilted'),
    ('RL_ARF_BLRO_CROOKED6', 'rl_controls/RL_ARF_BLRO_CROOKED6.png', 'rep', 'rl_control', 'rep_arf_blro_crooked6', '126710BLRO',
     'rep_labelled', 'reported crooked 6'),
]


def local_manifest(out, sha_file):
    sha = {}
    if sha_file and os.path.exists(sha_file):
        for line in open(sha_file):
            p, h = line.strip().split(',')
            sha[p] = h
    with open(out, 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=COLS); w.writeheader()
        for pid, path, cls, grp, wid, model, prov, notes in LOCAL:
            w.writerow(dict(photo_id=pid, local_path=path, class_label=cls, group=grp, physical_watch_id=wid, model=model,
                            layout_group='primary_steel', provenance=prov, source='', split='', sha256=sha.get(path, ''),
                            image_url='', notes=notes))


if __name__ == '__main__':
    rows, excluded = harvest_catalogue(os.path.join(HERE, 'catalogue_provenance_strong.csv'))
    local_manifest(os.path.join(HERE, 'manifest_local.csv'), sys.argv[1] if len(sys.argv) > 1 else None)
    from collections import Counter
    print('provenance-strong catalogue rows:', len(rows), ' excluded by model:', excluded)
    for k, v in sorted(Counter((r['layout_group'], r['model'], r['provenance']) for r in rows).items()):
        print('  ', k, v, 'photos /', len({r['physical_watch_id'] for r in rows if (r['layout_group'], r['model'], r['provenance']) == k}), 'watches')
