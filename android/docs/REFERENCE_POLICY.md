# Reference sourcing decision

Online reference sourcing is permanently removed from the 1.3 product architecture.

The app has no INTERNET permission and does not fetch manufacturer pages or images. This avoids unstable page scraping, silent model substitution, unclear licensing and misleading provenance. References can enter analysis only through Android's user-controlled document picker. A user may explicitly save selected photos to the private, exact-model personal reference library.

If online sourcing is proposed in a future major version, it requires a separate product/privacy review, an explicit network UI, recorded source URL, retrieval time, pixel resolution, exact-model match evidence, suitability/angle confidence and image licensing basis. It must not be restored as an implicit fallback.
