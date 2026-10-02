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
                    "seed_urls": ["https://www.bobswatches.com/rolex/submariner-{model}"],
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
             patch.object(discover, "page_links", return_value=[hit]), \
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

    def test_reviewed_listing_seed_is_a_normal_clean_discovery_candidate(self):
        cfg = {
            "model": "124060",
            "family": "submariner_12",
            "replica_factories": [],
            "discovery": {
                "minimum_gen_candidates": 1,
                "allow_bootstrap": False,
                "sources": [{
                    "class": "gen",
                    "domain": "europeanwatch.com",
                    "name": "European Watch Company",
                    "id_prefix": "auto_ewc",
                    "source_type": "dealer_listing",
                    "target": 1,
                    "require_listing_id": True,
                    "seed_urls": [],
                    "listing_seed_urls": [
                        "https://www.europeanwatch.com/watch/rolex-124060-submariner-71272"
                    ],
                }],
            },
        }
        page = (
            "https://www.europeanwatch.com/watch/rolex-124060-submariner-71272",
            "Rolex 124060 Submariner Stock 71272",
            200,
        )
        with tempfile.TemporaryDirectory() as td, \
             patch.object(discover, "page_links", return_value=[]), \
             patch.object(discover, "fetch_page", return_value=page):
            out = Path(td) / "candidates.csv"
            report = discover.discover(cfg, out)
            with out.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
        self.assertTrue(report["clean_discovery"])
        self.assertEqual(1, report["web_candidates"])
        self.assertEqual("71272", rows[0]["listing_id"])
        self.assertEqual("European Watch Company", rows[0]["source_name"])

    def _cfg(self, src):
        return {"model": "124060", "family": "submariner_12", "replica_factories": [],
                "discovery": {"minimum_gen_candidates": 0, "sources": [src]}}

    def test_search_engine_queries_are_rejected(self):
        src = {"class": "gen", "domain": "bobswatches.com", "name": "B", "source_type": "dealer_listing",
               "queries": ["site:bobswatches.com {model}"]}
        with tempfile.TemporaryDirectory() as td, self.assertRaises(ValueError):
            discover.discover(self._cfg(src), Path(td) / "c.csv")

    def test_quarantined_watchfinder_is_rejected(self):
        src = {"class": "gen", "domain": "watchfinder.co.uk", "name": "Watchfinder",
               "source_type": "dealer_listing", "seed_urls": ["https://www.watchfinder.co.uk/x"]}
        with tempfile.TemporaryDirectory() as td, self.assertRaises(ValueError):
            discover.discover(self._cfg(src), Path(td) / "c.csv")

    def test_no_search_engine_or_browser_ua_code_remains(self):
        code = (HERE / "discover.py").read_text() + (HERE / "reddit_evidence.py").read_text() \
            + (HERE / "acquire.py").read_text() + (HERE / "reddit_oauth.py").read_text()
        for banned in ("bing.com", "duckduckgo", "google.com/search", "Mozilla/", ".rss", "comments/{", ".json?raw_json"):
            self.assertNotIn(banned, code, banned)

    def test_curated_replica_seeds_need_model_factory_and_imgur_album(self):
        with tempfile.TemporaryDirectory(dir=HERE.parents[1] / "calibration") as td:
            seeds = Path(td) / "seeds.csv"
            seeds.write_text(
                "candidate_id,model,factory,source_url,image_album_url,provenance_note\n"
                "ok,124060,VSF,https://www.reddit.com/r/RepTimeQC/comments/a1,https://imgur.com/a/AAA,n\n"
                "nofac,124060,,https://www.reddit.com/r/RepTimeQC/comments/a2,https://imgur.com/a/BBB,n\n"
                "wrongmodel,126610LN,VSF,https://www.reddit.com/r/RepTimeQC/comments/a3,https://imgur.com/a/CCC,n\n"
                "noalbum,124060,VSF,https://www.reddit.com/r/RepTimeQC/comments/a4,,n\n", encoding="utf-8")
            cfg = self._cfg({"class": "gen", "domain": "bobswatches.com", "name": "B", "source_type": "dealer_listing", "seed_urls": []})
            cfg["discovery"]["replica_seed_csv"] = str(seeds.relative_to(HERE.parents[1]))
            out = Path(td) / "c.csv"
            report = discover.discover(cfg, out)
            with out.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
        self.assertEqual(1, report["replica_seed_candidates"])
        self.assertEqual(["ok"], [r["candidate_id"] for r in rows])
        self.assertEqual("https://imgur.com/a/AAA", rows[0]["image_album_url"])

    def test_shipped_model_configs_pass_source_policy(self):
        import json
        for fn in (HERE.parents[1] / "calibration" / "models").glob("*.json"):
            for src in json.loads(fn.read_text())["discovery"]["sources"]:
                discover.check_source_policy(src)


if __name__ == "__main__":
    unittest.main()
