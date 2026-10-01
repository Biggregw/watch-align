import json
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import acquire  # noqa: E402


class Response:
    def __init__(self, payload):
        self.text = json.dumps(payload)


class FakeHttp:
    def __init__(self, post):
        self.post = post
        self.calls = []

    def get(self, url, api=False):
        self.calls.append((url, api))
        return Response([{"data": {"children": [{"data": self.post}]}}])


class RedditAcquireTest(unittest.TestCase):
    def test_direct_reddit_image_is_acquired_from_post_json(self):
        http = FakeHttp({
            "url_overridden_by_dest": "https://i.redd.it/watch123.jpg",
            "preview": {"images": [{"source": {"url": "https://preview.redd.it/watch123.jpg?width=1080&amp;format=pjpg"}}]},
        })
        refs = acquire.reddit_image_refs(
            "https://www.reddit.com/r/RepTimeQC/comments/abc123/vsf_124060_qc/",
            http,
            10,
        )
        self.assertEqual("https://i.redd.it/watch123.jpg", refs[0].url)
        self.assertTrue(http.calls[0][1], "Reddit JSON must use the documented API path")

    def test_native_gallery_keeps_gallery_order_and_full_resolution_sources(self):
        http = FakeHttp({
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

    def test_non_reddit_media_hosts_are_not_silently_downloaded(self):
        http = FakeHttp({"url_overridden_by_dest": "https://example.com/not-a-reddit-image.jpg"})
        refs = acquire.reddit_image_refs(
            "https://reddit.com/r/RepTimeQC/comments/abc123/qc/",
            http,
            10,
        )
        self.assertEqual([], refs)


if __name__ == "__main__":
    unittest.main()
