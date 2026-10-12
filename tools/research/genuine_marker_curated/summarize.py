"""Publish CSV/Markdown and select exact-polygon outliers after the frozen runner.
Usage: summarize.py WORKDIR REPORTDIR REPO [GITHUB_RUN_URL]
No image sourcing, no production changes, no new manufacturing thresholds.
"""
import importlib.util
import itertools
import json
import math
import sys
from collections import Counter,defaultdict
from pathlib import Path
from prepare import read,write,INPUT_FIELDS,EXPECTED
from study_stats import tables,watch_marker_summary


def measured_duplicates(rows,repo):
    path=repo/"tools/research/sub124060/dedup_measured.py"
    spec=importlib.util.spec_from_file_location("existing_dedup",path); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    grouped=defaultdict(list)
    for r in rows:grouped[r["family"],r["photo_id"]].append(r)
    vectors={}
    for key,sample in grouped.items():
        accepted=sample[0]["pose_valid"]=="true"
        if not accepted:continue
        adapted={"dial_radius_px":sample[0]["dial_R_px"]}
        for r in sample:
            h=r["hour"];adapted["m"+h+"_usable"]=r["detector_usable"]
            adapted["m"+h+"_local_radial_px"]=float(r["local_radial_R"])*float(r["dial_R_px"]) if r["local_radial_R"] else ""
            adapted["m"+h+"_local_tangential_px"]=float(r["local_tangential_R"])*float(r["dial_R_px"]) if r["local_tangential_R"] else ""
        vectors[key]=module.vec(adapted)
    pairs=[]
    for a,b in itertools.combinations(sorted(vectors),2):
        if a[0]!=b[0]:continue
        if grouped[a][0]["physical_watch_id"]==grouped[b][0]["physical_watch_id"]:continue
        common=set(vectors[a])&set(vectors[b])
        if len(common)<module.MIN_COMMON:continue
        distance=max(max(abs(vectors[a][h][j]-vectors[b][h][j]) for j in (0,1)) for h in common)
        if distance<module.TOL:
            pairs.append(dict(family=a[0],photo_a=a[1],photo_b=b[1],watch_a=grouped[a][0]["physical_watch_id"],watch_b=grouped[b][0]["physical_watch_id"],common_markers=len(common),max_offset_difference_R=distance,
                              reason="Existing measured shared-dial test; identity/stock-image warning, not independently proven physical-watch equality"))
    return pairs


def triangle_dimensions(polygon):
    points=json.loads(polygon);i=min(range(3),key=lambda j:math.hypot(*points[j]));base=[p for j,p in enumerate(points) if j!=i]
    return math.dist(*base),math.dist(points[i],[sum(p[j] for p in base)/2 for j in (0,1)])


def main():
    work,out,repo=(Path(x).resolve() for x in sys.argv[1:4]);out.mkdir(parents=True,exist_ok=True)
    run=sys.argv[4] if len(sys.argv)>4 else ""
    inputs=read(work/"verified_inputs.csv"); by_id={r["photo_id"]:r for r in inputs}
    rows=[r for family in EXPECTED for r in read(work/(family+"_markers.csv"))]
    pairs=measured_duplicates(rows,repo)
    shared={r[k] for r in pairs for k in ("photo_a","photo_b")}
    for r in rows:
        source=by_id[r["photo_id"]]
        r["identity_status"]=source["identity_status"]
        r["source_url"]=source["source_url"];r["original_image_url"]=source["image_url"]
        r["shared_identity_warning"]=str(r["photo_id"] in shared or source["known_shared_dial"]=="true").lower()
        r["baseline_eligible"]=str(r["area_trusted"]=="true" and r["shared_identity_warning"]=="false").lower()
        r["assessment"]="fully_usable_area_checks_cleared" if r["area_trusted"]=="true" else "withheld" if r["qc_status"]=="NOT_ASSESSED" else "partially_usable_area_withheld"
        r["position_assessed"]=str(any(s.startswith(("position|","lateral|")) for s in r["measures"].split(";"))).lower()
        r["rotation_assessed"]=str(any(s.startswith(("rotation|","centreline|")) for s in r["measures"].split(";"))).lower()
        if r["expected_area_R2"] and r["dial_R_px"]:r["expected_area_canonical_px2"]=str(float(r["expected_area_R2"])*float(r["dial_R_px"])**2)
    write(out/"per_marker.csv",rows)
    write(out/"physical_watch_marker_summary.csv",watch_marker_summary(rows))
    distributions=tables(rows);write(out/"model_shape_distributions.csv",distributions)
    write(out/"identity_shared_dial_pairs.csv",pairs,["family","photo_a","photo_b","watch_a","watch_b","common_markers","max_offset_difference_R","reason"])
    write(out/"provenance_verified.csv",[{k:v for k,v in r.items() if k!="path"} for r in inputs])
    write(out/"excluded_repeat_photos.csv",[{k:v for k,v in r.items() if k!="path"} for r in inputs if r["selected_for_measurement"]!="true"], [k for k in inputs[0] if k!="path"])
    counts=Counter((r["family"],r["shape"],r["area_reason"]) for r in rows if r["area_trusted"]!="true")
    write(out/"withheld_reasons.csv",[dict(family=k[0],shape=k[1],reason=k[2],markers=n) for k,n in sorted(counts.items())],["family","shape","reason","markers"])
    triangles=[]
    for r in rows:
        if r["shape"]!="triangle" or not r["mismatch_pct"]:continue
        ew,eh=triangle_dimensions(r["expected_polygon"]);aw,ah=triangle_dimensions(r["detected_polygon"])
        triangles.append(dict(photo_id=r["photo_id"],physical_watch_id=r["physical_watch_id"],family=r["family"],identity_status=r["identity_status"],
                              extra_pct=r["extra_pct"],missing_pct=r["missing_pct"],mismatch_pct=r["mismatch_pct"],area_trusted=r["area_trusted"],
                              base_width_difference_pct=100*(aw/ew-1),height_difference_pct=100*(ah/eh-1),
                              position_based_radial_deviation_R=r["triangle_radial_genuine_adjusted_R"],fitted_base_to_nominal_track_R=r["triangle_base_to_nominal_track_R"],reference_base_to_nominal_track_R=r["reference_base_to_nominal_track_R"],
                              direct_observed_gap_R="",note="All triangle area withheld; model-circle base clearance is unvalidated geometry, not detected metal-to-tick endpoints"))
    write(out/"triangle_diagnostics.csv",triangles)
    selected=[]
    for family in EXPECTED:
        for shape in ("baton","round","triangle"):
            group=[r for r in rows if r["family"]==family and r["shape"]==shape and r["mismatch_pct"]]
            measured=sorted([r for r in group if r["baseline_eligible"]=="true"],key=lambda r:-float(r["mismatch_pct"]))[:2]
            withheld=sorted([r for r in group if r["area_trusted"]!="true"],key=lambda r:-float(r["mismatch_pct"]))[:(2 if shape=="triangle" else 1)]
            for r in measured+withheld:
                selected.append(dict(photo_id=r["photo_id"],physical_watch_id=r["physical_watch_id"],family=family,hour=r["hour"],shape=shape,area_trusted=r["area_trusted"],identity_status=r["identity_status"],
                                     mismatch_pct=r["mismatch_pct"],extra_pct=r["extra_pct"],missing_pct=r["missing_pct"],area_reason=r["area_reason"],source_url=r["source_url"],original_image_url=r["original_image_url"],file="outliers/"+r["photo_id"]+"_"+r["hour"]+".png"))
    write(out/"outlier_index.csv",selected)
    selected_ids={r["photo_id"] for r in selected}
    for family in EXPECTED:
        write(work/(family+"_gallery_inputs.csv"),[{k:r[k] for k in INPUT_FIELDS} for r in inputs if r["family"]==family and r["photo_id"] in selected_ids],INPUT_FIELDS)
    verification=json.loads((work/"input_verification.json").read_text());verification.update(actions_run=run,selected_outliers=len(selected),marker_rows=len(rows),measured_shared_dial_warning_pairs=len(pairs))
    (out/"verification_summary.json").write_text(json.dumps(verification,indent=2)+"\n")
    report=["# Curated genuine marker measurements — 12 October 2026","", "Research only. Frozen PR #52 production source `593966433079d04816e98f78da5b66abd1f4553d`. No production, geometry, calibration, QC, perspective or denominator changes. No new photos/APK/merge.","",
            "Source: curated run [38222239457](https://github.com/Biggregw/watch-align/actions/runs/38222239457), artifact `WatchAlign-genuine-faceon-132-originals-Codex-ready`. All 132 original SHA256 values verified before analysis. Only this archive was downloaded; 124060 placeholder identities were resolved against the frozen catalogue by exact SHA.","",
            "One original per recorded watch, chosen before measurement by highest minimum source dimension, then source pixel count, then SHA. Repeated originals are retained in the provenance audit, not counted again. Recorded/inferred listing groups are not serial-authenticated independent physical watches. Cross-vendor resale overlap remains unproven. Existing measured shared-dial warnings exclude suspect photos from distributions, without changing app findings.","",
            "| Family | Original photos | Recorded groups selected | Reliable area groups (any marker) | Partially assessed groups |", "|---|---:|---:|---:|---:|"]
    for family in EXPECTED:
        a=[r for r in rows if r["family"]==family]
        report.append(f"| {family} | {EXPECTED[family]} | {len({r['physical_watch_id'] for r in a})} | {len({r['physical_watch_id'] for r in a if r['baseline_eligible']=='true'})} | {len({r['physical_watch_id'] for r in a if r['assessment']=='partially_usable_area_withheld'})} |")
    report += ["", "## Filled-template distributions", "", "Every marker remains an individual observation. Equal total weight per recorded watch for descriptive shape distributions; no overall watch quality score. P90/P95 publication requires >=20/>=40 usable recorded groups to retain at least two expected upper-tail groups. Neither percentiles nor maxima are Rolex factory tolerances or replica acceptance thresholds.","", "| Family / shape | Usable groups | Usable markers | Median total % | P90 % | P95 % | Maximum % |", "|---|---:|---:|---:|---:|---:|---:|"]
    fmt=lambda v:f"{v:.6f}" if isinstance(v,(int,float)) else "—"
    for t in distributions:report.append(f"| {t['family']} / {t['shape']} | {t['watch_groups_with_usable_area']} | {t['usable_area_markers']} | {fmt(t['median_mismatch_pct'])} | {fmt(t['p90_mismatch_pct'])} | {fmt(t['p95_mismatch_pct'])} | {fmt(t['max_mismatch_pct'])} |")
    report += ["", "## Interpretation and limitations", "", "Mismatch = 100 × (extra area + missing area) / filled expected ring-adjusted genuine-template area. Entire fitted outer-metal footprint; no experimental vertex-calibrated shapes. Orange extra and magenta missing use exactly the saved numerical polygons. Continuous geometric area is separate from photographic boundary uncertainty. `area_trusted` means existing app gates cleared, not independently proven physical edge accuracy.","",
               "All 12 triangle area percentages remain withheld, including offline diagnostic values. Their base width/height differences and percentage directions appear in `triangle_diagnostics.csv`; review the genuine outliers before attributing systematic shape disagreements to reference geometry, manufacture or bright bevels. No triangle template is changed or scoring enabled.","",
               "Spacing remains separate. The existing 12 radial genuine comparison is a position-based estimate. Fitted-base-to-nominal-track values are unvalidated model-circle geometry; no direct metal-to-detected-tick endpoint measurement exists in this pipeline. Direct gap fields remain blank. Ring shift/scale/rotation are separately exported and never added to marker-area mismatch.","",
               "Source bytes remain unchanged. The frozen desktop loader can apply its existing 3200-px working-size rule; original and working dimensions are both recorded. No presentation thumbnail or transformed photo becomes a manufacturing sample. Native resolution associations are confounded by watch/source/lighting; this cohort does not independently separate physical variation from photographic edge localisation or normal-view acceptability.","",
               "No unavailable or unreliable marker is silently removed: `per_marker.csv` includes every marker on every selected original, failed poses, component withholding and interference reasons. Non-selected repeat photographs are separately listed. Full-photo selection was human near-frontal QC screening; successful pose correction alone is not evidence of acceptable photographic suitability.","",
               "## LESSONS LEARNED","", "Provenance and original access must precede measurement. Photographic curation does not guarantee clean polished edges. Identity grouping remains a named source of uncertainty, and repeated source views must not increase manufacturing sample counts. Individual maxima need raw/outlined inspection, not automatic tolerance expansion. Genuine marker variation and edge/template uncertainty remain mixed until independently resolved boundaries and further held-out controls support separation.","",
               "[Per-marker results](per_marker.csv) · [Watch/marker summaries](physical_watch_marker_summary.csv) · [Distributions](model_shape_distributions.csv) · [Outlier index](outlier_index.csv) · [Provenance](provenance_verified.csv) · [Identity warnings](identity_shared_dial_pairs.csv)","", "Measurement run: "+run]
    (out/"REPORT.md").write_text("\n".join(report)+"\n")
    print(json.dumps(verification,indent=2))


if __name__=="__main__":main()
