import json
import os
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import acquire  # noqa: E402


class Response:
    def __init__(self, payload=None, text=None):
        self.text = text if text is not None else json.dumps(payload)


class FakeHttp:
    def __init__(self, post=None, rss=None, json_fail=False, rss_fail=False):
        self.post = post or {}
        self.rss = rss or ""
        self.json_fail = json_fail
        self.rss_fail = rss_fail
        self.calls = []
        self.post_calls = []

    def get(self, url, headers=None, api=False):
        self.calls.append((url, api))
        if url.endswith("/.rss"):
            if self.rss_fail:
                raise RuntimeError("rss blocked")
            return Response(text=self.rss)
        if "oauth.reddit.com/by_id/" in url:
            return Response({"data": {"children": [{"data": self.post}]}})
        if ".json" in url:
            if self.json_fail:
                raise RuntimeError("json blocked")
            return Response([{"data": {"children": [{"data": self.post}]}}])
        raise AssertionError(f"unexpected url {url}")

    def post(self, url, data, headers=None, auth=None):
        self.post_calls.append((url, data, auth))
        return Response({"access_token": "test-token", "token_type": "bearer"})


class RedditAcquireTest(unittest.TestCase):
    def setUp(self):
        acquire._OAUTH_TOKEN = None

    def test_direct_reddit_image_is_acquired_from_post_json(self):
        http = FakeHttp(post={
            "url_overridden_by_dest": "https://i.redd.it/watch123.jpg",
            "preview": {"images": [{"source": {"url": "https://preview.redd.it/watch123.jpg?width=1080&amp;format=pjpg"}}]},
        })
        refs = acquire.reddit_image_refs(
            "https://www.reddit.com/r/RepTimeQC/comments/abc123/vsf_124060_qc/",
            http,
            10,
        )
        self.assertEqual("https://i.redd.it/watch123.jpg", refs[0].url)
        self.assertTrue(http.calls[0][1])

    def test_native_gallery_keeps_gallery_order_and_full_resolution_sources(self):
        http = FakeHttp(post={
            "gallery_data": {"items": [{"media_id": "two"}, {"media_id": "one"}]},
            "media_metadata": {
                "one": {"s": {"u": "https://i.redd.it/one.jpg?x=1&amp;y=2"}},
                "two": {"s": {"u": "https://i.redd.it/two.jpg"}},
            },
        })
        refs = acquire.reddit_image_refs(
            "https://reddit.com/r/RepTimeQC/comments/xyz789/qc/",
            http,
            10,
        )
        self.assertEqual(
            ["https://i.redd.it/two.jpg", "https://i.redd.it/one.jpg?x=1&y=2"],
            [r.url for r in refs],
        )

    def test_oauth_expands_native_gallery_when_credentials_are_configured(self):
        http = FakeHttp(post={
            "gallery_data": {"items": [{"media_id": "a"}, {"media_id": "b"}]},
            "media_metadata": {
                "a": {"s": {"u": "https://i.redd.it/a.jpg"}},
                "b": {"s": {"u": "https://i.redd.it/b.jpg"}},
            },
        })
        with patch.dict(os.environ, {"REDDIT_CLIENT_ID": "id", "REDDIT_CLIENT_SECRET": "secret"}):
            refs = acquire.reddit_oauth_image_refs(
                "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc/", http, 10
            )
        self.assertTrue(http.post_calls, "OAuth token request was not attempted")
        self.assertEqual("https://www.reddit.com/api/v1/access_token", http.post_calls[0][0])
        self.assertEqual(("id", "secret"), http.post_calls[0][2])
        self.assertTrue(any("oauth.reddit.com/by_id/t3_abc123" in u for u, _ in http.calls), "OAuth post lookup was not attempted")
        self.assertEqual(["https://i.redd.it/a.jpg", "https://i.redd.it/b.jpg"], [r.url for r in refs])

    def test_public_post_rss_expands_multiple_images_if_the_feed_exposes_them(self):
        rss = '''<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <title>QC VSF 124060</title>
            <link rel="alternate" href="https://www.reddit.com/r/RepTimeQC/comments/abc123/qc/" />
            <content type="html">&lt;div&gt;
              &lt;img src="https://preview.redd.it/one.jpg?width=1080&amp;amp;format=pjpg" /&gt;
              &lt;a href="https://i.redd.it/two.png"&gt;second&lt;/a&gt;
              &lt;img src="https://i.redd.it/three.jpg" /&gt;
            &lt;/div&gt;</content>
          </entry>
        </feed>'''
        http = FakeHttp(rss=rss, json_fail=True)
        row = {
            "source_url": "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc/",
            "image_album_url": "",
            "direct_image_url": "https://i.redd.it/one.jpg",
        }
        refs = acquire.resolve_candidate(row, http, Path("."), 10)
        self.assertEqual(
            ["https://i.redd.it/one.jpg", "https://i.redd.it/two.png", "https://i.redd.it/three.jpg"],
            [r.url for r in refs],
        )
        self.assertTrue(any(url.endswith("/.rss") for url, _ in http.calls))

    def test_direct_hint_survives_when_both_anonymous_metadata_paths_are_blocked(self):
        http = FakeHttp(json_fail=True, rss_fail=True)
        row = {
            "source_url": "https://www.reddit.com/r/RepTimeQC/comments/abc123/qc/",
            "image_album_url": "",
            "direct_image_url": "https://preview.redd.it/qcface.jpg?width=640&format=pjpg",
        }
        refs = acquire.resolve_candidate(row, http, Path("."), 10)
        self.assertEqual(["https://i.redd.it/qcface.jpg"], [r.url for r in refs])

    def test_blocked_json_fails_closed_instead_of_crashing(self):
        http = FakeHttp(json_fail=True)
        refs = acquire.reddit_image_refs(
            "https://reddit.com/r/RepTimeQC/comments/abc123/qc/", http, 10
        )
        self.assertEqual([], refs)

    def test_non_reddit_media_hosts_are_not_silently_downloaded(self):
        http = FakeHttp(post={"url_overridden_by_dest": "https://example.com/not-a-reddit-image.jpg"})
        refs = acquire.reddit_image_refs(
            "https://reddit.com/r/RepTimeQC/comments/abc123/qc/",
            http,
            10,
        )
        self.assertEqual([], refs)


if __name__ == "__main__":
    unittest.main()
