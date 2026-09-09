# Watch Align 1.2.5

Hotfix for official reference lookup failures.

## Fixed
- Handles Rolex 403/blocked official source responses without surfacing the raw provider error.
- Falls back to trusted cached references when official downloads are unavailable.
- Shows a clear action when no cached reference exists: upload a genuine/reference image or add one to the reference library.

Includes the stalled-comparison worker, full-turn rotation alignment, and repository cleanup from 1.2.4.
