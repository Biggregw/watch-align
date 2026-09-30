"""Class / reference / factory / source-type inference with evidence and confidence.

Ported from the app's TitleTags (alpha67) rather than invented anew, and extended with evidence:
every inferred value records which words or which source rule produced it, and a confidence of
"high", "medium" or "low". Only high/medium values can reach an ACCEPT; a low value, a conflict,
or a title that mentions a genuine watch next to a replica sends the watch to QUARANTINE.
"""
from __future__ import annotations

import csv
import re
from dataclasses import dataclass, field
from pathlib import Path

from .canonical import host_of, reddit_subreddit
from .config import REPO_ROOT, SUPPORTED_MODELS, UNSUPPORTED_GMT_MODELS

HIGH, MEDIUM, LOW = "high", "medium", "low"
_RANK = {HIGH: 3, MEDIUM: 2, LOW: 1, "": 0}


def at_least(conf: str, level: str) -> bool:
    return _RANK.get(conf, 0) >= _RANK[level]


_REF = re.compile(r"(?<!\d)(1267(?:10|11|13|15|18|19|20|29))\s*-?\s*(BLNR|BLRO|GRNR|CHNR|VTNR)?(?![A-Z0-9])", re.I)
_OLD_REF = re.compile(r"(?<!\d)(" + "|".join(UNSUPPORTED_GMT_MODELS) + r")(?:\s*-?\s*[A-Z]{2,4})?(?!\d)", re.I)
_SUFFIX = re.compile(r"(?<![A-Z0-9])(BLNR|BLRO|GRNR|CHNR|VTNR)(?![A-Z0-9])", re.I)
# (pattern, model, note). Nicknames are "medium": they identify the model in practice but are
# not a reference number.
_NICK = [
    (r"bruce\s*wayne", "126710GRNR", ""),
    (r"batgirl|batman", "126710BLNR", ""),
    (r"pepsi", "126710BLRO", "126719BLRO (white gold) is also called Pepsi"),
    (r"sprite|destro|lefty|left[- ]?handed", "126720VTNR", ""),
    (r"root\s*beer", "126711CHNR", "126715CHNR (Everose) is also a root beer"),
    (r"guinness", "126715CHNR", ""),
]
_SUFFIX_DEFAULT = {"BLNR": "126710BLNR", "BLRO": "126710BLRO", "GRNR": "126710GRNR", "CHNR": "126711CHNR", "VTNR": "126720VTNR"}
# Base factory vocabulary (from TitleTags); extended from the repository manifests at runtime.
_BASE_FACTORIES = [
    (r"vsf|vs\s*factory", "VSF"), (r"clean|cf|clean\s*factory", "Clean"), (r"arf|ar\s*factory", "ARF"), (r"gmf", "GMF"),
    (r"c\+|c\+\s*factory", "C+"), (r"ewf|ew\s*factory", "EWF"), (r"apsf|aps", "APSF"), (r"bpf?", "BP"), (r"djf", "DJF"),
    (r"zf", "ZF"), (r"noob", "Noob"), (r"rich", "Rich"),
]
_MIXED = re.compile(r"\b(gens?|genuine|real|retail|authentic|legit\s*check)\b|\bvs\.?\s*(gen|real)", re.I)
_QC = re.compile(r"\bqc\b|\[qc\]|\bw2c\b", re.I)

# Source provenance rules by host / subreddit.
REPLICA_SUBREDDITS = {"reptimeqc", "reptime", "repwatch", "repwatches"}
MARKETPLACE_SUBREDDITS = {"watchexchange"}
DEALER_HOSTS = {
    "watchfinder.co.uk": "established_dealer", "watchfinder.com": "established_dealer",
    "swisswatchexpo.com": "established_dealer", "bobswatches.com": "established_dealer",
    "phillips.com": "auction_house", "dist.phillips.com": "auction_house",
    "sothebys.com": "auction_house", "christies.com": "auction_house",
    "rolex.com": "official", "media.rolex.com": "official",
    "watches-of-switzerland.co.uk": "rolex_cpo",
}
MARKETPLACE_HOSTS = {"chrono24.com", "chrono24.co.uk", "ebay.com", "ebay.co.uk"}


@dataclass
class Inference:
    class_label: str = ""
    class_confidence: str = ""
    provenance: str = ""
    source_type: str = ""
    model: str = ""
    model_confidence: str = ""
    factory: str = ""
    factory_confidence: str = ""
    unsupported_model: str = ""
    mixed: bool = False
    evidence: list = field(default_factory=list)

    def label_confidence(self) -> str:
        """Overall confidence: the weakest of the parts that matter for this class."""
        parts = [self.class_confidence, self.model_confidence]
        if self.class_label == "rep":
            parts.append(self.factory_confidence)
        return min(parts, key=lambda c: _RANK.get(c, 0)) if all(parts) else LOW


def learn_factories(manifests: list[Path] | None = None) -> list[tuple[str, str]]:
    """Factory vocabulary: the base list plus any factory named in the repository's manifests."""
    vocab = list(_BASE_FACTORIES)
    known = {name.lower() for _, name in vocab}
    if manifests is None:
        manifests = [p for p in (REPO_ROOT / "datasets").rglob("*.csv")]
    for path in manifests:
        try:
            with path.open(newline="", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                if not reader.fieldnames or "factory" not in reader.fieldnames:
                    continue
                for row in reader:
                    name = (row.get("factory") or "").strip()
                    name = re.sub(r"\s*factory$", "", name, flags=re.I).strip()
                    if not name or name.lower() in known or name.lower() in ("rolex", "unknown", "n/a"):
                        continue
                    known.add(name.lower())
                    vocab.append((re.escape(name.lower()), name))
        except (OSError, UnicodeDecodeError, csv.Error):
            continue
    return vocab


_FACTORY_VOCAB: list[tuple[str, str]] | None = None


def factories() -> list[tuple[str, str]]:
    global _FACTORY_VOCAB
    if _FACTORY_VOCAB is None:
        _FACTORY_VOCAB = learn_factories()
    return _FACTORY_VOCAB


def infer_model(text: str) -> tuple[str, str, list[str], str]:
    """(model, confidence, evidence, unsupported_model)."""
    up = text.upper()
    low = text.lower()
    found: dict[str, tuple[str, str]] = {}
    for m in _REF.finditer(up):
        ref, suf = m.group(1), m.group(2)
        if suf:
            found.setdefault(ref + suf, (HIGH, f"reference '{m.group(0).strip()}'"))
        else:
            s = _SUFFIX.search(up)
            nick = next(((mod, pat) for pat, mod, _ in _NICK if re.search(r"\b(" + pat + r")\b", low)), None)
            if s:
                found.setdefault(ref + s.group(1).upper(), (HIGH, f"reference '{ref}' with '{s.group(1)}'"))
            elif nick and nick[0].startswith(ref):
                found.setdefault(nick[0], (HIGH, f"reference '{ref}' with nickname"))
            elif nick:
                found.setdefault(ref + nick[0][6:], (MEDIUM, f"reference '{ref}' with nickname of {nick[0]}"))
            else:
                found.setdefault(ref, (LOW, f"reference '{ref}' without a bezel code"))
    if not found:
        s = _SUFFIX.search(up)
        if s:
            mod = _SUFFIX_DEFAULT[s.group(1).upper()]
            found[mod] = (MEDIUM, f"bezel code '{s.group(1)}' without reference")
    if not found:
        for pat, mod, note in _NICK:
            if re.search(r"\b(" + pat + r")\b", low):
                if mod == "126711CHNR" and re.search(r"everose|rose\s*gold|126715", low):
                    mod, note = "126715CHNR", ""
                found.setdefault(mod, (MEDIUM, f"nickname '{re.search(pat, low).group(0)}'" + (f" ({note})" if note else "")))
    unsupported = ""
    om = _OLD_REF.search(up)
    if om and not found:
        unsupported = om.group(1)
    full = {k: v for k, v in found.items() if k in SUPPORTED_MODELS}
    if len(full) > 1:
        return "", LOW, [f"conflicting models: {', '.join(sorted(full))}"], unsupported
    if full:
        mod, (conf, ev) = next(iter(full.items()))
        return mod, conf, [ev], unsupported
    if found:
        mod, (conf, ev) = next(iter(found.items()))
        return "", LOW, [ev + " (not a supported reference)"], unsupported
    return "", "", [], unsupported


def infer_factory(text: str) -> tuple[str, str, list[str]]:
    low = text.lower()
    hits = []
    for pat, name in factories():
        m = re.search(r"(?<![a-z0-9])(" + pat + r")(?![a-z0-9])", low)
        if m and name not in [h[0] for h in hits]:
            hits.append((name, m.group(0)))
    if not hits:
        return "", "", []
    if len(hits) > 1:
        return "", LOW, ["several factories named: " + ", ".join(h[0] for h in hits)]
    name, word = hits[0]
    return name, HIGH, [f"factory word '{word}'"]


def infer(text: str, url: str = "", host_hint: str = "") -> Inference:
    """Infers everything from the source text (title) and the source URL."""
    text = text or ""
    inf = Inference()
    sub = (reddit_subreddit(url) or "").lower() if url else ""
    host = host_hint or (host_of(url) if url else "")
    inf.mixed = bool(_MIXED.search(text))
    if sub in REPLICA_SUBREDDITS:
        inf.source_type, inf.class_label, inf.provenance = "replica_qc_community", "rep", "rep_labelled"
        inf.class_confidence = HIGH if _QC.search(text) else MEDIUM
        inf.evidence.append(f"posted in r/{sub}" + (" with a QC title" if _QC.search(text) else " (no QC tag in title)"))
    elif sub in MARKETPLACE_SUBREDDITS:
        inf.source_type, inf.class_label, inf.provenance, inf.class_confidence = "marketplace", "gen", "gen_candidate", MEDIUM
        inf.evidence.append(f"marketplace listing in r/{sub}: seller-asserted genuine, not authenticated")
    elif host in DEALER_HOSTS or any(host.endswith("." + h) for h in DEALER_HOSTS):
        tier = DEALER_HOSTS.get(host) or next(v for h, v in DEALER_HOSTS.items() if host.endswith("." + h))
        inf.source_type, inf.class_label, inf.provenance, inf.class_confidence = tier, "gen", tier, HIGH
        inf.evidence.append(f"{tier.replace('_', ' ')} source ({host})")
    elif host in MARKETPLACE_HOSTS:
        inf.source_type, inf.class_label, inf.provenance, inf.class_confidence = "marketplace", "gen", "gen_candidate", MEDIUM
        inf.evidence.append(f"marketplace listing ({host}): seller-asserted genuine, not authenticated")
    else:
        inf.source_type = "unknown"
        if _QC.search(text) and infer_factory(text)[0]:
            inf.class_label, inf.provenance, inf.class_confidence = "rep", "rep_labelled", MEDIUM
            inf.evidence.append("QC title naming a factory, unknown site")
        else:
            inf.class_label, inf.class_confidence = "unsure", LOW
            inf.evidence.append("no provenance rule matched this source")
    if inf.mixed and inf.class_label == "rep":
        inf.class_label, inf.class_confidence = "unsure", LOW
        inf.evidence.append("title also mentions a genuine watch; photos may include it")
    inf.model, inf.model_confidence, ev, inf.unsupported_model = infer_model(text)
    inf.evidence += ev
    if inf.class_label == "gen":
        inf.factory, inf.factory_confidence = "Rolex", inf.class_confidence
    else:
        inf.factory, inf.factory_confidence, fev = infer_factory(text)
        inf.evidence += fev
    return inf
