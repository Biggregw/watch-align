# Watch Align 1.2.6

Hotfix for official reference image gathering.

## Fixed
- Rejects large non-dial product/detail images, such as bracelet shots, before they can be cached as official comparison references.
- Requires gathered official references to contain a usable front-facing watch head/dial region.
- Keeps searching other page assets or the official brochure when an unsuitable image is encountered.

Includes the stalled-comparison worker, full-turn rotation alignment, and official-source 403 fallback from 1.2.5.
