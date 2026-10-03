#!/usr/bin/env python3
"""End-to-end autonomous Watch Align family calibrator.

Live mode discovers and acquires public evidence, freezes the exact bytes into an immutable
content-addressed snapshot, then measures/calibrates from the fixed workspace. Offline replay uses
that snapshot through the same post-acquisition execution path.
"""
from __future__ import annotations

import argparse, csv, json, os, shutil, subprocess, sys
from collections import Counter
from pathlib import Path

HERE=Path(__file__).resolve().parent
REPO=HERE.parents[1]
sys.path.insert(0,str(HERE))
from discover import discover  # noqa: E402
from reddit_evidence import enrich as enrich_reddit_evidence  # noqa: E402
import split as locked_split  # noqa: E402
from genuine_envelope import save as save_genuine_envelope  # noqa: E402
import contracts  # noqa: E402
import run_manifest as manifest_tools  # noqa: E402
import evidence_snapshot  # noqa: E402
import calibration_execution  # noqa: E402


def sh(cmd, cwd=REPO):
    print("+"," ".join(map(str,cmd)),flush=True)
    subprocess.run([str(x) for x in cmd],cwd=cwd,check=True)


def config_path(model: str)->Path:
    p=REPO/"calibration"/"models"/f"{model.upper()}.json"
    if not p.exists(): raise SystemExit(f"No calibrator config for {model}: {p}")
    return p


def paths(model: str, root: Path):
    m=model.upper();base=root/m
    return {
        "base":base,"pool":base/"discovered_candidates.csv","acq":base/"dataset",
        "split":base/"locked_split.csv","geom":base/"geometry","cal":base/"calibration.json",
        "replica":base/"replica_evidence.json","manifest":base/"run_manifest.json",
        "snapshot":base/"evidence_snapshot_v1",
    }


def replica_evidence(config: dict, acquisition_root: Path, output: Path) -> dict:
    """Summarise whether replica stress evidence is single-shot or repeatable multi-photo data."""
    summary=acquisition_root/"candidate_summary.csv"
    minimum=int(config.get("discovery",{}).get("minimum_replica_multi_photo_watches",6))
    if not summary.exists():
        result={"state":"NO_DATA","watches":0,"images":0,"with_2plus_photos":0,
                "minimum_multi_photo_watches":minimum,"oauth_configured":False}
    else:
        with summary.open(newline="",encoding="utf-8") as fh:
            rows=[r for r in csv.DictReader(fh) if (r.get("class_label") or r.get("class") or "").lower()=="rep"]
        counts=[]
        factories=Counter()
        album_backed=0
        for row in rows:
            try: n=int(row.get("images_acquired") or 0)
            except ValueError: n=0
            if n<=0: continue
            counts.append(n)
            factories[(row.get("factory") or "unknown")]+=1
            if (row.get("image_album_url") or "").strip(): album_backed+=1
        multi=sum(n>=2 for n in counts)
        result={
            "state":"ADEQUATE" if multi>=minimum else "PARTIAL",
            "watches":len(counts),
            "images":sum(counts),
            "with_2plus_photos":multi,
            "with_5plus_photos":sum(n>=5 for n in counts),
            "single_photo_watches":sum(n==1 for n in counts),
            "album_backed_watches":album_backed,
            "max_images_for_one_watch":max(counts,default=0),
            "minimum_multi_photo_watches":minimum,
            "by_factory":dict(sorted(factories.items())),
            "oauth_configured":bool(os.environ.get("REDDIT_CLIENT_ID") and os.environ.get("REDDIT_CLIENT_SECRET")),
            "note":"Replica evidence is a stress test only and never moves genuine-derived limits.",
        }
    output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    return result


def run(model: str, root: Path, fresh=False) -> dict:
    requested=contracts.exact_model(model,"requested model")
    cp=config_path(requested)
    config=json.loads(cp.read_text(encoding="utf-8"))
    contracts.validate_config(config,requested,cp)
    P=paths(requested,root)
    if fresh and P["base"].exists(): shutil.rmtree(P["base"])
    if P["base"].exists() and P["snapshot"].exists():
        raise SystemExit(f"immutable snapshot already exists for this workspace: {P['snapshot']}; use --fresh or replay it")
    P["base"].mkdir(parents=True,exist_ok=True)

    # Freeze code/config provenance before any network work.
    manifest=manifest_tools.build(config,cp,P["base"],REPO)
    manifest_tools.save(manifest,P["manifest"])

    # 1) Discover from approved public sources.
    d=discover(config,P["pool"])
    d=enrich_reddit_evidence(config,P["pool"],d)
    contracts.validate_csv_exact_model(P["pool"],requested,"discovery candidates")
    if d["by_class"].get("gen",0) < config["discovery"].get("minimum_gen_candidates",1):
        status={"model":requested,"state":"NEEDS_MORE_SOURCES","stage":"discovery","discovery":d}
        empty={"model":requested,"family":config["family"],"state":"NO_CALIBRATABLE_METRICS","method":"all_reliable_genuine_photo_envelope_v1","metrics":{}}
        save_genuine_envelope(empty,P["cal"])
        (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n");return status

    # 2) Acquire. Caches may help acquisition speed, but they are never the evidence identity.
    if config.get("acquisition_adapter")!="submariner_acquire_v3":
        raise SystemExit(f"Unsupported acquisition adapter {config.get('acquisition_adapter')}")
    sh([sys.executable,REPO/"tools/watch_calibrator/acquire.py","--pool",P["pool"],"--out",P["acq"],"--max-images",str(config["discovery"].get("max_images_per_watch",12))])
    acquired=P["acq"]/"acquired_images.csv"
    contracts.validate_csv_exact_model(acquired,requested,"acquired images")
    manifest["evidence_manifest"]=manifest_tools.evidence_manifest_identity(acquired,P["base"])
    manifest_tools.save(manifest,P["manifest"])
    rep_ev=replica_evidence(config,P["acq"],P["replica"])

    # 3) Lock watch-level partitioning before snapshot creation so replay freezes the exact split.
    if not P["split"].exists():
        locked_split.create(locked_split.watches_from_acquisition(acquired),P["split"])

    # 4) Freeze exact bytes. From this point on, this run has a durable replay identity independent
    # of dealer/CDN behaviour. The live workspace remains the measurement source for this run, but
    # every byte is verified while entering the snapshot.
    snapshot=evidence_snapshot.create(
        requested,config["family"],cp,acquired,P["acq"],P["split"],P["snapshot"]
    )
    manifest_tools.attach_snapshot(manifest,snapshot,P["snapshot"],P["base"])
    manifest_tools.save(manifest,P["manifest"])

    # 5) Measure/calibrate through the same execution function used by offline replay.
    execution=calibration_execution.execute(config,P["acq"],P["split"],P["geom"],P["cal"],P["base"])
    final=execution.pop("final")
    manifest_tools.bind_measurement_adapter(manifest,config,execution["measurement_adapter"])
    manifest_tools.save(manifest,P["manifest"])

    status={
        "model":requested,"state":final["state"],"stage":"complete","discovery":d,
        "replica_evidence":rep_ev,**execution,
        "calibration":str(P["cal"]),"run_manifest":str(P["manifest"]),
        "evidence_snapshot":str(P["snapshot"]),"snapshot_id":snapshot["snapshot_id"],
    }
    (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n",encoding="utf-8")
    return status


def main(argv=None):
    ap=argparse.ArgumentParser();ap.add_argument("model");ap.add_argument("--root",type=Path,default=REPO/"datasets"/"watch_calibrator");ap.add_argument("--fresh",action="store_true");a=ap.parse_args(argv)
    print(json.dumps(run(a.model,a.root,a.fresh),indent=2));return 0

if __name__=="__main__": raise SystemExit(main())
