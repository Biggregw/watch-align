Watch Align V1 1.2.0 is a major usability and reference-consensus upgrade.

The main screen is now task-led instead of implementation-led. Users choose Check my watch, Compare with genuine, or Manual overlay. Gen Compare automatically uses official manufacturer references when available, while manual reference upload is moved into an Advanced section.

New guided UX:
- photo pre-check for resolution, sharpness, watch-boundary detection and perspective suitability
- model suggestion for the currently supported Rolex models
- official-reference status and preview before comparison
- automatic closest-photographic-geometry reference selection
- clear top-level verdict cards for visual match, measurement reliability, photo suitability and multi-reference agreement
- plain-language area verdicts for dial, markers, bezel and date/cyclops
- technical details remain available in expandable sections rather than dominating the page
- unavailable/withheld measurements are hidden from the detailed results instead of being presented as pseudo-precision
- overlay opacity control and genuine/watch blink control
- recent comparison history and last model/task are remembered locally in the browser on that machine
- a visual watch-position guide is available in the UI styling for future capture-preview surfaces
- official reference selector/preview remains available for advanced inspection

Reference handling:
- 126710BLNR uses the official Rolex Jubilee and Oyster source set already introduced in V1.1.x
- when multiple official images are cached, Watch Align chooses the one whose framing is closest to the candidate image
- up to three suitable official references are analysed for consensus
- an apparent defect is not presented as stable unless it repeats across suitable references; reference-dependent disagreements are described as inconclusive

Confidence semantics remain separated: visual alignment confidence, perspective suitability and measurement reliability are distinct. Perspective distortion remains an image-geometry estimate, not a calibrated physical camera angle.

Measurements remain QC/image-geometry diagnostics, not physical metrology or proof of authenticity. Older production releases remain available for rollback.
