"""Model-neutral geometry extraction wrapper over the mature research measurement backend.

The current adapter is submariner_research_v2. Layout comes from model config, so 124060 and
future 12-series Submariners can reuse the same calibration machinery without copying analysis code.
"""
from __future__ import annotations

import json, sys
from collections import defaultdict
from pathlib import Path

HERE=Path(__file__).resolve().parent
HARV=HERE.parent/"dataset_harvester"
sys.path.insert(0,str(HARV))
from subresearch import geometry124060 as G  # noqa: E402


def empty_tables(out_dir: Path, prefix: str):
    out_dir.mkdir(parents=True,exist_ok=True)
    G.write(out_dir/f"{prefix}_photo.csv",[],["metric"])
    G.write(out_dir/f"{prefix}_watch.csv",[],["physical_watch_id","metric","median"])
    G.write(out_dir/f"{prefix}_repeatability.csv",[],["metric"])


def extract(measure_dir: Path, out_dir: Path, config: dict, partition: str, class_tag: str="gen") -> dict:
    if config.get("measurement_adapter")!="submariner_research_v2":
        raise ValueError(f"unsupported measurement adapter: {config.get('measurement_adapter')}")
    G.SCOPE={"family":config["family"],"model":str(config["model"]).upper(),"class_tag":class_tag,"partition":partition}
    G.BATON_HOURS=tuple(config["layout"].get("batons",[]))
    G.ROUND_HOURS=tuple(config["layout"].get("rounds",[]))
    prefix=f"{config['model']}_{partition}_{class_tag}"
    try:
        imgs,lms=G.load(measure_dir)
    except SystemExit:
        empty_tables(out_dir,prefix)
        result={"model":config["model"],"partition":partition,"class_tag":class_tag,"photos":0,"watches":0,"photos_by_watch":{},"excluded":{},
                "photo_csv":str(out_dir/f"{prefix}_photo.csv"),"watch_csv":str(out_dir/f"{prefix}_watch.csv"),"repeatability_csv":str(out_dir/f"{prefix}_repeatability.csv")}
        out_dir.mkdir(parents=True,exist_ok=True);(out_dir/f"{prefix}_summary.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8");return result
    # Autonomous path deliberately has no hand-curated exclusion file. It relies on detector/fit
    # confidence and synthetic repeatability; weak metrics are rejected later by calibration gates.
    photos,excluded=G.photo_records(imgs,lms,{})
    photo_rows,watch_rows,rep_rows=G.analyse(photos)
    out_dir.mkdir(parents=True,exist_ok=True)
    G.write(out_dir/f"{prefix}_photo.csv",photo_rows)
    G.write(out_dir/f"{prefix}_watch.csv",watch_rows)
    G.write(out_dir/f"{prefix}_repeatability.csv",rep_rows)
    by_watch=defaultdict(int)
    for p in photos: by_watch[p["physical_watch_id"]]+=1
    result={"model":config["model"],"partition":partition,"class_tag":class_tag,"photos":len(photos),"watches":len(by_watch),
            "photos_by_watch":dict(sorted(by_watch.items())),"excluded":{k:len(v) for k,v in excluded.items()},
            "photo_csv":str(out_dir/f"{prefix}_photo.csv"),"watch_csv":str(out_dir/f"{prefix}_watch.csv"),"repeatability_csv":str(out_dir/f"{prefix}_repeatability.csv")}
    (out_dir/f"{prefix}_summary.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    return result


def main(argv=None):
    import argparse
    ap=argparse.ArgumentParser();ap.add_argument("measure_dir",type=Path);ap.add_argument("out_dir",type=Path);ap.add_argument("config",type=Path);ap.add_argument("partition");ap.add_argument("--class-tag",default="gen");a=ap.parse_args(argv)
    c=json.loads(a.config.read_text());print(json.dumps(extract(a.measure_dir,a.out_dir,c,a.partition,a.class_tag),indent=2));return 0

if __name__=="__main__": raise SystemExit(main())
