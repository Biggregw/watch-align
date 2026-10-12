#!/usr/bin/env python3
"""Read-only curation of already archived verified original source bytes.
Never edits an image. Repackages strict hand-reviewed shortlists for future Codex sessions.
"""
import csv,hashlib,io,json,zipfile
from pathlib import Path
root=Path("curation_inputs")
out=Path("curated_genuine");out.mkdir(exist_ok=True)
gmt_keep={47,55,56,59,62,64,65,67,74,78,79,99,101,106,108,109,111,112,113,114,116,120,121,123,135,137,142,145,146,150,152}
sub_keep=set(range(62))-{18,30,31,32,36,39,43}
s124_prefixes=set(("d2d17532532ec40b,937808e420a1e00b,c14f8b3abb49c038,e4ea736606798ecd,"
"077c7533c2846cb2,cb9df048dfd43767,c2c6f5cc69fa1c6c,a873cd649dee3d36,"
"39d163d48710cb5a,d04c99024b491471,63dc466faad01512,2fdf38169ee1b57a,"
"5cec141d6bc20f7a,033133202cf757da,300443c8ae84ff63,3cce6d1b709364b0,"
"59db582c885be64a,f97405415bac87e3,80d288dcc8689345,ce897d84b86f5ae9,"
"57baf569cec2a1fa,855041528fc1ac79,7e07e72d310bb5b1,ef5f5a66fe27f59f,"
"6bb224f7e4a96d4b,22b6183aa899f82a,46a616a7fc9dacde,1b33b3c613d20838,"
"754299bf17ee6817,fa3d832d3793a70f,d29283758a85581c,e8d6f0479a29d86d,"
"deeac9c977cff01b,ae62696b350860cc,17484cbe61baaa1a,8cc107d61cf3afca,"
"6cac8c2178b1448e,14a800efc5982cf7,448814812e7079fd,f830414d02ab4d92,"
"f27a64f84579f41b,c58f78ef6c1cd5b7,9e6811c0987db1f0,5e97eb15aa3ce07d,"
"87da4af1942b938d,5986c5a3258ae9ec").split(','))
assert len(s124_prefixes)==46
images={}
meta={}
# Earlier 124060 calibration archive: sha256-addressed original byte objects
for p in (root/"old_calibrator").rglob("*"):
    if p.is_file() and len(p.name)==64 and p.name[:16] in s124_prefixes:
        body=p.read_bytes()
        assert hashlib.sha256(body).hexdigest()==p.name
        images[p.name[:16]]=body
# EWC photo pack inside historical artifact
for container in (root/"old_ewc").rglob("*.zip"):
    with zipfile.ZipFile(container) as z:
        for file in z.namelist():
            if not file.lower().endswith((".jpg",".jpeg",".png")):continue
            body=z.read(file);sha=hashlib.sha256(body).hexdigest()
            if sha[:16] in s124_prefixes: images[sha[:16]]=body
assert set(images)==s124_prefixes,("missing selected 124060",sorted(s124_prefixes-set(images)))
for k,b in images.items():
    sha=hashlib.sha256(b).hexdigest()
    meta[("submariner_124060",sha)]={"model_group":"submariner_124060","photo_sha256":sha,"physical_watch_id":"see_124060_selection_audit",
        "model":"124060","source_record":"watch-calibrator-124060-37194686346 / EWC extra original image pack",
        "selection":"visually_hand_reviewed_near_frontal"}
# GMT/Sub date catalogues delivered by previous exact-hash download
archive_cache={}
raw_manifest=list(csv.DictReader(open(root/"catalogued"/"recovery_all_catalogued.csv",newline="")))
for grp,approved,variants in [
    ("gmt_126710",gmt_keep,{"126710BLRO","126710BLNR","126710GRNR"}),
    ("submariner_126610",sub_keep,{"126610LN","126610LV","126610"})
]:
    items=[x for x in raw_manifest if x["model_group"]==grp and x["recovery_archive"]]
    for i,row in enumerate(items):
        if i not in approved:continue
        assert row["model"] in variants
        name=row["recovery_archive"]
        if name not in archive_cache:
            archive_cache[name]=zipfile.ZipFile(root/"catalogued"/name)
        data=archive_cache[name].read(row["recovery_member"])
        sha=hashlib.sha256(data).hexdigest()
        assert sha==row["expected_sha256"]
        images[sha]=data
        meta[(grp,sha)]={"model_group":grp,"photo_sha256":sha,"physical_watch_id":row["physical_watch_id"],
            "model":row["model"],"source_record":row["image_url"],"selection":"visually_hand_reviewed_near_frontal"}
    count=sum(m["model_group"]==grp for m in meta.values())
    assert count==len(approved),(grp,count,len(approved))
for f in archive_cache.values():f.close()
assert len(images)==132,(len(images),len(meta))
assert len(meta)==132
# Pack per model, maximal 22MiB (original bytes copied into zip unchanged)
records=list(meta.values());parts=[]
for grp in ["submariner_124060","gmt_126710","submariner_126610"]:
    chunk=[];sz=0;chunks=[]
    for r in (x for x in records if x["model_group"]==grp):
        data=images[r["photo_sha256"][:16] if grp=="submariner_124060" else r["photo_sha256"]]
        if chunk and sz+len(data)>22*1024*1024:
            chunks.append(chunk);chunk=[];sz=0
        chunk.append((r,data));sz+=len(data)
    if chunk:chunks.append(chunk)
    for i,chunk in enumerate(chunks,1):
        name=f"WatchAlign_{grp}_Unmodified_FaceOn_{i:02d}of{len(chunks):02d}.zip"
        with zipfile.ZipFile(out/name,"w",compression=zipfile.ZIP_STORED) as z:
            for r,body in chunk:
                path=f"{grp}/originals/{r['photo_sha256']}.jpg"
                z.writestr(path,body)
            z.writestr("README.txt","Each photo is original, SHA256-verified, unchanged. Selected by human QC-style frontal-photo review. Marker-level measurability and independent physical-watch identities are NOT established. Do not calculate acceptance thresholds yet.\n")
        assert (out/name).stat().st_size<25*1024*1024
        parts.append(name)
with (out/"Photo_Provenance_And_SHA256.csv").open("w",newline="") as f:
    fields=["model_group","model","photo_sha256","physical_watch_id","source_record","selection"]
    w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(records)
(out/"README_FOR_CODEX.txt").write_text(
"Watch Align genuine marker reference, 2026-10-12. 132 SHA256-verified unmodified photographic candidates: "
"46 124060, 31 steel GMT 126710, 55 steel 126610. These are not independent Rolex tolerance samples yet. "
"Do not resize, crop, retouch, or adjust sources. Reject any individual marker measurement contaminated by hands/glare. "
"Keep current PR52 detector/geometry/thresholds unchanged. Source catalogues live in repo and listed in Photo_Provenance_And_SHA256.csv. "
"Source run IDs: 37194686346 (124060 main), 37831647255 (124060 EWC), 38220410538 (GMT and Sub Date exact hash recovery). "
"Human QC frontal selection is a photographic candidate screen, not final pose/edge reliability validation. "
"Cross-vendor physical-watch identity and overlap remain unproven.\n")
summary={"total_originals":len(images),"by_model":{g:sum(x["model_group"]==g for x in records) for g in ["submariner_124060","gmt_126710","submariner_126610"]},"parts":parts}
(out/"Curated_Package_Summary.json").write_text(json.dumps(summary,indent=2))
print(json.dumps(summary,indent=2))
