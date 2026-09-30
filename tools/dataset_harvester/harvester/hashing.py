"""Exact and perceptual image hashes.

sha256 of the downloaded bytes identifies exact duplicates. Two 64-bit perceptual hashes of the
whole image (dHash, the same definition as datasets/126710BLNR/fetch_images.py, and a DCT pHash)
catch re-encoded or resized copies. A pHash of the dial region alone catches crops of the same
photograph, which whole-image hashes miss.
"""
from __future__ import annotations

import hashlib

import numpy as np
from PIL import Image, ImageFilter


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def dhash(image: Image.Image) -> str:
    """64-bit difference hash; identical to fetch_images.dhash so existing hashes compare."""
    tiny = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    px = list(tiny.getdata())
    value = 0
    bit = 0
    for y in range(8):
        for x in range(8):
            if px[y * 9 + x] > px[y * 9 + x + 1]:
                value |= 1 << bit
            bit += 1
    return f"{value:016x}"


def _dct_matrix(n: int) -> np.ndarray:
    k = np.arange(n)[:, None]
    i = np.arange(n)[None, :]
    m = np.cos(np.pi * (2 * i + 1) * k / (2 * n)) * np.sqrt(2.0 / n)
    m[0, :] = np.sqrt(1.0 / n)
    return m


_D32 = _dct_matrix(32)


def phash(image: Image.Image) -> str:
    """64-bit DCT perceptual hash (32x32 grey, low 8x8 frequencies vs their median, DC excluded)."""
    g = np.asarray(image.convert("L").resize((32, 32), Image.Resampling.LANCZOS), dtype=np.float64)
    d = _D32 @ g @ _D32.T
    low = d[:8, :8].flatten()
    med = np.median(low[1:])
    value = 0
    for bit, v in enumerate(low):
        if v > med:
            value |= 1 << bit
    return f"{value:016x}"


def dial_phash(image: Image.Image, cx: float, cy: float, radius: float) -> str:
    """pHash of the square around the dial (coordinates in this image's pixels)."""
    box = tuple(int(round(v)) for v in dial_box(cx, cy, radius))
    return phash(image.crop(box))


def hamming(a: str, b: str) -> int:
    if not a or not b:
        return 64
    return (int(a, 16) ^ int(b, 16)).bit_count()


def _region(img: Image.Image, box=None, n: int = 256) -> np.ndarray:
    if box is not None:
        img = img.crop(tuple(int(round(v)) for v in box))
    g = img.convert("L").resize((n, n), Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(1.2))
    return np.asarray(g, dtype=np.int16)


def differing_fraction(a: Image.Image, b: Image.Image, box_a=None, box_b=None, shift: int = 4) -> float:
    """Fraction of pixels that differ by more than 40 grey levels between two regions resampled to
    256x256 (smallest over +-shift px of misalignment). A resized or re-encoded copy of one photo
    scores ~0; two studio photos of different watches in the same set-up (hands, date, bezel
    position differ) score several percent. Used to confirm hash matches before calling anything a
    duplicate, because perceptual hashes alone confuse catalogue photos of different watches."""
    x, y = _region(a, box_a), _region(b, box_b)
    n, m, best = x.shape[0], shift, 1.0
    for dx in range(-m, m + 1, 2):
        for dy in range(-m, m + 1, 2):
            A = x[m:n - m, m:n - m]
            B = y[m + dy:n - m + dy, m + dx:n - m + dx]
            best = min(best, float((np.abs(A - B) > 40).mean()))
    return best


def dial_box(cx: float, cy: float, radius: float) -> tuple:
    r = radius * 1.02
    return (cx - r, cy - r, cx + r, cy + r)
