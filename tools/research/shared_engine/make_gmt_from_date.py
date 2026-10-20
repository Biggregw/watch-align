#!/usr/bin/env python3
"""Owner's three options (20 Oct 2026): the GMT-Master II option measures exactly as the Submariner Date option.

Writes the GMT model folder from the Submariner Date folder: the same dial geometry, genuine marker references,
uncertainties and triangle nominal. Kept from the GMT: its id, label and the dial-print spots of its seconds-hand
search (GMT print sits at different angles from the Submariner's). genuine_track.csv is not copied: it is the GMT's
own genuine photos measured by this configuration (CI, three-options.yml).

Evidence: tools/research/shared_engine/REPORT.md (same photo through each family's configuration: sideways and tilt
identical; in/out differs only by the reference radius, the GMT master's batons 0.40 % R inside the Submariner's).

Usage: make_gmt_from_date.py [assets_models_dir]
"""
import json
import os
import shutil
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..',
                                                           'android', 'app', 'src', 'main', 'assets', 'models')
SRC, DST = os.path.join(ROOT, 'submariner_126610'), os.path.join(ROOT, 'gmt_126710')
SRC_ID, DST_ID = 'submariner_126610', 'gmt_126710'
LABEL = 'GMT-Master II'
NOTE = ('GMT-Master II 126710 family. Measured exactly as the Submariner Date option (owner, 20 Oct 2026): the '
        'Submariner dial master geometry, genuine marker references and uncertainties, copied from submariner_126610 by '
        'tools/research/shared_engine/make_gmt_from_date.py. Only the id, label and the seconds-hand print spots are the '
        "GMT's own. Canonical units: dial radius 1, 12 at the top.")
FILES = ['alignment/luminous_alignment.json', 'alignment/metal_alignment.properties', 'reference/genuine_reference.csv',
         'reference/nominal.properties', 'reference/triangle_nominal.properties', 'reference/triangle_reference.csv',
         'reference/uncertainty.properties']
HEADER = '# gmt_126710: copied from submariner_126610 (tools/research/shared_engine/make_gmt_from_date.py); the GMT option measures as the Submariner Date option.\n'


def main():
    gmt = json.load(open(os.path.join(DST, 'model.json')))
    model = json.load(open(os.path.join(SRC, 'model.json')))
    model['id'], model['label'], model['note'] = DST_ID, LABEL, NOTE
    model['seconds_hand'] = gmt['seconds_hand']
    with open(os.path.join(DST, 'model.json'), 'w') as o:
        json.dump(model, o, indent=4)
        o.write('\n')
    for f in FILES:
        s, d = os.path.join(SRC, f), os.path.join(DST, f)
        if f.endswith('.json'):
            j = json.load(open(s))
            assert j['model'] == SRC_ID, f
            j['model'] = DST_ID
            j['provenance'] = 'Copied from submariner_126610 (the GMT option measures as the Submariner Date option). ' + j.get('provenance', '')
            with open(d, 'w') as o:
                json.dump(j, o, indent=2)
                o.write('\n')
        elif f.endswith('.properties'):
            with open(d, 'w') as o:
                o.write(HEADER + open(s).read())
        else:
            shutil.copyfile(s, d)
    print('wrote', DST)


if __name__ == '__main__':
    main()
