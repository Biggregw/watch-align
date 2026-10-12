"""Fail closed unless the photographic validation matches this isolated implementation."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
report=json.loads((ROOT/'docs/research/luminous-boundary-fix-2026-10-12/validation.json').read_text())
assert report['validation_passed'] and report['noncircular_and_outer_unchanged']
assert report['originals']==132 and report['processing_variants']==18
assert report['exact_original_repeats']==3 and report['batgirl_original_verified']
assert report['contradictory_processing_circle_withheld_in_all_7_cases']
for path,sha in report['source_hashes'].items():
    assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha, path
allowed={'android/app/build.gradle','android/app/src/main/AndroidManifest.xml',
         'android/app/src/main/java/com/watchalign/mobile/LuminousCircularBoundary.java',
         'android/app/src/main/java/com/watchalign/mobile/LuminousMarkerDetector.java',
         'android/app/src/test/java/com/watchalign/mobile/LuminousCircularBoundaryTest.java'}
changed=set(subprocess.check_output(['git','diff','--name-only','8a53c1e5','--','android'],cwd=ROOT,text=True).splitlines())
assert changed<=allowed, 'Protected implementation/reference changes: '+str(changed-allowed)
print('Photographic validation and frozen outer/reference/pose safeguards verified')
