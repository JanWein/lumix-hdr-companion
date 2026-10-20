"""Deterministic synthetic HDR test scene, not an AI-generated photograph."""
from pathlib import Path
from PIL import Image, TiffImagePlugin
import numpy as np
import struct


def signed_exposure_bias(exif_bytes: bytes) -> bytes:
    """EXIF ExposureBiasValue is SRATIONAL, including zero and positive values.

    Pillow can infer unsigned RATIONAL for nonnegative IFDRational values.
    AndroidX rejects that type for this tag. Keep the existing numeric payload
    and explicitly encode the required signed type in our synthetic fixtures.
    """
    data = bytearray(exif_bytes)
    if data[:6] != b"Exif\0\0":
        raise ValueError("Expected an EXIF segment")
    base = 6
    order = "<" if data[base:base + 2] == b"II" else ">"

    def entries(offset):
        start = base + offset
        count = struct.unpack_from(order + "H", data, start)[0]
        return range(start + 2, start + 2 + count * 12, 12)

    primary = struct.unpack_from(order + "I", data, base + 4)[0]
    exif_offset = next(struct.unpack_from(order + "I", data, p + 8)[0]
                       for p in entries(primary)
                       if struct.unpack_from(order + "H", data, p)[0] == 34665)
    bias_entry = next(p for p in entries(exif_offset)
                      if struct.unpack_from(order + "H", data, p)[0] == 37380)
    old_type = struct.unpack_from(order + "H", data, bias_entry + 2)[0]
    if old_type not in (5, 10):
        raise ValueError("Unexpected ExposureBiasValue representation")
    struct.pack_into(order + "H", data, bias_entry + 2, 10)
    return bytes(data)


def make_demo(folder: Path):
    folder.mkdir(parents=True, exist_ok=True)
    y, x = np.mgrid[0:384, 0:576]
    level = np.exp((x / 575 * 8 - 5) * np.log(2))
    texture = .7 + .3 * np.sin(x * .19) * np.sin(y * .11)
    scene = np.stack([level * texture * (1.1 + y / 768), level * texture,
                      level * texture * (1.4 - y / 768)], axis=-1)
    scene[70:170, 380:500] *= 4
    for i, (bias, seconds) in enumerate([(0, .125), (-1, .0625), (1, .25)]):
        linear = np.clip(scene * (seconds / .125), 0, 1)
        srgb = np.where(linear <= .0031308, linear * 12.92, 1.055 * linear ** (1 / 2.4) - .055)
        exif = Image.Exif()
        exif[271] = "Synthetic test"
        exif[272] = "Bracket fixture (not a real S9)"
        exif[34665] = {33434: TiffImagePlugin.IFDRational(seconds),
                       33437: TiffImagePlugin.IFDRational(8), 34855: 100,
                       36867: "2026:10:20 05:00:00", 37521: str(i * 2),
                       37380: TiffImagePlugin.IFDRational(bias)}
        Image.fromarray(np.uint8(np.rint(np.clip(srgb, 0, 1) * 255))).save(
            folder / f"BRACKET_{i}.jpg", quality=97,
            exif=signed_exposure_bias(exif.tobytes()))
    np.save(folder / "ground_truth.npy", scene)


if __name__ == "__main__":
    import sys
    make_demo(Path(sys.argv[1] if len(sys.argv)>1 else "demo"))
