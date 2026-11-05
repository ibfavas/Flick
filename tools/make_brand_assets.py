#!/usr/bin/env python3
"""Generate Flick v1.0.6 brand assets from the approved brand images.

Reads the approved logo and wordmark from concepts/ (not part of the app
source) and writes Android resources into app/src/main/res:
- mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png and
  ic_launcher_round.png (logo 3: white swoosh + cyan dot on deep teal).
- drawable-xxxhdpi/ic_launcher_foreground.png (logo, for the adaptive icon).
- drawable-xxxhdpi/flick_wordmark.png (wordmark with its teal background
  removed, so it blends onto the app's deep teal theme).

The generated logo is a squircle on a WHITE background: its corners must
become transparent, otherwise the launcher icon shows white corners. Only
border-connected near-white pixels are made transparent (flood fill), so
the white swoosh inside the icon, which is enclosed by teal, is untouched.
The wordmark's background is a dark teal gradient; it is removed with a
luminosity-to-alpha pass (the gradient never exceeds #004A51, well below
the threshold, while the white letters and cyan dot stay fully opaque).
"""
import os
import sys
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONCEPTS = os.path.join(PROJECT, "concepts")
RES = os.path.join(PROJECT, "app", "src", "main", "res")

LOGO = os.path.join(
    CONCEPTS,
    "media-generation-flick-logo-3-swoosh-0-2eb8abda-18a1-4bf6-acd9-909d6e881fa8.webp",
)
WORDMARK = os.path.join(
    CONCEPTS,
    "media-generation-flick-font-2-normal-i-cyan-dot-0-28207d6a-57be-4232-b776-b1b6b05b90f3.webp",
)


def transparent_corners(rgb: Image.Image) -> Image.Image:
    """Make border-connected near-white pixels transparent (flood fill)."""
    arr = np.asarray(rgb)
    white = (arr.min(axis=2) > 235).astype(np.uint8) * 255
    mask = Image.fromarray(white, mode="L")
    draw = ImageDraw.Draw(mask)
    for seed in [(0, 0), (mask.width - 1, 0), (0, mask.height - 1),
                 (mask.width - 1, mask.height - 1)]:
        ImageDraw.floodfill(mask, seed, 128, thresh=5)
    alpha = np.asarray(mask)
    alpha = np.where(alpha == 128, 0, 255).astype(np.uint8)
    # Feather the edge by one pixel so the squircle rim is smooth.
    alpha_img = Image.fromarray(alpha, mode="L").filter(ImageFilter.GaussianBlur(1))
    out = rgb.convert("RGBA")
    out.putalpha(alpha_img)
    return out


def luminosity_to_alpha(rgb: Image.Image, threshold: int = 95) -> Image.Image:
    """Turn a dark background transparent; bright letters stay opaque."""
    arr = np.asarray(rgb).astype(np.float32)
    brightest = arr.max(axis=2)
    alpha = np.clip((brightest - threshold) / 160.0, 0.0, 1.0)
    safe = np.maximum(alpha, 0.01)
    rgb_out = np.clip(arr / safe[..., None], 0, 255).astype(np.uint8)
    out = np.dstack([rgb_out, (alpha * 255).astype(np.uint8)])
    return Image.fromarray(out, mode="RGBA")


def transparent_logo(rgb: Image.Image) -> Image.Image:
    """Logo 3 with no background at all: only the white swoosh and the
    cyan dot remain, for use on the glass pad (v1.0.8).
    1. luminosity_to_alpha(threshold=95): the teal gradient never exceeds
       a max channel of ~95, so it becomes fully transparent; the white
       swoosh and the cyan dot stay opaque.
    2. Flood-fill border-connected opaque near-white pixels to
       transparent: the squircle sits on a white background, so its
       corners are still white after step 1. The swoosh is an island of
       white enclosed by (now transparent) teal, so the flood fill
       cannot reach it.
    3. Crop to the remaining content with a small margin.
    """
    rgba = luminosity_to_alpha(rgb, threshold=95)
    arr = np.asarray(rgba)
    white = ((arr[..., 3] > 200) & (arr[..., :3].min(axis=2) > 235)).astype(np.uint8) * 255
    mask = Image.fromarray(white, mode="L")
    # Creating the Draw object makes the fromarray image writable:
    # without it the image is readonly and floodfill silently no-ops.
    ImageDraw.Draw(mask)
    for seed in [(0, 0), (mask.width - 1, 0), (0, mask.height - 1),
                 (mask.width - 1, mask.height - 1)]:
        ImageDraw.floodfill(mask, seed, 128, thresh=5)
    flood = np.asarray(mask)
    new_alpha = np.where(flood == 128, 0, arr[..., 3]).astype(np.uint8)
    out = rgba.copy()
    out.putalpha(Image.fromarray(new_alpha, mode="L"))
    bbox = out.getbbox()
    if bbox is not None:
        pad = 8
        box = (max(0, bbox[0] - pad), max(0, bbox[1] - pad),
               min(out.width, bbox[2] + pad), min(out.height, bbox[3] + pad))
        out = out.crop(box)
    return out


def main() -> None:
    logo = Image.open(LOGO).convert("RGB")
    print("logo:", logo.size)
    logo_rgba = transparent_corners(logo)

    # Sample a deep teal for the adaptive-icon background: average of the
    # squircle edge midpoints (inside the icon, away from the white swoosh).
    w, h = logo.size
    samples = [logo.getpixel(p) for p in
               [(w // 2, 60), (60, h // 2), (w - 61, h // 2), (w // 2, h - 61)]]
    avg = tuple(sum(c[i] for c in samples) // len(samples) for i in range(3))
    print("adaptive background teal: #%02X%02X%02X" % avg)

    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in sizes.items():
        d = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        small = logo_rgba.resize((px, px), Image.LANCZOS)
        small.save(os.path.join(d, "ic_launcher.png"), optimize=True)
        small.save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
        print(f"mipmap-{density}: ic_launcher.png {px}px")

    fg_dir = os.path.join(RES, "drawable-xxxhdpi")
    os.makedirs(fg_dir, exist_ok=True)
    fg = logo_rgba.resize((512, 512), Image.LANCZOS)
    fg.save(os.path.join(fg_dir, "ic_launcher_foreground.png"), optimize=True)
    print("drawable-xxxhdpi: ic_launcher_foreground.png 512px")

    # Transparent logo for the glass pad (v1.0.8): swoosh + cyan dot only.
    logo_t = transparent_logo(logo)
    if logo_t.width > 512:
        logo_t = logo_t.resize(
            (512, int(512 * logo_t.height / logo_t.width)), Image.LANCZOS)
    logo_t.save(os.path.join(fg_dir, "flick_logo_transparent.png"), optimize=True)
    print("drawable-xxxhdpi: flick_logo_transparent.png", logo_t.size)

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
    wm_rgba.save(os.path.join(fg_dir, "flick_wordmark.png"), optimize=True)
    print("drawable-xxxhdpi: flick_wordmark.png", wm_rgba.size)


if __name__ == "__main__":
    sys.exit(main())
