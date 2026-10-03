import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import sys

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import evidence_snapshot as es  # noqa: E402


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)


class EvidenceSnapshotTest(unittest.TestCase):
    def fixture(self, root: Path):
        acq=root/"dataset";acq.mkdir()
        img=acq/"images"/"w1"/"01.jpg";img.parent.mkdir(parents=True);img.write_bytes(b"watch-image")
        digest=sha(b"watch-image")
        rows=[{
            "candidate_id":"w1","physical_watch_id":"w1","model":"124060","family":"submariner_12",
            "class_label":"gen","source_name":"source","source_url":"https://example.test/watch",
            "image_index":"1","image_url":"https://example.test/image.jpg","sha256":digest,
            "local_path":"images/w1/01.jpg","width":"1000","height":"1000","bytes":str(len(b"watch-image")),
            "exact_duplicate_of":"","acquisition_status":"acquired","acquisition_note":"",
        }]
        acquired=acq/"acquired_images.csv";write_csv(acquired,rows)
        split=root/"locked_split.csv";write_csv(split,[{
            "physical_watch_id":"w1","partition":"development","stratum":"gen/124060/source",
            "class_label":"gen","model":"124060","factory":"","source_name":"source","added_in":"locked_split",
        }])
        config=root/"124060.json";config.write_text(json.dumps({"model":"124060","family":"submariner_12"})+"\n")
        return config,acquired,acq,split,digest

    def test_create_verify_and_materialize_are_content_addressed(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config,acquired,acq,split,digest=self.fixture(root)
            snap=root/"snapshot"
            manifest=es.create("124060","submariner_12",config,acquired,acq,split,snap)
            self.assertEqual(manifest["snapshot_id"],es.verify(snap)["snapshot_id"])
            self.assertEqual(1,manifest["counts"]["unique_image_objects"])
            self.assertTrue((snap/"objects"/"sha256"/digest[:2]/digest).is_file())

            replay=root/"replay_dataset";replay_split=root/"replay_split.csv";replay_cfg=root/"replay_config.json"
            es.materialize(snap,replay,replay_split,replay_cfg)
            self.assertEqual(b"watch-image",(replay/"images"/"w1"/"01.jpg").read_bytes())
            self.assertEqual(acquired.read_bytes(),(replay/"acquired_images.csv").read_bytes())
            self.assertEqual(split.read_bytes(),replay_split.read_bytes())
            self.assertEqual(config.read_bytes(),replay_cfg.read_bytes())

    def test_same_bytes_are_stored_once_for_duplicate_rows(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config,acquired,acq,split,digest=self.fixture(root)
            img2=acq/"images"/"w2"/"02.jpg";img2.parent.mkdir(parents=True);img2.write_bytes(b"watch-image")
            rows=list(csv.DictReader(acquired.open(newline="",encoding="utf-8")))
            second=dict(rows[0]);second.update({
                "candidate_id":"w2","physical_watch_id":"w2","image_index":"2",
                "local_path":"images/w2/02.jpg","exact_duplicate_of":"w1:1"})
            write_csv(acquired,[rows[0],second])
            manifest=es.create("124060","submariner_12",config,acquired,acq,split,root/"snapshot")
            self.assertEqual(2,manifest["counts"]["acquired_rows"])
            self.assertEqual(1,manifest["counts"]["unique_image_objects"])
            self.assertEqual(digest,manifest["objects"][0]["sha256"])

    def test_create_rejects_source_byte_drift(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config,acquired,acq,split,_=self.fixture(root)
            (acq/"images"/"w1"/"01.jpg").write_bytes(b"changed-after-manifest")
            with self.assertRaisesRegex(es.SnapshotError,"image hash mismatch"):
                es.create("124060","submariner_12",config,acquired,acq,split,root/"snapshot")

    def test_verify_rejects_object_tampering(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config,acquired,acq,split,digest=self.fixture(root)
            snap=root/"snapshot";es.create("124060","submariner_12",config,acquired,acq,split,snap)
            (snap/"objects"/"sha256"/digest[:2]/digest).write_bytes(b"tampered")
            with self.assertRaisesRegex(es.SnapshotError,"object hash mismatch"):
                es.verify(snap)

    def test_unsafe_local_path_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config,acquired,acq,split,_=self.fixture(root)
            rows=list(csv.DictReader(acquired.open(newline="",encoding="utf-8")))
            rows[0]["local_path"]="../escape.jpg";write_csv(acquired,rows)
            with self.assertRaisesRegex(es.SnapshotError,"safe relative path"):
                es.create("124060","submariner_12",config,acquired,acq,split,root/"snapshot")

    def test_existing_snapshot_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config,acquired,acq,split,_=self.fixture(root)
            snap=root/"snapshot";es.create("124060","submariner_12",config,acquired,acq,split,snap)
            with self.assertRaisesRegex(es.SnapshotError,"already exists"):
                es.create("124060","submariner_12",config,acquired,acq,split,snap)


if __name__=="__main__": unittest.main()
