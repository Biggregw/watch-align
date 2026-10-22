"""Per-marker eligibility masks, enforced where the watch-family calibrator consumes measurements.

A masked run is declared in the model config ("marker_eligibility": {"policy": "marker-mask-v1"}) and reads the
masks only from <acquisition root>/eligibility_masks.jsonl (written by tools.image_archive.integration
.restore_acquired(..., purpose='marker_masked')). Everything fails closed: a photograph without a valid mask bound to
its own bytes, a mask for another layout, or a metric with no declared marker dependency stops the run. There is no
fallback to unmasked values.

Calibrator metrics are composites computed by the app (Sub124060Calibration.assess) over several markers, so a value
is kept only when every marker it depends on is ELIGIBLE. Per-marker values (marker.<hour>.<quantity>) depend on
their own marker only. Excluded values are blanked before any statistic and logged by marker and reason, never by
value.

The unmasked legacy path is unchanged, except that it refuses an acquisition root that holds masks (a partially
eligible photograph can never enter it) and refuses photo rows that carry mask columns.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))
from tools.image_archive import eligibility as el  # noqa: E402
from tools.image_archive.faceon import MODELS, REQUIRED  # noqa: E402
from tools.image_archive.integration import MASKS_FILE  # noqa: E402

MASK_COLUMNS = ("sha256", "mask_policy_version", "mask_digest", "eligible_marker_ids")
MaskError = el.MaskError
PER_MARKER = re.compile(r"^marker\.(\d{1,2})\.[a-z_]+$")


def required(config: dict) -> bool:
    spec = config.get("marker_eligibility")
    if not spec:
        return False
    if spec.get("policy") != el.MASK_POLICY_VERSION:
        raise MaskError("unknown marker eligibility policy in model config")
    return True


def layout(config: dict) -> str:
    model = MODELS.get(str(config.get("model")))
    if model is None:
        raise MaskError("no marker layout for calibrator model %r" % config.get("model"))
    return model


RING_MARKERS = "ring.markers"   # GeometryMeasure: the hours the photo's ring fit actually used
MIN_RING = 5


def dependencies(metric: str, model: str, row: dict | None = None) -> frozenset[int]:
    """Hour markers whose readings a calibrator metric is computed from (Sub124060Calibration.assess; MarkerGeometry).

    A ring-level metric depends on exactly the markers its photo's ring fit used (row[RING_MARKERS]): the fit already
    leaves out ineligible markers, so a hand at one marker does not discard the whole-ring measurement. Without a valid
    list (at least MIN_RING round markers / batons of the layout) the value cannot be verified and the run stops.
    """
    shapes = el.SHAPES[model]
    m = PER_MARKER.match(metric)
    if m:
        hour = int(m.group(1))
        if hour not in REQUIRED[model]:
            raise MaskError("%s is not an hour marker on this layout" % metric)
        return frozenset({hour})
    if metric.startswith("twelve."):
        return frozenset({12})
    if metric.startswith("round."):        # median ring radius / spacing over every round marker found
        return frozenset(h for h, s in shapes.items() if s == "round")
    if metric == "baton.3_9_line_offset_r":
        return frozenset({3, 9})
    if metric == "axis.12_6_line_offset_r":
        return frozenset({12, 6})
    if metric.startswith("ring."):
        if row is None:
            raise MaskError("a ring metric needs the list of markers its ring fit used")
        try:
            used = frozenset(int(x) for x in (row.get(RING_MARKERS) or "").split())
        except ValueError:
            raise MaskError("invalid ring marker list")
        allowed = {h for h, s in shapes.items() if s in ("round", "baton")}
        if len(used) < MIN_RING or not used <= allowed:
            raise MaskError("ring value without a valid list of the markers it used")
        return used
    raise MaskError("no marker dependency declared for metric %s" % metric)


def load(path: Path) -> dict[str, dict]:
    """sha256 -> validated mask. A missing or empty file, an invalid mask or two masks for one photo stop the run."""
    if not path.is_file():
        raise MaskError("masked calibration requires %s" % path.name)
    out: dict[str, dict] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            mask = json.loads(line)
        except ValueError:
            raise MaskError("unreadable eligibility mask")
        el.validate(mask)
        prior = out.get(mask["sha256"])
        if prior is not None and prior["mask_digest"] != mask["mask_digest"]:
            raise MaskError("two different masks for one photograph")
        out[mask["sha256"]] = mask
    if not out:
        raise MaskError("no eligibility masks")
    return out


def guard_legacy(config: dict, acq_root: Path) -> None:
    """The unmasked legacy path never sees an acquisition root restored with masks."""
    if not required(config) and (acq_root / MASKS_FILE).exists():
        raise MaskError("acquisition root holds partially eligible photographs; the unmasked legacy calibration "
                        "path is refused (declare marker_eligibility in the model config)")


def _f(v) -> float:
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except (TypeError, ValueError):
        return math.nan


def apply(config: dict, acq_root: Path, raw_csv: Path, out_csv: Path, exclusions_csv: Path) -> dict:
    """Mask one CalibMeasure photo CSV: verify each photo's bytes against its mask, blank ineligible values."""
    model = layout(config)
    metrics = [s["metric"] for s in config["calibration_metrics"]]
    for m in metrics:   # every metric must declare its markers (ring metrics per row, below)
        if not m.startswith("ring."):
            dependencies(m, model)
    masks = load(acq_root / MASKS_FILE)
    sha_by_path: dict[Path, str] = {}
    with (acq_root / "acquired_images.csv").open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            if r.get("local_path"):
                sha_by_path[(acq_root / r["local_path"]).resolve()] = (r.get("sha256") or "").lower()
    with raw_csv.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        fields = list(reader.fieldnames or [])
        rows = list(reader)
    if any(c in fields for c in MASK_COLUMNS):
        raise MaskError("raw measurement CSV already carries mask columns")
    kept = excluded = 0
    out_rows, log = [], []
    for r in rows:
        path = Path(r.get("path") or "").resolve()
        digest = sha_by_path.get(path)
        if not digest:
            raise MaskError("measured photo is not in acquired_images.csv")
        try:
            actual = hashlib.sha256(path.read_bytes()).hexdigest()
        except OSError:
            raise MaskError("measured photo is unreadable")
        if actual != digest:
            raise MaskError("photo bytes do not match their SHA-256")
        mask = masks.get(digest)
        if mask is None:
            raise MaskError("photo %s has no eligibility mask" % digest[:12])
        if mask["model"] != model:
            raise MaskError("mask layout %s does not match calibrator layout %s" % (mask["model"], model))
        ok = set(mask["eligible_marker_ids"])
        for metric in metrics:
            if not math.isfinite(_f(r.get(metric))):
                continue
            missing = sorted(dependencies(metric, model, r) - ok)
            if missing:
                r[metric] = ""
                excluded += 1
                log.append({"sha256": digest, "physical_watch_id": r.get("physical_watch_id", ""), "metric": metric,
                            "excluded_markers": " ".join(map(str, missing)),
                            "statuses": " ".join("%d:%s" % (h, mask["markers"][str(h)]["status"]) for h in missing)})
            else:
                kept += 1
        r.update({"sha256": digest, "mask_policy_version": mask["mask_policy_version"],
                  "mask_digest": mask["mask_digest"], "eligible_marker_ids": " ".join(map(str, sorted(ok)))})
        out_rows.append(r)
    with out_csv.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields + list(MASK_COLUMNS))
        w.writeheader()
        w.writerows(out_rows)
    with exclusions_csv.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=["sha256", "physical_watch_id", "metric", "excluded_markers", "statuses"])
        w.writeheader()
        w.writerows(log)
    return {"photos": len(out_rows), "values_kept": kept, "values_excluded": excluded}


def check_rows(rows: list[dict], metrics: list[str], config: dict | None) -> None:
    """Consumption-time check before any statistic: masked rows must prove every value they carry is eligible."""
    masked = required(config or {})
    for r in rows:
        carries = any(r.get(c) not in (None, "") for c in MASK_COLUMNS)
        if not masked:
            if carries:
                raise MaskError("masked measurements reached the unmasked legacy calibration path")
            continue
        if r.get("mask_policy_version") != el.MASK_POLICY_VERSION or not re.fullmatch(r"[0-9a-f]{64}", r.get("mask_digest") or ""):
            raise MaskError("unmasked or unknown-policy measurement row in a masked calibration")
        try:
            ok = {int(x) for x in (r.get("eligible_marker_ids") or "").split()}
        except ValueError:
            raise MaskError("invalid eligible marker list")
        model = layout(config)
        for metric in metrics:
            if math.isfinite(_f(r.get(metric))) and not dependencies(metric, model, r) <= ok:
                raise MaskError("an ineligible marker's value reached calibration (%s)" % metric)
