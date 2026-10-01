import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import discover  # noqa: E402


class FakeResponse:
    def __init__(self, text, status=200):
        self.text = text
        self.status_code = status

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(self.status_code)


class RedditDiscoveryTest(unittest.TestCase):
    def test_rss_search_extracts_model_factory_context_and_native_image_hint(self):
        atom = '''<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <title>QC VSF Rolex Submariner 124060</title>
            <link rel="alternate" href="https://www.reddit.com/r/RepTimeQC/comments/abc123/qc_vsf_124060/" />
            <content type="html">&lt;table&gt;&lt;tr&gt;&lt;td&gt;&lt;a href="https://www.reddit.com/r/RepTimeQC/comments/abc123/qc_vsf_124060/"&gt;&lt;img src="https://preview.redd.it/qcface.jpg?width=640&amp;amp;format=pjpg" /&gt;&lt;/a&gt;&lt;/td&gt;&lt;td&gt;Factory name: VSF Model: Submariner 124060&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;</content>
          </entry>
        </feed>'''
        with patch.object(discover.requests, "get", return_value=FakeResponse(atom)):
            hits = discover.reddit_rss_search("124060", 25)
        self.assertEqual(1, len(hits))
        url, title, detail, direct, album = hits[0]
        self.assertIn("reddit.com/r/RepTimeQC/comments/abc123", url)
        self.assertIn("VSF", detail)
        self.assertEqual("https://i.redd.it/qcface.jpg", direct)
        self.assertEqual("", album)

    def test_rss_search_carries_imgur_album_when_present(self):
        atom = '''<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><entry>
        <title>Clean 124060 QC</title>
        <link rel="alternate" href="https://www.reddit.com/r/RepTimeQC/comments/clean1/qc/" />
        <content type="html">Factory: Clean Model: 124060 https://imgur.com/a/AbCd123</content>
        </entry></feed>'''
        with patch.object(discover.requests, "get", return_value=FakeResponse(atom)):
            hits = discover.reddit_rss_search("124060", 25)
        self.assertEqual("https://imgur.com/a/AbCd123", hits[0][4])

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
                    "queries": [],
                    "reddit_direct": True,
                }],
            },
        }
        hit = (
            "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc_vsf_124060/",
            "QC VSF Rolex Submariner 124060",
            "Pictures included in post Factory name: VSF",
            "https://i.redd.it/qcface.jpg",
            "",
        )
        with tempfile.TemporaryDirectory() as td, \
             patch.object(discover, "reddit_search", return_value=[hit]), \
             patch.object(discover, "reddit_album", return_value=("", "QC VSF Rolex Submariner 124060 pictures included in post Factory name: VSF")):
            out = Path(td) / "candidates.csv"
            report = discover.discover(cfg, out)
            self.assertEqual(1, report["by_class"]["rep"])
            with out.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
            self.assertEqual("VSF", rows[0]["factory"])
            self.assertEqual("", rows[0]["image_album_url"])
            self.assertEqual("https://i.redd.it/qcface.jpg", rows[0]["direct_image_url"])
            self.assertIn("reddit.com/r/RepTimeQC/comments/abc123", rows[0]["source_url"])

    def test_replica_without_any_media_path_is_not_counted(self):
        cfg = {
            "model": "124060",
            "family": "submariner_12",
            "replica_factories": ["VSF"],
            "discovery": {
                "minimum_gen_candidates": 0,
                "sources": [{
                    "class": "rep", "domain": "reddit.com", "name": "r/RepTimeQC",
                    "id_prefix": "auto_rep", "source_type": "forum_qc", "target": 1,
                    "require_album": False, "require_factory": True, "queries": [],
                    "reddit_direct": True,
                }],
            },
        }
        hit = ("https://reddit.com/r/RepTimeQC/comments/noimg/qc/", "VSF 124060", "VSF 124060", "", "")
        with tempfile.TemporaryDirectory() as td, \
             patch.object(discover, "reddit_search", return_value=[hit]), \
             patch.object(discover, "reddit_album", return_value=("", "VSF 124060")):
            report = discover.discover(cfg, Path(td) / "candidates.csv")
        self.assertEqual(0, report["by_class"]["rep"])


if __name__ == "__main__":
    unittest.main()
