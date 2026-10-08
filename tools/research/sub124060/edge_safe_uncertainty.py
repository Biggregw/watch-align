#!/usr/bin/env python3
"""The 12 edge filter (edge_filter.py) may never make a clear finding easier.

edge_filter.py removes edge-affected genuine 12 readings so they cannot set the 12's genuine RANGE. Rebuilt on the
filtered data, the 12's single-photo uncertainty also shrinks (GMT: side angle 0.49 -> 0.15 deg), and on the GMT genuine
catalogue that turned a genuine photo's 12 from worth a look into clear (CI run 37804668525). Real photos of genuine
watches do show that scatter, so the allowance keeps it. Per key, the more cautious of the two files is kept:

  twelve_* allowances (.deg .degR .R .px)  larger of filtered / unfiltered (a larger allowance -> fewer clear)
  twelve_sides_agreement.limit             smaller of the two (a smaller limit -> more 12 angles capped at worth a look)

Each value keeps its own '.watches' count. Every other key is the filtered file's (they are identical: the filter
touches only the 12).

Usage: edge_safe_uncertainty.py --filtered alpha99_uncertainty.properties --unfiltered unfiltered.properties --out out.properties

--all-families applies the same rule to every family, not only the 12 (Alpha102: a quality-selected genuine image pack
must not shrink the photo-to-photo allowances the app applies to ordinary photos; --unfiltered is then the previous
allowance file).
"""
import argparse

SIGMA_SUFFIXES = ('.deg', '.degR', '.R', '.px')


def read(p):
    lines = open(p).read().splitlines()
    return lines, {l.split('=')[0].strip(): l.split('=', 1)[1].strip() for l in lines if '=' in l and not l.startswith('#')}


def main():
    ap = argparse.ArgumentParser()
    for k in ('--filtered', '--unfiltered', '--out'):
        ap.add_argument(k, required=True)
    ap.add_argument('--all-families', action='store_true')
    a = ap.parse_args()
    lines, f = read(a.filtered)
    _, u = read(a.unfiltered)
    pick = {}
    for k in f:
        if (not a.all_families and not k.startswith('twelve_')) or k.endswith('.watches') or k not in u:
            continue
        fv, uv = float(f[k]), float(u[k])
        if k == 'k_sigma':
            continue
        if k == 'twelve_sides_agreement.limit':
            pick[k] = 'u' if uv < fv else 'f'
        elif k.endswith(SIGMA_SUFFIXES):
            pick[k] = 'u' if uv > fv else 'f'
    out = ['# ' + ('all' if a.all_families else '12') + ' values: the more cautious of the two input files (edge_safe_uncertainty.py)']
    for l in lines:
        k = l.split('=')[0].strip() if '=' in l and not l.startswith('#') else None
        base = k[:-len('.watches')] if k and k.endswith('.watches') else k
        if base in pick and pick[base] == 'u':
            l = f'{k}={u[k]}'
            if k == base:
                print(f'{k}: {f[k]} -> {u[k]} (unfiltered)')
        out.append(l)
    open(a.out, 'w').write('\n'.join(out) + '\n')


if __name__ == '__main__':
    main()
