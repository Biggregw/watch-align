"""Verify measured geometry and make selected, derived outlier evidence Git-readable.
Originals are never committed. Usage: finish.py WORKDIR REPORTDIR REPO
"""
import hashlib
import html
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path
from prepare import read


def area(points):
    return abs(sum(p[0]*q[1]-q[0]*p[1] for p,q in zip(points,points[1:]+points[:1])))/2


def main():
    work,out,repo=(Path(x).resolve() for x in sys.argv[1:4])
    subprocess.run(["git","diff","--exit-code","593966433079d04816e98f78da5b66abd1f4553d","--","android/app/src/main"],cwd=repo,check=True)
    inputs=read(work/"verified_inputs.csv"); selected={r["photo_id"]:r for r in inputs if r["selected_for_measurement"]=="true"}
    assert len(inputs)==132
    assert len({(r["family"],r["physical_watch_id"]) for r in selected.values()})==len(selected)
    for r in inputs:assert hashlib.sha256(Path(r["path"]).read_bytes()).hexdigest()==r["sha256"]
    rows=read(out/"per_marker.csv"); actual={r["photo_id"] for r in rows}
    assert actual==set(selected)
    checked=0
    for r in rows:
        assert r["source_sha256"]==selected[r["photo_id"]]["sha256"]
        if r["shape"]=="triangle":assert r["area_trusted"]=="false"
        assert not r["direct_metal_to_tick_gap_R"]
        if not r["mismatch_pct"]:continue
        expected=float(r["expected_area_R2"]);actual=float(r["actual_area_R2"]);overlap=float(r["overlap_area_R2"])
        assert math.isclose(area(json.loads(r["expected_polygon"])),expected,abs_tol=1e-12)
        assert math.isclose(area(json.loads(r["detected_polygon"])),actual,abs_tol=1e-12)
        extra=100*(actual-overlap)/expected;missing=100*(expected-overlap)/expected
        assert math.isclose(extra,float(r["extra_pct"]),abs_tol=1e-8)
        assert math.isclose(missing,float(r["missing_pct"]),abs_tol=1e-8)
        assert math.isclose(extra+missing,float(r["mismatch_pct"]),abs_tol=1e-8)
        checked+=1
    for id_,meta in selected.items():
        expected=12 if meta["family"]=="submariner_124060" else 11
        sample=[r for r in rows if r["photo_id"]==id_]
        assert len(sample)==expected and len({r["hour"] for r in sample})==expected
    outliers=read(out/"outlier_index.csv"); target=out/"outliers";target.mkdir(exist_ok=True)
    sections=["<!doctype html><meta charset='utf-8'><title>Curated genuine marker outliers</title><style>body{font:16px sans-serif;background:#102030;color:white;margin:24px}img{max-width:100%}a{color:#8dddff}article{margin:32px 0}</style><h1>Curated genuine marker outliers</h1><p>Exact frozen scoring/fitted polygons. Orange extra; magenta missing; yellow expected; cyan fitted. WITHHELD values are offline diagnostics, not enabled QC percentages. All sources remain unchanged; displayed crops are derived evidence only.</p>"]
    for r in outliers:
        name=r["photo_id"]+"_"+r["hour"]+".png"
        source=work/"gallery"/r["family"]/name
        assert source.is_file(),name
        shutil.copyfile(source,target/name)
        sections.append("<article><h2>"+html.escape(r["family"]+" · "+r["physical_watch_id"]+" · "+r["photo_id"]+" · hour "+r["hour"])+"</h2><p><a href='"+html.escape(r["source_url"],quote=True)+"'>Original source</a> · "+html.escape(r["identity_status"])+"</p><img src='"+r["file"]+"'><p>"+html.escape(r["area_reason"])+"</p></article>")
    (out/"outliers.html").write_text("\n".join(sections))
    summary=json.loads((out/"verification_summary.json").read_text());summary.update(polygon_area_checks=checked,production_unchanged=True,outlier_images=len(outliers))
    (out/"verification_summary.json").write_text(json.dumps(summary,indent=2)+"\n")
    shutil.copyfile(work/"input_verification.json",out/"input_verification.json")
    print(json.dumps(summary,indent=2))


if __name__=="__main__":main()
