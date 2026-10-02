import csv
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import discover  # noqa: E402
import reddit_oauth  # noqa: E402


class FakeResponse:
    def __init__(self, payload, status=200):
        self.payload = payload
        self.status_code = status

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(self.status_code)

    def json(self):
        return self.payload


class FakeSession:
    def __init__(self, posts):
        self.posts = posts
        self.gets = []
        self.posts_calls = []

    def post(self, url, **kw):
        self.posts_calls.append(url)
        assert url == "https://www.reddit.com/api/v1/access_token", url
        return FakeResponse({"access_token": "tok"})

    def get(self, url, params=None, headers=None, timeout=None):
        self.gets.append((url, headers or {}))
        assert url.startswith("https://oauth.reddit.com/"), url
        return FakeResponse({"data": {"children": [{"data": p} for p in self.posts]}})


CREDS = {"REDDIT_CLIENT_ID": "id", "REDDIT_CLIENT_SECRET": "secret"}
POST = {
    "title": "QC VSF Rolex Submariner 124060",
    "selftext": "Factory name: VSF https://imgur.com/a/AbCd123",
    "permalink": "/r/RepTimeQC/comments/abc123/qc_vsf_124060/",
    "url_overridden_by_dest": "https://preview.redd.it/qcface.jpg?width=640",
}


def rep_cfg():
    return {
        "model": "124060", "family": "submariner_12", "replica_factories": ["VSF", "Clean"],
        "discovery": {"minimum_gen_candidates": 0, "sources": [{
            "class": "rep", "domain": "reddit.com", "name": "r/RepTimeQC", "id_prefix": "auto_rep",
            "source_type": "forum_qc", "target": 1, "require_album": False, "require_factory": True,
            "reddit_direct": True,
        }]},
    }


class RedditDiscoveryTest(unittest.TestCase):
    def setUp(self):
        reddit_oauth.reset()

    def test_official_api_search_extracts_context_image_and_album(self):
        session = FakeSession([POST])
        with patch.dict(os.environ, CREDS), patch.object(reddit_oauth, "requests", session):
            hits = discover.reddit_search("124060")
        self.assertEqual(1, len(hits))
        url, title, detail, direct, album = hits[0]
        self.assertIn("reddit.com/r/RepTimeQC/comments/abc123", url)
        self.assertIn("VSF", detail)
        self.assertEqual("https://i.redd.it/qcface.jpg", direct)
        self.assertEqual("https://imgur.com/a/AbCd123", album)
        self.assertTrue(all(u.startswith("https://oauth.reddit.com/") for u, _ in session.gets))
        self.assertTrue(all("Mozilla" not in h.get("User-Agent", "") for _, h in session.gets))

    def test_without_credentials_reddit_is_skipped_and_no_request_made(self):
        session = FakeSession([POST])
        with tempfile.TemporaryDirectory() as td, \
             patch.dict(os.environ, {"REDDIT_CLIENT_ID": "", "REDDIT_CLIENT_SECRET": ""}), \
             patch.object(reddit_oauth, "requests", session):
            report = discover.discover(rep_cfg(), Path(td) / "c.csv")
        self.assertEqual(0, report["by_class"]["rep"])
        self.assertIn("skipped", report["reddit_api"])
        self.assertEqual([], session.gets)
        self.assertEqual([], session.posts_calls)

    def test_native_qc_post_does_not_require_imgur_album(self):
        hit = ("https://www.reddit.com/r/RepTimeQC/comments/abc123/qc_vsf_124060/",
               "QC VSF Rolex Submariner 124060", "QC VSF Rolex Submariner 124060 Factory name: VSF",
               "https://i.redd.it/qcface.jpg", "")
        with tempfile.TemporaryDirectory() as td, patch.object(discover, "reddit_search", return_value=[hit]):
            out = Path(td) / "c.csv"
            report = discover.discover(rep_cfg(), out)
            with out.open(newline="", encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
        self.assertEqual(1, report["by_class"]["rep"])
        self.assertEqual("VSF", rows[0]["factory"])
        self.assertEqual("https://i.redd.it/qcface.jpg", rows[0]["direct_image_url"])

    def test_replica_without_any_media_path_is_not_counted(self):
        hit = ("https://reddit.com/r/RepTimeQC/comments/noimg/qc/", "VSF 124060", "VSF 124060", "", "")
        with tempfile.TemporaryDirectory() as td, patch.object(discover, "reddit_search", return_value=[hit]):
            report = discover.discover(rep_cfg(), Path(td) / "c.csv")
        self.assertEqual(0, report["by_class"]["rep"])


if __name__ == "__main__":
    unittest.main()
