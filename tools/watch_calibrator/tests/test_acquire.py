import json
import os
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import acquire  # noqa: E402
import reddit_oauth  # noqa: E402


class Response:
    def __init__(self, payload=None, text=None):
        self.text = text if text is not None else json.dumps(payload)


class FakeHttp:
    """Only the official OAuth endpoint is answered; any other Reddit URL is a test failure."""

    def __init__(self, post=None):
        self.post_data = post or {}
        self.calls = []

    def get(self, url, headers=None, api=False):
        self.calls.append((url, headers or {}))
        if url.startswith("https://oauth.reddit.com/by_id/"):
            return Response({"data": {"children": [{"data": self.post_data}]}})
        raise AssertionError(f"prohibited/unexpected url {url}")


CREDS = {"REDDIT_CLIENT_ID": "id", "REDDIT_CLIENT_SECRET": "secret"}
URL = "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc/"


class RedditAcquireTest(unittest.TestCase):
    def setUp(self):
        reddit_oauth.reset()

    def test_oauth_expands_native_gallery_in_order(self):
        http = FakeHttp(post={
            "gallery_data": {"items": [{"media_id": "a"}, {"media_id": "b"}]},
            "media_metadata": {
                "a": {"s": {"u": "https://i.redd.it/a.jpg"}},
                "b": {"s": {"u": "https://i.redd.it/b.jpg"}},
            },
        })
        self.assertEqual("abc123", acquire.reddit_post_id(URL))
        with patch.dict(os.environ, CREDS), patch.object(reddit_oauth, "token", return_value="tok"):
            refs = acquire.reddit_oauth_image_refs(URL, http, 10)
        self.assertEqual(["https://i.redd.it/a.jpg", "https://i.redd.it/b.jpg"], [r.url for r in refs])
        self.assertEqual("https://oauth.reddit.com/by_id/t3_abc123", http.calls[0][0])
        self.assertEqual("bearer tok", http.calls[0][1]["Authorization"])
        self.assertNotIn("Mozilla", http.calls[0][1]["User-Agent"])

    def test_without_credentials_no_reddit_request_is_made(self):
        http = FakeHttp()
        row = {"source_url": URL, "image_album_url": "", "direct_image_url": ""}
        with patch.dict(os.environ, {"REDDIT_CLIENT_ID": "", "REDDIT_CLIENT_SECRET": ""}):
            refs = acquire.resolve_candidate(row, http, Path("."), 10)
        self.assertEqual([], refs)
        self.assertEqual([], http.calls)

    def test_reddit_page_never_falls_through_to_generic_resolver(self):
        http = FakeHttp()
        row = {"source_url": URL, "image_album_url": "", "direct_image_url": ""}
        with patch.dict(os.environ, {"REDDIT_CLIENT_ID": "", "REDDIT_CLIENT_SECRET": ""}), \
             patch.object(acquire, "ORIGINAL_RESOLVE", side_effect=AssertionError("anonymous fetch")):
            self.assertEqual([], acquire.resolve_candidate(row, http, Path("."), 10))

    def test_api_supplied_direct_hint_is_kept(self):
        http = FakeHttp()
        row = {"source_url": URL, "image_album_url": "",
               "direct_image_url": "https://preview.redd.it/qcface.jpg?width=640&format=pjpg"}
        with patch.dict(os.environ, {"REDDIT_CLIENT_ID": "", "REDDIT_CLIENT_SECRET": ""}):
            refs = acquire.resolve_candidate(row, http, Path("."), 10)
        self.assertEqual(["https://i.redd.it/qcface.jpg"], [r.url for r in refs])

    def test_non_reddit_media_hosts_are_not_silently_downloaded(self):
        http = FakeHttp(post={"url_overridden_by_dest": "https://example.com/not-a-reddit-image.jpg"})
        with patch.dict(os.environ, CREDS), patch.object(reddit_oauth, "token", return_value="tok"):
            refs = acquire.reddit_oauth_image_refs(URL, http, 10)
        self.assertEqual([], refs)

    def test_anonymous_reddit_helpers_are_gone(self):
        for name in ("reddit_rss_image_refs", "reddit_image_refs"):
            self.assertFalse(hasattr(acquire, name), name)


if __name__ == "__main__":
    unittest.main()
