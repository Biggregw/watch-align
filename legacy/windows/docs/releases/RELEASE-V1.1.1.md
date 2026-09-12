Watch Align V1 1.1.1 fixes automatic genuine/reference lookup for Rolex GMT-Master II 126710BLNR Gen Compare.

V1.1.0 could fall through to the old “upload a reference” error when Rolex’s JavaScript product pages did not expose direct stock-image URLs to the app. V1.1.1 keeps the product-page lookup but adds a deterministic fallback to Rolex’s official brochure PDF for the exact model code. Watch Align extracts the largest official product image, caches it locally, records the Rolex page/brochure URL, bracelet variant, model code, verification type and SHA-256 provenance, then uses it automatically for Gen Compare.

Supported official 126710BLNR sources:
- Jubilee: m126710blnr-0002
- Oyster: m126710blnr-0003

The Gen Compare UI now makes clear that the manual genuine/reference upload is optional when an official source is available.

The confidence semantics introduced in V1.1.0 are unchanged: visual alignment confidence, perspective suitability and measurement reliability remain separate, and perspective-sensitive measurements may be withheld without falsely downgrading a strong visual overlay.

Measurements remain image-geometry diagnostics, not physical metrology or proof of authenticity. V1.1.0 and V1.0.0 remain available for rollback.
