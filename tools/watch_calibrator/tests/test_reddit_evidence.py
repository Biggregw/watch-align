import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import reddit_evidence  # noqa: E402
from discover import FIELDS  # noqa: E402


class RedditEvidenceTest(unittest.TestCase):
    def test_album_backed_watch_replaces_native_fallback_before_target_fill(self):
        cfg = {
            "model": "124060",
            "family": "submariner_12",
            "replica_factories": ["VSF", "Clean"],
            "discovery": {
                "target_rep": 2,
                "sources": [{
                    "class": "rep", "reddit_direct": True, "target": 2,
                    "name": "r/RepTimeQC", "id_prefix": "auto_rep",
                    "source_type": "forum_qc",
                }],
            },
        }
        gen = {k: "" for k in FIELDS}
        gen.update({"candidate_id": "g1", "physical_watch_id": "g1", "family": "submariner_12",
                    "model": "124060", "class_tag": "gen", "source_name": "dealer", "source_url": "https://dealer/g1"})
        native1 = {k: "" for k in FIELDS}
        native1.update({"candidate_id": "r1", "physical_watch_id": "r1", "family": "submariner_12",
                        "model": "124060", "class_tag": "rep", "factory": "VSF", "source_name": "r/RepTimeQC",
                        "source_url": "https://www.reddit.com/r/RepTimeQC/comments/r1/qc", "direct_image_url": "https://i.redd.it/r1.jpg"})
        native2 = dict(native1)
        native2.update({"candidate_id": "r2", "physical_watch_id": "r2",
                        "source_url": "https://www.reddit.com/r/RepTimeQC/comments/r2/qc", "direct_image_url": "https://i.redd.it/r2.jpg"})
        with tempfile.TemporaryDirectory() as td:
            pool = Path(td) / "pool.csv"
            with pool.open("w", newline="", encoding="utf-8") as fh:
                w = csv.DictWriter(fh, fieldnames=FIELDS); w.writeheader(); w.writerows([gen, native1, native2])
            found = [{"source_url": "https://www.reddit.com/r/RepTimeQC/comments/full/qc",
                      "factory": "Clean", "album": "https://imgur.com/a/fullalbum", "direct": ""}]
            with patch.object(reddit_evidence, "discover_imgur_qc", return_value=found):
                report = reddit_evidence.enrich(cfg, pool, {"candidates": 3, "by_class": {"gen": 1, "rep": 2}, "by_source": {}})
            with pool.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
        reps = [r for r in rows if r["class_tag"] == "rep"]
        self.assertEqual(2, len(reps))
        self.assertEqual("https://imgur.com/a/fullalbum", reps[0]["image_album_url"])
        self.assertEqual(1, report["replica_media_candidates"]["album_backed"])
        self.assertEqual(1, report["replica_media_candidates"]["native_direct_fallback"])


if __name__ == "__main__":
    unittest.main()
