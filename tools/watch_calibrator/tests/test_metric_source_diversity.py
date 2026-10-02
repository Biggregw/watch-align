import csv
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
from calibrate import propose, finalize  # noqa: E402


def write_csv(path: Path, rows: list[dict]):
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = list(rows[0]) if rows else ["metric"]
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def config():
    return {
        "model": "124060",
        "family": "submariner_12",
        "discovery": {
            "genuine_source_diversity": {
                "minimum_sources": 4,
                "minimum_watches_per_source": 4,
                "minimum_acquired_watches": 20,
                "max_single_source_share": 0.5,
            }
        },
        "calibration_policy": {
            "min_development_watches": 6,
            "min_validation_watches": 2,
            "min_holdout_watches": 2,
            "clear_sigma": 3.0,
            "check_sigma": 5.0,
            "check_over_clear": 1.5,
            "validation_clear_rate_min": 0.75,
            "validation_check_rate_min": 1.0,
            "holdout_clear_rate_min": 0.75,
            "holdout_check_rate_min": 1.0,
            "outlier_mad_k": 3.5,
        },
        "calibration_metrics": [{
            "metric": "m",
            "app_key": "m",
            "sided": "two",
            "minimum_half_width": 0.01,
            "allow_pose_sensitive": False,
            "max_clear_half_width": None,
            "defect_evidence": None,
        }],
    }


def repeatability(path: Path):
    write_csv(path, [{
        "metric": "m",
        "repeatability_class": "measured",
        "pose_sensitive": "",
        "within_watch_mad_median": "0.001",
    }])


def watch_rows(spec: list[tuple[str, str, float]]):
    return [
        {"physical_watch_id": wid, "source_name": source, "metric": "m", "median": value}
        for wid, source, value in spec
    ]


class MetricSourceDiversityTest(unittest.TestCase):
    def test_many_watches_from_one_dealer_cannot_calibrate_metric(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            write_csv(p / "dev.csv", watch_rows([
                (f"b{i}", "Bob's Watches", 0.0) for i in range(8)
            ]))
            write_csv(p / "val.csv", watch_rows([
                ("v1", "SwissWatchExpo", 0.0),
                ("v2", "European Watch Company", 0.0),
            ]))
            repeatability(p / "repeat.csv")
            r = propose(config(), p / "dev.csv", p / "repeat.csv", p / "val.csv")
        metric = r["metrics"]["m"]
        self.assertEqual("INSUFFICIENT", metric["status"])
        self.assertIn("source diversity", metric["reason"])
        self.assertEqual(1, metric["development_sources"])

    def test_three_development_sources_and_two_validation_sources_can_freeze(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = []
            for source, prefix in (("Bob's Watches", "b"), ("SwissWatchExpo", "s"), ("European Watch Company", "e")):
                dev += [(f"{prefix}1", source, 0.0), (f"{prefix}2", source, 0.001)]
            write_csv(p / "dev.csv", watch_rows(dev))
            write_csv(p / "val.csv", watch_rows([
                ("v1", "DavidSW", 0.0),
                ("v2", "Nashville Watch", 0.001),
            ]))
            repeatability(p / "repeat.csv")
            r = propose(config(), p / "dev.csv", p / "repeat.csv", p / "val.csv")
        metric = r["metrics"]["m"]
        self.assertEqual("FROZEN_PENDING_HOLDOUT", metric["status"])
        self.assertEqual(3, metric["development_sources"])
        self.assertEqual(2, metric["validation_sources"])

    def test_holdout_requires_two_sources_for_the_metric(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = []
            for source, prefix in (("Bob's Watches", "b"), ("SwissWatchExpo", "s"), ("European Watch Company", "e")):
                dev += [(f"{prefix}1", source, 0.0), (f"{prefix}2", source, 0.001)]
            write_csv(p / "dev.csv", watch_rows(dev))
            write_csv(p / "val.csv", watch_rows([
                ("v1", "DavidSW", 0.0),
                ("v2", "Nashville Watch", 0.001),
            ]))
            repeatability(p / "repeat.csv")
            frozen = propose(config(), p / "dev.csv", p / "repeat.csv", p / "val.csv")
            write_csv(p / "hold.csv", watch_rows([
                ("h1", "DavidSW", 0.0),
                ("h2", "DavidSW", 0.001),
            ]))
            final = finalize(frozen, p / "hold.csv")
        metric = final["metrics"]["m"]
        self.assertEqual("INSUFFICIENT_HOLDOUT_SOURCE_DIVERSITY", metric["status"])
        self.assertIn("source diversity", metric["reason"])


if __name__ == "__main__":
    unittest.main()
