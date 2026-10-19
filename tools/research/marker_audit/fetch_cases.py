#!/usr/bin/env python3
"""Recover the ten pinned originals. Different bytes are never accepted.

Some dealer CDN responses re-encode even with a JPEG Accept header. The explicit
format query recovered the original bytes in this investigation. Both attempts
and their SHA-256 values are recorded; failure to recover any case is fatal.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "dataset_harvester"))
from harvester.http import FetchError, Http

# Keep acquisition usable without installing the plotting dependencies.
CASES = {
    "6f071d93608b9081": 12, "22060f9a8f867d35": 12,
    "800cd2600f322aa5": 12, "02d7c97e429c6984": 12,
    "482e344ce9c60e61": 12, "e418538d0e9af0bf": 12,
    "94ad771e9e2aedde": 5, "2802b4a30cfb277b": 9,
    "c11761654b519222": 12, "ee07ceedbb6f0bd3": 6,
}


def selected(catalogue):
    rows = [r for r in catalogue if r["photo_id"] in CASES]
    if len(rows) != len(CASES) or {r["photo_id"] for r in rows} != set(CASES):
        raise ValueError("Catalogue must contain each of the ten cases exactly once")
    return rows


def fetch(rows, root, log):
    http = Http()
    outcomes = []
    for row in rows:
        dest = root / row["local_path"]
        want = row["sha256"].lower()
        entry = {"photo_id": row["photo_id"], "expected_sha256": want, "attempts": [], "verified": False}
        if dest.is_file() and hashlib.sha256(dest.read_bytes()).hexdigest() == want:
            entry.update(verified=True, cached=True)
        else:
            # A mismatching file must not remain at the path the runner reads.
            if dest.exists():
                dest.unlink()
            url = row["image_url"]
            attempts = [(url, "image/jpeg,image/png;q=0.9,image/*;q=0.5")]
            if "bobswatches.com/" in url:
                attempts.append((url + ("&" if "?" in url else "?") + "format=jpg", "image/jpeg"))
                attempts.append((url + ("&" if "?" in url else "?") + "format=jpeg", "image/jpeg"))
            for attempt, accept in attempts:
                try:
                    response = http.get(attempt, headers={"Accept": accept, "Cache-Control": "no-cache"})
                    sha = hashlib.sha256(response.content).hexdigest()
                    entry["attempts"].append({"url": attempt, "accept": accept, "sha256": sha, "bytes": len(response.content), "content_type": response.content_type})
                    if sha == want:
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        dest.write_bytes(response.content)
                        entry["verified"] = True
                        break
                except FetchError as error:
                    entry["attempts"].append({"url": attempt, "error": str(error)})
                    break  # Respect access refusals; no alternate request after one.
        outcomes.append(entry)
        print(row["photo_id"], "verified" if entry["verified"] else "UNAVAILABLE", flush=True)
        log.write_text(json.dumps(outcomes, indent=2) + "\n")
    return all(e["verified"] for e in outcomes) and bool(outcomes)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("catalogue", type=Path)
    p.add_argument("image_root", type=Path)
    p.add_argument("output", type=Path)
    a = p.parse_args()
    a.output.mkdir(parents=True, exist_ok=True)
    rows = selected(list(csv.DictReader(a.catalogue.open())))
    with (a.output / "cases.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader(); w.writerows(rows)
    if not fetch(rows, a.image_root, a.output / "fetch.json"):
        raise SystemExit("Incomplete verified ten-case audit")
