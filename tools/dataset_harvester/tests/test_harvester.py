"""Deterministic tests for the dataset harvester: no network, no Java (fake HTTP and a fake analysis
harness stand in for them; the real harness is exercised by the end-to-end run documented in
docs/research/dataset_harvester.md).

    python3 -m unittest discover -s tools/dataset_harvester/tests -v
"""
from __future__ import annotations

import io
import json
import os
import random
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from harvester import adapters as ad  # noqa: E402
from harvester import canonical as cn  # noqa: E402
from harvester import decide as dc  # noqa: E402
from harvester import hashing, metadata, quality  # noqa: E402
from harvester import reddit as rd  # noqa: E402
from harvester.config import Paths  # noqa: E402
from harvester.http import FetchError, Response  # noqa: E402
from harvester.outputs import MANIFEST_COLUMNS, markdown, read_csv  # noqa: E402
from harvester.pipeline import Pipeline  # noqa: E402
from harvester.resolvers import parse_imgur_album  # noqa: E402
from harvester.state import ACCEPT, DEFERRED, DONE, NEW, QUARANTINE, REJECT, SourceRecord, State  # noqa: E402

FIX = HERE / "fixtures"


# ---------------------------------------------------------------------------------------- helpers
def studio_photo(hand_deg: float, date: int) -> Image.Image:
    """Catalogue-style photo: identical set-up every time, only the hands and the date differ
    (the Bob's Watches case that fooled whole-image perceptual hashes)."""
    im = dial_photo(99)
    d = ImageDraw.Draw(im)
    cx, cy = 600 + random.Random(99).randint(-60, 60), 500
    a = np.radians(hand_deg)
    d.line((cx, cy, cx + 260 * np.sin(a), cy - 260 * np.cos(a)), fill=(230, 230, 230), width=14)
    d.line((cx, cy, cx + 170 * np.sin(a + 0.4), cy - 170 * np.cos(a + 0.4)), fill=(230, 230, 230), width=20)
    d.rectangle((cx + 200, cy - 20, cx + 250, cy + 20), fill=(250, 250, 250))
    d.text((cx + 212, cy - 8), str(date), fill=(0, 0, 0))
    return im


def dial_photo(seed: int, size=(1200, 1000), radius=380, blur=0.0) -> Image.Image:
    """A synthetic 'watch photo': light textured background, dark dial with 12 bright markers and a
    seed-dependent pattern, so different seeds are different photographs."""
    rnd = random.Random(seed)
    w, h = size
    bg = np.full((h, w, 3), 200, np.uint8) + np.random.default_rng(seed).integers(0, 30, (h, w, 3), dtype=np.uint8)
    im = Image.fromarray(bg)
    d = ImageDraw.Draw(im)
    cx, cy = w // 2 + rnd.randint(-60, 60), h // 2 + rnd.randint(-40, 40)
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(15, 15, 18))
    for k in range(12):
        a = k * np.pi / 6
        x, y = cx + 0.8 * radius * np.sin(a), cy - 0.8 * radius * np.cos(a)
        s = 18 if k else 30
        d.ellipse((x - s, y - s, x + s, y + s), fill=(235, 235, 225))
    for _ in range(25):   # seed-specific texture so photos differ
        x, y = cx + rnd.uniform(-0.6, 0.6) * radius, cy + rnd.uniform(-0.6, 0.6) * radius
        d.rectangle((x, y, x + rnd.randint(10, 60), y + rnd.randint(4, 30)), fill=(rnd.randint(60, 200),) * 3)
    d.line((cx, cy, cx + rnd.uniform(-1, 1) * radius * 0.6, cy - radius * 0.5), fill=(220, 220, 220), width=10)
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    return im


def jpeg(im: Image.Image, q=92) -> bytes:
    b = io.BytesIO()
    im.save(b, "JPEG", quality=q)
    return b.getvalue()


class FakeHarness:
    """Stands in for the Java harness: finds the dark dial by thresholding (enough for synthetic
    photos), reports GOOD pose unless the file name says 'retake', and 'measures' every image."""

    def __init__(self, retake: set | None = None, few_markers: set | None = None):
        self.suit_calls, self.measure_calls = 0, 0
        self.retake = retake or set()   # sha256 of photos the "app" calls RETAKE
        self.few = few_markers or set() # sha256 of photos with only 4 round markers found

    def suitability(self, paths, work):
        self.suit_calls += 1
        out = {}
        for p in paths:
            im = Image.open(p).convert("L")
            a = np.asarray(im)
            ys, xs = np.nonzero(a < 40)
            rec = {"path": p, "preview_w": im.width, "preview_h": im.height, "orig_w": im.width, "orig_h": im.height}
            if len(xs) < 2000:
                rec.update(dial_found=False, twelve_found=False, pose="UNASSESSABLE", round_found=0, round_total=8)
            else:
                r = float(np.sqrt(len(xs) / np.pi)) * 1.05
                rec.update(dial_found=True, dial_cx=float(xs.mean()), dial_cy=float(ys.mean()), dial_a=r, dial_b=r, dial_angle_deg=0.0,
                           twelve_found="noTwelve" not in p, hand_at_twelve="hand" in p, too_small=False,
                           pose="RETAKE" if hashing.sha256_bytes(Path(p).read_bytes()) in self.retake else "GOOD",
                           round_found=4 if hashing.sha256_bytes(Path(p).read_bytes()) in self.few else 8, round_total=8,
                           ellipse_ratio=0.98, ellipse_tilt_deg=11.0, ellipse_minor_axis_clock_deg=175.0,
                           rehaut_top_px=20.0, rehaut_bottom_px=22.0, rehaut_left_px=21.0, rehaut_right_px=21.0,
                           rehaut_top_cov=0.9, rehaut_bottom_cov=0.8, rehaut_left_cov=0.85, rehaut_right_cov=0.9)
            out[p] = rec
        return out

    def measure(self, rows, out_dir):
        self.measure_calls += 1
        out = {}
        for k, r in enumerate(rows):
            # deterministic, image-dependent values so per-watch summaries can be checked
            v = int(hashing.sha256_bytes(Path(r["path"]).read_bytes())[:2], 16) / 1000.0
            out[r["path"]] = {"path": r["path"], "class": r["class_label"], "twelve": "true", "no_dial": "false",
                              "gap": f"{0.05 + v:.4f}", "rot": f"{v * 10:.3f}", "sp59": "", "sp01": "NaN"}
        return out


class FakeHttp:
    offline = False

    def __init__(self, files: dict[str, bytes]):
        self.files, self.calls = files, []

    def get(self, url, headers=None, api=False, max_bytes=0):
        self.calls.append(url)
        if url not in self.files:
            raise FetchError("gone", url, retryable=False)
        return Response(url, 200, self.files[url], "image/jpeg")


class ListAdapter:
    name = "test"

    def __init__(self, cands):
        self.cands = cands

    def status(self):
        return True, "enabled (test)"

    def discover(self, ctx):
        yield from self.cands


def make_pipeline(tmp: Path, cands, files=None, **kw) -> Pipeline:
    files = files or {}
    return Pipeline(paths=Paths(tmp / "data"), http=FakeHttp(files), harness=kw.pop("harness", None) or FakeHarness(retake_shas(files)),
                    adapters={"test": ListAdapter(cands)}, log=lambda *a: None, **kw)


def retake_shas(files: dict) -> set:
    return {hashing.sha256_bytes(b) for u, b in files.items() if "retake" in u}


def rep_cand(url, images, title="[QC] VSF 126710BLNR", **kw):
    return ad.Candidate(url=url, adapter="test", provider="reddit", source_id=cn.reddit_post_id(url), title=title, image_urls=images, **kw)


# ------------------------------------------------------------------------------------------ tests
class CanonicalTest(unittest.TestCase):
    def test_reddit_variants(self):
        want = "https://www.reddit.com/r/RepTimeQC/comments/1abc23/"
        for u in ("https://old.reddit.com/r/RepTimeQC/comments/1ABC23/some_title/?utm_source=share&share_id=x",
                  "http://reddit.com/r/RepTimeQC/comments/1abc23", "https://www.reddit.com/r/RepTimeQC/comments/1abc23/t/#c"):
            self.assertEqual(want, cn.canonical_url(u))
        self.assertEqual("https://www.reddit.com/comments/1abc23/", cn.canonical_url("https://redd.it/1abc23"))
        self.assertEqual("https://www.reddit.com/comments/1abc23/", cn.canonical_url("https://www.reddit.com/gallery/1abc23"))
        self.assertEqual("1abc23", cn.reddit_post_id("https://old.reddit.com/r/RepTimeQC/comments/1ABC23/x/"))

    def test_imgur_and_params(self):
        self.assertEqual("https://imgur.com/a/AbC12", cn.canonical_url("https://imgur.com/gallery/qc-photos-AbC12?x=1"))
        self.assertEqual("https://imgur.com/a/AbC12", cn.canonical_url("https://m.imgur.com/a/AbC12"))
        self.assertEqual("https://i.imgur.com/XyZ99.jpg", cn.canonical_url("https://imgur.com/XyZ99"))
        self.assertEqual("https://example.com/a?id=3", cn.canonical_url("HTTPS://WWW.Example.com/a/?utm_campaign=x&id=3&fbclid=q"))
        self.assertEqual("https://cdn.x.com/i.jpg?width=928", cn.canonical_url("https://cdn.x.com/i.jpg?width=928&utm_source=a"))
        self.assertTrue(cn.is_direct_image("https://i.redd.it/abc.jpeg"))
        self.assertFalse(cn.is_direct_image("https://i.imgur.com/abc.gifv"))


class ParseTest(unittest.TestCase):
    def test_reddit_listing(self):
        posts, after = rd.parse_listing(json.loads((FIX / "reddit_listing.json").read_text()))
        self.assertEqual("t3_ccc", after)
        self.assertEqual(3, len(posts))
        self.assertEqual(["https://preview.redd.it/m2.jpg?width=3024", "https://preview.redd.it/m1.jpg?width=3024&format=pjpg"], posts[0]["images"])
        self.assertEqual("https://www.reddit.com/r/RepTimeQC/comments/aaa/", posts[0]["permalink"])
        self.assertEqual(["https://i.imgur.com/XyZ9876.jpg"], posts[2]["images"])
        self.assertEqual(["AbCdE12"], posts[2]["imgur_albums"])

    def test_imgur_and_brave(self):
        links = [x["link"] for x in parse_imgur_album(json.loads((FIX / "imgur_album.json").read_text()))]
        self.assertEqual(["https://i.imgur.com/a1.jpg", "https://i.imgur.com/a4.png"], links)
        res = ad.parse_brave(json.loads((FIX / "brave_web.json").read_text()))
        self.assertEqual(2, len(res))
        self.assertEqual("QC photos from my TD", res[0]["snippet"])
        self.assertEqual("reddit", ad.provider_for(res[0]["url"]))
        self.assertEqual("page", ad.provider_for(res[1]["url"]))


class MetadataTest(unittest.TestCase):
    def check(self, title, url, cls, model, factory, conf):
        inf = metadata.infer(title, url)
        self.assertEqual((cls, model, factory, conf), (inf.class_label, inf.model, inf.factory, inf.label_confidence()), f"{title}: {inf.evidence}")

    def test_titles(self):
        qc = "https://www.reddit.com/r/RepTimeQC/comments/abc/x/"
        self.check("[QC] VSF 126710BLNR jubilee", qc, "rep", "126710BLNR", "VSF", "high")
        self.check("QC Clean Pepsi GMT", qc, "rep", "", "Clean", "low")               # Pepsi is 126710BLRO or 126719BLRO
        self.check("QC Clean Pepsi jubilee", qc, "rep", "126710BLRO", "Clean", "medium")
        self.check("QC 126720 sprite GMF", qc, "rep", "126720VTNR", "GMF", "high")
        self.check("QC everose root beer ARF", qc, "rep", "", "ARF", "low")            # Everose alone fits both CHNR refs
        self.check("QC root beer everose gold ARF", qc, "rep", "126715CHNR", "ARF", "medium")
        self.check("GMT QC please", qc, "rep", "", "", "low")                        # model and factory unknown
        self.check("QC VSF batman vs gen comparison", qc, "unsure", "126710BLNR", "VSF", "low")  # mixed
        self.check("QC VSF and Clean BLNR side by side", qc, "rep", "126710BLNR", "", "low")     # two factories
        self.check("126710BLRO pre-owned", "https://www.watchfinder.co.uk/x/1", "gen", "126710BLRO", "Rolex", "high")
        self.check("WTS 126710BLNR", "https://www.reddit.com/r/Watchexchange/comments/q/x/", "gen", "126710BLNR", "Rolex", "medium")
        self.check("126710BLNR and 126710BLRO QC", qc, "rep", "", "", "low")          # conflicting models

    def test_ambiguous_suffixes_and_nicknames(self):
        f = metadata.infer_model
        cases = {
            # (title): expected reference ("" = ambiguous -> low confidence -> quarantine)
            "BLNR": "126710BLNR", "batman": "126710BLNR", "batgirl QC": "126710BLNR",
            "BLRO": "", "pepsi": "", "pepsi jubilee": "126710BLRO", "pepsi steel": "126710BLRO",
            "pepsi meteorite": "126719BLRO", "BLRO white gold": "126719BLRO", "126719": "126719BLRO",
            "GRNR": "", "GRNR steel": "126710GRNR", "GRNR two tone": "126713GRNR", "GRNR steel and yellow gold": "126713GRNR",
            "GRNR yellow gold": "126718GRNR", "bruce wayne": "126710GRNR", "126713": "126713GRNR", "126718": "126718GRNR",
            "guinness": "", "zombie": "", "guinness two-tone": "126713GRNR", "zombie rolesor": "126713GRNR",
            "guinness yellow gold": "126718GRNR", "guinness everose": "",
            "CHNR": "", "root beer": "", "root beer two tone": "126711CHNR", "root beer everose rolesor": "126711CHNR",
            "root beer everose gold": "126715CHNR", "everose root beer": "", "126711": "126711CHNR", "126715": "126715CHNR",
            "VTNR": "", "sprite": "", "destro": "", "lefty": "", "sprite jubilee": "126720VTNR", "VTNR white gold": "126729VTNR",
            "126720 sprite": "126720VTNR", "126729": "126729VTNR",
            "126710": "", "126710 sprite": "", "126710 batman": "126710BLNR", "126710 pepsi": "126710BLRO",
            "126710 bruce wayne": "126710GRNR", "126710GRNR and 126710BLNR": "",
        }
        for title, want in cases.items():
            model, conf, ev, _ = f(title)
            self.assertEqual(want, model, f"{title!r}: {ev}")
            if not want:
                self.assertEqual("low", conf, f"{title!r} must be low confidence")
        # Guinness/Zombie never maps to the Everose root beer references.
        self.assertNotIn(f("guinness")[0], ("126715CHNR", "126711CHNR"))

    def test_ambiguous_reference_is_quarantined(self):
        tmp = Path(tempfile.mkdtemp())
        files = {"https://i.redd.it/g1.jpg": jpeg(dial_photo(61))}
        R = "https://www.reddit.com/r/RepTimeQC/comments/{}/x/"
        p = make_pipeline(tmp, [rep_cand(R.format("g1"), ["https://i.redd.it/g1.jpg"], title="[QC] VSF Guinness")], files)
        p.discover()
        p.process()
        s = next(iter(p.state.sources.values()))
        self.assertEqual(QUARANTINE, s.decision)
        self.assertIn(dc.Q_MODEL, s.decision_reasons)

    def test_unsupported_model_and_vocabulary(self):
        inf = metadata.infer("QC VSF 116710LN", "https://www.reddit.com/r/RepTimeQC/comments/a/x/")
        self.assertEqual("116710", inf.unsupported_model)
        names = [n for _, n in metadata.factories()]
        for f in ("VSF", "Clean", "ARF", "C+"):
            self.assertIn(f, names)


class QualityTest(unittest.TestCase):
    def suit(self, **kw):
        base = dict(dial_found=True, orig_w=1200, orig_h=1000, preview_w=1200, preview_h=1000, dial_cx=600, dial_cy=500,
                    dial_a=380, dial_b=380, dial_angle_deg=0, twelve_found=True, pose="GOOD", round_found=8, round_total=8)
        base.update(kw)
        return base

    def test_reason_codes(self):
        img = dial_photo(1)
        g = quality.dial_geometry(self.suit())
        img = dial_photo(1)
        cx, cy = g["cx"], g["cy"]
        # Place the fake geometry where the synthetic dial really is.
        a = np.asarray(img.convert("L"))
        ys, xs = np.nonzero(a < 40)
        s = dict(dial_cx=float(xs.mean()), dial_cy=float(ys.mean()))
        self.assertTrue(quality.assess(img, self.suit(**s))["suitable"])
        self.assertEqual([quality.REJECT_NO_DIAL], quality.assess(img, self.suit(dial_found=False))["reasons"])
        r = quality.assess(img, self.suit(dial_found=False, dial_located=True, no_readable_dial=True, twelve_found=False, **s))["reasons"]
        self.assertEqual([quality.REJECT_LANDMARKS], r)      # located, landmarks unreadable: not "no dial"
        self.assertIn(quality.REJECT_POSE, quality.assess(img, self.suit(pose="RETAKE", **s))["reasons"])
        self.assertIn(quality.INCONCLUSIVE_POSE, quality.assess(img, self.suit(pose="UNASSESSABLE", **s))["reasons"])
        self.assertIn(quality.REJECT_OCCLUSION, quality.assess(img, self.suit(twelve_found=False, hand_at_twelve=True, **s))["reasons"])
        self.assertIn(quality.REJECT_LANDMARKS, quality.assess(img, self.suit(twelve_found=False, **s))["reasons"])
        few = quality.assess(img, self.suit(round_found=5, **s))
        self.assertFalse(few["suitable"])
        self.assertEqual([quality.INCONCLUSIVE_MARKERS], few["reasons"])
        self.assertTrue(quality.assess(img, self.suit(round_found=6, **s))["suitable"])
        self.assertIn(quality.REJECT_INCOMPLETE, quality.assess(img, self.suit(dial_cx=1100, **{"dial_cy": s["dial_cy"]}))["reasons"])
        small = img.resize((240, 200))
        self.assertIn(quality.REJECT_LOW_RES, quality.assess(small, self.suit(orig_w=240, orig_h=200, preview_w=240, preview_h=200,
                                                                             dial_cx=s["dial_cx"] / 5, dial_cy=s["dial_cy"] / 5, dial_a=76, dial_b=76))["reasons"])
        self.assertIn(quality.REJECT_BLUR, quality.assess(dial_photo(1, blur=6), self.suit(**s))["reasons"])
        glare = img.copy()
        ImageDraw.Draw(glare).ellipse((s["dial_cx"] - 250, s["dial_cy"] - 250, s["dial_cx"] + 250, s["dial_cy"] + 250), fill=(250, 250, 250))
        r = quality.assess(glare, self.suit(**s))["reasons"]
        self.assertTrue(quality.REJECT_GLARE in r or quality.REJECT_OVER in r, r)
        self.assertEqual([quality.INCONCLUSIVE_ANALYSIS], quality.assess(img, {"error": "boom"})["reasons"])
        self.assertEqual([quality.REJECT_UNREADABLE], quality.assess(None, None)["reasons"])

    def test_perspective_diagnostics(self):
        p = quality.perspective(dict(rehaut_top_px=20, rehaut_bottom_px=25, rehaut_left_px=21, rehaut_right_px=21,
                                     rehaut_top_cov=0.9, rehaut_bottom_cov=0.7, rehaut_left_cov=0.8, rehaut_right_cov=0.95,
                                     ellipse_minor_axis_clock_deg=172.0, ellipse_tilt_deg=12.0))
        self.assertAlmostEqual(0.8, p["rehaut_top_bottom_ratio"])
        self.assertAlmostEqual(0.7, p["rehaut_confidence"])
        self.assertEqual("top_bottom", p["perspective_axis"])


class HashTest(unittest.TestCase):
    def test_near_duplicates(self):
        a, b = dial_photo(1), dial_photo(2)
        resized = Image.open(io.BytesIO(jpeg(a.resize((780, 650)), 70))).convert("RGB")
        self.assertLessEqual(hashing.hamming(hashing.dhash(a), hashing.dhash(resized)), 6)
        self.assertLessEqual(hashing.hamming(hashing.phash(a), hashing.phash(resized)), 8)
        self.assertGreater(hashing.hamming(hashing.phash(a), hashing.phash(b)), 8)

    def test_dial_hash_catches_crops(self):
        a = dial_photo(3)
        g = np.asarray(a.convert("L"))
        ys, xs = np.nonzero(g < 40)
        cx, cy, r = xs.mean(), ys.mean(), np.sqrt(len(xs) / np.pi) * 1.05
        crop = a.crop((int(cx - 1.15 * r), int(cy - 1.1 * r), int(cx + 1.12 * r), int(cy + 1.18 * r)))   # inside the frame
        self.assertGreater(hashing.hamming(hashing.phash(a), hashing.phash(crop)), 8)   # whole-image hash misses it
        c2 = np.asarray(crop.convert("L"))
        ys2, xs2 = np.nonzero(c2 < 40)
        d1 = hashing.dial_phash(a, cx, cy, r)
        d2 = hashing.dial_phash(crop, xs2.mean(), ys2.mean(), np.sqrt(len(xs2) / np.pi) * 1.05)
        self.assertLessEqual(hashing.hamming(d1, d2), 6)
        self.assertGreater(hashing.hamming(d1, hashing.dial_phash(dial_photo(4), cx, cy, r)), 6)


class StudioPhotoTest(unittest.TestCase):
    def test_hash_match_is_confirmed_by_pixels(self):
        a, b = studio_photo(40, 26), studio_photo(80, 29)
        # The case that matters: perceptual hashes say "same", the pixels say otherwise.
        self.assertLessEqual(hashing.hamming(hashing.dhash(a), hashing.dhash(b)), 6)
        self.assertLessEqual(hashing.hamming(hashing.phash(a), hashing.phash(b)), 8)
        a_copy = Image.open(io.BytesIO(jpeg(a.resize((800, 667)), 70))).convert("RGB")
        self.assertLess(hashing.differing_fraction(a, a_copy), 0.005)       # same photograph
        self.assertGreater(hashing.differing_fraction(a, b), 0.005)         # different watch, same set-up


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="harvest_test_"))

    def files(self):
        f = {f"https://i.redd.it/w1_{k}.jpg": jpeg(dial_photo(10 + k)) for k in range(3)}           # watch 1: 3 photos
        f["https://i.redd.it/w2_0.jpg"] = jpeg(dial_photo(20))                                       # watch 2
        f["https://i.redd.it/w3_0.jpg"] = f["https://i.redd.it/w1_0.jpg"]                           # exact copy of w1's photo
        f["https://i.redd.it/w4_0.jpg"] = jpeg(dial_photo(10).resize((900, 750)), 75)                # resized copy of w1_0
        f["https://i.redd.it/w5_retake.jpg"] = jpeg(dial_photo(50))
        return f

    def test_studio_photos_are_not_duplicates_but_crops_are(self):
        a, b = studio_photo(40, 26), studio_photo(80, 29)
        g = np.asarray(a.convert("L"))
        ys, xs = np.nonzero(g < 40)
        cx, cy, r = xs.mean(), ys.mean(), np.sqrt(len(xs) / np.pi) * 1.05
        crop = a.crop((int(cx - 1.15 * r), int(cy - 1.1 * r), int(cx + 1.12 * r), int(cy + 1.18 * r)))
        files = {"https://x.example/s1.jpg": jpeg(a), "https://x.example/s2.jpg": jpeg(b), "https://x.example/s3.jpg": jpeg(crop, 85)}
        R = "https://www.reddit.com/r/RepTimeQC/comments/{}/x/"
        cands = [rep_cand(R.format("s1"), ["https://x.example/s1.jpg"], title="[QC] VSF 126710BLNR"),
                 rep_cand(R.format("s2"), ["https://x.example/s2.jpg"], title="[QC] VSF 126710BLNR"),
                 rep_cand(R.format("s3"), ["https://x.example/s3.jpg"], title="[QC] VSF 126710BLNR")]
        p = make_pipeline(self.tmp, cands, files)
        p.discover()
        p.process()
        S = {s.source_id: s for s in p.state.sources.values()}
        self.assertEqual(ACCEPT, S["s1"].decision)
        self.assertEqual(ACCEPT, S["s2"].decision, S["s2"].decision_reasons)   # different watch, same studio
        self.assertEqual((REJECT, [dc.R_DUPLICATE]), (S["s3"].decision, S["s3"].decision_reasons))   # crop of s1
        self.assertEqual(S["s1"].physical_watch_id, S["s3"].duplicate_of_watch)

    def cands(self):
        R = "https://www.reddit.com/r/RepTimeQC/comments/{}/x/"
        return [
            rep_cand(R.format("w1"), [f"https://i.redd.it/w1_{k}.jpg" for k in range(3)]),
            rep_cand(R.format("w2"), ["https://i.redd.it/w2_0.jpg"], title="[QC] Clean 126710BLRO"),
            rep_cand(R.format("w3"), ["https://i.redd.it/w3_0.jpg"], title="[QC] ARF 126710GRNR"),
            rep_cand(R.format("w4"), ["https://i.redd.it/w4_0.jpg"], title="[QC] ARF 126720VTNR"),
            rep_cand(R.format("w5"), ["https://i.redd.it/w5_retake.jpg"], title="[QC] VSF 126710BLRO"),
            rep_cand(R.format("w6"), ["https://i.redd.it/w2_0.jpg"], title="QC photos, which factory is this?"),
            ad.Candidate(url="https://www.reddit.com/r/RepTimeQC/comments/w7/x/", adapter="test", title="[QC] VSF 126710BLNR"),  # needs Reddit API
        ]

    def run_once(self, **kw):
        p = make_pipeline(self.tmp, self.cands(), self.files(), **kw)
        p.discover()
        p.process()
        return p, p.report()

    def test_end_to_end_decisions_grouping_dedup_report(self):
        os.environ.pop("REDDIT_CLIENT_ID", None)
        p, rep = self.run_once()
        S = {s.source_id or s.key: s for s in p.state.sources.values()}
        w1 = S["w1"]
        self.assertEqual(ACCEPT, w1.decision, w1.decision_reasons)
        self.assertEqual(3, len(w1.image_shas))                         # one watch, three views
        self.assertEqual(ACCEPT, S["w2"].decision)
        self.assertEqual((REJECT, [dc.R_DUPLICATE]), (S["w3"].decision, S["w3"].decision_reasons))   # exact copy
        self.assertEqual(w1.physical_watch_id, S["w3"].duplicate_of_watch)
        self.assertEqual(REJECT, S["w4"].decision)                         # resized copy
        self.assertEqual(REJECT, S["w5"].decision)
        self.assertIn("reject_pose", S["w5"].decision_reasons)
        self.assertEqual(REJECT, S["w6"].decision)                         # its only photo is w2's
        w7 = p.state.sources["https://www.reddit.com/r/RepTimeQC/comments/w7/"]
        self.assertEqual(DEFERRED, w7.status)
        self.assertEqual("needs_reddit_api", w7.failure_reason)
        # Report: watches and images counted separately; totals add up.
        ds = rep["dataset"]
        self.assertEqual(2, ds["accepted_watches"])
        self.assertEqual(4, ds["accepted_usable_images"])
        self.assertEqual(rep["watches_accepted"] + rep["watches_quarantined"] + rep["watches_rejected"], 6)
        self.assertEqual(2, rep["exact_duplicates"])            # w3 (copy of w1) and w6 (same photo as w2)
        self.assertGreaterEqual(rep["near_duplicates"], 1)
        self.assertEqual(1, rep["sources_deferred"])
        md = markdown(rep)
        self.assertIn("Replica: 2 independent watches", md)
        self.assertIn("reject_pose", md)
        # Manifest round trip in the corpus list format.
        rows = read_csv(p.paths.manifest)
        self.assertEqual(MANIFEST_COLUMNS[:3], ["local_path", "class_label", "physical_watch_id"])
        self.assertEqual(list(rows[0].keys()), MANIFEST_COLUMNS)
        acc = [r for r in rows if r["dataset_decision"] == ACCEPT]
        self.assertEqual(4, len(acc))
        self.assertEqual(2, len({r["physical_watch_id"] for r in acc}))
        self.assertTrue(all(Path(r["local_path"]).exists() for r in acc))

    def test_quarantine_of_ambiguous_metadata(self):
        R = "https://www.reddit.com/r/RepTimeQC/comments/{}/x/"
        cands = [rep_cand(R.format("q1"), ["https://i.redd.it/w1_0.jpg"], title="QC which factory? GMT"),
                 rep_cand(R.format("q2"), ["https://i.redd.it/w2_0.jpg"], title="QC VSF batman next to my gen")]
        p = make_pipeline(self.tmp, cands, self.files())
        p.discover()
        p.process()
        S = {s.source_id: s for s in p.state.sources.values()}
        self.assertEqual(QUARANTINE, S["q1"].decision)
        self.assertIn(dc.Q_MODEL, S["q1"].decision_reasons)
        self.assertIn(dc.Q_FACTORY, S["q1"].decision_reasons)
        self.assertEqual(QUARANTINE, S["q2"].decision)
        self.assertIn(dc.Q_CLASS, S["q2"].decision_reasons)

    def test_idempotent_and_resumable(self):
        files = self.files()
        http = FakeHttp(files)
        h = FakeHarness(retake_shas(files))
        p = Pipeline(paths=Paths(self.tmp / "data"), http=http, harness=h, adapters={"test": ListAdapter(self.cands())}, log=lambda *a: None, chunk=2)
        p.discover()
        # Simulate an interruption: the harness dies on the second chunk.
        calls = {"n": 0}
        real = h.suitability

        def flaky(paths, work):
            calls["n"] += 1
            if calls["n"] == 2:
                raise KeyboardInterrupt("killed")
            return real(paths, work)
        h.suitability = flaky
        with self.assertRaises(KeyboardInterrupt):
            p.process()
        st = State(self.tmp / "data" / "state")
        self.assertEqual(2, sum(1 for s in st.sources.values() if s.status == DONE))   # first chunk saved
        self.assertTrue(any(s.status == NEW for s in st.sources.values()))
        downloaded_first = len(http.calls)
        # Resume: a new pipeline over the same state finishes the rest.
        http2 = FakeHttp(files)
        p2 = Pipeline(paths=Paths(self.tmp / "data"), http=http2, harness=FakeHarness(retake_shas(files)), adapters={"test": ListAdapter(self.cands())}, log=lambda *a: None)
        p2.discover()
        self.assertEqual(0, p2.c["sources_discovered_new"])
        p2.process()
        r2 = p2.report()
        self.assertEqual(2, r2["dataset"]["accepted_watches"])
        # Images of the interrupted chunk were downloaded once and are not fetched again.
        self.assertLessEqual(len(http2.calls), len(files) - 2)
        self.assertGreater(downloaded_first, 0)
        # A third run does nothing new: no downloads, no decisions change.
        http3 = FakeHttp(files)
        p3 = Pipeline(paths=Paths(self.tmp / "data"), http=http3, harness=FakeHarness(retake_shas(files)), adapters={"test": ListAdapter(self.cands())}, log=lambda *a: None)
        p3.discover()
        p3.process()
        r3 = p3.report()
        self.assertEqual(0, len(http3.calls))
        self.assertEqual(0, r3.get("sources_examined", 0))
        self.assertEqual(r2["dataset"], r3["dataset"])
        # --reprocess re-evaluates from the stored copies without downloading.
        http4 = FakeHttp(files)
        p4 = Pipeline(paths=Paths(self.tmp / "data"), http=http4, harness=FakeHarness(retake_shas(files)), adapters={"test": ListAdapter(self.cands())},
                      log=lambda *a: None, reprocess=True)
        p4.process()
        self.assertEqual(0, len(http4.calls))
        self.assertEqual(r2["dataset"]["accepted_watches"], p4.report()["dataset"]["accepted_watches"])

    def test_state_round_trip(self):
        st = State(self.tmp / "s")
        st.add_source(SourceRecord(key="https://x/1", title="a,b \"c\"", image_urls=["u1"], meta={"k": [1, 2]}))
        self.assertFalse(st.add_source(SourceRecord(key="https://x/1")))
        st.save()
        st2 = State(self.tmp / "s")
        self.assertEqual("a,b \"c\"", st2.sources["https://x/1"].title)
        self.assertEqual([1, 2], st2.sources["https://x/1"].meta["k"])
        self.assertTrue(st2.sources["https://x/1"].discovered_at)

    def test_harness_unavailable_fails_closed(self):
        from harvester.harness import HarnessUnavailable

        class NoJava(FakeHarness):
            def suitability(self, paths, work):
                raise HarnessUnavailable("java not found")
        p = make_pipeline(self.tmp, self.cands()[:2], self.files(), harness=NoJava())
        p.discover()
        p.process()
        self.assertTrue(all(s.decision == "" and s.status == "failed" for s in p.state.sources.values()))
        self.assertTrue(any("harness unavailable" in e for e in p.run["errors"]))


class ReviewFindingsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="harvest_review_"))
        for k in ("REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET"):
            os.environ.pop(k, None)

    def test_lost_image_cache_is_restored_from_redownload(self):
        files = {"https://i.redd.it/a1.jpg": jpeg(dial_photo(71))}
        R = "https://www.reddit.com/r/RepTimeQC/comments/a1/x/"
        p = make_pipeline(self.tmp, [rep_cand(R, ["https://i.redd.it/a1.jpg"])], files)
        p.discover()
        p.process()
        sha = next(iter(p.state.images))
        path = Path(p.state.images[sha].local_path)
        path = path if path.is_absolute() else Path(__file__).resolve().parents[3] / path
        self.assertTrue(path.exists())
        # Simulate a fresh Actions runner: state restored from data/harvest, image cache lost.
        import shutil
        shutil.rmtree(p.paths.images_dir)
        self.assertFalse(path.exists())
        http = FakeHttp(files)
        p2 = Pipeline(paths=Paths(self.tmp / "data"), http=http, harness=FakeHarness(), adapters={}, log=lambda *a: None, reprocess=True)
        p2.process()
        self.assertEqual(["https://i.redd.it/a1.jpg"], http.calls)            # re-downloaded once
        self.assertTrue(path.exists())                                          # restored into the store
        self.assertEqual(files["https://i.redd.it/a1.jpg"], path.read_bytes())
        self.assertEqual(1, p2.c["images_restored"])
        self.assertEqual(ACCEPT, next(iter(p2.state.sources.values())).decision)

    def test_search_then_reddit_api_enrichment(self):
        url = "https://www.reddit.com/r/RepTimeQC/comments/zz9/qc/"
        brave = ad.Candidate(url=url + "?utm_source=share", adapter="search:brave", provider="reddit",
                             title="QC photos - my TD sent these", priority=0.2, meta={"query": "q"})
        p = make_pipeline(self.tmp, [brave], {"https://i.redd.it/zz9.jpg": jpeg(dial_photo(72))})
        p.discover()
        p.process()
        key = cn.canonical_url(url)
        s = p.state.sources[key]
        self.assertEqual(DEFERRED, s.status)                                    # no Reddit API: waits
        discovered_at, retries = s.discovered_at, s.retry_count
        api = ad.Candidate(url=url, adapter="reddit", provider="reddit", source_id="zz9", title="[QC] VSF 126710BLNR jubilee",
                           image_urls=["https://i.redd.it/zz9.jpg"], priority=0.9, meta={"subreddit": "RepTimeQC"})
        p2 = make_pipeline(self.tmp, [api], {"https://i.redd.it/zz9.jpg": jpeg(dial_photo(72))})
        p2.discover()
        s = p2.state.sources[key]
        self.assertEqual(0, p2.c["sources_discovered_new"])
        self.assertEqual(NEW, s.status)                                         # re-queued, not reset
        self.assertEqual(discovered_at, s.discovered_at)
        self.assertEqual(retries, s.retry_count)
        self.assertEqual("[QC] VSF 126710BLNR jubilee", s.title)                # API title beats the snippet
        self.assertEqual(["QC photos - my TD sent these"], s.meta["earlier_titles"])
        self.assertEqual("zz9", s.source_id)
        self.assertEqual(0.9, s.priority)
        self.assertEqual(["search:brave", "reddit"], s.meta["adapters"])
        p2.process()
        s = p2.state.sources[key]
        self.assertEqual((ACCEPT, "126710BLNR", "VSF"), (s.decision, s.model, s.factory), s.decision_reasons)
        # A lower-authority adapter never overwrites curated metadata.
        cur = ad.Candidate(url=url, adapter="repo", class_label="rep", model="126710BLNR", factory="Clean",
                           provenance="rep_labelled", label_confidence="high", physical_watch_id="rep_clean_zz9",
                           label_evidence=["curated"], meta={"curated": True})
        p2.state.add_source(p2._record(cur))
        again = ad.Candidate(url=url, adapter="search:brave", title="some snippet")
        p2.state.add_source(p2._record(again))
        s = p2.state.sources[key]
        self.assertEqual("Clean", s.factory)
        self.assertEqual("[QC] VSF 126710BLNR jubilee", s.title)

    def test_marker_layout_inconclusive_needs_another_image(self):
        a, b = jpeg(dial_photo(81)), jpeg(dial_photo(82))
        c = jpeg(dial_photo(83))
        files = {"https://i.redd.it/m1.jpg": a, "https://i.redd.it/m2.jpg": b, "https://i.redd.it/m3.jpg": c}
        few = {hashing.sha256_bytes(a), hashing.sha256_bytes(c)}
        R = "https://www.reddit.com/r/RepTimeQC/comments/{}/x/"
        p = make_pipeline(self.tmp, [rep_cand(R.format("m1"), ["https://i.redd.it/m1.jpg", "https://i.redd.it/m2.jpg"]),
                                     rep_cand(R.format("m3"), ["https://i.redd.it/m3.jpg"], title="[QC] VSF 126710GRNR steel")],
                          files, harness=FakeHarness(few_markers=few))
        p.discover()
        p.process()
        S = {s.source_id: s for s in p.state.sources.values()}
        self.assertEqual(ACCEPT, S["m1"].decision)                                # second photo passes
        ia = p.state.images[hashing.sha256_bytes(a)]
        self.assertFalse(ia.suitable)
        self.assertIn(quality.INCONCLUSIVE_MARKERS, ia.reasons)
        self.assertEqual(QUARANTINE, S["m3"].decision)                            # its only photo is inconclusive
        self.assertIn(dc.Q_INCONCLUSIVE, S["m3"].decision_reasons)

    def test_reference_imagery_is_not_population(self):
        files = {"https://media.rolex.com/m126710blnr.jpg": jpeg(dial_photo(91)), "https://i.redd.it/p1.jpg": jpeg(dial_photo(92))}
        ref = ad.Candidate(url="https://www.rolex.com/watches/gmt-master-ii/m126710blnr-0002", adapter="repo", source_id="official",
                           image_urls=["https://media.rolex.com/m126710blnr.jpg"], class_label="gen", model="126710BLNR", factory="Rolex",
                           provenance="official", physical_watch_id="official_catalogue", label_confidence="high",
                           label_evidence=["curated"], meta={"curated": True, "split": "reference"})
        p = make_pipeline(self.tmp, [ref, rep_cand("https://www.reddit.com/r/RepTimeQC/comments/p1/x/", ["https://i.redd.it/p1.jpg"])], files)
        p.discover()
        p.process()
        rep = p.report()
        S = {s.source_id: s for s in p.state.sources.values()}
        self.assertEqual((ACCEPT, "reference_only"), (S["official"].decision, S["official"].sample_role))
        self.assertEqual({"rep": 1}, rep["dataset"]["by_class_watches"])        # reference not counted
        self.assertEqual(["official_catalogue"], rep["dataset"]["reference_only_watches"])
        pri = dc.priorities(p.state, ["VSF"])
        self.assertEqual(0, next(n for c, m, f, n, w in pri if (c, m) == ("gen", "126710BLNR")))
        rows = read_csv(p.paths.manifest)
        self.assertEqual({"reference_only", "population"}, {r["sample_role"] for r in rows})
        self.assertIn("Reference-only (not counted as population): official_catalogue", markdown(rep))

    def test_owner_tagged_without_trail_is_quarantined_until_verified(self):
        img = self.tmp / "phone" / "w1.jpg"
        img.parent.mkdir(parents=True)
        img.write_bytes(jpeg(dial_photo(93)))
        rows = [{"local_path": str(img), "class_label": "rep", "physical_watch_id": "w1", "model": "126710BLNR", "factory": "Rich",
                 "source": "", "notes": ""},
                {"local_path": str(img), "class_label": "rep", "physical_watch_id": "w2", "model": "126710BLNR", "factory": "Rich",
                 "source": "https://www.reddit.com/r/RepTimeQC/comments/abc/x/", "notes": ""}]
        c1 = list(ad.phone_candidates(rows[:1], Path("/"), "collected", "phone tags", reviewed=True))[0]
        self.assertEqual("owner_tagged", c1.provenance)
        c2 = list(ad.phone_candidates(rows[1:], Path("/"), "collected", "phone tags", reviewed=True))[0]
        self.assertEqual("owner_tagged_traceable", c2.provenance)
        p = make_pipeline(self.tmp, [c1], {})
        p.discover()
        p.process()
        s = next(iter(p.state.sources.values()))
        self.assertEqual(QUARANTINE, s.decision)
        self.assertIn(dc.Q_UNTRACEABLE, s.decision_reasons)
        self.assertEqual("Rich", s.factory)                                       # Rich stays a valid factory
        # An explicit verified override releases it.
        ov = self.tmp / "overrides.csv"
        ov.write_text("physical_watch_id,verified_by,verified_on,evidence\ncollected_w1,Greg,2026-09-30,seller invoice\n")
        os.environ["HARVEST_PROVENANCE_OVERRIDES"] = str(ov)
        try:
            p2 = Pipeline(paths=Paths(self.tmp / "data"), http=FakeHttp({}), harness=FakeHarness(), adapters={}, log=lambda *a: None, reprocess=True)
            p2.process()
        finally:
            os.environ.pop("HARVEST_PROVENANCE_OVERRIDES")
        s = next(iter(p2.state.sources.values()))
        self.assertEqual(("owner_verified", ACCEPT), (s.provenance, s.decision), s.decision_reasons)

    def test_watch_level_measurement_summary(self):
        files = {f"https://i.redd.it/s{k}.jpg": jpeg(dial_photo(100 + k)) for k in range(3)}
        p = make_pipeline(self.tmp, [rep_cand("https://www.reddit.com/r/RepTimeQC/comments/s1/x/", list(files))], files)
        p.discover()
        p.process()
        p.report()
        rows = read_csv(p.paths.watch_measurements)
        self.assertEqual(1, len(rows))                                            # one row per watch, not per image
        r = rows[0]
        self.assertEqual("3", r["usable_images"])
        self.assertEqual("3", r["gap_n"])
        gaps = sorted(float(im.measurement_row["gap"]) for im in p.state.images.values())
        self.assertAlmostEqual(gaps[1], float(r["gap_median"]), places=3)
        self.assertAlmostEqual(gaps[0], float(r["gap_min"]), places=3)
        self.assertAlmostEqual(gaps[2], float(r["gap_max"]), places=3)
        self.assertAlmostEqual(float(np.median([abs(g - gaps[1]) for g in gaps])), float(r["gap_mad"]), places=3)
        self.assertEqual("0", r["sp59_n"])                                        # empty / NaN values are not counted
        self.assertTrue(r["primary_image"])

    def test_search_relevance_and_image_host_queries(self):
        grp = ("rep", "126720VTNR", "VSF")
        self.assertEqual("needs_reddit_api", ad.relevance("https://www.reddit.com/r/RepTimeQC/comments/a/x/", "[QC] VSF 126720VTNR", grp, False))
        self.assertEqual("", ad.relevance("https://www.reddit.com/r/RepTimeQC/comments/a/x/", "[QC] VSF 126720VTNR", grp, True))
        self.assertEqual("", ad.relevance("https://imgur.com/a/AbC12", "VSF sprite QC album", grp, False))
        self.assertEqual("irrelevant_host", ad.relevance("https://www.youtube.com/watch?v=1", "VSF 126720VTNR review", grp, False))
        self.assertEqual("not_gmt", ad.relevance("https://imgur.com/a/Z1", "VSF submariner QC", grp, False))
        self.assertEqual("page_without_qc_or_factory", ad.relevance("https://someblog.example/gmt-sprite", "GMT sprite thoughts", grp, False))
        self.assertEqual("unsupported_model", ad.relevance("https://imgur.com/a/Q1", "QC VSF 116710LN", grp, False))
        self.assertEqual("genuine_source_without_provenance_rule",
                         ad.relevance("https://randomshop.example/126710BLNR", "126710BLNR for sale", ("gen", "126710BLNR", "Rolex"), False))
        self.assertEqual("", ad.relevance("https://www.bobswatches.com/rolex-gmt-126710blnr.html", "Rolex GMT-Master II 126710BLNR",
                                          ("gen", "126710BLNR", "Rolex"), False))
        pri = [("rep", "126720VTNR", "VSF", 0, 1.0), ("gen", "126710GRNR", "Rolex", 0, 0.9)]
        qs = [q for q, _ in ad.search_queries(pri, 6, reddit_available=False)]
        self.assertTrue(qs[0].endswith("site:imgur.com") and "126720VTNR" in qs[0] and "VSF" in qs[0])
        self.assertIn("site:bobswatches.com", qs[1])
        qs2 = [q for q, _ in ad.search_queries(pri, 6, reddit_available=True)]
        self.assertEqual(3, len(qs2))                                             # plus a plain QC query for the API

    def test_search_adapter_filters_before_storing(self):
        class Fake:
            name = "fake"
            def configured(self): return True
            def search(self, http, q, n):
                return [{"url": "https://www.youtube.com/watch?v=1", "title": "VSF 126720VTNR", "snippet": ""},
                        {"url": "https://www.reddit.com/r/RepTimeQC/comments/q9/x/", "title": "[QC] VSF 126720VTNR", "snippet": ""},
                        {"url": "https://imgur.com/a/Kx9", "title": "VSF 126720VTNR QC", "snippet": ""}]
        sa = ad.SearchAdapter()
        sa.provider = Fake()
        p = Pipeline(paths=Paths(self.tmp / "data"), http=FakeHttp({}), harness=FakeHarness(), adapters={"search": sa}, log=lambda *a: None)
        p.discover()
        self.assertEqual(["https://imgur.com/a/Kx9"], list(p.state.sources))
        self.assertEqual({"irrelevant_host": 1 * len(ad.search_queries(p.state and dc.priorities(p.state, p.factories), 6)),
                          "needs_reddit_api": len(ad.search_queries(dc.priorities(p.state, p.factories), 6))},
                         p.run["search_results_filtered"])


class AlbumStorageTest(unittest.TestCase):
    def test_album_files_in_work_dir_are_copied_into_the_store(self):
        tmp = Path(tempfile.mkdtemp())
        p = make_pipeline(tmp, [])
        p.paths.ensure()
        f = p.paths.work_dir / "imgur_x" / "1.jpg"
        f.parent.mkdir(parents=True)
        f.write_bytes(jpeg(dial_photo(7)))
        rec = SourceRecord(key="https://imgur.com/a/x")
        from harvester.resolvers import ImageRef
        h = p._ingest(rec, ImageRef(path=str(f)), {})
        stored = Path(p.state.images[h].local_path)
        stored = stored if stored.is_absolute() else Path(__file__).resolve().parents[3] / stored
        self.assertTrue(stored.exists())
        self.assertNotIn("work", stored.parts)
        self.assertEqual(1, p.c["images_downloaded"])


class AdapterTest(unittest.TestCase):
    def test_optional_adapters_disabled_without_credentials(self):
        for k in ("BRAVE_SEARCH_API_KEY", "REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET", "HARVEST_PHONE_INBOX"):
            os.environ.pop(k, None)
        a = ad.all_adapters()
        self.assertTrue(a["repo"].status()[0])
        for k in ("search", "reddit", "phone"):
            ok, why = a[k].status()
            self.assertFalse(ok)
            self.assertTrue(why.startswith("disabled"))

    def test_repository_sources(self):
        ctx = ad.DiscoveryContext(http=None, priorities=[])
        cands = list(ad.RepoSourcesAdapter().discover(ctx))
        ids = {c.source_id for c in cands}
        self.assertGreaterEqual(len(cands), 60)
        for sid in ("official_rolex_2026", "rep_vsf_7s6PyXJ", "wf_441391", "gen_126710BLRO_phillips_122669"):
            self.assertIn(sid, ids)
        rep = next(c for c in cands if c.source_id == "rep_vsf_7s6PyXJ")
        self.assertEqual(("rep", "VSF", "high"), (rep.class_label, rep.factory, rep.label_confidence))
        gen = next(c for c in cands if c.source_id == "gen_wex_vmbUDwy")
        self.assertEqual("gen_candidate", gen.provenance)

    def test_search_queries_follow_priorities(self):
        st = State(Path(tempfile.mkdtemp()) / "s")
        pri = dc.priorities(st, ["VSF", "Clean", "ARF"])
        qs = ad.search_queries(pri, 4)
        self.assertEqual(4, len(qs))
        self.assertTrue(all("GMT" in q or "QC" in q for q, _ in qs))


if __name__ == "__main__":
    unittest.main()
