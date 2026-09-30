"""DISCOVER -> NORMALISE SOURCE -> DOWNLOAD -> GROUP -> DEDUPLICATE -> CHECK QUALITY -> CLASSIFY
METADATA -> MEASURE -> ASSESS VALUE -> ACCEPT/QUARANTINE/REJECT -> SAVE STATE -> CONTINUE."""
from __future__ import annotations

import hashlib
import io
import os
import shutil
import time
import traceback
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageOps

from . import adapters as ad
from . import decide as dc
from . import hashing, metadata, quality
from . import reddit as rd
from .canonical import canonical_url
from .config import LIMITS, REPO_ROOT, THRESHOLDS, Paths
from .harness import Harness, HarnessUnavailable
from .http import FetchError, Http
from .outputs import MANIFEST_COLUMNS, WATCH_COLUMNS, build_report, manifest_rows, watch_rows, write_csv, write_reports
from .resolvers import Deferred, ImageRef, resolve
from .state import (ACCEPT, DEFERRED, DONE, FAILED, FAILED_FINAL, NEW, QUARANTINE, REJECT, ImageRecord, SourceRecord,
                    State, now_iso)

Image.MAX_IMAGE_PIXELS = 80_000_000


def rel(p: Path) -> str:
    try:
        return str(Path(p).resolve().relative_to(REPO_ROOT))
    except ValueError:
        return str(Path(p).resolve())


def absolute(p: str) -> Path:
    q = Path(p)
    return q if q.is_absolute() else REPO_ROOT / q


def open_image(data: bytes) -> Image.Image | None:
    try:
        im = Image.open(io.BytesIO(data))
        im.load()
        return ImageOps.exif_transpose(im).convert("RGB")
    except Exception:
        return None


class Pipeline:
    def __init__(self, paths: Paths | None = None, http: Http | None = None, harness: Harness | None = None,
                 adapters: dict | None = None, dry_run: bool = False, reprocess: bool = False,
                 max_sources: int = LIMITS.default_max_sources, chunk: int = 8, log=print, should_stop=lambda: False):
        self.paths = paths or Paths()
        self.dry_run = dry_run
        self.http = http or Http(offline=dry_run)
        self.harness = harness or Harness()
        self.adapters = adapters if adapters is not None else ad.all_adapters()
        self.reprocess = reprocess
        self.max_sources = max_sources
        self.chunk = chunk
        self.log = log
        self.should_stop = should_stop
        if not dry_run:
            self.paths.ensure()
        self.state = State(self.paths.state_dir)
        self.factories = [name for _, name in metadata.factories()]
        creds = rd.credentials()
        self.reddit = rd.RedditApi(self.http, creds) if creds and not dry_run else None
        self.run = {"run_id": datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"), "started_at": now_iso(), "dry_run": dry_run,
                    "adapters": {}, "errors": [], "images_rejected_by_reason": {}, "watch_reasons": {}}
        self.c = Counter()

    # ------------------------------------------------------------------------------------ discover
    def discover(self, only: list[str] | None = None) -> None:
        pri = dc.priorities(self.state, self.factories)
        known_shas = set(self.state.images)
        ctx = ad.DiscoveryContext(http=self.http, priorities=pri, max_queries=int(os.environ.get("HARVEST_SEARCH_MAX_QUERIES", "6")), known_shas=known_shas)
        for name, a in self.adapters.items():
            if only and name not in only:
                self.run["adapters"][name] = "not selected"
                continue
            ok, why = a.status()
            self.run["adapters"][name] = why
            if not ok:
                continue
            if self.dry_run and name in ("search", "reddit"):
                self.run.setdefault("planned_queries", {})[name] = [q for q, _ in ad.search_queries(pri, ctx.max_queries)]
                continue
            try:
                for cand in a.discover(ctx):
                    if cand.meta.get("error"):
                        self.run["errors"].append(f"{name}: {cand.meta['error']}")
                        continue
                    rec = self._record(cand)
                    if self.state.add_source(rec):
                        self.c["sources_discovered_new"] += 1
                    else:
                        self.c["sources_already_known"] += 1
            except FetchError as e:
                self.run["errors"].append(f"{name}: {e}")
        if not self.dry_run:
            self.state.save()

    def _record(self, cand: ad.Candidate) -> SourceRecord:
        key = cand.key
        return SourceRecord(
            key=key, url=canonical_url(cand.url) if cand.url else "", adapter=cand.adapter, provider=cand.provider or ad.provider_for(cand.url),
            source_id=cand.source_id, title=cand.title, image_urls=list(cand.image_urls), local_paths=list(cand.local_paths),
            class_label=cand.class_label, model=cand.model, factory=cand.factory, provenance=cand.provenance,
            physical_watch_id=cand.physical_watch_id, label_confidence=cand.label_confidence,
            label_evidence=list(cand.label_evidence), priority=cand.priority,
            meta=dict(cand.meta, curated=bool(cand.class_label and cand.label_confidence)),
        )

    # ------------------------------------------------------------------------------------- process
    def available_providers(self) -> set:
        s = {"local", "direct", "page"}
        if self.reddit:
            s.add("reddit")
        if os.environ.get("IMGUR_CLIENT_ID") or shutil.which("gallery-dl"):
            s.add("imgur_album")
        return s

    def process(self) -> None:
        pending = self.state.pending(self.reprocess, LIMITS.max_retries, self.available_providers())[: self.max_sources]
        self.c["sources_pending_total"] = len(self.state.pending(self.reprocess, LIMITS.max_retries, self.available_providers()))
        if self.dry_run:
            self.run["plan"] = [self._plan(s) for s in pending]
            self.c["sources_examined"] = 0
            return
        for i in range(0, len(pending), self.chunk):
            if self.should_stop():
                self.run["errors"].append("stopped on request; the remaining sources stay pending")
                break
            batch = pending[i:i + self.chunk]
            ready = [s for s in batch if self._prepare(s)]
            self.state.save()
            if ready:
                try:
                    self._analyse(ready)
                    self._measure(ready)
                except HarnessUnavailable as e:
                    for s in ready:
                        self._fail(s, "harness_unavailable", str(e), retryable=True)
                    self.run["errors"].append(f"analysis harness unavailable: {e}")
                    self.state.save()
                    break
                for s in ready:
                    self._decide(s)
            self.state.save()
            self.write_outputs()

    def _plan(self, s: SourceRecord) -> dict:
        from .canonical import imgur_album_id, is_direct_image, reddit_post_id
        if any(absolute(p).exists() for p in s.local_paths):
            how = f"local files ({len(s.local_paths)})"
        elif s.image_urls:
            how = f"download {len(s.image_urls)} known image URL(s)"
        elif s.meta.get("album_url") or (s.url and imgur_album_id(s.url)):
            how = "imgur album" + ("" if "imgur_album" in self.available_providers() else " (deferred: no Imgur client id or gallery-dl)")
        elif s.url and reddit_post_id(s.url):
            how = "reddit post via official API" if rd.credentials() else "deferred: needs Reddit API credentials"
        elif s.url and is_direct_image(s.url):
            how = "direct image"
        else:
            how = "listing page (robots.txt respected)"
        return {"source": s.url or s.key, "watch": s.physical_watch_id, "class": s.class_label, "how": how}

    def _fail(self, s: SourceRecord, reason: str, detail: str = "", retryable: bool = True) -> None:
        s.retry_count += 1
        s.failure_reason = f"{reason}: {detail}" if detail else reason
        s.status = FAILED if retryable and s.retry_count < LIMITS.max_retries else FAILED_FINAL
        s.processed_at = now_iso()
        self.c["sources_failed"] += 1
        self.run["errors"].append(f"{s.url or s.key}: {s.failure_reason}" + (f" (retry {s.retry_count}/{LIMITS.max_retries})" if s.status == FAILED else " (final)"))

    def _labels(self, s: SourceRecord) -> None:
        """CLASSIFY METADATA. Curated labels stay; otherwise infer from the source text and URL."""
        inf = metadata.infer(s.title, s.url)
        s.meta["unsupported_model"] = inf.unsupported_model
        if s.meta.get("curated"):
            # A curated genuine source recorded conservatively as gen_candidate but hosted by an
            # auction house / dealer / Rolex CPO gets that tier, with the reason kept as evidence.
            if s.class_label == "gen" and s.provenance == "gen_candidate" and inf.provenance in dc.STRONG_GENUINE and inf.provenance != "owner_tagged":
                s.label_evidence.append(f"provenance raised to {inf.provenance} by source host ({s.url}); curated file said gen_candidate")
                s.provenance, s.label_confidence = inf.provenance, metadata.HIGH
            if not s.model:
                s.model = inf.model
                s.label_evidence += inf.evidence
            return
        s.class_label, s.provenance = inf.class_label, inf.provenance
        s.model, s.factory = inf.model, inf.factory
        s.label_confidence = inf.label_confidence()
        s.label_evidence = inf.evidence
        s.meta["source_type"] = inf.source_type
        s.meta["confidence_parts"] = {"class": inf.class_confidence, "model": inf.model_confidence, "factory": inf.factory_confidence}

    def _prepare(self, s: SourceRecord) -> bool:
        """NORMALISE SOURCE + DOWNLOAD + exact/near duplicate check. True when images are ready."""
        self.c["sources_examined"] += 1
        work = self.paths.work_dir / hashlib.sha1(s.key.encode()).hexdigest()[:12]
        try:
            refs = resolve(s, self.http, work, self.reddit)
        except Deferred as d:
            s.status, s.failure_reason, s.provider = DEFERRED, d.reason, d.provider
            self.c["sources_deferred"] += 1
            return False
        except FetchError as e:
            self._fail(s, e.reason, e.detail, e.retryable)
            return False
        except Exception as e:  # a resolver bug must not stop the run
            self._fail(s, "resolver_error", f"{type(e).__name__}: {e}")
            return False
        self._labels(s)
        if not s.physical_watch_id:
            ident = s.source_id or hashlib.sha1(s.key.encode()).hexdigest()[:10]
            s.physical_watch_id = f"hv_{s.provider}_{ident}"
        shas, errors = [], Counter()
        url_sha = s.meta.setdefault("url_sha", {})
        for ref in refs:
            try:
                h = self._ingest(s, ref, url_sha)
                if h and h not in shas:
                    shas.append(h)
            except FetchError as e:
                errors[e.reason] += 1
        if errors:
            s.meta["download_errors"] = dict(errors)
        if not shas:
            if errors:
                self._fail(s, "no_images_downloaded", ", ".join(f"{k} x{v}" for k, v in errors.items()),
                           retryable=any(k in ("network_error", "rate_limited", "http_error") for k in errors))
                return False
            s.image_shas = []
            s.decision, s.decision_reasons, s.status, s.processed_at = REJECT, [dc.R_NO_IMAGES], DONE, now_iso()
            self.c["watches_rejected"] += 1
            self.run["watch_reasons"][dc.R_NO_IMAGES] = self.run["watch_reasons"].get(dc.R_NO_IMAGES, 0) + 1
            return False
        s.image_shas = shas
        return True

    def _ingest(self, s: SourceRecord, ref: ImageRef, url_sha: dict) -> str | None:
        if ref.path:
            path = Path(ref.path)
            data = path.read_bytes()
            if self.paths.work_dir.resolve() in path.resolve().parents:
                # Fetched into scratch space (album tools): stored like any other download.
                self.c["images_downloaded"] += 1
                stored = None
            else:
                self.c["images_local"] += 1
                stored = path
        else:
            known = url_sha.get(ref.url)
            if known and known in self.state.images and absolute(self.state.images[known].local_path).exists():
                return known
            r = self.http.get(ref.url)
            data = r.content
            if len(data) < LIMITS.min_download_bytes:
                raise FetchError("too_small", ref.url, retryable=False)
            self.c["images_downloaded"] += 1
            stored = None
        img = open_image(data)
        if img is None:
            self.c["images_unreadable"] += 1
            raise FetchError("unreadable_image", ref.url or ref.path, retryable=False)
        h = hashing.sha256_bytes(data)
        if ref.url:
            url_sha[ref.url] = h
        existing = self.state.images.get(h)
        if existing:
            if s.key not in existing.source_keys:
                existing.source_keys.append(s.key)
                if existing.source_keys[0] != s.key:
                    self.c["exact_duplicates"] += 1
            if not absolute(existing.local_path).exists() and stored is not None:
                existing.local_path = rel(stored)
            return h
        if stored is None:
            fmt = (Image.open(io.BytesIO(data)).format or "JPEG").lower()
            ext = {"jpeg": ".jpg", "png": ".png", "webp": ".webp"}.get(fmt, ".jpg")
            stored = self.paths.images_dir / h[:2] / f"{h}{ext}"
            stored.parent.mkdir(parents=True, exist_ok=True)
            stored.write_bytes(data)
        rec = ImageRecord(sha256=h, seq=self.state.next_seq(), source_keys=[s.key], image_url=ref.url, local_path=rel(stored), width=img.width,
                          height=img.height, bytes=len(data), dhash=hashing.dhash(img), phash=hashing.phash(img))
        # Near duplicate (resized / re-encoded copy of a photo already held): hash match, then a
        # pixel-level confirmation; if the other photo is no longer on disk it stays "possible".
        for other in sorted(self.state.images.values(), key=lambda o: o.seq):
            if hashing.hamming(rec.dhash, other.dhash) <= THRESHOLDS.near_dup_dhash_max \
                    and hashing.hamming(rec.phash, other.phash) <= THRESHOLDS.near_dup_phash_max:
                kind = self._confirm(img, None, other, None)
                if kind:
                    rec.duplicate_of, rec.duplicate_kind = other.sha256, kind
                    self.c["near_duplicates" if kind == "near" else "possible_duplicates"] += 1
                    if kind == "near":
                        break
        self.state.images[h] = rec
        return h

    def _confirm(self, img: Image.Image, box, other: ImageRecord, other_box) -> str:
        """"near" when the pixels confirm the same photograph, "possible" when the other photo is
        unavailable to compare, "" when the hashes matched different photographs."""
        try:
            o = ImageOps.exif_transpose(Image.open(absolute(other.local_path))).convert("RGB")
        except Exception:
            return "possible"
        if box is None and abs(img.width / img.height - o.width / o.height) > 0.03:
            return ""
        # Dial crops come from two independent dial fits, so allow a little more misalignment.
        frac = hashing.differing_fraction(img, o, box, other_box, shift=4 if box is None else 8)
        return "near" if frac <= THRESHOLDS.same_photo_max_diff_fraction else ""

    def _analyse(self, ready: list[SourceRecord]) -> None:
        """CHECK IMAGE QUALITY with the app's own analysis, then pixel checks and dial-crop dedup."""
        todo = []
        for s in ready:
            for h in s.image_shas:
                im = self.state.images[h]
                if (not im.analysed_at or self.reprocess) and h not in [t.sha256 for t in todo]:
                    todo.append(im)
        if not todo:
            return
        results = self.harness.suitability([str(absolute(im.local_path)) for im in todo], self.paths.work_dir / "suit")
        for im in todo:
            suit = results.get(str(absolute(im.local_path)))
            try:
                img = ImageOps.exif_transpose(Image.open(absolute(im.local_path))).convert("RGB")
            except Exception:
                img = None
            a = quality.assess(img, suit)
            im.suitability = {"app": suit, "perspective": a["perspective"], "details": a["details"]}
            im.quality, im.reasons, im.suitable = a["quality"], a["reasons"], a["suitable"]
            im.analysed_at = now_iso()
            g = quality.dial_geometry(suit or {})
            if img is not None and g is not None:
                im.dial_phash = hashing.dial_phash(img, g["cx"], g["cy"], g["radius"])
                if not im.duplicate_of or im.duplicate_kind == "possible":
                    for other in sorted(self.state.images.values(), key=lambda o: o.seq):
                        if other.seq >= im.seq or not other.dial_phash or \
                                hashing.hamming(im.dial_phash, other.dial_phash) > THRESHOLDS.near_dup_dial_phash_max:
                            continue
                        og = quality.dial_geometry(((other.suitability or {}).get("app")) or {})
                        if og is None:
                            continue
                        kind = self._confirm(img, hashing.dial_box(g["cx"], g["cy"], g["radius"]), other,
                                             hashing.dial_box(og["cx"], og["cy"], og["radius"]))
                        if kind == "near":
                            im.duplicate_of, im.duplicate_kind = other.sha256, "dial"
                            self.c["near_duplicates"] += 1
                            break

    def _measure(self, ready: list[SourceRecord]) -> None:
        """RUN WATCH ALIGN MEASUREMENTS (the regression Batch driver) on suitable, non-duplicate images."""
        rows, ims = [], {}
        for s in ready:
            for h in s.image_shas:
                im = self.state.images[h]
                if not im.suitable or im.duplicate_of or dc.dup_owner(self.state, s, im):
                    if not im.measurement_status:
                        im.measurement_status = "skipped"
                    continue
                if im.measurement_status == "measured" and not self.reprocess:
                    continue
                p = str(absolute(im.local_path))
                if p not in ims:
                    rows.append({"path": p, "class_label": s.class_label, "physical_watch_id": s.physical_watch_id, "factory": s.factory})
                    ims[p] = im
        if not rows:
            return
        out = self.paths.measurements_dir / self.run["run_id"] / f"chunk_{self.c['measure_chunks']:03d}"
        self.c["measure_chunks"] += 1
        res = self.harness.measure(rows, out)
        for p, im in ims.items():
            row = res.get(p)
            if row and not any(str(v).startswith("ERROR") for v in row.values()) and row.get("no_dial", "false") != "true":
                im.measurement_status, im.measurement_file = "measured", rel(out / "r*.csv")
                im.measurement_row = {k: v for k, v in row.items() if k not in ("path", "overlay")}
            else:
                im.measurement_status = "failed"
                im.suitable = False
                if quality.INCONCLUSIVE_MEASUREMENT not in im.reasons:
                    im.reasons.append(quality.INCONCLUSIVE_MEASUREMENT)

    def _decide(self, s: SourceRecord) -> None:
        """ASSESS VALUE + ACCEPT / QUARANTINE / REJECT."""
        decision, reasons = dc.decide(self.state, s, s.meta.get("unsupported_model", ""))
        s.decision, s.decision_reasons = decision, reasons
        s.value_score, s.value_notes = dc.value(self.state, s) if decision != REJECT else (0.0, [])
        s.status, s.processed_at, s.failure_reason = DONE, now_iso(), ""
        self.c[{"ACCEPT": "watches_accepted", "QUARANTINE": "watches_quarantined", "REJECT": "watches_rejected"}[decision]] += 1
        for r in reasons:
            key = r if not r[0].isdigit() else "accepted"
            self.run["watch_reasons"][key] = self.run["watch_reasons"].get(key, 0) + 1
        for h in s.image_shas:
            im = self.state.images[h]
            if dc.usable_for(self.state, s, im):
                self.c["images_measurement_quality"] += 1
            elif any(r.startswith("reject") for r in im.reasons):
                self.c["images_rejected"] += 1
            elif im.reasons:
                self.c["images_inconclusive"] += 1
            for r in im.reasons:
                self.run["images_rejected_by_reason"][r] = self.run["images_rejected_by_reason"].get(r, 0) + 1

    # ------------------------------------------------------------------------------------- outputs
    def write_outputs(self) -> None:
        write_csv(self.paths.manifest, MANIFEST_COLUMNS, manifest_rows(self.state))
        write_csv(self.paths.watches, WATCH_COLUMNS, watch_rows(self.state))

    COUNTERS = ("sources_discovered_new", "sources_already_known", "sources_pending_total", "sources_examined", "sources_deferred",
                "sources_failed", "images_downloaded", "images_local", "images_unreadable", "exact_duplicates", "near_duplicates",
                "possible_duplicates", "images_rejected", "images_inconclusive", "images_measurement_quality", "watches_accepted", "watches_quarantined",
                "watches_rejected")

    def report(self) -> dict:
        self.run.update({k: self.c.get(k, 0) for k in self.COUNTERS})
        self.run.update({k: v for k, v in self.c.items() if k not in self.COUNTERS and k != "measure_chunks"})
        self.run["finished_at"] = now_iso()
        rep = build_report(self.state, self.run, self.factories)
        if not self.dry_run:
            self.write_outputs()
            write_reports(self.paths.reports_dir, rep)
        return rep
