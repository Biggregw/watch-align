import json
from pathlib import Path
import tempfile
import unittest

from fetch_cases import CASES, selected
from prepare_trace import generate
from render import check_coverage
from compare import compare, index, coverage


class AuditIntegrityTest(unittest.TestCase):
    def test_equal_partial_runs_and_missing_marker_hours_fail(self):
        manifest = [{"photo_id": "a"}, {"photo_id": "b"}]
        rows = {("a", str(h)): {"model": "gmt_126710", "hour": str(h)}
                for h in range(1, 13) if h != 3}
        self.assertEqual({"a": "TRACK", "b": "NOPOSE"},
                         coverage(rows, manifest, "TRACK a\nNOPOSE b\n"))
        with self.assertRaises(ValueError):
            coverage(rows, manifest, "TRACK a\n")
        del rows[("a", "6")]
        with self.assertRaises(ValueError):
            coverage(rows, manifest, "TRACK a\nNOPOSE b\n")

    def test_empty_conflicting_or_missing_comparison_rows_fail(self):
        with self.assertRaises(ValueError):
            index([])
        row = {"photo_id": "a", "hour": "6", "watch": "first", "state": "POSSIBLE"}
        with self.assertRaises(ValueError):
            index([row, {**row, "state": "NO_VISIBLE_ISSUE"}])
        unique, duplicates = index([row, {**row, "watch": "second"}])
        self.assertEqual(1, len(unique))
        self.assertEqual(1, len(duplicates))
        with self.assertRaises(ValueError):
            compare(unique, {})

    def test_missing_or_duplicate_catalogue_cases_fail(self):
        rows = [{"photo_id": p} for p in CASES]
        self.assertEqual(10, len(selected(rows)))
        for invalid in ([], rows[:-1], rows[:-1] + [rows[0]], rows + [rows[0]]):
            with self.assertRaises(ValueError):
                selected(invalid)

    def test_green_summary_cannot_hide_missing_photo_or_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            photos = [{"photo_id": p, "status": "verified_traced"} for p in CASES]
            for p, h in CASES.items():
                (root / (p + ".json")).write_text(json.dumps({"photo_id": p, "model": "submariner_126610", "markers": [{"hour": h}]}))
            coverage = {"expected": 10, "traced": 10, "photos": photos}
            path = root / "coverage.json"
            path.write_text(json.dumps(coverage))
            check_coverage(root)
            photos[-1]["status"] = "missing"
            path.write_text(json.dumps(coverage))
            with self.assertRaises(ValueError):
                check_coverage(root)
            photos[-1]["status"] = "verified_traced"
            path.write_text(json.dumps(coverage))
            p = next(iter(CASES))
            (root / (p + ".json")).write_text(json.dumps({"photo_id": p, "model": "submariner_126610", "markers": []}))
            with self.assertRaises(ValueError):
                check_coverage(root)

    def test_changed_instrumentation_anchor_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            src, dst = Path(tmp) / "source.java", Path(tmp) / "trace.java"
            src.write_text("final class MetalFit {}")
            with self.assertRaises(ValueError):
                generate(src, dst)
            self.assertFalse(dst.exists())


if __name__ == "__main__":
    unittest.main()
