from __future__ import annotations

import hashlib
import json
import math
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps


@dataclass(frozen=True)
class Frame:
    path: Path
    timestamp: float
    exposure: float
    aperture: float
    iso: float
    bias: float
    model: str
    size: tuple[int, int]


def read_frame(path: Path) -> Frame:
    """Auto mode deliberately requires metadata instead of using file mtime."""
    with Image.open(path) as image:
        root = image.getexif()
        exif = {**dict(root), **root.get_ifd(34665)}
        needed = [33434, 33437, 34855, 36867, 37380]
        if any(tag not in exif for tag in needed):
            raise ValueError(f"{path.name}: EXIF for automatic grouping is incomplete")
        timestamp = datetime.strptime(str(exif[36867]), "%Y:%m:%d %H:%M:%S").timestamp()
        sub = str(exif.get(37521, "0")).strip()
        if sub.isdigit():
            timestamp += float("0." + sub)
        exposure, aperture, iso = (float(exif[t]) for t in [33434, 33437, 34855])
        bias = float(exif[37380])
        if not all(math.isfinite(v) for v in [exposure, aperture, iso, bias]) or min(exposure, aperture, iso) <= 0:
            raise ValueError(f"{path.name}: invalid exposure metadata")
        orientation = int(exif.get(274, 1))
        size = image.size[::-1] if orientation in [5, 6, 7, 8] else image.size
        return Frame(path, timestamp, exposure, aperture, iso, bias, str(exif.get(272, "")), size)


def valid_group(frames: list[Frame], count: int, step: float, max_gap: float) -> bool:
    if len(frames) != count or count not in [3, 5, 7] or step <= 0:
        return False
    if any(b.timestamp - a.timestamp > max_gap for a, b in zip(frames, frames[1:])):
        return False
    f = frames[0]
    if any(x.model != f.model or x.size != f.size or abs(x.aperture - f.aperture) > .02
           or abs(x.iso - f.iso) > .5 for x in frames):
        return False
    # A complete regular bias pattern plus matching shutter intervals is required.
    ordered = sorted(frames, key=lambda x: x.bias)
    return all(abs((b.bias - a.bias) - step) < .12
               and abs(math.log2(b.exposure / a.exposure) - step) < .15
               for a, b in zip(ordered, ordered[1:]))


def group_frames(frames: list[Frame], count=3, step=1., max_gap=8.) -> list[list[Frame]]:
    """Conservative, non-overlapping windows. Not a maker-note bracket detector."""
    frames = sorted(frames, key=lambda x: (x.timestamp, x.path.name))
    groups, i = [], 0
    while i + count <= len(frames):
        window = frames[i:i + count]
        if valid_group(window, count, step, max_gap):
            groups.append(window)
            i += count
        else:
            i += 1
    return groups


def group_id(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for p in paths:
        digest.update(p.name.encode())
        with p.open("rb") as source:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(block)
    return digest.hexdigest()[:20]


def load_image(path: Path, max_side: int) -> np.ndarray:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        if max_side:
            image.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
        return cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)


def merge(paths: list[Path], output: Path, mode="hdr", max_side=2400, align=True) -> dict:
    """HDR: Debevec response calibration + radiance; fusion: Mertens, SDR only."""
    if len(paths) not in [3, 5, 7] or len(set(p.resolve() for p in paths)) != len(paths):
        raise ValueError("Select 3, 5 or 7 distinct JPEG frames")
    if mode not in ["hdr", "fusion"]:
        raise ValueError("mode must be hdr or fusion")
    images = [load_image(p, max_side) for p in paths]
    if any(x.shape != images[0].shape for x in images):
        raise ValueError("All frames must have identical oriented dimensions")
    if align:
        # calculateShift avoids AlignMTB's full-image zero-border failure on tiny inputs.
        reference = cv2.cvtColor(images[len(images) // 2], cv2.COLOR_BGR2GRAY)
        aligner = cv2.createAlignMTB(max_bits=5)
        shifts = [aligner.calculateShift(reference, cv2.cvtColor(x, cv2.COLOR_BGR2GRAY)) for x in images]
        images = [aligner.shiftMat(x, shift) for x, shift in zip(images, shifts)]
        h, w = reference.shape
        left, top = max(0, *(s[0] for s in shifts)), max(0, *(s[1] for s in shifts))
        right, bottom = min(w, *(w + s[0] for s in shifts)), min(h, *(h + s[1] for s in shifts))
        if right - left < .8 * w or bottom - top < .8 * h:
            raise ValueError("Alignment exceeds safe overlap; use tripod or --no-align")
        images = [x[top:bottom, left:right].copy() for x in images]
    output.mkdir(parents=True, exist_ok=True)
    key = group_id(paths)
    stem = output / ("HDR_" + key + "_" + mode + f"_{max_side}_{int(align)}")
    complete = stem.with_suffix(".json")
    if complete.exists():
        old = json.loads(complete.read_text())
        if (old.get("mode") == mode and old.get("max_side") == max_side and old.get("align") == align
                and Path(old["jpeg"]).is_file() and (mode != "hdr" or Path(old["radiance_hdr"]).is_file())):
            return old
    if mode == "hdr":
        frames = [read_frame(p) for p in paths]
        f = frames[0]
        if any(abs(x.aperture - f.aperture) > .02 or abs(x.iso - f.iso) > .5 for x in frames):
            raise ValueError("Radiance HDR requires fixed aperture and ISO")
        times = np.array([x.exposure for x in frames], dtype=np.float32)
        if np.ptp(np.log2(times)) < .5:
            raise ValueError("Exposure times do not form a useful bracket")
        cv2.setRNGSeed(7)
        response = cv2.createCalibrateDebevec(samples=70, random=False).process(images, times)
        radiance = cv2.createMergeDebevec().process(images, times, response)
        if not np.isfinite(radiance).all() or np.any(radiance < 0):
            raise ValueError("Non-finite or negative radiance; unsuitable source images")
        # Deterministic global photographic mapping for a convenient SDR preview.
        luminance = np.maximum(.0722 * radiance[:, :, 0] + .7152 * radiance[:, :, 1] + .2126 * radiance[:, :, 2], 1e-6)
        average = float(np.exp(np.mean(np.log(luminance))))
        scaled = radiance * (.18 / average)
        mapped = scaled / (1. + scaled)
        result = np.power(np.clip(mapped, 0, 1), 1. / 2.2)
        hdr_temp = stem.with_suffix(".partial.hdr")
        if not cv2.imwrite(str(hdr_temp), radiance):
            raise IOError("HDR export failed")
        hdr_temp.replace(stem.with_suffix(".hdr"))
    else:
        result = cv2.createMergeMertens(1., 1., 1.).process(images)
    jpeg = np.rint(np.clip(np.nan_to_num(result), 0, 1) * 255).astype(np.uint8)
    temporary = stem.with_suffix(".partial.jpg")
    if not cv2.imwrite(str(temporary), jpeg, [cv2.IMWRITE_JPEG_QUALITY, 95]):
        raise IOError("JPEG export failed")
    temporary.replace(stem.with_suffix(".jpg"))
    manifest = {"mode": mode, "max_side": max_side, "align": align,
                "sources": [str(p) for p in paths], "jpeg": str(stem.with_suffix(".jpg")),
                "radiance_hdr": str(stem.with_suffix(".hdr")) if mode == "hdr" else None,
                "width": jpeg.shape[1], "height": jpeg.shape[0],
                "limitations": "Static scenes only; no deghosting. JPEG tone curves affect radiometry. SDR JPEG has no gain map."}
    temporary = complete.with_suffix(".partial.json")
    temporary.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    temporary.replace(complete)
    return manifest
