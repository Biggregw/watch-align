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

# Every current GMT-Master II reference and its bezel code. A reference number alone is unique
# except 126710 (BLNR / BLRO / GRNR), which needs its bezel code or a nickname.
REFERENCE_SUFFIX = {"126711": "CHNR", "126713": "GRNR", "126715": "CHNR", "126718": "GRNR", "126719": "BLRO",
                    "126720": "VTNR", "126729": "VTNR"}
# Bezel code (or nickname) -> the references that share it. Only BLNR is unique; for the others a
# bezel code or nickname alone does NOT pick a reference: material evidence must.
FAMILY = {
    "BLNR": ("126710BLNR",),
    "BLRO": ("126710BLRO", "126719BLRO"),
    "GRNR": ("126710GRNR", "126713GRNR", "126718GRNR"),
    "CHNR": ("126711CHNR", "126715CHNR"),
    "VTNR": ("126720VTNR", "126729VTNR"),
}
# (pattern, candidate references, note). A nickname narrows to a family; material words decide.
_NICK = [
    (r"batgirl|batman", ("126710BLNR",), ""),
    (r"bruce\s*wayne", ("126710GRNR",), "nickname of the Oystersteel 126710GRNR"),
    (r"guinness|zombie", ("126713GRNR", "126718GRNR"), "nickname of the yellow Rolesor / yellow gold GRNR"),
    (r"pepsi", FAMILY["BLRO"], "Pepsi is both the steel 126710BLRO and the white gold 126719BLRO"),
    (r"sprite|destro|lefty|left[- ]?handed", FAMILY["VTNR"], "left-handed green/black is both 126720VTNR (steel) and 126729VTNR (white gold)"),
    (r"root\s*beer", FAMILY["CHNR"], "root beer is both 126711CHNR (Everose Rolesor) and 126715CHNR (Everose gold)"),
]
# Material evidence per reference. Two-tone wording is checked first; when present, "gold" words
# in the same title describe the two-tone, not a solid-gold case.
_TWO_TONE = r"two[- ]?tone|rolesor|\btt\b|steel\s*(?:and|&|/)\s*(?:yellow\s*|everose\s*|rose\s*)?gold"
_MATERIAL = {
    "126710BLRO": r"\b(?:oyster)?steel\b|stainless|\bss\b|jubilee",   # 126719BLRO is only on Oyster
    "126719BLRO": r"white\s*gold|\bwg\b|meteorite",
    "126710GRNR": r"\b(?:oyster)?steel\b|stainless|\bss\b",
    "126713GRNR": _TWO_TONE,
    "126718GRNR": r"yellow\s*gold|\byg\b|solid\s*gold|full\s*gold",
    "126711CHNR": _TWO_TONE,
    "126715CHNR": r"(?:everose|rose)\s*gold|solid\s*(?:everose|rose)|full\s*(?:everose|rose)",
    "126720VTNR": r"\b(?:oyster)?steel\b|stainless|\bss\b|jubilee",   # 126729VTNR is only on Oyster
    "126729VTNR": r"white\s*gold|\bwg\b|meteorite",
}
_TWO_TONE_REFS = {"126713GRNR", "126711CHNR"}
_SOLID_GOLD_REFS = {"126718GRNR", "126715CHNR"}
_STEEL_REFS = {"126710BLRO", "126710GRNR", "126720VTNR"}


def resolve_family(candidates: tuple, low: str) -> tuple[str, str]:
    """(reference, evidence) when exactly one candidate has material evidence, else ("", why)."""
    if len(candidates) == 1:
        return candidates[0], "unique"
    two_tone = re.search(_TWO_TONE, low) is not None
    hits = []
    for ref in candidates:
        if two_tone and (ref in _SOLID_GOLD_REFS or ref in _STEEL_REFS):
            continue   # "steel and yellow gold" is two-tone: neither all-steel nor solid gold
        if not two_tone and ref in _TWO_TONE_REFS:
            continue
        if re.search(_MATERIAL[ref], low):
            hits.append(ref)
    if len(hits) == 1:
        return hits[0], f"material words match {hits[0]}"
    if not hits:
        return "", f"shared by {', '.join(candidates)}; no material evidence"
    return "", f"shared by {', '.join(candidates)}; material evidence conflicts ({', '.join(hits)})"


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
    """(model, confidence, evidence, unsupported_model).

    high   = explicit reference with its bezel code, or a reference number that has only one bezel.
    medium = a bezel code / nickname narrowed to one reference (unique, or by material words).
    low    = ambiguous or conflicting: the watch is quarantined rather than given a guessed reference.
    """
    up, low = text.upper(), text.lower()
    found: dict[str, tuple[str, str]] = {}
    ambiguous: list[str] = []
    nick = next(((cands, pat, note) for pat, cands, note in _NICK if re.search(r"\b(" + pat + r")\b", low)), None)
    for m in _REF.finditer(up):
        ref, suf = m.group(1), (m.group(2) or "").upper()
        if not suf:
            s = _SUFFIX.search(up)
            suf = s.group(1).upper() if s else ""
        if not suf and ref in REFERENCE_SUFFIX:
            suf = REFERENCE_SUFFIX[ref]
        if not suf and nick:
            cands = [c for c in nick[0] if c.startswith(ref)]
            if len(cands) == 1:
                found.setdefault(cands[0], (HIGH, f"reference '{ref}' with nickname"))
                continue
        if suf:
            found.setdefault(ref + suf, (HIGH, f"reference '{m.group(0).strip()}'" + ("" if m.group(2) else f" with '{suf}'")))
        else:
            ambiguous.append(f"reference '{ref}' without a bezel code (126710 is BLNR, BLRO or GRNR)")
    if not found and not ambiguous:
        s = _SUFFIX.search(up)
        if s:
            ref, why = resolve_family(FAMILY[s.group(1).upper()], low)
            if ref:
                found[ref] = (MEDIUM, f"bezel code '{s.group(1)}'" + ("" if why == "unique" else f", {why}"))
            else:
                ambiguous.append(f"bezel code '{s.group(1)}' {why}")
    if not found and not ambiguous and nick:
        word = re.search(nick[1], low).group(0)
        ref, why = resolve_family(nick[0], low)
        if ref:
            found[ref] = (MEDIUM, f"nickname '{word}'" + ("" if why == "unique" else f", {why}") + (f" ({nick[2]})" if nick[2] else ""))
        else:
            ambiguous.append(f"nickname '{word}' {why}")
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
    if ambiguous:
        return "", LOW, ambiguous, unsupported
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
