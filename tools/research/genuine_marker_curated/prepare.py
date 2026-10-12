"""Verify/extract only the curated artifact; match its bytes to frozen catalogues.
No image is resized or sourced from a website. Baseline selection precedes measurement.
Usage: prepare.py CURATED_ARTIFACT WORK_DIRECTORY [REPO=.]
"""
import csv
import hashlib
import json
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from PIL import Image

EXPECTED = {"submariner_124060": 46, "gmt_126710": 31, "submariner_126610": 55}
CATALOGUES = {"submariner_124060": "tools/research/sub124060/catalogue_124060.csv", "gmt_126710": "tools/research/alpha96_calibration/catalogue_provenance_strong.csv", "submariner_126610": "tools/research/sub126610/catalogue_126610.csv"}
MODELS = {"submariner_124060": {"124060"}, "gmt_126710": {"126710BLNR", "126710BLRO", "126710GRNR"}, "submariner_126610": {"126610", "126610LN", "126610LV"}}
INPUT_FIELDS = ["photo_id", "physical_watch_id", "model", "family", "path", "role", "original_photo_id", "source_width", "source_height"]


def read(path):
    with Path(path).open(newline="") as f:
        return list(csv.DictReader(f))


def write(path, rows, fields=None):
    fields = fields or list(dict.fromkeys(k for r in rows for k in r))
    with Path(path).open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields); w.writeheader(); w.writerows(rows)


def choose_one_per_watch(records):
    grouped = defaultdict(list)
    for r in records:
        grouped[r["family"], r["physical_watch_id"]].append(r)
    for photos in grouped.values():
        # Image dimensions are known before any measurement. Highest min side, then area,
        # then byte hash; no score or detector result influences this deterministic selection.
        chosen = min(photos, key=lambda r: (-min(int(r["source_width"]), int(r["source_height"])), -int(r["source_width"])*int(r["source_height"]), r["sha256"]))
        for r in photos:
            r["selected_for_measurement"] = str(r is chosen).lower()
            r["selection_reason"] = "largest_original_resolution_then_sha256" if r is chosen else "repeat_photo_of_same_recorded_watch; not an independent sample"


def main():
    artifact, work = (Path(x).resolve() for x in sys.argv[1:3])
    repo = Path(sys.argv[3] if len(sys.argv)>3 else ".").resolve()
    work.mkdir(parents=True, exist_ok=True)
    notes = list(artifact.rglob("README_FOR_CODEX.txt"))
    assert len(notes)==1, "Curated handoff README missing or ambiguous"
    print(notes[0].read_text())
    manifest_paths = list(artifact.rglob("Photo_Provenance_And_SHA256.csv"))
    assert len(manifest_paths)==1, "Curated provenance manifest missing or ambiguous"
    entries = read(manifest_paths[0])
    assert Counter(r["model_group"] for r in entries)==Counter(EXPECTED), "Unexpected family counts"
    assert len({r["photo_sha256"] for r in entries})==132, "Duplicate exact photos in curated input"
    raw = {}
    for archive in sorted(artifact.rglob("*.zip")):
        with zipfile.ZipFile(archive) as z:
            for member in z.infolist():
                if member.filename.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                    data=z.read(member); sha=hashlib.sha256(data).hexdigest()
                    assert sha not in raw, "Duplicate original across curated ZIP parts"
                    raw[sha]=data
    wanted={r["photo_sha256"] for r in entries}
    assert set(raw)==wanted, "Image hash set differs from manifest"
    catalogues={family: {r["sha256"]:r for r in read(repo/path) if r["class_label"]=="gen"} for family,path in CATALOGUES.items()}
    shared={}
    for family in EXPECTED:
        path=repo/"tools/research"/("alpha96_calibration/results/dedup/photos.csv" if family=="gmt_126710" else ("sub124060" if family=="submariner_124060" else "sub126610")+"/results/runner/dedup_measured.csv")
        shared[family]={r["photo_id"] for r in read(path) if r.get("shared_dial")=="1"}
    records=[]
    originals=work/"originals"; originals.mkdir(exist_ok=True)
    for entry in entries:
        family=entry["model_group"]; sha=entry["photo_sha256"]
        assert entry["model"] in MODELS[family], "Incompatible material/layout"
        assert sha in catalogues[family], "Original not present in frozen genuine catalogue: "+sha
        source=catalogues[family][sha]
        assert source["model"]==entry["model"], "Model claim conflict"
        recorded=entry["physical_watch_id"]
        assert recorded=="see_124060_selection_audit" or recorded==source["physical_watch_id"], "Watch identity conflict"
        watch=source["physical_watch_id"]
        path=originals/(sha+".jpg"); path.write_bytes(raw[sha])
        with Image.open(path) as image:
            width,height=image.size; orientation=image.getexif().get(274,"absent"); image_format=image.format
        sku=re.search(r"SKU(\d+)",source["image_url"],re.I)
        records.append(dict(photo_id=sha[:16],sha256=sha,family=family,model=entry["model"],physical_watch_id=watch,
                            curated_physical_watch_id=recorded,identity_status="provisional_inferred_listing_group" if watch.startswith("auto_") else "recorded_listing_or_lot_group",
                            identity_note="Not serial-verified; cross-vendor resale/identity overlap remains unproven",listing_sku=sku.group(1) if sku else "",
                            source_url=source.get("listing_url") or source["source"],image_url=source["image_url"],provenance=source["provenance"],
                            source_width=width,source_height=height,exif_orientation=orientation,image_format=image_format,path=str(path),role="original",original_photo_id=sha[:16],
                            photographic_selection=entry["selection"],known_shared_dial=str(sha[:16] in shared[family]).lower()))
    choose_one_per_watch(records)
    write(work/"verified_inputs.csv",records)
    for family in EXPECTED:
        sample=[r for r in records if r["family"]==family and r["selected_for_measurement"]=="true"]
        write(work/(family+"_inputs.csv"),[{k:r[k] for k in INPUT_FIELDS} for r in sample],INPUT_FIELDS)
    summary=dict(originals_verified=len(records),family_photos=dict(Counter(r["family"] for r in records)),recorded_watch_groups_by_family={f:len({r["physical_watch_id"] for r in records if r["family"]==f}) for f in EXPECTED},
                 measured_photo_selection_count=sum(r["selected_for_measurement"]=="true" for r in records),source_run=38222239457,
                 artifact="WatchAlign-genuine-faceon-132-originals-Codex-ready",curated_readme=notes[0].read_text(),
                 selection="one original per recorded watch; largest min side then pixel count then SHA; no measurement-dependent choice")
    (work/"input_verification.json").write_text(json.dumps(summary,indent=2)+"\n")
    print(json.dumps(summary,indent=2))


if __name__=="__main__":main()
