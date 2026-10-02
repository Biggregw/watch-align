import csv
import tempfile
import unittest
from collections import Counter
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import split  # noqa: E402


class SplitSourceTest(unittest.TestCase):
    def test_genuine_watches_are_stratified_by_source(self):
        watches = []
        for source in ("Dealer A", "Dealer B"):
            for i in range(4):
                watches.append({
                    "physical_watch_id": f"{source}-{i}",
                    "class_label": "gen",
                    "model": "124060",
                    "factory": "",
                    "source_name": source,
                })
        rows = split.make_split(watches)
        strata = {r["stratum"] for r in rows}
        self.assertIn("gen/124060/Dealer A", strata)
        self.assertIn("gen/124060/Dealer B", strata)
        for source in ("Dealer A", "Dealer B"):
            parts = Counter(r["partition"] for r in rows if r["source_name"] == source)
            self.assertGreater(parts["development"], 0)
            self.assertGreater(parts["validation"], 0)
            self.assertGreater(parts["holdout"], 0)

    def test_acquisition_preserves_source_name(self):
        fields = [
            "candidate_id", "physical_watch_id", "class_label", "model", "factory", "source_name",
            "acquisition_status", "exact_duplicate_of"
        ]
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "acquired_images.csv"
            with path.open("w", newline="", encoding="utf-8") as fh:
                w = csv.DictWriter(fh, fieldnames=fields)
                w.writeheader()
                w.writerow({
                    "candidate_id": "c1", "physical_watch_id": "w1", "class_label": "gen",
                    "model": "124060", "factory": "", "source_name": "Dealer A",
                    "acquisition_status": "acquired", "exact_duplicate_of": "",
                })
            watches = split.watches_from_acquisition(path)
        self.assertEqual("Dealer A", watches[0]["source_name"])


if __name__ == "__main__":
    unittest.main()
