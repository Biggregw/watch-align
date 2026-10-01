#!/usr/bin/env python3
"""End-to-end autonomous Watch Align family calibrator.

Input is a model id only. The model config defines approved source discovery, layout, measurement
adapter and calibration metrics. The runner discovers source watches, acquires images, locks a
watch-level split, measures development/validation, freezes candidate limits, and only then opens
holdout. It never lets replica data move a limit.
"""
from __future__ import annotations

import argparse, json, shutil, subprocess, sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
REPO=HERE.parents[1]
sys.path.insert(0,str(HERE))
from discover import discover  # noqa: E402
from geometry import extract  # noqa: E402
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
            "devval":base/"measure_devval","holdout":base/"measure_holdout","geom":base/"geometry","cal":base/"calibration.json"}


def measure(config, root, out, split, partitions, include_holdout=False):
    if config.get("measurement_adapter")!="submariner_research_v2":
        raise SystemExit(f"Unsupported measurement adapter {config.get('measurement_adapter')}")
    cmd=[sys.executable,REPO/"tools/dataset_harvester/submariner_measure.py","--root",root,"--out",out,"--split",split,
         "--model",str(config["model"]),"--variants",config.get("variants","orig,s94,s88,x+1,x-1,x+2,x-2,y+1,y-1,y+2,y-2,r+5,r-5"),
         "--shards",str(config.get("shards",2))]
    for p in partitions: cmd += ["--partition",p]
    if include_holdout: cmd += ["--include-holdout"]
    sh(cmd)


def run(model: str, root: Path, fresh=False) -> dict:
    cp=config_path(model); config=json.loads(cp.read_text(encoding="utf-8")); P=paths(model,root)
    if fresh and P["base"].exists(): shutil.rmtree(P["base"])
    P["base"].mkdir(parents=True,exist_ok=True)

    # 1) Discover from the public web. No manually supplied pool is required.
    d=discover(config,P["pool"])
    if d["by_class"].get("gen",0) < config["discovery"].get("minimum_gen_candidates",1):
        status={"model":model,"state":"NEEDS_MORE_SOURCES","stage":"discovery","discovery":d}
        save({"model":model,"family":config["family"],"state":"NO_CALIBRATABLE_METRICS","metrics":{},"policy":config["calibration_policy"]},P["cal"])
        (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n");return status

    # 2) Acquire. This adapter already performs provenance-aware dealer/Imgur resolution and exact hashes.
    if config.get("acquisition_adapter")!="submariner_acquire_v2":
        raise SystemExit(f"Unsupported acquisition adapter {config.get('acquisition_adapter')}")
    sh([sys.executable,REPO/"tools/dataset_harvester/submariner_acquire.py","--pool",P["pool"],"--out",P["acq"],"--max-images",str(config["discovery"].get("max_images_per_watch",12))])

    # 3) Build dataset and lock a watch-level split once. The split file is the boundary between discovery and calibration.
    if not P["split"].exists():
        sh([sys.executable,REPO/"tools/dataset_harvester/submariner_measure.py","--root",P["acq"],"--out",P["base"]/"dataset_stage",
            "--split",P["split"],"--create-split","--dataset-only"])

    # 4) Development + validation only. Holdout remains unopened.
    measure(config,P["acq"],P["devval"],P["split"],["development","validation"],False)
    gd=extract(P["devval"],P["geom"],config,"development","gen")
    gv=extract(P["devval"],P["geom"],config,"validation","gen")
    try: rd=extract(P["devval"],P["geom"],config,"development","rep")
    except BaseException: rd=None

    pref=f"{config['model']}_"
    frozen=propose(config,
        P["geom"]/f"{pref}development_gen_watch.csv",
        P["geom"]/f"{pref}development_gen_repeatability.csv",
        P["geom"]/f"{pref}validation_gen_watch.csv",
        P["geom"]/f"{pref}development_rep_watch.csv" if rd else None)
    save(frozen,P["base"]/"frozen_before_holdout.json")
    if frozen["state"]!="FROZEN_PENDING_HOLDOUT":
        save(frozen,P["cal"])
        status={"model":model,"state":frozen["state"],"stage":"validation","discovery":d,"development":gd,"validation":gv}
        (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n");return status

    # 5) Limits are now frozen. Open holdout exactly once for confirmation. No value below can move a limit.
    measure(config,P["acq"],P["holdout"],P["split"],["holdout"],True)
    gh=extract(P["holdout"],P["geom"],config,"holdout","gen")
    final=finalize(frozen,P["geom"]/f"{pref}holdout_gen_watch.csv")
    save(final,P["cal"])
    status={"model":model,"state":final["state"],"stage":"complete","discovery":d,"development":gd,"validation":gv,"holdout":gh,
            "calibration":str(P["cal"])}
    (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n",encoding="utf-8")
    return status


def main(argv=None):
    ap=argparse.ArgumentParser();ap.add_argument("model");ap.add_argument("--root",type=Path,default=REPO/"datasets"/"watch_calibrator");ap.add_argument("--fresh",action="store_true");a=ap.parse_args(argv)
    print(json.dumps(run(a.model,a.root,a.fresh),indent=2));return 0

if __name__=="__main__": raise SystemExit(main())
