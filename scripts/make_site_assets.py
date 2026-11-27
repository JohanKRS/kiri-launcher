#!/usr/bin/env python3
"""Builds the pictures, logo and font of the Kiri website (site/) from the app's own files.

    python3 scripts/make_site_assets.py

Needs Pillow and fontTools (with brotli). It reads:
- the Play Store pictures in fastlane/metadata/android/{en-US,pt-BR}/images: the phone screen is cut
  out of each phone screenshot (the store caption stays behind, the site writes its own in three
  languages), and the TV screenshots are used whole;
- the "Fios de névoa" icon drawing (app/src/main/res/drawable/ic_launcher_foreground_lilac.xml);
- the Figtree font (app/src/main/res/font/figtree.ttf).
Run it again after any of those change.
"""
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "fastlane/metadata/android"
SITE = ROOT / "site"
IMG = SITE / "assets/img"
BRAND = SITE / "assets/brand"
FONTS = SITE / "assets/fonts"
LOCALES = {"en": "en-US", "pt": "pt-BR"}

# Where the phone's screen sits in each 1080x1920 store screenshot, a little inside its rounded frame.
PHONE_SCREEN = (204, 356, 876, 1848)


def cut_phone_screens():
    for lang, folder in LOCALES.items():
        for path in sorted((STORE / folder / "images/phoneScreenshots").glob("*.png")):
            shot = Image.open(path).convert("RGB").crop(PHONE_SCREEN)
            shot.save(IMG / f"phone-{lang}-{path.stem}.webp", quality=84, method=6)


def tv_screens():
    for lang, folder in LOCALES.items():
        for path in sorted((STORE / folder / "images/tvScreenshots").glob("*.png")):
            shot = Image.open(path).convert("RGB").resize((1600, 900), Image.LANCZOS)
            shot.save(IMG / f"tv-{lang}-{path.stem}.webp", quality=82, method=6)


def logo_lines():
    """The icon's strokes, as (x1, y1, x2, y2, group) in its 108-unit box: group 0 is the flower
    cluster, 1 and 2 its two fading reflections."""
    xml = (ROOT / "app/src/main/res/drawable/ic_launcher_foreground_lilac.xml").read_text()
    paths = re.findall(r'android:pathData="([^"]+)"', xml)
    lines = []
    for group, data in enumerate(paths):
        for x1, y1, x2, y2 in re.findall(r"M([\d.]+),([\d.]+) L([\d.]+),([\d.]+)", data):
            lines.append((float(x1), float(y1), float(x2), float(y2), group))
    return lines


def svg_logo(background: bool) -> str:
    """The icon as SVG. Each stroke gets its order as --i, so the site can draw them one by one."""
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">']
    out.append("<defs>")
    out.append('<linearGradient id="k" x1="0" y1="26" x2="0" y2="76" gradientUnits="userSpaceOnUse">'
               '<stop offset="0" stop-color="#C4B0EE"/><stop offset="1" stop-color="#F6F2FF"/></linearGradient>')
    if background:
        out.append('<radialGradient id="b" cx="54" cy="34.6" r="86.4" gradientUnits="userSpaceOnUse">'
                   '<stop offset="0" stop-color="#3B2D66"/><stop offset="1" stop-color="#1A1430"/></radialGradient>')
    out.append("</defs>")
    if background:
        out.append('<rect x="18" y="18" width="72" height="72" rx="17" fill="url(#b)"/>')
    styles = {0: 'stroke="url(#k)"', 1: 'stroke="#F6F2FF" stroke-opacity=".6"', 2: 'stroke="#F6F2FF" stroke-opacity=".34"'}
    for i, (x1, y1, x2, y2, g) in enumerate(logo_lines()):
        out.append(f'<path d="M{x1} {y1}H{x2}" {styles[g]} stroke-width="2.2" stroke-linecap="round" style="--i:{i}"/>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def draw_logo(size: int, background: bool) -> Image.Image:
    """The icon drawn with Pillow (for the PNG icons and the link preview)."""
    scale = 4
    big = size * scale
    k = big / 72
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    if background:
        bg = Image.new("RGBA", (big, big))
        px = bg.load()
        for y in range(big):
            for x in range(big):
                d = (((x / k + 18 - 54) ** 2 + (y / k + 18 - 34.6) ** 2) ** 0.5) / 86.4
                d = min(1, d)
                px[x, y] = tuple(int(a + (b - a) * d) for a, b in zip((0x3B, 0x2D, 0x66), (0x1A, 0x14, 0x30))) + (255,)
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=int(17 * k), fill=255)
        im.paste(bg, (0, 0), mask)
    d = ImageDraw.Draw(im)
    w = 2.2 * k
    for x1, y1, x2, y2, g in logo_lines():
        if g == 0:
            t = (y1 - 26) / 50
            c = tuple(int(a + (b - a) * t) for a, b in zip((0xC4, 0xB0, 0xEE), (0xF6, 0xF2, 0xFF))) + (255,)
        else:
            c = (0xF6, 0xF2, 0xFF, int(255 * (0.6 if g == 1 else 0.34)))
        ax, ay, bx, by = ((x1 - 18) * k, (y1 - 18) * k, (x2 - 18) * k, (y2 - 18) * k)
        layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.line((ax, ay, bx, by), fill=c, width=int(w))
        r = w / 2
        ld.ellipse((ax - r, ay - r, ax + r, ay + r), fill=c)
        ld.ellipse((bx - r, by - r, bx + r, by + r), fill=c)
        im.alpha_composite(layer)
    return im.resize((size, size), Image.LANCZOS)


def link_preview():
    """og-image.png: the Aurora wallpaper, the logo and the name, 1200x630."""
    w, h = 1200, 630
    base = Image.new("RGB", (w, h))
    px = base.load()
    stops = [(0, (0x2B, 0x2A, 0x48)), (0.48, (0x22, 0x50, 0x7F)), (1, (0x22, 0x49, 0x5C))]
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
                if t <= t1:
                    f = (t - t0) / (t1 - t0)
                    px[x, y] = tuple(int(a + (b - a) * f) for a, b in zip(c0, c1))
                    break
    # The two soft lights of Aurora (ellipseGlow in TvArt.kt): a color that fades out from a centre.
    for cx, cy, rx, ry, color, strength in ((w, 0, w * 0.7, h * 0.8, (0x3F, 0x9D, 0xB2), 0.62),
                                            (0, h, w * 0.8, h * 0.9, (0x7A, 0x52, 0xC4), 0.6)):
        for y in range(h):
            for x in range(w):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                if d < 1:
                    a = strength * (1 - d ** 0.5) ** 1.6
                    r, g, b = px[x, y]
                    px[x, y] = (int(r + (color[0] - r) * a), int(g + (color[1] - g) * a), int(b + (color[2] - b) * a))
    im = base.convert("RGBA")
    logo = draw_logo(300, background=False)
    im.alpha_composite(logo, (150, (h - 300) // 2 - 10))
    font = FONTS / "figtree.ttf"
    title = ImageFont.truetype(str(font), 150)
    title.set_variation_by_axes([500])
    sub = ImageFont.truetype(str(font), 44)
    sub.set_variation_by_axes([400])
    d = ImageDraw.Draw(im)
    d.text((500, 170), "Kiri", font=title, fill=(246, 242, 255))
    d.text((506, 360), "A light, glassy home screen", font=sub, fill=(226, 218, 250))
    d.text((506, 420), "Phone, tablet and TV", font=sub, fill=(190, 180, 230))
    im.convert("RGB").save(SITE / "og-image.png", optimize=True)


def font():
    from fontTools import subset
    src = ROOT / "app/src/main/res/font/figtree.ttf"
    (FONTS / "figtree.ttf").write_bytes(src.read_bytes())  # kept for this script's own drawing
    opts = subset.Options()
    opts.flavor = "woff2"
    opts.layout_features = ["*"]
    f = subset.load_font(str(src), opts)
    s = subset.Subsetter(opts)
    # Latin and Latin-1 (Portuguese and Spanish), punctuation, arrows and the middle dot.
    s.populate(unicodes=list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x2010, 0x2040)) + [0x2190, 0x2192, 0x2197, 0x20AC])
    s.subset(f)
    subset.save_font(f, str(FONTS / "figtree.woff2"), opts)


def main():
    for d in (IMG, BRAND, FONTS):
        d.mkdir(parents=True, exist_ok=True)
    font()
    cut_phone_screens()
    tv_screens()
    (BRAND / "logo.svg").write_text(svg_logo(background=False))
    (SITE / "favicon.svg").write_text(svg_logo(background=True))
    draw_logo(180, True).save(SITE / "apple-touch-icon.png", optimize=True)
    draw_logo(192, True).save(SITE / "icon-192.png", optimize=True)
    link_preview()
    (FONTS / "figtree.ttf").unlink()
    print("site assets written", file=sys.stderr)


if __name__ == "__main__":
    main()
