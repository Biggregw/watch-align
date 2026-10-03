import csv
import hashlib
import json
import socket
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import calibration_execution  # noqa: E402
import evidence_snapshot  # noqa: E402
import measurement_contract as mc  # noqa: E402
import network_isolation  # noqa: E402
import production_measure  # noqa: E402
import replay  # noqa: E402
import replay_acceptance as ra  # noqa: E402
import run_manifest as manifest_tools  # noqa: E402
import submariner12_measurement_adapter as sub12  # noqa: E402

MODEL="124060"
LIVE="production_app_route_v1"
CANDIDATE="submariner12_measured_v1"
# Two photos for one genuine watch in each genuine partition. Replica partitions stay empty.
WATCHES=(("w1","development"),("w2","validation"),("w3","holdout"))


def config_value():
    return {
        "model":MODEL,"family":"submariner_12",
        "acquisition_adapter":"submariner_acquire_v3","measurement_adapter":LIVE,
        "layout":{"triangle":[12]},
        "discovery":{"genuine_source_diversity":{
            "minimum_sources":1,"minimum_watches_per_source":1,"minimum_acquired_watches":1,
            "max_single_source_share":1.0}},
        "calibration_policy":{
            "min_development_watches":1,"min_validation_watches":1,"min_holdout_watches":1,
            "clear_sigma":3.0,"check_sigma":5.0,"check_over_clear":1.5,
            "validation_clear_rate_min":0.75,"validation_check_rate_min":1.0,
            "holdout_clear_rate_min":0.75,"holdout_check_rate_min":1.0,"outlier_mad_k":3.5},
        "calibration_metrics":[
            {"metric":"m.a","app_key":"m.a","sided":"two","minimum_half_width":0.1,"allow_pose_sensitive":False},
            {"metric":"m.b","app_key":"m.b","sided":"upper","minimum_half_width":0.1,"allow_pose_sensitive":False},
        ],
    }


def write_csv(path,fields,rows):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=fields);w.writeheader();w.writerows(rows)


def value(photo):
    # Deterministic per-image value; m.b is always measured but withheld by the reliability policy.
    n=int(hashlib.sha256(Path(photo).name.encode()+Path(photo).parent.name.encode()).hexdigest()[:4],16)
    return 1.0+n/65536.0, 0.5+n/131072.0


def legacy_harness(values=value):
    def fake(model,photos,out_csv):
        rows=[]
        for wid,photo in photos:
            a,_=values(photo)
            rows.append({"physical_watch_id":wid,"model":model,"path":str(photo).replace(",",";"),
                         "dial_source":"AUTO_EDGE_FIT","dial_reproducible":"true","pose_tilt_deg":"2.000000",
                         "m.a":f"{a:.6f}","m.b":""})
        write_csv(out_csv,["physical_watch_id","model","path","dial_source","dial_reproducible","pose_tilt_deg","m.a","m.b"],rows)
    return fake


def contract_harness(withhold_all=False,values=value):
    def fake(model,photos,out_csv):
        rows=[]
        for wid,photo in photos:
            a,b=values(photo)
            base={"schema_version":"1","physical_watch_id":wid,"model":model,"family":"submariner_12",
                  "path":str(photo).replace(",",";"),"adapter_id":CANDIDATE,"adapter_version":"1",
                  "reliability_policy":"sub124060_production_reliability_v1","dial_source":"AUTO_EDGE_FIT",
                  "dial_reproducible":"true","pose_tilt_deg":"2.000000",
                  "expected_layout":"submariner_124060_no_date_v1","observed_layout_state":"compatible_no_date"}
            if withhold_all:
                rows.append({**base,"metric":"m.a","raw_value":f"{a:.6f}","reliability_state":"withheld",
                             "reliability_reason":"test withheld","eligible_value":""})
            else:
                rows.append({**base,"metric":"m.a","raw_value":f"{a:.6f}","reliability_state":"accepted",
                             "reliability_reason":"","eligible_value":f"{a:.6f}"})
            rows.append({**base,"metric":"m.b","raw_value":f"{b:.6f}","reliability_state":"withheld",
                         "reliability_reason":"resize repeatability failed","eligible_value":""})
        write_csv(out_csv,mc.FIELDS,rows)
    return fake


def build_live(root,photos=2,values=value):
    """The post-acquisition half of run.run(): freeze, measure through the legacy route, record."""
    base=root/"live"/MODEL;acq=base/"dataset";base.mkdir(parents=True)
    acquired_rows,split_rows=[],[]
    for wid,partition in WATCHES:
        for i in range(1,photos+1):
            data=f"{wid}-{i}".encode()
            local=f"images/{wid}/{i:02d}.jpg";(acq/local).parent.mkdir(parents=True,exist_ok=True);(acq/local).write_bytes(data)
            acquired_rows.append({"physical_watch_id":wid,"candidate_id":wid,"model":MODEL,"family":"submariner_12",
                "class_label":"gen","local_path":local,"sha256":hashlib.sha256(data).hexdigest(),"bytes":str(len(data)),
                "width":"10","height":"10","exact_duplicate_of":"","acquisition_status":"acquired"})
        split_rows.append({"physical_watch_id":wid,"partition":partition,"stratum":f"gen/{MODEL}/s{wid}",
            "class_label":"gen","model":MODEL,"factory":"","source_name":f"s{wid}","added_in":"locked_split"})
    acquired=acq/"acquired_images.csv";write_csv(acquired,list(acquired_rows[0]),acquired_rows)
    split=base/"locked_split.csv";write_csv(split,list(split_rows[0]),split_rows)
    cfg_path=root/f"{MODEL}.json";config=config_value();cfg_path.write_text(json.dumps(config,indent=2)+"\n")

    manifest=manifest_tools.build(config,cfg_path,base,HERE.parents[1])
    manifest["evidence_manifest"]=manifest_tools.evidence_manifest_identity(acquired,base)
    snap=evidence_snapshot.create(MODEL,"submariner_12",cfg_path,acquired,acq,split,base/"evidence_snapshot_v1")
    manifest_tools.attach_snapshot(manifest,snap,base/"evidence_snapshot_v1",base)
    with mock.patch.object(production_measure,"run_harness",side_effect=legacy_harness(values)):
        execution=calibration_execution.execute(config,acq,split,base/"geometry",base/"calibration.json",base)
    final=execution.pop("final")
    manifest_tools.bind_measurement_adapter(manifest,config,execution["measurement_adapter"])
    manifest_tools.save(manifest,base/"run_manifest.json")
    status={"model":MODEL,"state":final["state"],**execution,"calibration":str(base/"calibration.json"),
            "snapshot_id":snap["snapshot_id"]}
    (base/"run_status.json").write_text(json.dumps(status,indent=2)+"\n")
    return base


def build(root,adapter=CANDIDATE,withhold_all=False,photos=2,values=value):
    live=build_live(root,photos,values)
    with mock.patch.object(sub12,"run_harness",side_effect=contract_harness(withhold_all,values)):
        replay.replay(live/"evidence_snapshot_v1",root/"replay",fresh=True,measurement_adapter_id=adapter)
    return live,root/"replay"/MODEL


class ReplayAcceptanceTest(unittest.TestCase):
    def test_accepts_equivalent_measured_only_replay(self):
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td))
            report=ra.verify(live,rep,LIVE,CANDIDATE)
            self.assertEqual("ACCEPTED",report["state"])
            self.assertEqual(6,report["verified_images"])
            self.assertEqual(CANDIDATE,report["replay_adapter"]["id"])
            self.assertEqual({"photos":6,"metric_rows":12,"raw_values":12,"eligible_values":6},report["contract_totals"])

    def test_rejected_outlier_keeps_live_and_replay_calibration_identical(self):
        # Five photos per watch; one obvious spike on w1 is rejected from both workspaces. Rejected
        # photos must be identified by manifest identity, not by live/replay workspace paths.
        def values(photo):
            spike=Path(photo).parent.name=="w1" and Path(photo).name=="05.jpg"
            return (5.0 if spike else 1.0+int(Path(photo).stem)/1000.0),0.5
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td),photos=5,values=values)
            rejected=json.loads((rep/"calibration.json").read_text())["metrics"]["m.a"]["obvious_photo_outliers"]
            self.assertEqual([("w1","images/w1/05.jpg")],[(r["physical_watch_id"],r["local_path"]) for r in rejected])
            self.assertEqual(hashlib.sha256(b"w1-5").hexdigest(),rejected[0]["image_sha256"])
            self.assertEqual((live/"calibration.json").read_bytes(),(rep/"calibration.json").read_bytes())
            self.assertEqual("ACCEPTED",ra.verify(live,rep,LIVE,CANDIDATE)["state"])

    def test_refuses_vacuous_comparison_and_contractless_candidate(self):
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td))
            with self.assertRaisesRegex(ra.AcceptanceError,"vacuous"):
                ra.verify(live,rep,CANDIDATE,CANDIDATE)
            with self.assertRaisesRegex(ra.AcceptanceError,"does not emit a measurement contract"):
                ra.verify(live,rep,CANDIDATE,LIVE)

    def test_rejects_replay_that_did_not_use_the_candidate_adapter(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);live=build_live(root)
            with mock.patch.object(production_measure,"run_harness",side_effect=legacy_harness()):
                replay.replay(live/"evidence_snapshot_v1",root/"replay",fresh=True)
            with self.assertRaisesRegex(ra.AcceptanceError,"replay override None"):
                ra.verify(live,root/"replay"/MODEL,LIVE,CANDIDATE)

    def test_rejects_calibration_and_snapshot_identity_differences(self):
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td))
            with (rep/"calibration.json").open("a") as fh:
                fh.write(" ")
            status=json.loads((rep/"run_status.json").read_text());status["snapshot_id"]="0"*64
            (rep/"run_status.json").write_text(json.dumps(status))
            with self.assertRaises(ra.AcceptanceError) as ctx:
                ra.verify(live,rep,LIVE,CANDIDATE)
            self.assertIn("calibration.json SHA-256 differs",str(ctx.exception))
            self.assertIn("replay run_status snapshot_id",str(ctx.exception))

    def test_rejects_empty_genuine_contract(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            live=build_live(root)
            # Live values are present, so make the live route measure nothing too: the point here is the
            # contract emptiness check, not the calibration comparison.
            with mock.patch.object(sub12,"run_harness",side_effect=contract_harness(withhold_all=True)):
                replay.replay(live/"evidence_snapshot_v1",root/"replay",fresh=True,measurement_adapter_id=CANDIDATE)
            with self.assertRaisesRegex(ra.AcceptanceError,"genuine contract has no eligible values"):
                ra.verify(live,root/"replay"/MODEL,LIVE,CANDIDATE)

    def test_rejects_engine_input_not_derived_from_contract(self):
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td))
            wide=rep/"geometry"/f"{MODEL}_development_gen_photo.csv"
            with wide.open(newline="") as fh:
                rows=list(csv.DictReader(fh))
            rows[0]["m.b"]="0.500000"
            write_csv(wide,list(rows[0]),rows)
            with self.assertRaisesRegex(ra.AcceptanceError,"not exactly the contract's eligible values"):
                ra.verify(live,rep,LIVE,CANDIDATE)

    def test_rejects_tampered_contract_and_mutated_evidence(self):
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td))
            contract=mc.output_path(rep/"geometry",MODEL,"validation","gen")
            with contract.open(newline="") as fh:
                rows=list(csv.DictReader(fh))
            withheld=next(r for r in rows if r["reliability_state"]=="withheld");withheld["eligible_value"]=withheld["raw_value"]
            write_csv(contract,mc.FIELDS,rows)
            (rep/"dataset"/"images"/"w1"/"01.jpg").write_bytes(b"changed")
            with self.assertRaises(ra.AcceptanceError) as ctx:
                ra.verify(live,rep,LIVE,CANDIDATE)
            self.assertIn("validation/gen: contract does not re-validate",str(ctx.exception))
            self.assertIn("is not the frozen snapshot object",str(ctx.exception))

    def test_requires_replay_workspace_as_recorded(self):
        with tempfile.TemporaryDirectory() as td:
            live,rep=build(Path(td))
            with self.assertRaisesRegex(ra.AcceptanceError,"as recorded"):
                ra.verify(live,rep/".."/MODEL,LIVE,CANDIDATE)


class OfflineBoundaryTest(unittest.TestCase):
    def test_replay_import_graph_has_no_acquisition_or_network_client(self):
        code=("import sys;sys.path.insert(0,sys.argv[1]);import replay,replay_acceptance;"
              "print('\\n'.join(sorted(sys.modules)))")
        loaded=set(subprocess.check_output([sys.executable,"-c",code,str(HERE)],text=True).split())
        forbidden={"discover","acquire","dealer_media","reddit_evidence","reddit_oauth",
                   "requests","urllib3","bs4","urllib.request","http.client"}
        self.assertEqual(set(),loaded&forbidden)

    def test_network_probe_reports_any_reachable_network(self):
        with mock.patch.object(socket,"if_nameindex",return_value=[(1,"lo")]), \
             mock.patch.object(socket,"create_connection",side_effect=OSError("unreachable")), \
             mock.patch.object(socket,"getaddrinfo",side_effect=socket.gaierror("no resolver")):
            self.assertEqual([],network_isolation.problems())
        with mock.patch.object(socket,"if_nameindex",return_value=[(1,"lo"),(2,"eth0")]), \
             mock.patch.object(socket,"create_connection",return_value=mock.Mock()), \
             mock.patch.object(socket,"getaddrinfo",return_value=[()]):
            found=network_isolation.problems()
        self.assertEqual(5,len(found))


if __name__=="__main__": unittest.main()
