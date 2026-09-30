"""Persistent, resumable harvester state.

Two JSON-lines files under <data>/state/: sources.jsonl (one record per canonical source URL, i.e.
one post/album/listing) and images.jsonl (one record per unique image, keyed by sha256). Lines are
sorted by key so the files diff cleanly in git. Every write is atomic (temp file + rename), and the
pipeline saves after each source, so an interrupted run resumes where it stopped.
"""
from __future__ import annotations

import json
import os
import tempfile
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

# Source status values.
NEW = "new"                 # discovered, not processed yet
DONE = "done"               # processed to a dataset decision
FAILED = "failed"           # failed; will be retried (retry_count < max)
FAILED_FINAL = "failed_final"
DEFERRED = "deferred"       # needs an adapter/credential that is not available now

ACCEPT, QUARANTINE, REJECT = "ACCEPT", "QUARANTINE", "REJECT"


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


@dataclass
class SourceRecord:
    key: str                                  # canonical URL (or local:<path> for local-only sources)
    url: str = ""
    adapter: str = ""                          # discovery adapter
    provider: str = ""                         # reddit / imgur / direct / page / local / phone
    source_id: str = ""                        # post/album/listing id at the provider
    discovered_at: str = ""
    processed_at: str = ""
    status: str = NEW
    failure_reason: str = ""
    retry_count: int = 0
    title: str = ""                            # original text used for inference (kept verbatim)
    image_urls: list = field(default_factory=list)
    local_paths: list = field(default_factory=list)   # images already on disk (repository corpus)
    image_shas: list = field(default_factory=list)
    physical_watch_id: str = ""
    class_label: str = ""                      # gen / rep / unsure
    model: str = ""
    factory: str = ""
    provenance: str = ""                       # official / established_dealer / rep_labelled / ...
    label_confidence: str = ""                 # high / medium / low
    label_evidence: list = field(default_factory=list)
    decision: str = ""
    decision_reasons: list = field(default_factory=list)
    value_score: float = 0.0
    value_notes: list = field(default_factory=list)
    duplicate_of_watch: str = ""
    priority: float = 0.0
    meta: dict = field(default_factory=dict)


@dataclass
class ImageRecord:
    sha256: str
    seq: int = 0                               # order first held (the first copy is the original)
    source_keys: list = field(default_factory=list)
    image_url: str = ""
    local_path: str = ""                       # relative to the repository root when inside it
    width: int = 0
    height: int = 0
    bytes: int = 0
    dhash: str = ""
    phash: str = ""
    dial_phash: str = ""
    duplicate_of: str = ""                     # sha256 of the first copy seen
    duplicate_kind: str = ""                   # exact / near / dial
    analysed_at: str = ""
    suitability: dict = field(default_factory=dict)   # raw app analysis + pixel checks
    quality: dict = field(default_factory=dict)
    suitable: bool = False
    reasons: list = field(default_factory=list)       # reason codes (reject_* / inconclusive_*)
    measurement_status: str = ""               # "", measured, failed, skipped
    measurement_file: str = ""
    measurement_row: dict = field(default_factory=dict)


def _load(path: Path, cls):
    out = {}
    if not path.exists():
        return out
    names = set(cls.__dataclass_fields__)
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            d = json.loads(line)
            rec = cls(**{k: v for k, v in d.items() if k in names})
            out[getattr(rec, "key", None) or getattr(rec, "sha256")] = rec
    return out


def _atomic_write(path: Path, lines: Iterable[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name, dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            for line in lines:
                f.write(line)
                f.write("\n")
        os.replace(tmp, path)
    except BaseException:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


class State:
    def __init__(self, state_dir: Path):
        self.dir = Path(state_dir)
        self.sources: dict[str, SourceRecord] = _load(self.dir / "sources.jsonl", SourceRecord)
        self.images: dict[str, ImageRecord] = _load(self.dir / "images.jsonl", ImageRecord)

    def save(self) -> None:
        _atomic_write(self.dir / "sources.jsonl",
                      (json.dumps(asdict(self.sources[k]), sort_keys=True, ensure_ascii=False) for k in sorted(self.sources)))
        _atomic_write(self.dir / "images.jsonl",
                      (json.dumps(asdict(self.images[k]), sort_keys=True, ensure_ascii=False) for k in sorted(self.images)))

    # ---- sources ----
    def add_source(self, rec: SourceRecord) -> bool:
        """Adds a newly discovered source. False (and nothing changes) when it is already known."""
        if rec.key in self.sources:
            known = self.sources[rec.key]
            # A later adapter may know more (e.g. local copies); merge without resetting status.
            for p in rec.local_paths:
                if p not in known.local_paths:
                    known.local_paths.append(p)
            return False
        rec.discovered_at = rec.discovered_at or now_iso()
        self.sources[rec.key] = rec
        return True

    def pending(self, reprocess: bool = False, max_retries: int = 3, available_providers: set | None = None) -> list[SourceRecord]:
        out = []
        for s in self.sources.values():
            if reprocess and s.status in (DONE, FAILED, FAILED_FINAL):
                out.append(s)
            elif s.status == NEW:
                out.append(s)
            elif s.status == FAILED and s.retry_count < max_retries:
                out.append(s)
            elif s.status == DEFERRED and available_providers is not None and s.provider in available_providers:
                out.append(s)
        out.sort(key=lambda s: (-s.priority, s.discovered_at, s.key))
        return out

    def next_seq(self) -> int:
        return 1 + max((im.seq for im in self.images.values()), default=0)

    def watch_ids(self) -> set[str]:
        return {s.physical_watch_id for s in self.sources.values() if s.physical_watch_id}
