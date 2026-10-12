"""Check recorded replay invariants and require APK measurement code to match that replay."""
import csv, json, subprocess
from pathlib import Path
root=Path('docs/research/luminous-fit-fix-2026-10-12')
meta=json.loads((root/'validation.json').read_text())
source=Path('android/app/src/main/java/com/watchalign/mobile')
classes=['LuminousMarkerDetector.java','LuminousSeparator.java','LuminousPartialFit.java','LuminousMeasurement.java','LuminousAlignmentOverlay.java','LuminousGeometry.java','LuminousReference.java']
subprocess.run(['git','diff','--exit-code',meta['measurement_commit'],'--',*[str(source/name) for name in classes]],check=True)
count=0
for before,after in [(root/f'{model}_before.csv',root/f'{model}_after.csv') for model in ('gmt_126710','submariner_124060','submariner_126610')]+[(root/'controls/controls_before.csv',root/'controls/controls_after.csv')]:
    old={(r['photo_id'],r['hour']):r for r in csv.DictReader(before.open())}
    rows=list(csv.DictReader(after.open()));assert len(old)==len(rows)
    for r in rows:
        b=old[r['photo_id'],r['hour']]
        for key in ('outer_status','outer_rotation_deg','outer_calibrated_rotation_deg','outer_area_reason','outer_total_pct'):
            if b.get(key,''):assert b[key]==r[key],(r['photo_id'],r['hour'],key)
        if b['assessed']=='true':
            assert r['assessed']=='true'
            for key in ('total_pct','extra_pct','missing_pct','rotation_deg','dx_R','dy_R','size_pct','detected_polygon','expected_polygon'):
                if b.get(key,'') and b[key]!='NaN':assert b[key]==r[key],(r['photo_id'],r['hour'],key)
        for prefix in ('','fitted_','alignment_'):
            if r[prefix+'total_pct']:
                assert r['expected_polygon']
                assert r['detected_polygon' if not prefix else prefix+'polygon']
                assert abs(float(r[prefix+'extra_pct'])+float(r[prefix+'missing_pct'])-float(r[prefix+'total_pct']))<1e-9
    count+=len(rows)
assert count==meta['marker_rows']+33
print(f'{count} recorded marker rows: frozen outer/physical outputs, three-mode polygon arithmetic, replayed source checked')
