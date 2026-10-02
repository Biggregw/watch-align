import csv
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import source_diversity  # noqa: E402


FIELDS = ["candidate_id", "physical_watch_id", "class_label", "source_name", "images_acquired"]


def config():
    return {
        "discovery": {
            "genuine_source_diversity": {
                "minimum_sources": 4,
                "minimum_watches_per_source": 4,
                "minimum_acquired_watches": 20,
                "max_single_source_share": 0.5,
            }
        }
    }


def write_summary(path: Path, counts: dict[str, int]):
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=FIELDS)
        w.writeheader()
        n = 0
        for source, count in counts.items():
            for _ in range(count):
                n += 1
                w.writerow({
                    "candidate_id": f"c{n}",
                    "physical_watch_id": f"w{n}",
                    "class_label": "gen",
                    "source_name": source,
                    "images_acquired": 3,
                })


class SourceDiversityTest(unittest.TestCase):
    def test_diverse_acquired_population_passes(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "candidate_summary.csv"
            write_summary(path, {"A": 6, "B": 5, "C": 5, "D": 4})
            report = source_diversity.evaluate(config(), path)
        self.assertTrue(report["passed"])
        self.assertEqual(4, report["qualifying_source_count"])
        self.assertEqual(20, report["acquired_genuine_watches"])

    def test_one_large_dealer_cannot_hide_missing_diversity(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "candidate_summary.csv"
            write_summary(path, {"Bob": 18, "B": 2, "C": 2, "D": 2})
            report = source_diversity.evaluate(config(), path)
        self.assertFalse(report["passed"])
        self.assertEqual("NEEDS_MORE_SOURCE_DIVERSITY", report["state"])
        self.assertTrue(any("maximum allowed" in x for x in report["reasons"]))
        self.assertTrue(any("genuine sources" in x for x in report["reasons"]))

    def test_discovered_but_unacquired_watches_do_not_count(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "candidate_summary.csv"
            write_summary(path, {"A": 5, "B": 5, "C": 5, "D": 5})
            rows = list(csv.DictReader(path.open(newline="", encoding="utf-8")))
            rows[-1]["images_acquired"] = "0"
            with path.open("w", newline="", encoding="utf-8") as fh:
                w = csv.DictWriter(fh, fieldnames=FIELDS)
                w.writeheader(); w.writerows(rows)
            report = source_diversity.evaluate(config(), path)
        self.assertFalse(report["passed"])
        self.assertEqual(19, report["acquired_genuine_watches"])


if __name__ == "__main__":
    unittest.main()
