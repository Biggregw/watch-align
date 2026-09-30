"""Discovery adapters: each finds candidate sources (posts, albums, listings, local photo groups)
and says nothing about image quality. Adapters are independent and optional; one that lacks
credentials reports itself disabled and the others carry on."""
from __future__ import annotations

import csv
import hashlib
import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Protocol
from urllib.parse import quote

from .canonical import canonical_url, host_of, imgur_album_id, is_direct_image, reddit_post_id
from .config import REPO_ROOT
from .http import FetchError, Http
from .metadata import HIGH, MEDIUM
from . import reddit as rd


@dataclass
class Candidate:
    url: str
    adapter: str
    provider: str = ""
    source_id: str = ""
    title: str = ""
    image_urls: list = field(default_factory=list)
    local_paths: list = field(default_factory=list)
    # Labels known from the source itself (curated manifests, phone tags). Empty = infer.
    class_label: str = ""
    model: str = ""
    factory: str = ""
    provenance: str = ""
    physical_watch_id: str = ""
    label_confidence: str = ""
    label_evidence: list = field(default_factory=list)
    priority: float = 0.0
    meta: dict = field(default_factory=dict)

    @property
    def key(self) -> str:
        return canonical_url(self.url) if self.url else "local:" + (self.local_paths[0] if self.local_paths else self.source_id)


class Adapter(Protocol):
    name: str
    def status(self) -> tuple[bool, str]: ...
    def discover(self, ctx: "DiscoveryContext") -> Iterable[Candidate]: ...


@dataclass
class DiscoveryContext:
    http: Http
    priorities: list          # [(class, model, factory, watches, weight)] most-needed first
    max_queries: int = 6
    known_shas: set = field(default_factory=set)


def provider_for(url: str) -> str:
    if not url:
        return "local"
    if reddit_post_id(url):
        return "reddit"
    if imgur_album_id(url):
        return "imgur_album"
    if is_direct_image(url):
        return "direct"
    return "page"


def _rel(p: Path) -> str:
    try:
        return str(p.resolve().relative_to(REPO_ROOT))
    except ValueError:
        return str(p.resolve())


def _rows(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _sha_file(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ------------------------------------------------------------------------------------------------
class RepoSourcesAdapter:
    """Every source already recorded in this repository: corpus manifests, genuine control sets,
    validation source lists, the phone-collected manifest and anything dropped in datasets/inbox/.
    Needs no credentials. Curated labels are taken as they are (high confidence, the curation is
    the evidence); images already downloaded locally are used instead of downloading again."""

    name = "repo"

    def __init__(self, root: Path = REPO_ROOT):
        self.root = Path(root)

    def status(self) -> tuple[bool, str]:
        return True, "enabled (no credentials needed)"

    def discover(self, ctx: DiscoveryContext) -> Iterable[Candidate]:
        yield from self._corpus_manifest(self.root / "datasets" / "126710BLNR", "manifest.csv", "resolved_images.csv")
        yield from self._corpus_manifest(self.root / "tools" / "research" / "validation" / "gmt12", "126710blro_sources.csv", None)
        yield from self._phase_b(self.root / "datasets" / "gmt_phase_b_genuine")
        yield from self._gmt12_validation(self.root / "tools" / "research" / "validation" / "gmt12")
        collected = Path(os.environ.get("HARVEST_COLLECTED_MANIFEST") or self.root / "datasets" / "collected" / "manifest.csv")
        yield from self._collected(collected)
        yield from self._inbox(self.root / "datasets" / "inbox")

    # 126710BLNR corpus format (also used by 126710blro_sources.csv).
    def _corpus_manifest(self, base: Path, manifest: str, resolved: str | None) -> Iterable[Candidate]:
        mpath = base / manifest
        if not mpath.exists():
            return
        local: dict[str, list[str]] = {}
        if resolved and (base / resolved).exists():
            for r in _rows(base / resolved):
                p = base / r["local_path"]
                if p.exists():
                    local.setdefault(r["source_id"], []).append(_rel(p))
        for r in _rows(mpath):
            url = r.get("source_url") or r.get("image_url") or ""
            img = r.get("image_url") or ""
            prov = r.get("provenance", "")
            conf = HIGH if prov in ("official", "rep_labelled") else MEDIUM
            ev = [f"curated repository manifest {_rel(mpath)} (provenance {prov})"]
            if prov == "gen_candidate":
                ev.append("gen_candidate: seller-asserted genuine, not independently authenticated")
            yield Candidate(
                url=url, adapter=self.name, provider=provider_for(img if img and r.get("fetch_mode") == "direct" else (img or url)),
                source_id=r["source_id"], title=r.get("qc_notes", ""),
                image_urls=[img] if img and r.get("fetch_mode") == "direct" else [],
                local_paths=local.get(r["source_id"], []),
                class_label=r["class_label"], model=r.get("model", ""),
                factory=re.sub(r"\s*factory$", "", r.get("factory", ""), flags=re.I),
                provenance=prov, physical_watch_id=r["physical_watch_id"], label_confidence=conf, label_evidence=ev,
                meta={"album_url": img if r.get("fetch_mode") == "gallery" else "", "split": r.get("split", ""),
                      "bracelet": r.get("bracelet", ""), "max_images": r.get("max_images", "")},
            )

    # Phase B genuine control set: dealer/auction listings; accepted_images.csv holds the image
    # URLs a human already selected for each listing.
    def _phase_b(self, base: Path) -> Iterable[Candidate]:
        mpath = base / "manifest.csv"
        if not mpath.exists():
            return
        accepted: dict[str, list[str]] = {}
        if (base / "accepted_images.csv").exists():
            for r in _rows(base / "accepted_images.csv"):
                accepted.setdefault(r["source_id"], []).append(r["image_url"])
        local: dict[str, list[str]] = {}
        img_dir = base / "images"
        if img_dir.exists():
            for p in sorted(img_dir.rglob("*")):
                if p.is_file() and p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"):
                    sid = p.parent.name if p.parent != img_dir else p.stem.rsplit("_", 1)[0]
                    local.setdefault(sid, []).append(_rel(p))
        for r in _rows(mpath):
            sid = r["source_id"]
            yield Candidate(
                url=r["source_url"], adapter=self.name, provider="direct" if accepted.get(sid) else "page", source_id=sid,
                title=r.get("provenance_note", ""), image_urls=accepted.get(sid, []), local_paths=local.get(sid, []),
                class_label="gen", model=r.get("model", ""), factory="Rolex", provenance=r["provenance_class"],
                physical_watch_id=r["physical_watch_id"], label_confidence=HIGH,
                label_evidence=[f"phase B genuine control set ({r['provenance_class']}): {r.get('provenance_note', '')}"],
            )

    def _gmt12_validation(self, base: Path) -> Iterable[Candidate]:
        mpath = base / "manifest.csv"
        if mpath.exists():
            for r in _rows(mpath):
                loc = base / r["image_locator"]
                url = loc.read_text(encoding="utf-8").strip() if loc.exists() and loc.suffix == ".url" else ""
                model = (re.search(r"1267\d\d[A-Z]{4}", r.get("watch_family", "")) or [""])[0]
                if r.get("rep_confirmed") == "yes":
                    yield Candidate(url=url, adapter=self.name, provider=provider_for(url), source_id=r["id"], title=r.get("notes", ""),
                                    image_urls=[url] if url and is_direct_image(url) else [], class_label="rep", model=model,
                                    factory="", provenance="rep_labelled", physical_watch_id=f"gmt12_{r['id']}", label_confidence=MEDIUM,
                                    label_evidence=["gmt12 validation manifest: replica confirmed; factory not recorded"])
        spath = base / "gmt12_sources.txt"
        if spath.exists():
            for line in spath.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#"):
                    yield Candidate(url=line, adapter=self.name, provider=provider_for(line), label_evidence=[f"listed in {_rel(spath)}"])

    # Phone-collected set, after tools/testset/ingest.py (tags reviewed; corrections applied there).
    def _collected(self, mpath: Path) -> Iterable[Candidate]:
        if not mpath.exists():
            return
        yield from phone_candidates(_rows(mpath), self.root, "collected",
                                    evidence="phone Collect tags, reviewed at ingest (datasets/collected/manifest.csv)", reviewed=True)

    # datasets/inbox/<class>/...: images (one folder per watch, or loose files = one watch each)
    # and *.txt URL lists. The folder name gives the class: gen, rep, or unknown.
    def _inbox(self, base: Path) -> Iterable[Candidate]:
        if not base.exists():
            return
        for cls_dir in sorted(p for p in base.iterdir() if p.is_dir()):
            cls = cls_dir.name if cls_dir.name in ("gen", "rep") else "unsure"
            for txt in sorted(cls_dir.rglob("*.txt")):
                for line in txt.read_text(encoding="utf-8").splitlines():
                    line = line.strip()
                    if line and not line.startswith("#"):
                        yield Candidate(url=line, adapter=self.name, provider=provider_for(line), class_label="" if cls == "unsure" else cls,
                                        label_confidence=MEDIUM if cls != "unsure" else "", label_evidence=[f"URL listed in {_rel(txt)}"])
            groups: dict[Path, list[Path]] = {}
            for p in sorted(cls_dir.rglob("*")):
                if p.is_file() and p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"):
                    groups.setdefault(p.parent if p.parent != cls_dir else p, []).append(p)
            for g, files in groups.items():
                sid = "inbox_" + hashlib.sha1(_rel(g).encode()).hexdigest()[:10]
                yield Candidate(url="", adapter=self.name, provider="local", source_id=sid, title=g.name,
                                local_paths=[_rel(f) for f in files], class_label=cls,
                                label_confidence=MEDIUM if cls != "unsure" else "",
                                label_evidence=[f"placed in {_rel(cls_dir)}"], physical_watch_id=sid)


def phone_candidates(rows: Iterable[dict], base: Path, prefix: str, evidence: str, reviewed: bool, known_shas: set | None = None) -> Iterable[Candidate]:
    """Phone manifest rows grouped by physical watch (one candidate per watch)."""
    groups: dict[str, list[dict]] = {}
    for r in rows:
        wid = r.get("physical_watch_id") or ""
        if not wid.startswith(prefix + "_"):
            wid = f"{prefix}_{wid}"
        groups.setdefault(wid, []).append(r)
    for wid, rs in groups.items():
        paths = []
        for r in rs:
            p = base / r["local_path"]
            if not p.exists():
                continue
            if known_shas is not None and _sha_file(p) in known_shas:
                continue
            paths.append(_rel(p))
        if not paths:
            continue
        r0 = rs[0]
        cls = r0.get("class_label", "unsure")
        yield Candidate(url=r0.get("source", "") if r0.get("source", "").startswith("http") else "", adapter="phone" if prefix == "phone" else "repo",
                        provider="local", source_id=wid, title=r0.get("notes", ""), local_paths=paths,
                        class_label=cls if cls in ("gen", "rep") else "unsure", model=r0.get("model", ""), factory=r0.get("factory", ""),
                        provenance="owner_tagged", physical_watch_id=wid,
                        label_confidence=(HIGH if reviewed else MEDIUM) if cls in ("gen", "rep") else "",
                        label_evidence=[evidence])


class PhoneInboxAdapter:
    """Optional: a checkout of the phone upload branch (testset-inbox), path in HARVEST_PHONE_INBOX.
    Photos already present in datasets/collected (same bytes) are skipped: those rows carry
    reviewed tags."""

    name = "phone"

    def __init__(self, path: str | None = None):
        self.path = Path(path or os.environ.get("HARVEST_PHONE_INBOX", "")) if (path or os.environ.get("HARVEST_PHONE_INBOX")) else None

    def status(self) -> tuple[bool, str]:
        if not self.path:
            return False, "disabled: HARVEST_PHONE_INBOX not set (optional manual source)"
        base = self.path / "testset" if (self.path / "testset").is_dir() else self.path
        if not (base / "manifests").is_dir():
            return False, f"disabled: no manifests/ under {self.path}"
        return True, f"enabled ({base})"

    def discover(self, ctx: DiscoveryContext) -> Iterable[Candidate]:
        if not self.status()[0]:
            return
        base = self.path / "testset" if (self.path / "testset").is_dir() else self.path
        rows = []
        # Photos already ingested into datasets/collected carry reviewed tags there; skip them here.
        known = set(ctx.known_shas)
        cm = Path(os.environ.get("HARVEST_COLLECTED_MANIFEST") or REPO_ROOT / "datasets" / "collected" / "manifest.csv")
        if cm.exists():
            with cm.open(newline="", encoding="utf-8") as f:
                known |= {r.get("sha256", "") for r in csv.DictReader(f)}
        for m in sorted((base / "manifests").glob("*.csv")):
            rows += _rows(m)
        yield from phone_candidates(rows, base, "collected", evidence="phone Collect tags (unreviewed upload)",
                                    reviewed=False, known_shas=known)


# ------------------------------------------------------------------------------------------------
class SearchProvider(Protocol):
    name: str
    def configured(self) -> bool: ...
    def search(self, http: Http, query: str, count: int) -> list[dict]: ...


class BraveSearch:
    """Brave Search API (documented, key in BRAVE_SEARCH_API_KEY). Returns [{url, title, snippet}]."""

    name = "brave"
    endpoint = "https://api.search.brave.com/res/v1/web/search"

    def configured(self) -> bool:
        return bool(os.environ.get("BRAVE_SEARCH_API_KEY", "").strip())

    def search(self, http: Http, query: str, count: int = 20) -> list[dict]:
        r = http.get(f"{self.endpoint}?q={quote(query)}&count={count}&safesearch=off",
                     headers={"X-Subscription-Token": os.environ["BRAVE_SEARCH_API_KEY"].strip(), "Accept": "application/json"}, api=True)
        return parse_brave(json.loads(r.content))


def parse_brave(obj: dict) -> list[dict]:
    return [{"url": x.get("url", ""), "title": x.get("title", ""), "snippet": re.sub(r"<[^>]+>", "", x.get("description", "") or "")}
            for x in ((obj or {}).get("web") or {}).get("results") or [] if x.get("url")]


SEARCH_PROVIDERS = {"brave": BraveSearch}


def search_queries(priorities: list, limit: int) -> list[tuple[str, tuple]]:
    """Queries for the most-needed (class, model, factory) groups, most needed first."""
    out = []
    for cls, model, factory, _watches, _w in priorities:
        if cls == "rep":
            q = f"\"{model}\" {factory if factory and factory != '*' else ''} QC photos".replace("  ", " ")
        else:
            q = f"\"{model}\" Rolex GMT-Master II pre-owned authenticated dial"
        out.append((q, (cls, model, factory)))
        if len(out) >= limit:
            break
    return out


class SearchAdapter:
    """Web-search discovery through a documented search API (provider in HARVEST_SEARCH_PROVIDER,
    default brave). Disabled without a key. Never scrapes a search engine's HTML."""

    name = "search"

    def __init__(self, provider: str | None = None):
        cls = SEARCH_PROVIDERS.get((provider or os.environ.get("HARVEST_SEARCH_PROVIDER", "brave")).lower())
        self.provider = cls() if cls else None

    def status(self) -> tuple[bool, str]:
        if not self.provider:
            return False, "disabled: unknown HARVEST_SEARCH_PROVIDER"
        if not self.provider.configured():
            return False, f"disabled: no API key for {self.provider.name} (set BRAVE_SEARCH_API_KEY)"
        return True, f"enabled ({self.provider.name})"

    def discover(self, ctx: DiscoveryContext) -> Iterable[Candidate]:
        if not self.status()[0]:
            return
        for q, group in search_queries(ctx.priorities, ctx.max_queries):
            try:
                results = self.provider.search(ctx.http, q, 20)
            except FetchError as e:
                yield Candidate(url="", adapter=self.name, meta={"error": f"{e.reason} for query {q!r}"})
                continue
            for i, r in enumerate(results):
                yield Candidate(url=r["url"], adapter=f"{self.name}:{self.provider.name}", provider=provider_for(r["url"]),
                                title=(r["title"] + " — " + r["snippet"]).strip(" —"), priority=ctx_weight(ctx, group) - i * 0.01,
                                meta={"query": q, "target_group": list(group)})


def ctx_weight(ctx: DiscoveryContext, group: tuple) -> float:
    for cls, model, factory, _n, w in ctx.priorities:
        if (cls, model, factory) == group:
            return w
    return 0.0


class RedditAdapter:
    """Official Reddit API search of replica QC and marketplace subreddits (optional)."""

    name = "reddit"
    subreddits = ("RepTimeQC",)

    def status(self) -> tuple[bool, str]:
        return (True, "enabled (official API)") if rd.credentials() else (False, "disabled: REDDIT_CLIENT_ID / REDDIT_CLIENT_SECRET not set")

    def discover(self, ctx: DiscoveryContext) -> Iterable[Candidate]:
        creds = rd.credentials()
        if not creds:
            return
        api = rd.RedditApi(ctx.http, creds)
        seen = 0
        for cls, model, factory, _n, w in ctx.priorities:
            if cls != "rep" or seen >= ctx.max_queries:
                continue
            seen += 1
            q = f"{model} {factory}" if factory and factory != "*" else model
            for sub in self.subreddits:
                try:
                    posts, _after = api.search(sub, q)
                except FetchError as e:
                    yield Candidate(url="", adapter=self.name, meta={"error": f"{e.reason} searching r/{sub}"})
                    continue
                for i, p in enumerate(posts):
                    yield Candidate(url=p["permalink"], adapter=self.name, provider="reddit", source_id=p["id"], title=p["title"],
                                    image_urls=p["images"], priority=w - i * 0.01,
                                    meta={"imgur_albums": p["imgur_albums"], "subreddit": p["subreddit"], "query": q})


def all_adapters() -> dict[str, object]:
    return {"repo": RepoSourcesAdapter(), "phone": PhoneInboxAdapter(), "search": SearchAdapter(), "reddit": RedditAdapter()}
