import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import discover  # noqa: E402


class RedditDiscoveryTest(unittest.TestCase):
    def test_reddit_native_qc_post_does_not_require_imgur_album(self):
        cfg = {
            "model": "124060",
            "family": "submariner_12",
            "replica_factories": ["VSF", "Clean"],
            "discovery": {
                "minimum_gen_candidates": 0,
                "sources": [{
                    "class": "rep",
                    "domain": "reddit.com",
                    "name": "r/RepTimeQC",
                    "id_prefix": "auto_rep",
                    "source_type": "forum_qc",
                    "target": 1,
                    "require_album": False,
                    "require_factory": True,
                    "queries": ["site:reddit.com/r/RepTimeQC/comments {model} VSF"],
                }],
            },
        }
        hit = (
            "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc_vsf_124060/",
            "QC VSF Rolex Submariner 124060",
            "Pictures included in post",
        )
        with tempfile.TemporaryDirectory() as td, \
             patch.object(discover, "search_web", return_value=[hit]), \
             patch.object(discover, "reddit_album", return_value=("", "QC VSF Rolex Submariner 124060 pictures included in post")):
            out = Path(td) / "candidates.csv"
            report = discover.discover(cfg, out)
            self.assertEqual(1, report["by_class"]["rep"])
            with out.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
            self.assertEqual("VSF", rows[0]["factory"])
            self.assertEqual("", rows[0]["image_album_url"])
            self.assertIn("reddit.com/r/RepTimeQC/comments/abc123", rows[0]["source_url"])


if __name__ == "__main__":
    unittest.main()
