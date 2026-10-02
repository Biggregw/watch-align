#!/usr/bin/env python3
"""End-to-end autonomous Watch Align family calibrator.

Input is a model id only. The model config defines approved source discovery, layout, measurement
adapter and calibration metrics. Measurement runs the production app route (desktop harness
CalibMeasure), so only values that pass the app's reliability gates can shape a limit. The runner discovers source watches, acquires images, locks a
watch-level split, measures development/validation, freezes candidate limits, and only then opens
holdout. It never lets replica data move a limit.
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
from production_measure import measure as production_measure  # noqa: E402
from calibrate import propose, finalize, save  # noqa: E402


def sh(cmd, cwd=REPO):
    print("+"," ".join(map(str,cmd)),flush=True)
    subprocess.run([str(x) for x in cmd],cwd=cwd,check=True)


def config_path(model: str)->Path:
    p=REPO/"calibration"/"models"/f"{model.upper()}.json"
    if not p.exists(): raise SystemExit(f"No calibrator config for {model}: {p}")
    return p


def paths(model: str, root: Path):
    m=model.upper();base=root/m
    return {"base":base,"pool":base/"discovered_candidates.csv","acq":base/"dataset","split":base/"locked_split.csv",
            "geom":base/"geometry","cal":base/"calibration.json",
            "replica":base/"replica_evidence.json"}


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
    cp=config_path(model); config=json.loads(cp.read_text(encoding="utf-8")); P=paths(model,root)
    if fresh and P["base"].exists(): shutil.rmtree(P["base"])
    P["base"].mkdir(parents=True,exist_ok=True)

    # 1) Discover from the public web. No manually supplied pool is required. RepTimeQC evidence
    # is then enriched with independently discovered album-backed posts before native single-image
    # posts are used as fallback.
    d=discover(config,P["pool"])
    d=enrich_reddit_evidence(config,P["pool"],d)
    if d["by_class"].get("gen",0) < config["discovery"].get("minimum_gen_candidates",1):
        status={"model":model,"state":"NEEDS_MORE_SOURCES","stage":"discovery","discovery":d}
        save({"model":model,"family":config["family"],"state":"NO_CALIBRATABLE_METRICS","metrics":{},"policy":config["calibration_policy"]},P["cal"])
        (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n");return status

    # 2) Acquire. v3 resolves Imgur albums, official Reddit OAuth galleries when credentials are
    # available, and the image URL the official API gave discovery. No anonymous Reddit surfaces.
    if config.get("acquisition_adapter")!="submariner_acquire_v3":
        raise SystemExit(f"Unsupported acquisition adapter {config.get('acquisition_adapter')}")
    sh([sys.executable,REPO/"tools/watch_calibrator/acquire.py","--pool",P["pool"],"--out",P["acq"],"--max-images",str(config["discovery"].get("max_images_per_watch",12))])
    rep_ev=replica_evidence(config,P["acq"],P["replica"])

    # 3) Lock a watch-level split once. The split file is the boundary between discovery and calibration.
    if not P["split"].exists():
        locked_split.create(locked_split.watches_from_acquisition(P["acq"]/"acquired_images.csv"),P["split"])

    # 4) Development + validation only, measured through the production app route (alpha70 gates).
    # Holdout remains unopened.
    if config.get("measurement_adapter")!="production_app_route_v1":
        raise SystemExit(f"Unsupported measurement adapter {config.get('measurement_adapter')}")
    gd=production_measure(config,P["acq"],P["split"],P["geom"],"development","gen")
    gv=production_measure(config,P["acq"],P["split"],P["geom"],"validation","gen")
    rd=production_measure(config,P["acq"],P["split"],P["geom"],"development","rep")
    rv=production_measure(config,P["acq"],P["split"],P["geom"],"validation","rep")
    # Replica stress evidence (never moves a limit): development + validation replicas together.
    rep_csv=P["geom"]/f"{config['model']}_devval_rep_watch.csv"
    with rep_csv.open("w",newline="",encoding="utf-8") as out:
        w=None
        for part in ("development","validation"):
            src=P["geom"]/f"{config['model']}_{part}_rep_watch.csv"
            if not src.exists(): continue
            with src.open(newline="",encoding="utf-8") as fh:
                for row in csv.DictReader(fh):
                    if w is None: w=csv.DictWriter(out,fieldnames=list(row));w.writeheader()
                    w.writerow(row)

    pref=f"{config['model']}_"
    frozen=propose(config,
        P["geom"]/f"{pref}development_gen_watch.csv",
        P["geom"]/f"{pref}development_gen_repeatability.csv",
        P["geom"]/f"{pref}validation_gen_watch.csv",
        rep_csv if (rd.get("photos") or rv.get("photos")) else None,
        evidence_root=REPO)
    save(frozen,P["base"]/"frozen_before_holdout.json")
    if frozen["state"]!="FROZEN_PENDING_HOLDOUT":
        save(frozen,P["cal"])
        status={"model":model,"state":frozen["state"],"stage":"validation","discovery":d,"replica_evidence":rep_ev,
                "development":gd,"validation":gv,"replica_measured":{"development":rd,"validation":rv}}
        (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n");return status

    # 5) Limits are now frozen. Open holdout exactly once for confirmation. No value below can move a limit.
    gh=production_measure(config,P["acq"],P["split"],P["geom"],"holdout","gen")
    final=finalize(frozen,P["geom"]/f"{pref}holdout_gen_watch.csv")
    save(final,P["cal"])
    status={"model":model,"state":final["state"],"stage":"complete","discovery":d,"replica_evidence":rep_ev,
            "development":gd,"validation":gv,"replica_measured":{"development":rd,"validation":rv},"holdout":gh,"calibration":str(P["cal"])}
    (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n",encoding="utf-8")
    return status


def main(argv=None):
    ap=argparse.ArgumentParser();ap.add_argument("model");ap.add_argument("--root",type=Path,default=REPO/"datasets"/"watch_calibrator");ap.add_argument("--fresh",action="store_true");a=ap.parse_args(argv)
    print(json.dumps(run(a.model,a.root,a.fresh),indent=2));return 0

if __name__=="__main__": raise SystemExit(main())
