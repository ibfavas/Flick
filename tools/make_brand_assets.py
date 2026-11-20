#!/usr/bin/env python3
"""Generate the Android launcher icons and approved wordmark layers."""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageChops

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONCEPTS = os.path.join(PROJECT, "concepts")
RES = os.path.join(PROJECT, "app", "src", "main", "res")

LOGO = os.path.join(CONCEPTS, "flick-launcher.png")
WORDMARK = os.path.join(
    CONCEPTS,
    "media-generation-flick-font-2-normal-i-cyan-dot-0-28207d6a-57be-4232-b776-b1b6b05b90f3.webp",
)


def luminosity_to_alpha(rgb: Image.Image, threshold: int = 95) -> Image.Image:
    """Turn a dark background transparent; bright letters stay opaque."""
    arr = np.asarray(rgb).astype(np.float32)
    brightest = arr.max(axis=2)
    alpha = np.clip((brightest - threshold) / 160.0, 0.0, 1.0)
    safe = np.maximum(alpha, 0.01)
    rgb_out = np.clip(arr / safe[..., None], 0, 255).astype(np.uint8)
    out = np.dstack([rgb_out, (alpha * 255).astype(np.uint8)])
    return Image.fromarray(out, mode="RGBA")


def main() -> None:
    logo = Image.open(LOGO).convert("RGBA")
    # Discard the source PNG's near-invisible matte outside the rounded tile.
    alpha = logo.getchannel("A").point(lambda a: 0 if a < 16 else a)
    logo.putalpha(alpha)
    logo = logo.crop(alpha.getbbox())
    # Preserve the artwork's aspect ratio on a square transparent icon canvas.
    edge = max(logo.size)
    square = Image.new("RGBA", (edge, edge))
    square.paste(logo, ((edge - logo.width) // 2, (edge - logo.height) // 2))
    logo = square
    fg_dir = os.path.join(RES, "drawable-xxxhdpi")
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in sizes.items():
        small = logo.resize((px, px), Image.Resampling.LANCZOS)
        for name in ("ic_launcher", "ic_launcher_round"):
            small.save(os.path.join(RES, f"mipmap-{density}", f"{name}.png"), optimize=True)
    # Matching the tile edge to the adaptive background lets the launcher mask
    # define one silhouette. Legacy launchers use the transparent PNG directly.
    foreground = Image.new("RGBA", (432, 432))
    foreground.paste(logo.resize((304, 304), Image.Resampling.LANCZOS), (64, 64))
    foreground.save(os.path.join(fg_dir, "ic_launcher_foreground.png"), optimize=True)

    wm = Image.open(WORDMARK).convert("RGB")
    print("wordmark:", wm.size)
    arr = np.asarray(wm).astype(np.int16)
    bright = (arr.min(axis=2) > 180) | (
        (arr[..., 1] > 200) & (arr[..., 2] > 200) & (arr[..., 0] < 100))
    ys, xs = np.nonzero(bright)
    pad_x = int(wm.width * 0.03)
    pad_y = int(wm.height * 0.06)
    box = (max(0, xs.min() - pad_x), max(0, ys.min() - pad_y),
           min(wm.width, xs.max() + pad_x), min(wm.height, ys.max() + pad_y))
    print("wordmark crop box:", box)
    cropped = wm.crop(box)
    wm_rgba = luminosity_to_alpha(cropped)
    if wm_rgba.width > 1000:
        wm_rgba = wm_rgba.resize(
            (1000, int(1000 * wm_rgba.height / wm_rgba.width)), Image.LANCZOS)
    # Separate the tittle from the lettering of the approved font-2 artwork.
    # Coordinates refer to the 1000 px source above; both layers keep the same origin.
    alpha = wm_rgba.getchannel("A")
    dot = Image.new("L", alpha.size)
    ImageDraw.Draw(dot).rectangle((359, 34, 455, 131), fill=255)
    for name, mask in (("letters", ImageChops.subtract(alpha, dot)),
                       ("dot", ImageChops.multiply(alpha, dot))):
        layer = Image.new("RGBA", alpha.size, "white")
        layer.putalpha(mask)
        layer = layer.crop((38, 35, 963, 412)).resize((370, 151), Image.Resampling.LANCZOS)
        layer.save(os.path.join(fg_dir, f"wordmark_{name}.png"))


if __name__ == "__main__":
    sys.exit(main())
