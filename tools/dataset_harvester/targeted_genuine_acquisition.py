#!/usr/bin/env python3
"""Target the genuine-repeatability gap without weakening harvester acceptance rules.

This helper does two things before the normal harvester processes sources:
1. Reopens already-accepted genuine dealer/auction listings and adds gallery image URLs so the
   same physical watch can contribute multiple usable views.
2. Uses Brave with site-specific dealer/auction queries for exact 12-series GMT references, rather
   than repeating the broad queries that have saturated.

It only seeds/requeues sources. Quality, provenance, duplicate handling, measurement and
ACCEPT/QUARANTINE/REJECT decisions remain in the normal dataset harvester.
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "dataset_harvester"))

from harvester.adapters import BraveSearch, Candidate, provider_for, relevance  # noqa: E402
from harvester.canonical import host_of  # noqa: E402
from harvester.config import LIMITS, Paths, SUPPORTED_MODELS  # noqa: E402
from harvester.decide import accepted_counts  # noqa: E402
from harvester.http import FetchError, Http  # noqa: E402
from harvester.metadata import DEALER_HOSTS, HIGH, infer  # noqa: E402
from harvester.pipeline import Pipeline  # noqa: E402
from harvester.resolvers import page_images  # noqa: E402
from harvester.state import ACCEPT, DONE, NEW, QUARANTINE  # noqa: E402

# Site-specific searches deliberately avoid general editorial/blog queries where possible.
# Every host is already covered by the harvester's strong genuine-provenance rules.
SITE_QUERIES = (
    ("bobswatches.com", '-guide -blog -news'),
    ("watchfinder.co.uk", '-magazine -article'),
    ("watchfinder.com", '-magazine -article'),
    ("phillips.com", 'lot auction'),
    ("sothebys.com", 'lot auction'),
    ("christies.com", 'lot auction'),
    ("watches-of-switzerland.co.uk", 'pre-owned CPO'),
    ("swisswatchexpo.com", '-thewatchclub -guide -blog'),
)


def exact_genuine_counts(state) -> dict[str, int]:
    counts = {m: 0 for m in SUPPORTED_MODELS}
    c = accepted_counts(state)
    for (cls, model), n in c["by_model"].items():
        if cls == "gen" and model in counts:
            counts[model] = n
    return counts


def expand_existing_genuine_galleries(p: Pipeline, limit: int) -> tuple[int, int, int]:
    """Add gallery URLs to known genuine dealer listings and requeue only when something new appears."""
    rows = []
    for s in p.state.sources.values():
        if not s.url or s.class_label != "gen" or s.decision not in (ACCEPT, QUARANTINE):
            continue
        host = host_of(s.url)
        if host not in DEALER_HOSTS and not any(host.endswith("." + h) for h in DEALER_HOSTS):
            continue
        if DEALER_HOSTS.get(host, "") == "official" or s.sample_role == "reference_only":
            continue
        # Accepted listings are the best repeatability targets; quarantined listings may become
        # useful if another gallery view is measurable.
        rows.append((0 if s.decision == ACCEPT else 1, s))
    rows.sort(key=lambda x: (x[0], x[1].processed_at or "", x[1].key))

    pages, requeued, new_urls = 0, 0, 0
    for _, s in rows[:limit]:
        try:
            refs = page_images(p.http, s.url, LIMITS.max_images_per_source)
        except FetchError as e:
            print(f"gallery skip {s.url}: {e.reason}")
            continue
        except Exception as e:
            print(f"gallery skip {s.url}: {type(e).__name__}: {e}")
            continue
        pages += 1
        added = 0
        for ref in refs:
            if ref.url and ref.url not in s.image_urls:
                s.image_urls.append(ref.url)
                added += 1
        if not added:
            continue
        # A curated local image used to take precedence over image_urls in resolve(). We already
        # retain its bytes/hash in state, so clear only the source shortcut and let the gallery be
        # processed. This does not delete any image record or cached bytes.
        if s.local_paths:
            s.meta.setdefault("targeted_previous_local_paths", list(s.local_paths))
            s.local_paths = []
        s.status = NEW
        s.retry_count = 0
        s.failure_reason = ""
        s.priority = max(s.priority, 1000.0 if s.decision == ACCEPT else 950.0)
        s.meta["targeted_gallery_expanded"] = True
        s.meta["targeted_gallery_new_urls"] = s.meta.get("targeted_gallery_new_urls", 0) + added
        requeued += 1
        new_urls += added
    return pages, requeued, new_urls


def targeted_queries(state, max_queries: int) -> list[tuple[str, tuple[str, str, str]]]:
    counts = exact_genuine_counts(state)
    # Fill zero/low-coverage references first, but keep BLNR/BLRO in the rotation because they are
    # high-yield sources of independent genuine watches for a fresh holdout.
    model_order = sorted(SUPPORTED_MODELS, key=lambda m: (counts.get(m, 0), m not in ("126710BLNR", "126710BLRO"), m))
    out = []
    # Round-robin by site means one prolific dealer cannot consume the whole query budget.
    for host, extra in SITE_QUERIES:
        for model in model_order:
            q = f'"{model}" "Rolex GMT-Master II" site:{host} {extra}'.strip()
            out.append((" ".join(q.split()), ("gen", model, "Rolex")))
            if len(out) >= max_queries:
                return out
    return out


def discover_new_genuine(p: Pipeline, max_queries: int, results_per_query: int) -> tuple[int, int, int]:
    brave = BraveSearch()
    if not brave.configured():
        raise SystemExit("BRAVE_SEARCH_API_KEY is required")
    seen_results, accepted_results, newly_stored = 0, 0, 0
    for q, group in targeted_queries(p.state, max_queries):
        try:
            results = brave.search(p.http, q, results_per_query)
        except FetchError as e:
            print(f"search skip {q!r}: {e.reason}")
            continue
        for rank, r in enumerate(results):
            seen_results += 1
            text = (r.get("title", "") + " — " + r.get("snippet", "")).strip(" —")
            if relevance(r.get("url", ""), text, group, reddit_available=False):
                continue
            inf = infer(text, r.get("url", ""))
            # Do not trust the query itself as the label. The returned page/title/URL must infer
            # as the exact requested model on a strong genuine source.
            if inf.class_label != "gen" or inf.model != group[1] or inf.provenance not in {
                "established_dealer", "auction_house", "rolex_cpo"
            }:
                continue
            accepted_results += 1
            cand = Candidate(
                url=r["url"], adapter="search:brave", provider=provider_for(r["url"]),
                title=text, class_label="gen", model=inf.model, factory="Rolex",
                provenance=inf.provenance, label_confidence=HIGH,
                label_evidence=list(inf.evidence) + [f"targeted exact-reference dealer query: {q}"],
                priority=900.0 - rank * 0.01,
                meta={"query": q, "target_group": list(group), "targeted_genuine_listing": True},
            )
            if p.state.add_source(p._record(cand)):
                newly_stored += 1
    return seen_results, accepted_results, newly_stored


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", type=Path, default=Path(os.environ.get("HARVEST_DATA_DIR", ROOT / "datasets" / "harvest")))
    ap.add_argument("--max-queries", type=int, default=64)
    ap.add_argument("--results-per-query", type=int, default=20)
    ap.add_argument("--expand-limit", type=int, default=50)
    args = ap.parse_args(argv)

    p = Pipeline(paths=Paths(args.data_dir), http=Http(), harness=object(), max_sources=1)
    before = accepted_counts(p.state)
    pages, requeued, new_urls = expand_existing_genuine_galleries(p, args.expand_limit)
    searched, relevant, new_sources = discover_new_genuine(p, args.max_queries, args.results_per_query)
    p.state.save()

    print("# Targeted genuine acquisition seed")
    print(f"- accepted before: gen={before['by_class'].get('gen', 0)} rep={before['by_class'].get('rep', 0)}")
    print(f"- known genuine listing pages checked for extra views: {pages}")
    print(f"- existing genuine listings requeued with new gallery URLs: {requeued}")
    print(f"- new gallery image URLs added: {new_urls}")
    print(f"- Brave results inspected: {searched}")
    print(f"- exact-model strong-provenance results retained: {relevant}")
    print(f"- genuinely new source URLs stored: {new_sources}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
