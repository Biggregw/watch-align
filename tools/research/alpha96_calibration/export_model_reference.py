#!/usr/bin/env python3
"""RESEARCH -> APP. Copies a model's genuine reference from the research outputs into the app's model folder,
android/app/src/main/assets/models/<model>/reference/, byte for byte. ModelReferenceTest checks the copies are identical,
so the app can never drift from the research files.

  research file (this folder)       app file (reference/)        written by
  alpha98_reference.csv             genuine_reference.csv        build_alpha98_reference.py
  alpha98_nominal.properties        nominal.properties           build_alpha98_reference.py
  m12_nominal.properties            triangle_nominal.properties  calibrate_m12_nominal.py
  m12_genuine_reference.csv         triangle_reference.csv       calibrate_m12_nominal.py
  alpha99_uncertainty.properties    uncertainty.properties       measurement_uncertainty.py

Usage: export_model_reference.py --model gmt_126710 [--source-dir <dir with the research files>]
A new model's research outputs go in their own source directory with the same file names.
"""
import argparse
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(HERE, '..', '..', '..', 'android', 'app', 'src', 'main', 'assets', 'models')
FILES = (('alpha98_reference.csv', 'genuine_reference.csv'), ('alpha98_nominal.properties', 'nominal.properties'),
         ('m12_nominal.properties', 'triangle_nominal.properties'), ('m12_genuine_reference.csv', 'triangle_reference.csv'),
         ('alpha99_uncertainty.properties', 'uncertainty.properties'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--model', required=True)
    ap.add_argument('--source-dir', default=HERE)
    a = ap.parse_args()
    model_dir = os.path.join(APP, a.model)
    if not os.path.exists(os.path.join(model_dir, 'model.json')):
        raise SystemExit(f'no model spec at {model_dir}/model.json - write the spec first')
    out = os.path.join(model_dir, 'reference'); os.makedirs(out, exist_ok=True)
    for src, dst in FILES:
        p = os.path.join(a.source_dir, src)
        if os.path.exists(p):
            shutil.copyfile(p, os.path.join(out, dst)); print(f'{src} -> {os.path.relpath(os.path.join(out, dst))}')
        else:
            print(f'{src}: not present - the app reports the features that need it as not assessed')


if __name__ == '__main__':
    main()
