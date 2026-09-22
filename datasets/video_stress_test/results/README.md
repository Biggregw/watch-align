# video_stress_test results

Derived numeric results only from the same-watch video stress test (see
`docs/research/pose-library-overlay-video-results-2026-09-22.md`). The
source video frames themselves are the user's own images and are
deliberately **not** committed here, consistent with this repo's existing
convention of never committing source photo content (see the equivalent
handling of the fetched 126710BLNR corpus) -- they were sent directly to
the user as diagnostic overlays instead.

`video_frame_results.csv`: one row per frame, `local_path` is the original
filename only (no path), `physical_watch_id` is constant
(`video_same_watch`) since all frames are the same physical watch, not
independent watches.
