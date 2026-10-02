#!/usr/bin/env python3
"""End-to-end autonomous Watch Align family calibrator.

Input is a model id only. The model config defines approved source discovery, layout, measurement
adapter and calibration metrics. Measurement runs the production app route (desktop harness
CalibMeasure), so only values that pass the app's reliability gates can shape a limit.

The locked development/validation/holdout split is retained as a diagnostic for generalisation, but
it no longer decides which genuine observations count as normal. After all three genuine partitions
have been measured, the final production envelope is fitted from every reliable genuine photo.
Only obvious within-watch photo-level failures may be removed. Replica data is stress evidence only
and never moves a genuine-derived limit.
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
from calibrate import propose as legacy_propose, finalize as legacy_finalize, save as save_legacy  # noqa: E402
from genuine_envelope import build as build_genuine_envelope, save as save_genuine_envelope  # noqa: E402


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

    # 1) Discover from approved public sources. RepTimeQC evidence is enriched with independently
    # discovered album-backed posts before native single-image posts are used as fallback.
    d=discover(config,P["pool"])
    d=enrich_reddit_evidence(config,P["pool"],d)
    if d["by_class"].get("gen",0) < config["discovery"].get("minimum_gen_candidates",1):
        status={"model":model,"state":"NEEDS_MORE_SOURCES","stage":"discovery","discovery":d}
        empty={"model":model,"family":config["family"],"state":"NO_CALIBRATABLE_METRICS","method":"all_reliable_genuine_photo_envelope_v1","metrics":{}}
        save_genuine_envelope(empty,P["cal"])
        (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n");return status

    # 2) Acquire. No anonymous Reddit surfaces and no search-engine result pages are used.
    if config.get("acquisition_adapter")!="submariner_acquire_v3":
        raise SystemExit(f"Unsupported acquisition adapter {config.get('acquisition_adapter')}")
    sh([sys.executable,REPO/"tools/watch_calibrator/acquire.py","--pool",P["pool"],"--out",P["acq"],"--max-images",str(config["discovery"].get("max_images_per_watch",12))])
    rep_ev=replica_evidence(config,P["acq"],P["replica"])

    # 3) Lock a watch-level split once. It is now a diagnostic boundary, not permission to ignore
    # a valid genuine observation in the final envelope.
    if not P["split"].exists():
        locked_split.create(locked_split.watches_from_acquisition(P["acq"]/"acquired_images.csv"),P["split"])

    # 4) Measure every genuine partition through the production app route. A value exists only if
    # the app's reliability gates would expose it to the user.
    if config.get("measurement_adapter")!="production_app_route_v1":
        raise SystemExit(f"Unsupported measurement adapter {config.get('measurement_adapter')}")
    gd=production_measure(config,P["acq"],P["split"],P["geom"],"development","gen")
    gv=production_measure(config,P["acq"],P["split"],P["geom"],"validation","gen")
    gh=production_measure(config,P["acq"],P["split"],P["geom"],"holdout","gen")
    rd=production_measure(config,P["acq"],P["split"],P["geom"],"development","rep")
    rv=production_measure(config,P["acq"],P["split"],P["geom"],"validation","rep")

    pref=f"{config['model']}_"

    # 5) Preserve the old split/freeze calculation as an audit comparison only. It can show which
    # validation/holdout genuine values would have rejected the old narrow statistical band, but it
    # no longer controls the production calibration.
    rep_watch=P["geom"]/f"{config['model']}_devval_rep_watch.csv"
    with rep_watch.open("w",newline="",encoding="utf-8") as out:
        writer=None
        for part in ("development","validation"):
            src=P["geom"]/f"{config['model']}_{part}_rep_watch.csv"
            if not src.exists(): continue
            with src.open(newline="",encoding="utf-8") as fh:
                for row in csv.DictReader(fh):
                    if writer is None:
                        writer=csv.DictWriter(out,fieldnames=list(row));writer.writeheader()
                    writer.writerow(row)

    legacy=legacy_propose(config,
        P["geom"]/f"{pref}development_gen_watch.csv",
        P["geom"]/f"{pref}development_gen_repeatability.csv",
        P["geom"]/f"{pref}validation_gen_watch.csv",
        rep_watch if (rd.get("photos") or rv.get("photos")) else None,
        evidence_root=REPO)
    save_legacy(legacy,P["base"]/"frozen_before_holdout.json")
    if legacy.get("state")=="FROZEN_PENDING_HOLDOUT":
        legacy=legacy_finalize(legacy,P["geom"]/f"{pref}holdout_gen_watch.csv")
    save_legacy(legacy,P["base"]/"legacy_split_calibration.json")

    # 6) Final production calibration: every reliable genuine photo from every split defines the
    # accepted genuine envelope. Only obvious within-watch measurement spikes may be discarded.
    gen_photo={
        part:P["geom"]/f"{pref}{part}_gen_photo.csv"
        for part in ("development","validation","holdout")
    }
    gen_repeat={
        part:P["geom"]/f"{pref}{part}_gen_repeatability.csv"
        for part in ("development","validation","holdout")
    }
    rep_photo={
        part:P["geom"]/f"{pref}{part}_rep_photo.csv"
        for part in ("development","validation")
    }
    final=build_genuine_envelope(config,gen_photo,P["split"],gen_repeat,rep_photo)
    save_genuine_envelope(final,P["cal"])

    status={"model":model,"state":final["state"],"stage":"complete","discovery":d,"replica_evidence":rep_ev,
            "development":gd,"validation":gv,"holdout":gh,
            "replica_measured":{"development":rd,"validation":rv},
            "legacy_split_state":legacy.get("state"),
            "calibration":str(P["cal"])}
    (P["base"]/"run_status.json").write_text(json.dumps(status,indent=2)+"\n",encoding="utf-8")
    return status


def main(argv=None):
    ap=argparse.ArgumentParser();ap.add_argument("model");ap.add_argument("--root",type=Path,default=REPO/"datasets"/"watch_calibrator");ap.add_argument("--fresh",action="store_true");a=ap.parse_args(argv)
    print(json.dumps(run(a.model,a.root,a.fresh),indent=2));return 0

if __name__=="__main__": raise SystemExit(main())
