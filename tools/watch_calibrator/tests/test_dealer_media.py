import unittest
from pathlib import Path
from unittest.mock import patch
import sys

HERE = Path(__file__).resolve().parents[1]
HARVESTER = HERE.parents[0] / "dataset_harvester"
sys.path.insert(0, str(HARVESTER))
sys.path.insert(0, str(HERE))
import dealer_media  # noqa: E402
from harvester.resolvers import ImageRef  # noqa: E402


class Response:
    def __init__(self, text, url):
        self.text = text
        self.url = url


class FakeHttp:
    def __init__(self, text, url):
        self.text = text
        self.url = url
        self.calls = []

    def get(self, url, **kwargs):
        self.calls.append(url)
        return Response(self.text, self.url)


class DealerMediaTest(unittest.TestCase):
    def test_europeanwatch_unwraps_next_image_and_requires_stock_id(self):
        wrapped_good = (
            "https://www.europeanwatch.com/_next/image?w=3840&q=75&url="
            "https%3A%2F%2Fimages.europeanwatch.com%2Fimages%2F71%2F71272-1.jpg"
        )
        wrapped_other = (
            "https://www.europeanwatch.com/_next/image?w=3840&q=75&url="
            "https%3A%2F%2Fimages.europeanwatch.com%2Fimages%2F99%2F99999-1.jpg"
        )
        row = {
            "source_url": "https://www.europeanwatch.com/watch/rolex-124060-example-71272",
            "listing_id": "71272",
        }
        with patch.object(dealer_media, "page_images", return_value=[ImageRef(url=wrapped_good), ImageRef(url=wrapped_other)]):
            refs = dealer_media.europeanwatch_refs(row, object(), 10)
        self.assertEqual(
            ["https://images.europeanwatch.com/images/71/71272-1.jpg"],
            [r.url for r in refs],
        )

    def test_woocommerce_parser_keeps_only_product_gallery(self):
        page = """
        <html><body>
          <img src="https://example.com/site-logo.jpg">
          <div class="woocommerce-product-gallery">
            <a href="https://davidsw.com/uploads/watch-full-1.jpg"><img src="https://davidsw.com/uploads/watch-thumb-1.jpg"></a>
            <a href="https://davidsw.com/uploads/watch-full-2.jpg"><img data-large_image="https://davidsw.com/uploads/watch-full-2.jpg"></a>
          </div>
          <div class="related"><img src="https://davidsw.com/uploads/another-watch.jpg"></div>
        </body></html>
        """
        http = FakeHttp(page, "https://davidsw.com/shop/watch/rolex/example/")
        refs = dealer_media.woocommerce_gallery_refs(
            {"source_url": "https://davidsw.com/shop/watch/rolex/example/"}, http, 10
        )
        urls = [r.url for r in refs]
        self.assertIn("https://davidsw.com/uploads/watch-full-1.jpg", urls)
        self.assertIn("https://davidsw.com/uploads/watch-full-2.jpg", urls)
        self.assertNotIn("https://example.com/site-logo.jpg", urls)
        self.assertNotIn("https://davidsw.com/uploads/another-watch.jpg", urls)

    def test_unknown_dealer_returns_none_for_strict_fallback(self):
        row = {"source_url": "https://www.bobswatches.com/example"}
        self.assertIsNone(dealer_media.resolve_verified_dealer(row, object(), 10))


if __name__ == "__main__":
    unittest.main()
