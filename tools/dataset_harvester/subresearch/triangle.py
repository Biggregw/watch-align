"""Cross-variant consensus for the v2 Submariner 12-triangle detector (research only).

SubTriangle.java reports every triangle candidate in every variant (original, resized, shifted,
rotated). The physical outline a photo shows does not change between those variants, so the selected
outline should not either. Per photo:

  1. Only variants whose dial was edge-fitted take part (fallback dial localisation is excluded and
     counted); the original photo must be one of them.
  2. Candidates of all variants, mapped to original-image pixels, are grouped into outlines: same
     outline when the centroids are within CLUSTER_CENTRE_R dial radii and the widths within
     CLUSTER_WIDTH_RATIO.
  3. Each variant votes for the outline of its own top-ranked candidate. The consensus outline has the
     most votes (ties: lower mean score).
  4. In every variant the best-ranked candidate of the consensus outline is selected. A variant with no
     candidate of that outline reports "12" as not detected there.

Landmark rows: "12" = consensus selection, "12_top" = each variant's own top candidate (no consensus),
"12_legacy" = the v1 research fit. Nothing here is a verdict.
"""
from __future__ import annotations

import math
from collections import defaultdict

CLUSTER_CENTRE_R = 0.02
CLUSTER_WIDTH_RATIO = 1.12
# A variant takes part only when its dial fit is the SAME dial as the original's: in development photos
# the edge fit sometimes locks onto another ring (rehaut or bezel edge) in a perturbed copy while still
# reporting success; such variants are excluded like fallback dials.
# Same plausibility windows as SubTriangle.java (re-applied here so older driver output is treated alike).
PLAUS_RHO = (0.68, 0.92)
PLAUS_WIDTH = (0.12, 0.35)
PLAUS_GAP = (-0.01, 0.10)
PLAUS_APEX = (30.0, 58.0)
PLAUS_HW = (0.9, 1.9)
SCORE_MAX = 6.5


def plausible(c: dict) -> bool:
    if c.get("plausible") is False or str(c.get("plausible")) == "False":
        return False
    rho, w, gap = _f(c.get("rho_r")), _f(c.get("width_over_r")), _f(c.get("gap_over_r"))
    if not (PLAUS_RHO[0] <= rho <= PLAUS_RHO[1]) or not (PLAUS_WIDTH[0] <= w <= PLAUS_WIDTH[1]):
        return False
    if math.isfinite(gap) and not (PLAUS_GAP[0] <= gap <= PLAUS_GAP[1]):
        return False
    apex, hw = _f(c.get("apex_deg")), _f(c.get("length_over_r")) / w if w > 0 else math.nan
    if not (PLAUS_APEX[0] <= apex <= PLAUS_APEX[1] and PLAUS_HW[0] <= hw <= PLAUS_HW[1]):
        return False
    return _f(c.get("sel_score")) <= SCORE_MAX


DIAL_SAME_CENTRE_R = 0.01
DIAL_SAME_RADIUS_REL = 0.02


def dial_usable(r: dict, orig: dict) -> bool:
    if str(r.get("dial_found")) != "True" or r.get("dial_source") != "edge_fit":
        return False
    R = _f(orig.get("dial_r"))
    if not R > 0:
        return False
    d = math.hypot(_f(r.get("dial_cx")) - _f(orig.get("dial_cx")), _f(r.get("dial_cy")) - _f(orig.get("dial_cy")))
    return d <= DIAL_SAME_CENTRE_R * R and abs(_f(r.get("dial_r")) / R - 1) <= DIAL_SAME_RADIUS_REL


def _f(v):
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except (TypeError, ValueError):
        return math.nan


def cluster(cands: list[dict], base_r: float) -> list[int]:
    """Outline id per candidate (greedy, best score first)."""
    order = sorted(range(len(cands)), key=lambda i: _f(cands[i].get("sel_score")))
    reps: list[dict] = []
    ids = [-1] * len(cands)
    for i in order:
        c = cands[i]
        for k, rep in enumerate(reps):
            d = math.hypot(_f(c["x"]) - _f(rep["x"]), _f(c["y"]) - _f(rep["y"]))
            wr = _f(c["width_px"]) / _f(rep["width_px"]) if _f(rep["width_px"]) > 0 else math.inf
            if d <= CLUSTER_CENTRE_R * base_r and 1 / CLUSTER_WIDTH_RATIO <= wr <= CLUSTER_WIDTH_RATIO:
                ids[i] = k
                break
        else:
            ids[i] = len(reps)
            reps.append(c)
    return ids


def _clear(recs: list[dict], reason: str) -> None:
    for r in recs:
        lms = r.get("landmarks") or []
        for i, l in enumerate(lms):
            if l.get("landmark") == "12":
                lms[i] = {"landmark": "12", "kind": "triangle", "hour": 12, "detected": False, "reason": reason,
                          "detector": l.get("detector", ""), "cand_count": l.get("cand_count")}


def apply_consensus(records: list[dict]) -> dict:
    """Rewrites each record's landmark "12" to the consensus selection and adds "12_top".
    Returns per-photo consensus diagnostics {sha: {...}}."""
    by_photo = defaultdict(list)
    for rec in records:
        by_photo[rec.get("sha256", "")].append(rec)
    diag = {}
    for sha, recs in by_photo.items():
        orig = next((r for r in recs if r.get("variant") == "orig"), None)
        usable = [r for r in recs if orig is not None and dial_usable(r, orig)]
        excluded = [r.get("variant") for r in recs if r not in usable]
        for r in recs:
            lms = r.get("landmarks") or []
            top = next((l for l in lms if l.get("landmark") == "12"), None)
            if top is not None:
                t = dict(top)
                t["landmark"], t["kind"] = "12_top", "triangle_top"
                lms.append(t)
        if orig is None or orig not in usable or str(orig.get("edge_fit_valid")) != "True":
            _clear(recs, "original dial not edge-fitted: the v2 triangle study uses edge-fitted dials only")
            diag[sha] = {"variants_edge_fit": len(usable), "variants_excluded_fallback_dial": len(excluded), "consensus": False}
            continue
        base_r = _f(orig.get("dial_r"))
        flat, owner = [], []
        for r in usable:
            for c in r.get("tri_candidates") or []:
                if not plausible(c):
                    continue
                flat.append(c)
                owner.append(r["variant"])
        ids = cluster(flat, base_r) if flat else []
        for c, k in zip(flat, ids):
            c["consensus_cluster"] = k
        votes, scores = defaultdict(int), defaultdict(list)
        top_of = {}
        for c, v, k in zip(flat, owner, ids):
            scores[k].append(_f(c.get("sel_score")))
            if v not in top_of or _f(c.get("cand_rank")) < _f(top_of[v].get("cand_rank")):
                top_of[v] = c
        for v, c in top_of.items():
            votes[c["consensus_cluster"]] += 1
        if not votes:
            _clear(recs, "no plausible 12-triangle candidate in any variant")
            diag[sha] = {"variants_edge_fit": len(usable), "variants_excluded_fallback_dial": len(excluded), "consensus": False}
            continue
        win = min(votes, key=lambda k: (-votes[k], sum(scores[k]) / len(scores[k])))
        support = votes[win] / len(usable)
        for r in recs:
            lms = r.get("landmarks") or []
            idx = next((i for i, l in enumerate(lms) if l.get("landmark") == "12"), None)
            if idx is None:
                continue
            old = lms[idx]
            if r not in usable:
                lms[idx] = {"landmark": "12", "kind": "triangle", "hour": 12, "detected": False,
                            "reason": "dial not edge-fitted, or not the original's dial, in this variant (excluded)",
                            "detector": old.get("detector", "")}
                continue
            mine = [c for c, v in zip(flat, owner) if v == r["variant"] and c.get("consensus_cluster") == win]
            if not mine:
                lms[idx] = {"landmark": "12", "kind": "triangle", "hour": 12, "detected": False,
                            "reason": "consensus outline not found in this variant", "detector": old.get("detector", ""),
                            "consensus_cluster": win, "consensus_support": support,
                            "variant_top_cand_id": (top_of.get(r["variant"]) or {}).get("cand_id", "")}
                continue
            sel = min(mine, key=lambda c: _f(c.get("cand_rank")))
            vtop = top_of.get(r["variant"])
            new = {"landmark": "12", "kind": "triangle", "hour": 12, "detected": True, "detector": old.get("detector", ""),
                   "cand_count": old.get("cand_count")}
            new.update(sel)
            new.update({"consensus_cluster": win, "consensus_support": support,
                        "selected_is_variant_top": vtop is sel,
                        "variant_top_cand_id": (top_of.get(r["variant"]) or {}).get("cand_id", "")})
            lms[idx] = new
        diag[sha] = {"variants_edge_fit": len(usable), "variants_excluded_fallback_dial": len(excluded), "consensus": True,
                     "consensus_support": support, "outlines": len(set(ids))}
    return diag
