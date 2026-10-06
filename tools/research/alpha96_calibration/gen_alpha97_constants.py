#!/usr/bin/env python3
"""RESEARCH -> APP. Regenerate the frozen constants in android/.../Alpha97TwelveReadout.java from
m12_nominal.properties and m12_genuine_reference.csv (written by calibrate_m12_nominal.py). Only the generated block
between the BEGIN/END markers and the composition line in the class comment are rewritten; Alpha97TwelveReadoutTest
pins the result to the same files."""
import csv
import os
import re
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, '..', '..', '..', 'android/app/src/main/java/com/watchalign/mobile/Alpha97TwelveReadout.java')


def main():
    nom = {k.strip(): v.strip() for k, v in (l.split('=', 1) for l in open(os.path.join(HERE, 'm12_nominal.properties')) if '=' in l and not l.startswith('#'))}
    ref = list(csv.DictReader(open(os.path.join(HERE, 'm12_genuine_reference.csv'))))
    def arr(k):
        vals = [r[k] for r in ref]
        return '{\n' + ',\n'.join('            ' + ','.join(vals[i:i + 8]) for i in range(0, len(vals), 8)) + '}'
    block = (f"    // BEGIN GENERATED (gen_alpha97_constants.py)\n"
             f"    static final int N_WATCHES={nom['n_watches']};\n"
             f"    /** Genuine nominal relative to the Alpha92 master: 12 local offset components (units of R, radial + = outward). */\n"
             f"    static final double NOMINAL_RADIAL_R={nom['radial_R']},NOMINAL_TANGENTIAL_R={nom['tangential_R']};\n"
             f"    /** Genuine nominal triangle angles relative to the Alpha92 master, degrees. */\n"
             f"    static final double NOMINAL_LEFT_SIDE_DEG={nom['left_side_deg']},NOMINAL_RIGHT_SIDE_DEG={nom['right_side_deg']},\n"
             f"            NOMINAL_ROTATION_DEG={nom['rotation_deg']};\n\n"
             f"    /** Per-watch genuine context (leave-one-watch-out, absolute values): lateral (R), centreline (deg), max side (deg). */\n"
             f"    static final double[] GENUINE_LATERAL_R={arr('lateral_R')};\n"
             f"    static final double[] GENUINE_CENTRELINE_DEG={arr('centreline_deg')};\n"
             f"    static final double[] GENUINE_SIDES_DEG={arr('sides_deg')};\n"
             f"    // END GENERATED\n")
    src = open(JAVA).read()
    if '// BEGIN GENERATED' in src:
        src = re.sub(r'    // BEGIN GENERATED.*?    // END GENERATED\n', lambda m: block, src, flags=re.S)
    else:  # first run: replace the hand-placed constants
        src = re.sub(r'    static final int N_WATCHES=.*?static final double\[\] GENUINE_SIDES_DEG=\{.*?\};\n', lambda m: block, src, flags=re.S)
    comp = ', '.join(f'{s} {n}' for s, n in sorted(Counter(r['source'] for r in ref).items()))
    src = re.sub(r' \* Frozen reference: .*?\n \* shared / stock photos excluded\)',
                 f' * Frozen reference: {nom["n_watches"]} genuine watches ({comp}; SWE excluded;\n * shared / stock photos excluded)', src, flags=re.S)
    open(JAVA, 'w').write(src)
    print('regenerated', os.path.relpath(JAVA), 'N =', nom['n_watches'], comp)


if __name__ == '__main__':
    main()
