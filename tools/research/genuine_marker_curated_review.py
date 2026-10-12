"""Research-only review of published frozen measurements; no remeasurement/threshold changes.
Quarantine source-model contradictions, expose generated identity uncertainty, and publish small tables.
Usage: genuine_marker_curated_review.py REPORTDIR [REPO=.]
"""
import csv
import json
import math
import re
import shutil
import statistics
import sys
from collections import Counter,defaultdict
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"genuine_marker_curated"))
from prepare import read,write,INPUT_FIELDS
from study_stats import tables,watch_marker_summary

SERIES=re.compile(r"(?<!\d)(116710|126710|114060|124060|116610|126610|126613|126618|126619)(?!\d)",re.I)
CORE=("source_sha256","pose_valid","dial_R_px","H","ring_model","area_trusted","area_reason","expected_area_R2","actual_area_R2","extra_pct","missing_pct","mismatch_pct","rotation_raw_deg","rotation_reference_signed_deg","local_offset_R","expected_polygon","detected_polygon","qc_status")
COMPACT=("family","model","photo_id","physical_watch_id","hour","shape","source_width","source_height","working_width","working_height","dial_R_px","pose_valid","ellipse_axis_ratio","tick_rms_px","fit_coverage","fit_integrity","interference_clean","qc_status","area_trusted","baseline_eligible","assessment","expected_area_R2","actual_area_R2","extra_pct","missing_pct","mismatch_pct","rotation_raw_deg","rotation_reference_signed_deg","local_offset_R","position_assessed","rotation_assessed","area_reason","identity_status","shared_identity_warning","model_provenance_status")


def hints(model,source,image):
    observed=sorted(set(SERIES.findall(source+" "+image)))
    return observed,any(ref!=model[:6] for ref in observed)


def main():
    out=Path(sys.argv[1]).resolve();repo=Path(sys.argv[2] if len(sys.argv)>2 else ".").resolve()
    rows=read(out/"per_marker.csv"); before=[{k:r[k] for k in CORE} for r in rows]
    prov=read(out/"provenance_verified.csv"); by_id={r["photo_id"]:r for r in prov}
    alerts=[]
    for r in prov:
        refs,conflict=hints(r["model"],r["source_url"],r["image_url"])
        r["model_provenance_status"]="recorded_url_reference_conflict" if conflict else "no_recorded_url_reference_conflict; curated_catalogue_claim"
        r["url_reference_hints"]=";".join(refs)
        r["baseline_model_eligible"]=str(not conflict).lower()
        if r["physical_watch_id"].startswith(("auto_","hv_page_")):r["identity_status"]="provisional_inferred_listing_group"
        if conflict:alerts.append({k:r[k] for k in ("photo_id","sha256","family","model","physical_watch_id","source_url","image_url","url_reference_hints","model_provenance_status")})
    for r in rows:
        source=by_id[r["photo_id"]]
        r["model_provenance_status"]=source["model_provenance_status"]
        r["identity_status"]=source["identity_status"]
        if source["baseline_model_eligible"]=="false":r["baseline_eligible"]="false"
    assert [{k:r[k] for k in CORE} for r in rows]==before,"Frozen numerical measurements changed"
    write(out/"per_marker.csv",rows);write(out/"provenance_verified.csv",prov)
    write(out/"model_provenance_conflicts.csv",alerts,["photo_id","sha256","family","model","physical_watch_id","source_url","image_url","url_reference_hints","model_provenance_status"])
    prior=out/"curated_claimed_model_distributions.csv"
    if not prior.exists():shutil.copyfile(out/"model_shape_distributions.csv",prior)
    distributions=tables(rows);write(out/"model_shape_distributions.csv",distributions)
    write(out/"physical_watch_marker_summary.csv",watch_marker_summary(rows))
    for family in ("gmt_126710","submariner_124060","submariner_126610"):
        write(out/(family+"_markers_compact.csv"),[{k:r[k] for k in COMPACT} for r in rows if r["family"]==family],COMPACT)
    index=read(out/"outlier_index.csv")
    for r in index:
        source=by_id[r["photo_id"]];r["model_provenance_status"]=source["model_provenance_status"];r["identity_status"]=source["identity_status"]
        r["review_note"]="MODEL CONFLICT: diagnostic comparison against declared reference only; excluded from genuine baseline" if source["baseline_model_eligible"]=="false" else "Original runner-selected outlier; exact saved polygons; no severity assigned"
    write(out/"outlier_index.csv",index)
    diagnostics=read(out/"triangle_diagnostics.csv")
    for r in diagnostics:r["model_provenance_status"]=by_id[r["photo_id"]]["model_provenance_status"]
    write(out/"triangle_diagnostics.csv",diagnostics)
    resolution=[]
    for family in ("gmt_126710","submariner_124060","submariner_126610"):
        photos={r["photo_id"]:r for r in rows if r["family"]==family and r["dial_R_px"] and by_id[r["photo_id"]]["baseline_model_eligible"]=="true"}
        ordered=sorted(photos,key=lambda id_:float(photos[id_]["dial_R_px"]))
        bands={id_:min(3,i*4//len(ordered))+1 for i,id_ in enumerate(ordered)}
        for band in range(1,5):
            ids={id_ for id_ in ordered if bands[id_]==band};rs=[float(photos[id_]["dial_R_px"]) for id_ in ids]
            for shape in ("baton","round"):
                sample=[r for r in rows if r["photo_id"] in ids and r["shape"]==shape and r["baseline_eligible"]=="true"]
                resolution.append(dict(family=family,shape=shape,native_radius_rank_quartile=band,radius_min_px=min(rs) if rs else "",radius_max_px=max(rs) if rs else "",watch_groups=len({r["physical_watch_id"] for r in sample}),usable_markers=len(sample),
                                       median_observed_mismatch_pct=statistics.median(float(r["mismatch_pct"]) for r in sample) if sample else "",
                                       note="Descriptive native-resolution association; watches/sources/lighting differ. Not controlled resizing, uncertainty calibration or a QC cutoff."))
    write(out/"native_resolution_associations.csv",resolution)
    summary=json.loads((out/"verification_summary.json").read_text());summary.update(model_provenance_conflicts=len(alerts),reviewed_baseline_marker_count=sum(r["baseline_eligible"]=="true" for r in rows),
          numeric_measurement_rows_preserved=len(rows),source_model_note="Both recorded source URLs and image filenames reference 116710 for two curated 126710BLNR-labelled entries. Kept as diagnostics; excluded pending identity resolution.")
    (out/"verification_summary.json").write_text(json.dumps(summary,indent=2)+"\n")
    report=(out/"REPORT.md").read_text()
    report=report.replace("| gmt_126710 | 31 | 31 | 28 | 29 |","| gmt_126710 | 31 | 31 | 26 | 29 |")
    start=report.index("| Family / shape |");end=report.index("\n## Interpretation",start)
    table=["| Family / shape | Usable groups | Usable markers | Median total % | P90 % | P95 % | Maximum % |","|---|---:|---:|---:|---:|---:|---:|"]
    fmt=lambda v:f"{v:.6f}" if isinstance(v,(int,float)) else "—"
    for t in distributions:table.append(f"| {t['family']} / {t['shape']} | {t['watch_groups_with_usable_area']} | {t['usable_area_markers']} | {fmt(t['median_mismatch_pct'])} | {fmt(t['p90_mismatch_pct'])} | {fmt(t['p95_mismatch_pct'])} | {fmt(t['max_mismatch_pct'])} |")
    report=report[:start]+"\n".join(table)+"\n"+report[end:]
    note="\n## Post-run provenance review\n\nTwo curated entries (`b0a1a6ff0bf6db97`, `7888550a54853ebb`) claim 126710BLNR but both their recorded listing URLs and original image filenames say 116710. This is an unresolved source-model contradiction, not proof of a newly identified physical reference. They remain in the measured CSV as comparisons against the declared 126710 template, but are quarantined from the genuine 126710 baseline. All 1,474 numerical rows and app statuses are unchanged. The original claimed-model aggregate is preserved separately; the displayed table and current distribution CSV use the conservative reviewed subset. No exclusion was based on obtaining smaller percentages.\n\n`auto_*` and `hv_page_*` identifiers are explicitly marked provisional inferred listing groups. Other IDs are still listing/lot groups, not serial-verified watch independence. Four 124060 photos trigger the existing shared-dial test (two pairs); a GMT photo has a historical shared-image flag. All remain inspectable but do not enter the independent-group distributions. Counts of partially assessed groups overlap counts with at least one usable area; these are not mutually exclusive whole-watch categories.\n\nThe curated archive therefore contains 130 measured recorded groups, 128 with no detected source-model contradiction. There are 978 baseline-eligible marker observations after current reliability, shared-image and model-consistency exclusions. Those are conditional photographed-sample statistics, not 978 independent physical watches.\n\nSmall model-specific scalar CSVs are available as `gmt_126710_markers_compact.csv`, `submariner_124060_markers_compact.csv`, and `submariner_126610_markers_compact.csv`. The detailed CSV retains the exact polygons and all uncertainty/interference fields.\n"
    if "## Post-run provenance review" not in report:report+=note
    (out/"REPORT.md").write_text(report)
    print(json.dumps(summary,indent=2))


if __name__=="__main__":main()
