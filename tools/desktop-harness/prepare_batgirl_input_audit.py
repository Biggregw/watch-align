#!/usr/bin/env python3
"""Prepare the documented Batgirl input experiment; no Android/measurement code changes.

Usage: python3 prepare_batgirl_input_audit.py ORIGINAL_JPEG OUTPUT_DIRECTORY
Requires Pillow with ImageCms. Derivatives are repeat observations of ONE replica.
The original's ICC profile is either left as encoded RGB values or converted to sRGB.
PNG/JPEG derivatives strip the profile so desktop decode cannot add a second conversion.
"""
import csv
import hashlib
import io
import json
import sys
from pathlib import Path

import PIL
from PIL import Image, ImageCms


def main():
    original, out = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
    source_hash = hashlib.sha256(original.read_bytes()).hexdigest()
    if source_hash != "c50e0845ddb6c50701a4ba8ab6b396092e67988a747206f77743c02e7c7e54eb":
        raise ValueError("Expected the supplied 37828.jpg; do not substitute a screenshot")
    out.mkdir(parents=True, exist_ok=True)
    image = Image.open(original)
    profile = ImageCms.ImageCmsProfile(io.BytesIO(image.info["icc_profile"]))
    rgb = image.convert("RGB")
    converted = ImageCms.profileToProfile(image, profile, ImageCms.createProfile("sRGB"), outputMode="RGB")
    rows = [("native_opencv", str(original), "opencv"), ("native_imageio", str(original), "imageio")]

    def save(id_, img, quality=None):
        path = out / (id_ + (".png" if quality is None else ".jpg"))
        if quality is None:
            img.save(path, icc_profile=b"")
        else:
            img.save(path, quality=quality, icc_profile=b"")
        rows.append((id_, str(path), "opencv"))

    for prefix, img in (("encoded_p3", rgb), ("converted_srgb", converted)):
        save(prefix + "_1152_native", img)
        for quality in (95, 85):
            save(prefix + "_1152_native_q" + str(quality), img, quality)
        for method in ("box", "bilinear", "bicubic", "lanczos"):
            resized = img.resize((960, 1280), getattr(Image.Resampling, method.upper()))
            save(prefix + "_960_" + method, resized)
            if method == "bilinear":
                for quality in (95, 85):
                    save(prefix + "_960_bilinear_q" + str(quality), resized, quality)
        for width in (936, 984):
            save(prefix + "_" + str(width) + "_bilinear",
                 img.resize((width, round(img.height * width / img.width)), Image.Resampling.BILINEAR))

    with (out / "manifest.csv").open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(("id", "path", "decoder"))
        writer.writerows(rows)
    metadata = dict(source_sha256=source_hash, dimensions=image.size,
                    exif_orientation=image.getexif().get(274), icc_description=ImageCms.getProfileDescription(profile).strip(),
                    pillow=PIL.__version__, littlecms=ImageCms.core.littlecms_version,
                    input_sha256={id_: hashlib.sha256(Path(path).read_bytes()).hexdigest() for id_, path, _ in rows})
    (out / "preparation.json").write_text(json.dumps(metadata, indent=2) + "\n")


if __name__ == "__main__":
    main()
