"""The sha-keyed GMT regression fixture is well formed and its reader handles sharded Batch output."""
import csv
import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("gmt_golden", ROOT / "tools" / "desktop-harness" / "gmt_golden.py")
G = importlib.util.module_from_spec(spec)
spec.loader.exec_module(G)


class GmtGoldenFixtureTest(unittest.TestCase):
    def test_fixture_is_sha_keyed_and_complete(self):
        with (G.FIXTURE / "manifest.csv").open() as f:
            man = list(csv.DictReader(f))
        shas = [r["sha256"] for r in man]
        self.assertEqual(337, len(man))
        self.assertEqual(len(shas), len(set(shas)))
        self.assertTrue(all(len(s) == 64 for s in shas))
        with (G.FIXTURE / "expected_batch.csv").open() as f:
            exp = list(csv.DictReader(f))
        self.assertEqual(set(shas), {r["sha256"] for r in exp})
        self.assertNotIn("path", exp[0])
        self.assertNotIn("overlay", exp[0])
        with (G.FIXTURE / "expected_round.csv").open() as f:
            rnd = list(csv.DictReader(f))
        self.assertTrue({r["sha256"] for r in rnd} <= set(shas))

    def test_read_run_uses_the_header_of_whichever_shard_has_it(self):
        with tempfile.TemporaryDirectory() as d:
            (Path(d) / "r0.csv").write_text("path,gap\n/a.jpg,0.1\n")
            (Path(d) / "r1.csv").write_text("/b.jpg,0.2\n")
            rows = G.read_run(Path(d), False)
            self.assertEqual([{"path": "/a.jpg", "gap": "0.1"}, {"path": "/b.jpg", "gap": "0.2"}], rows)

    def test_check_reports_missing_images_instead_of_passing(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(2, G.check([Path(d)], 1, None))


if __name__ == "__main__":
    unittest.main()
