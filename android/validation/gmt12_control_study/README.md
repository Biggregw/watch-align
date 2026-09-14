# GMT 12-triangle control study

This directory records the first provenance-controlled run of the unchanged 12-triangle metric. It does not alter production code, genuine ranges, thresholds, or classification behavior.

The initial candidate pool was screened under these rules: one physical watch is one independent unit; only modern right-handed black-dial 12-series GMT-Master II variants are eligible; Sprite/VTNR, older references, meteorite and materially different dials are excluded; and a measurement is accepted only when all four rectification anchors and all five local feature points have been corrected.

Only two historical cases currently retain complete raw metric outputs: one official genuine 126710BLNR control and Greg's replica 126710BLNR. Their corrected coordinates were not persisted by the earlier app build, so the measurements are recoverable but cannot yet be replayed from coordinates. The D195/PHS material and Infamous_QC screenshots are assigned to the single `rep-001-greg-D195` watch ID.

Run `python3 summarize_controls.py` from this directory to regenerate the per-watch, distribution, breakdown, consistency, sensitivity-comparison and Markdown report outputs. The script refuses to treat multiple images of one `watch_id` as independent watches.

No calibration conclusion should be drawn until at least 10 independent accepted watches per group have replayable corrected coordinates. Nine additional genuine and nine additional replica watches are required.
