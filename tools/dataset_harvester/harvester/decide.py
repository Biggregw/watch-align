"""Per-watch dataset decision (ACCEPT / QUARANTINE / REJECT) and dataset-value assessment.

One source (post, album, listing, phone group) is one physical watch unless the repository says
otherwise (curated manifests may map several sources to one physical_watch_id). Counts are always
reported per independent physical watch as well as per image.
"""
from __future__ import annotations

import os
from collections import Counter, defaultdict

from .config import LIMITS, SUPPORTED_MODELS
from .metadata import MEDIUM, at_least
from .state import ACCEPT, QUARANTINE, REJECT, State

# Watch-level reason codes.
R_NO_IMAGES = "reject_no_images"
R_DUPLICATE = "reject_duplicate"
R_UNSUPPORTED = "reject_unsupported_model"
R_UNUSABLE = "reject_unusable_imagery"
Q_CLASS = "quarantine_uncertain_class"
Q_MODEL = "quarantine_uncertain_model"
Q_FACTORY = "quarantine_uncertain_factory"
Q_GEN_PROVENANCE = "quarantine_weak_genuine_provenance"
Q_SAME_WATCH = "quarantine_possible_same_watch"
Q_INCONCLUSIVE = "quarantine_inconclusive_images"
Q_SATURATED = "quarantine_low_value_group_saturated"

STRONG_GENUINE = {"official", "established_dealer", "auction_house", "rolex_cpo", "owner_verified"}
# A replica needs a traceable replica source: a QC post/curated replica label, an owner tag that
# links to its source, or an explicit verified override (provenance_overrides.csv).
REPLICA_PROVENANCE = {"rep_labelled", "owner_tagged_traceable", "owner_verified"}
Q_UNTRACEABLE = "quarantine_untraceable_provenance"
REFERENCE_ONLY, POPULATION = "reference_only", "population"

# Optional research acquisition targets. They are deliberately disabled by default so normal
# harvester behaviour is unchanged unless a workflow explicitly asks it to fill a corpus gap.
# When enabled, discovery still respects model/factory under-representation, but class-level gaps
# (for example 17 genuine against a target of 50) multiply the search priority.
TARGET_GEN_WATCHES = int(os.environ.get("HARVEST_TARGET_GEN_WATCHES", "0") or 0)
TARGET_REP_WATCHES = int(os.environ.get("HARVEST_TARGET_REP_WATCHES", "0") or 0)
TARGET_VIEWS_PER_WATCH = max(1, int(os.environ.get("HARVEST_TARGET_VIEWS_PER_WATCH", "2") or 2))


def group_key(cls: str, model: str, factory: str) -> tuple:
    return (cls, model or "?", (factory or "?") if cls == "rep" else "Rolex")


def accepted_counts(state: State, exclude_key: str = "") -> dict:
    """Independent-watch and usable-image counts of the ACCEPTED dataset."""
    watches: dict[str, tuple] = {}
    images = Counter()
    for s in state.sources.values():
        if s.decision != ACCEPT or s.key == exclude_key or s.sample_role == REFERENCE_ONLY:
            continue
        watches[s.physical_watch_id] = group_key(s.class_label, s.model, s.factory)
        images[s.physical_watch_id] += sum(1 for h in s.image_shas if state.images.get(h) and usable_for(state, s, state.images[h]))
    by_group, by_model, by_class, by_factory = Counter(), Counter(), Counter(), Counter()
    for wid, g in watches.items():
        by_group[g] += 1
        by_model[(g[0], g[1])] += 1
        by_class[g[0]] += 1
        if g[0] == "rep":
            by_factory[g[2]] += 1
    return {"watches": watches, "images": images, "by_group": by_group, "by_model": by_model, "by_class": by_class, "by_factory": by_factory}


def dup_owner(state: State, rec, im) -> str | None:
    """Source key that owns the original of this image when rec's copy is a duplicate, else None.
    Exact copies share one image record (first source listed owns it); near/dial copies point at
    the original image with duplicate_of."""
    if im.source_keys and im.source_keys[0] != rec.key:
        return im.source_keys[0]
    if im.duplicate_of and im.duplicate_kind != "possible" and im.duplicate_of in state.images:
        ks = state.images[im.duplicate_of].source_keys
        return ks[0] if ks else "?"
    return None


def usable_for(state: State, rec, im) -> bool:
    return im.suitable and im.measurement_status == "measured" and dup_owner(state, rec, im) is None


# Discovery emphasis (a prior, not a quota): the steel/two-tone references that are replicated
# most, and the factories most represented in the repository's sources, are searched first when
# counts tie. Precious-metal references still appear, just later.
MODEL_EMPHASIS = {"126710BLNR": 1.0, "126710BLRO": 1.0, "126710GRNR": 1.0, "126720VTNR": 1.0, "126711CHNR": 0.8,
                  "126713GRNR": 0.8, "126715CHNR": 0.5, "126718GRNR": 0.4, "126719BLRO": 0.4, "126729VTNR": 0.4}


def factory_emphasis(state: State, factories: list[str]) -> dict[str, float]:
    seen = Counter(s.factory for s in state.sources.values() if s.class_label == "rep" and s.factory)
    base = {"VSF": 3, "Clean": 3, "ARF": 2, "GMF": 1, "C+": 1}
    return {f: 0.4 + 0.6 * min(1.0, (seen.get(f, 0) + base.get(f, 0)) / 3.0) for f in factories}


def _target_multiplier(cls: str, c: dict) -> float:
    """Boost an under-target class without turning the target into a quota or acceptance rule.

    At zero target the multiplier is 1.0, preserving the original ordering. At the start of a
    large gap it approaches 4.0 and tapers smoothly to 1.0 as the target is reached.
    """
    target = TARGET_GEN_WATCHES if cls == "gen" else TARGET_REP_WATCHES if cls == "rep" else 0
    if target <= 0:
        return 1.0
    current = c["by_class"].get(cls, 0)
    if current >= target:
        return 1.0
    gap_fraction = (target - current) / target
    return 1.0 + 3.0 * gap_fraction


def priorities(state: State, factories: list[str], top: int = 40) -> list[tuple]:
    """Most-needed (class, model, factory) groups first.

    Base weight remains model/factory emphasis divided by accepted independent watches in the
    group. Optional HARVEST_TARGET_* environment variables multiply under-target classes so a
    research run can deliberately fill a fresh-holdout gap without changing acceptance rules.
    """
    c = accepted_counts(state)
    fe = factory_emphasis(state, factories)
    gen_mult = _target_multiplier("gen", c)
    rep_mult = _target_multiplier("rep", c)
    out = []
    for m in SUPPORTED_MODELS:
        me = MODEL_EMPHASIS.get(m, 0.5)
        n = c["by_group"].get(("gen", m, "Rolex"), 0)
        out.append(("gen", m, "Rolex", n, round(me * gen_mult / (1 + n), 4)))
        for f in factories:
            n = c["by_group"].get(("rep", m, f), 0)
            out.append(("rep", m, f, n, round(me * fe.get(f, 0.4) * rep_mult / (1 + n), 4)))
    out.sort(key=lambda x: (-x[4], x[0] != "gen", x[1], x[2]))
    return out[:top]


def value(state: State, rec) -> tuple[float, list[str]]:
    """How much this source adds: new independent watches first, repeatability views second."""
    c = accepted_counts(state, exclude_key=rec.key)
    notes, score = [], 0.0
    if rec.physical_watch_id in c["watches"]:
        existing_views = c["images"].get(rec.physical_watch_id, 0)
        new_views = sum(1 for h in rec.image_shas if h in state.images and usable_for(state, rec, state.images[h]))
        if new_views:
            needed = max(0, TARGET_VIEWS_PER_WATCH - existing_views)
            score = 0.5 + 0.25 * min(new_views, max(1, needed))
            notes.append(f"existing physical watch gains {new_views} usable view(s); had {existing_views}, repeatability target {TARGET_VIEWS_PER_WATCH}")
            return round(score, 3), notes
        return 0.0, ["physical watch already in the accepted set; no new usable repeatability view"]
    score += 1.0
    notes.append("new independent physical watch")
    g = group_key(rec.class_label, rec.model, rec.factory)
    ng, nm = c["by_group"].get(g, 0), c["by_model"].get((rec.class_label, rec.model), 0)
    score += 1.0 / (1 + ng) + 0.5 / (1 + nm)
    good_views = sum(1 for h in rec.image_shas if h in state.images and usable_for(state, rec, state.images[h]))
    if good_views >= TARGET_VIEWS_PER_WATCH:
        score += 0.5
        notes.append(f"repeatability-rich source: {good_views} usable views")
    notes.append(f"group {'/'.join(g)} has {ng} accepted watch(es); model {rec.model or '?'} ({rec.class_label}) has {nm}")
    return round(score, 3), notes


def decide(state: State, rec, inference_unsupported: str = "") -> tuple[str, list[str]]:
    imgs = [state.images[h] for h in rec.image_shas if h in state.images]
    if not imgs:
        return REJECT, [R_NO_IMAGES]
    reasons: list[str] = []
    # Duplicates: copies of images another source already holds.
    other_watch = Counter()
    dups = []
    for im in imgs:
        owner = dup_owner(state, rec, im)
        if owner is None:
            continue
        dups.append(im)
        s = state.sources.get(owner)
        if s and s.physical_watch_id and s.physical_watch_id != rec.physical_watch_id:
            other_watch[s.physical_watch_id] += 1
    non_dup = [im for im in imgs if im not in dups]
    for im in non_dup:
        if im.duplicate_kind == "possible" and im.duplicate_of in state.images:
            ks = state.images[im.duplicate_of].source_keys
            s = state.sources.get(ks[0]) if ks else None
            if s and s.physical_watch_id != rec.physical_watch_id:
                other_watch[s.physical_watch_id] += 1
    if not non_dup:
        if other_watch:
            rec.duplicate_of_watch = other_watch.most_common(1)[0][0]
        return REJECT, [R_DUPLICATE]
    if other_watch:
        rec.duplicate_of_watch = other_watch.most_common(1)[0][0]
        reasons.append(Q_SAME_WATCH)
    if inference_unsupported and not rec.model:
        return REJECT, [R_UNSUPPORTED]
    good = [im for im in non_dup if usable_for(state, rec, im)]
    inconclusive = [im for im in non_dup if any(r.startswith("inconclusive") for r in im.reasons)
                    or (im.suitable and im.measurement_status != "measured")]
    # Labels.
    if rec.class_label not in ("gen", "rep") or not at_least(rec.label_confidence, MEDIUM):
        reasons.append(Q_CLASS)
    if rec.model not in SUPPORTED_MODELS:
        reasons.append(Q_MODEL)
    if rec.class_label == "rep" and not rec.factory:
        reasons.append(Q_FACTORY)
    if rec.class_label == "gen" and rec.provenance not in STRONG_GENUINE:
        reasons.append(Q_UNTRACEABLE if rec.provenance == "owner_tagged" else Q_GEN_PROVENANCE)
    if rec.class_label == "rep" and rec.provenance not in REPLICA_PROVENANCE:
        reasons.append(Q_UNTRACEABLE)
    if not good:
        if inconclusive:
            return QUARANTINE, reasons + [Q_INCONCLUSIVE]
        img_reasons = sorted({r for im in non_dup for r in im.reasons}) or ([R_DUPLICATE] if not non_dup else [])
        return REJECT, [R_UNUSABLE] + img_reasons
    if reasons:
        return QUARANTINE, reasons
    counts = accepted_counts(state, exclude_key=rec.key)
    if counts["by_group"].get(group_key(rec.class_label, rec.model, rec.factory), 0) >= LIMITS.group_saturation \
            and rec.physical_watch_id not in counts["watches"]:
        return QUARANTINE, [Q_SATURATED]
    return ACCEPT, [f"{len(good)} measurement-quality image(s)"]


def dataset_summary(state: State) -> dict:
    c = accepted_counts(state)
    decisions = defaultdict(set)
    for s in state.sources.values():
        if s.decision and s.physical_watch_id:
            decisions[s.decision].add(s.physical_watch_id)
    by_class_multiview = Counter()
    for wid, g in c["watches"].items():
        if c["images"].get(wid, 0) >= TARGET_VIEWS_PER_WATCH:
            by_class_multiview[g[0]] += 1
    return {
        "accepted_watches": len(c["watches"]),
        "accepted_usable_images": sum(c["images"].values()),
        "by_class_watches": dict(c["by_class"]),
        "by_model_watches": {f"{k[0]}:{k[1]}": v for k, v in sorted(c["by_model"].items())},
        "by_factory_watches": dict(sorted(c["by_factory"].items())),
        "by_group_watches": {"/".join(k): v for k, v in sorted(c["by_group"].items())},
        "repeatability_target_views": TARGET_VIEWS_PER_WATCH,
        "multiview_watches": sum(by_class_multiview.values()),
        "multiview_watches_by_class": dict(by_class_multiview),
        "research_targets": {"gen_watches": TARGET_GEN_WATCHES, "rep_watches": TARGET_REP_WATCHES},
        "reference_only_watches": sorted({s.physical_watch_id for s in state.sources.values()
                                          if s.decision == ACCEPT and s.sample_role == REFERENCE_ONLY}),
        "quarantined_watches": len(decisions[QUARANTINE] - decisions[ACCEPT]),
        "rejected_watches": len(decisions[REJECT] - decisions[ACCEPT] - decisions[QUARANTINE]),
    }
