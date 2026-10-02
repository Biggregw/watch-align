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

    def test_model_label_gallery_excludes_unrelated_watch_images(self):
        page = """
        <html><head><meta property="og:image" content="https://cdn.example.com/hero.jpg"></head><body>
          <img alt="Rolex Certified Pre-owned Submariner M124060-0001 front" src="https://cdn.example.com/124060-front.jpg">
          <img alt="Rolex Certified Pre-owned Submariner M124060-0001 side" data-src="https://cdn.example.com/124060-side.jpg">
          <img alt="Related Rolex Submariner M126610LN" src="https://cdn.example.com/126610.jpg">
          <img alt="Site logo" src="https://cdn.example.com/logo.jpg">
        </body></html>
        """
        http = FakeHttp(page, "https://www.watchesofswitzerland.com/products/submariner-m124060-0001-40411298")
        refs = dealer_media.model_label_gallery_refs(
            {"source_url": http.url, "model": "124060"}, http, 10, include_og=False
        )
        self.assertEqual(
            ["https://cdn.example.com/124060-front.jpg", "https://cdn.example.com/124060-side.jpg"],
            [r.url for r in refs],
        )

    def test_sothebys_can_keep_exact_model_gallery_and_item_hero(self):
        page = """
        <html><head><meta property="og:image" content="https://sothebys.example/item-hero.jpg"></head><body>
          <img alt="Reference 124060 Submariner view 1" src="https://sothebys.example/lot-1.jpg">
          <img alt="Recommended Rolex 126610" src="https://sothebys.example/related.jpg">
        </body></html>
        """
        http = FakeHttp(page, "https://www.sothebys.com/en/buy/auction/x/reference-124060-submariner")
        refs = dealer_media.model_label_gallery_refs(
            {"source_url": http.url, "model": "124060"}, http, 10, include_og=True
        )
        self.assertEqual(
            ["https://sothebys.example/item-hero.jpg", "https://sothebys.example/lot-1.jpg"],
            [r.url for r in refs],
        )

    def test_phillips_uses_only_item_specific_social_hero(self):
        page = """
        <html><head>
          <meta property="og:image" content="https://assets.phillips.com/lot-206976.jpg">
          <meta name="twitter:image" content="https://assets.phillips.com/lot-206976.jpg">
        </head><body>
          <img alt="Lot 1" src="https://assets.phillips.com/other-watch.jpg">
        </body></html>
        """
        http = FakeHttp(page, "https://www.phillips.com/detail/rolex/206976")
        refs = dealer_media.phillips_refs({"source_url": http.url, "model": "124060"}, http, 10)
        self.assertEqual(["https://assets.phillips.com/lot-206976.jpg"], [r.url for r in refs])

    def test_unknown_dealer_returns_none_for_strict_fallback(self):
        row = {"source_url": "https://www.bobswatches.com/example"}
        self.assertIsNone(dealer_media.resolve_verified_dealer(row, object(), 10))


if __name__ == "__main__":
    unittest.main()
