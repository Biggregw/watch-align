"""Research dataset semantics for the Submariner acquisition manifest.

Input: acquired_images.csv from submariner_acquire.py (older manifests with class "genuine"/"replica"
are normalised too). Output: one row per image and one per physical watch, each with an explicit
ACCEPT / QUARANTINE / REJECT research state and reason code.

* The independence unit is physical_watch_id. Several photos of one watch are one sample.
* Exact duplicates (sha256) and near duplicates (dHash + pHash match, confirmed at pixel level by
  hashing.differing_fraction) inside one watch keep the first photo and REJECT the rest.
* The same photograph under two DIFFERENT watches cannot belong to both, and is usually a site or
  stock image: every copy is QUARANTINED (quarantine_cross_watch_duplicate), never counted.
* A dealer image whose own path does not carry the listing's id is QUARANTINED
  (quarantine_unverified_listing_image): dealer pages also show other watches' thumbnails.
* Only submariner_12 models are admitted; GMTs and 11-series references are REJECTED.
* Class labels are gen / rep. Replicas are stress cases, not classifier labels; nothing here uses
  class to accept or reject a photo.
Image suitability for measurement (dial found etc.) is NOT decided here; the measurement records it.
"""
from __future__ import annotations

import csv
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

from PIL import Image, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from harvester import hashing  # noqa: E402
from harvester.config import LIMITS, THRESHOLDS  # noqa: E402
from harvester.families import SUBMARINER_12, family_for_model  # noqa: E402

ACCEPT, QUARANTINE, REJECT = "ACCEPT", "QUARANTINE", "REJECT"
CLASS_ALIASES = {"gen": "gen", "genuine": "gen", "rep": "rep", "replica": "rep"}

IMAGE_FIELDS = ["sha256", "local_path", "physical_watch_id", "candidate_id", "family", "model", "class_label", "factory",
                "image_index", "image_url", "listing_id", "width", "height", "dhash", "phash",
                "research_state", "reason", "duplicate_of"]
WATCH_FIELDS = ["physical_watch_id", "family", "model", "class_label", "factory", "candidate_ids", "images_total",
                "images_accepted", "images_quarantined", "images_rejected", "research_state", "reason"]


def normalize_class(value: str) -> str:
    return CLASS_ALIASES.get((value or "").strip().lower(), "")


@dataclass
class Img:
    row: dict
    sha256: str
    local_path: str
    watch: str
    candidate: str
    model: str
    cls: str
    factory: str
    index: int
    url: str
    listing: str
    width: int = 0
    height: int = 0
    dhash: str = ""
    phash: str = ""
    state: str = ""
    reason: str = ""
    duplicate_of: str = ""
    image: Image.Image | None = field(default=None, repr=False)

    def out(self, family: str) -> dict:
        return {"sha256": self.sha256, "local_path": self.local_path, "physical_watch_id": self.watch,
                "candidate_id": self.candidate, "family": family, "model": self.model, "class_label": self.cls,
                "factory": self.factory, "image_index": self.index, "image_url": self.url, "listing_id": self.listing,
                "width": self.width, "height": self.height, "dhash": self.dhash, "phash": self.phash,
                "research_state": self.state, "reason": self.reason, "duplicate_of": self.duplicate_of}


def _set(im: Img, state: str, reason: str, dup: str = "") -> None:
    """First decision wins, except that QUARANTINE/REJECT always override ACCEPT."""
    if im.state and im.state != ACCEPT:
        return
    im.state, im.reason, im.duplicate_of = state, reason, dup


def same_photo(a: Image.Image, b: Image.Image) -> bool:
    if abs(a.width / a.height - b.width / b.height) > 0.03:
        return False
    return hashing.differing_fraction(a, b, shift=4) <= THRESHOLDS.same_photo_max_diff_fraction


def build(manifest_rows: list[dict], root: Path) -> tuple[list[dict], list[dict]]:
    imgs: list[Img] = []
    for r in manifest_rows:
        imgs.append(Img(row=r, sha256=r.get("sha256", ""), local_path=r.get("local_path", ""),
                        watch=(r.get("physical_watch_id") or "").strip() or r["candidate_id"],
                        candidate=r["candidate_id"], model=(r.get("model") or "").strip().upper(),
                        cls=normalize_class(r.get("class_label") or r.get("class") or ""),
                        factory=(r.get("factory") or "").strip(), index=int(r.get("image_index") or 0),
                        url=r.get("image_url") or "", listing=(r.get("listing_id") or "").strip()))
    imgs.sort(key=lambda i: (i.watch, i.candidate, i.index, i.sha256))

    # Watch-level label consistency.
    labels = defaultdict(set)
    for i in imgs:
        labels[i.watch].add((i.model, i.cls))
    for i in imgs:
        fam = family_for_model(i.model)
        if fam is None or fam.key != SUBMARINER_12.key:
            _set(i, REJECT, "reject_unsupported_model")
        elif len(labels[i.watch]) > 1:
            _set(i, QUARANTINE, "quarantine_conflicting_watch_labels")
        elif not i.cls:
            _set(i, QUARANTINE, "quarantine_unknown_class")

    # Readability, resolution, hashes.
    for i in imgs:
        try:
            with Image.open(root / i.local_path) as raw:
                im = ImageOps.exif_transpose(raw).convert("RGB")
        except Exception:
            _set(i, REJECT, "reject_unreadable")
            continue
        i.width, i.height = im.width, im.height
        i.dhash, i.phash = hashing.dhash(im), hashing.phash(im)
        im.thumbnail((640, 640), Image.Resampling.LANCZOS)   # enough for the 256x256 pixel confirmation
        i.image = im
        if min(im.width, im.height) < THRESHOLDS.min_image_side_px:
            _set(i, REJECT, "reject_low_resolution")

    # Dealer images must belong to this listing.
    for i in imgs:
        if i.listing and i.url and i.listing not in urlparse(i.url).path:
            _set(i, QUARANTINE, "quarantine_unverified_listing_image")

    # Exact duplicates.
    by_sha = defaultdict(list)
    for i in imgs:
        if i.sha256:
            by_sha[i.sha256].append(i)
    for sha, group in by_sha.items():
        watches = {i.watch for i in group}
        if len(watches) > 1:
            for i in group:
                _set(i, QUARANTINE, "quarantine_cross_watch_duplicate", sha)
        else:
            for i in group[1:]:
                _set(i, REJECT, "reject_exact_duplicate", group[0].sha256)

    # Near duplicates (resized / re-encoded copies), confirmed at pixel level.
    live = [i for i in imgs if i.image is not None and i.state != REJECT]
    first_of_sha = {}
    for i in live:
        first_of_sha.setdefault(i.sha256, i)
    uniq = list(first_of_sha.values())
    for a_i, a in enumerate(uniq):
        for b in uniq[a_i + 1:]:
            if hashing.hamming(a.dhash, b.dhash) > THRESHOLDS.near_dup_dhash_max or hashing.hamming(a.phash, b.phash) > THRESHOLDS.near_dup_phash_max:
                continue
            if not same_photo(a.image, b.image):
                continue
            if a.watch != b.watch:
                for x in (a, b):
                    for y in by_sha[x.sha256]:
                        _set(y, QUARANTINE, "quarantine_cross_watch_near_duplicate", (b if x is a else a).sha256)
            else:
                _set(b, REJECT, "reject_near_duplicate", a.sha256)

    for i in imgs:
        if not i.state:
            i.state = ACCEPT
        i.image = None

    image_rows = [i.out(SUBMARINER_12.key if i.reason != "reject_unsupported_model" else (family_for_model(i.model).key if family_for_model(i.model) else "")) for i in imgs]
    return image_rows, watch_rows(image_rows)


def watch_rows(image_rows: list[dict]) -> list[dict]:
    by = defaultdict(list)
    for r in image_rows:
        by[r["physical_watch_id"]].append(r)
    out = []
    for w, rs in sorted(by.items()):
        c = Counter(r["research_state"] for r in rs)
        reasons = Counter(r["reason"] for r in rs if r["research_state"] != ACCEPT)
        base = rs[0]
        if any(r["reason"] == "reject_unsupported_model" for r in rs):
            state, reason = REJECT, "reject_unsupported_model"
        elif any(r["reason"] in ("quarantine_conflicting_watch_labels", "quarantine_unknown_class") for r in rs):
            state, reason = QUARANTINE, sorted(r["reason"] for r in rs if r["reason"].startswith("quarantine_"))[0]
        elif c[ACCEPT] == 0:
            state, reason = QUARANTINE, "quarantine_no_accepted_images"
        else:
            state, reason = ACCEPT, ""
        out.append({"physical_watch_id": w, "family": base["family"], "model": base["model"], "class_label": base["class_label"],
                    "factory": base["factory"], "candidate_ids": ";".join(sorted({r["candidate_id"] for r in rs})),
                    "images_total": len(rs), "images_accepted": c[ACCEPT], "images_quarantined": c[QUARANTINE],
                    "images_rejected": c[REJECT], "research_state": state,
                    "reason": reason or ";".join(f"{k}:{v}" for k, v in sorted(reasons.items()))})
    return out


def add_unacquired(watches: list[dict], candidate_summary: list[dict]) -> list[dict]:
    """Candidates that produced no image stay visible as QUARANTINE quarantine_no_images."""
    have = {w["physical_watch_id"] for w in watches}
    out = list(watches)
    for c in candidate_summary:
        wid = (c.get("physical_watch_id") or "").strip() or c["candidate_id"]
        if wid in have:
            continue
        model = (c.get("model") or "").upper()
        fam = family_for_model(model)
        ok = fam is not None and fam.key == SUBMARINER_12.key
        out.append({"physical_watch_id": wid, "family": fam.key if fam else "", "model": model,
                    "class_label": normalize_class(c.get("class_label") or c.get("class") or ""), "factory": c.get("factory", ""),
                    "candidate_ids": c["candidate_id"], "images_total": 0, "images_accepted": 0, "images_quarantined": 0,
                    "images_rejected": 0, "research_state": QUARANTINE if ok else REJECT,
                    "reason": ("quarantine_no_images:" + (c.get("acquisition_status") or "")) if ok else "reject_unsupported_model"})
        have.add(wid)
    return sorted(out, key=lambda w: w["physical_watch_id"])


def independent_watch_count(image_rows: list[dict], state: str = ACCEPT) -> int:
    """Independent samples: distinct physical watches with at least one image in `state`."""
    return len({r["physical_watch_id"] for r in image_rows if r["research_state"] == state})


def read_csv(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write_csv(path: Path, rows: list[dict], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
