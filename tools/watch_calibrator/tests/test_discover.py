import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import discover  # noqa: E402


class DiscoverTest(unittest.TestCase):
    def test_bobs_sku_can_come_from_page_text(self):
        text = "Buy Used Rolex Submariner 124060 | Bob's Watches - Sku: 193105"
        self.assertEqual(
            "193105",
            discover.listing_id(
                "https://www.bobswatches.com/pre-owned-rolex-submariner-ref-124060.html",
                text,
                "124060",
            ),
        )

    def test_clean_discovery_never_reads_bootstrap_when_web_is_enough(self):
        cfg = {
            "model": "124060",
            "family": "submariner_12",
            "replica_factories": ["VSF"],
            "discovery": {
                "minimum_gen_candidates": 1,
                "allow_bootstrap": True,
                "bootstrap_pool_globs": ["does/not/matter/*.csv"],
                "sources": [{
                    "class": "gen",
                    "domain": "bobswatches.com",
                    "name": "Bob's Watches",
                    "id_prefix": "auto_bobs",
                    "source_type": "dealer_listing",
                    "target": 1,
                    "require_listing_id": True,
                    "queries": ["site:bobswatches.com {model}"],
                }],
            },
        }
        hit = (
            "https://www.bobswatches.com/used-rolex-submariner-124060.html",
            "Rolex 124060",
            "pre-owned",
        )
        page = (
            "https://www.bobswatches.com/used-rolex-submariner-124060.html",
            "Rolex Submariner 124060 2022 174149 Excellent Sku: 174149",
            200,
        )
        with tempfile.TemporaryDirectory() as td, \
             patch.object(discover, "search_web", return_value=[hit]), \
             patch.object(discover, "fetch_page", return_value=page), \
             patch.object(discover, "add_bootstrap", side_effect=AssertionError("bootstrap used")):
            out = Path(td) / "candidates.csv"
            report = discover.discover(cfg, out)
            self.assertTrue(report["clean_discovery"])
            self.assertEqual(1, report["web_candidates"])
            self.assertEqual(0, report["bootstrap_candidates"])
            with out.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
            self.assertEqual("174149", rows[0]["listing_id"])

    def test_reddit_album_and_factory_can_be_discovered_from_search_text(self):
        cfg = {
            "model": "124060",
            "family": "submariner_12",
            "replica_factories": ["VSF"],
            "discovery": {
                "minimum_gen_candidates": 0,
                "sources": [{
                    "class": "rep",
                    "domain": "reddit.com",
                    "name": "r/RepTimeQC",
                    "id_prefix": "auto_rep",
                    "source_type": "forum_qc",
                    "target": 1,
                    "require_album": True,
                    "require_factory": True,
                    "queries": ["site:reddit.com {model} VSF imgur"],
                }],
            },
        }
        hit = (
            "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc/",
            "QC VSF 124060",
            "https://imgur.com/a/TestAlbum",
        )
        with tempfile.TemporaryDirectory() as td, \
             patch.object(discover, "search_web", return_value=[hit]), \
             patch.object(discover, "reddit_album", return_value=("https://imgur.com/a/TestAlbum", "QC VSF 124060")):
            out = Path(td) / "candidates.csv"
            report = discover.discover(cfg, out)
            self.assertEqual(1, report["by_class"]["rep"])
            self.assertEqual(0, report["bootstrap_candidates"])


if __name__ == "__main__":
    unittest.main()
