#!/usr/bin/env python3
"""Erzeugt hochaufloesende, kantengeglaettete Texturen (144x192 statt 36x48).
Aufruf: python3 tools/generate_textures_hd.py
Eigene Texturen: einfach PNGs mit gleichen Namen (und gleichem Seitenverhaeltnis) austauschen."""
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
ITEM_TEX = os.path.join(RES, "assets", "minecraft", "textures", "item", "goldensmokes")
GUI_TEX = os.path.join(RES, "assets", "goldensmokes", "textures", "gui")
ICON_DIR = os.path.join(RES, "assets", "goldensmokes")
for d in (ITEM_TEX, GUI_TEX):
    os.makedirs(d, exist_ok=True)

SS = 4                       # Supersampling-Faktor
W, H, LID, SW = 144, 192, 48, 56
BAND = 36                    # Hoehe des Goldbands unten

CREAM_T, CREAM_B = (253, 249, 236), (238, 227, 196)
GOLD_T, GOLD_B = (240, 202, 104), (200, 154, 52)
GOLD_D, GOLD_L = (150, 108, 30), (252, 226, 150)
RED = (160, 28, 30)
DARK = (38, 30, 26)
BROWN = (118, 82, 20)

# Aufruf mit --neutral erzeugt die Variante "Golden Smokes" ohne Markennamen (fuer Modrinth).
NEUTRAL = "--neutral" in sys.argv
BRAND1, BRAND2 = ("GOLDEN", "SMOKES") if NEUTRAL else ("Marlboro", "GOLD")

SERIF_B = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
SANS_B = "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"


def font(path, size):
    try:
        return ImageFont.truetype(path, int(size))
    except OSError:
        return ImageFont.load_default()


def s(v):
    return int(round(v * SS))


def vgrad(img, x0, y0, x1, y1, top, bottom):
    """Vertikaler Farbverlauf (Koordinaten in Endaufloesung)."""
    X0, Y0, X1, Y1 = s(x0), s(y0), s(x1), s(y1)
    h = max(1, Y1 - Y0)
    t = np.linspace(0, 1, h)[:, None, None]
    col = np.array(top)[None, None, :] * (1 - t) + np.array(bottom)[None, None, :] * t
    arr = np.repeat(col, max(1, X1 - X0), axis=1).astype(np.uint8)
    alpha = np.full(arr.shape[:2] + (1,), 255, np.uint8)
    img.paste(Image.fromarray(np.concatenate([arr, alpha], axis=2), "RGBA"), (X0, Y0))


def fit_font(path, txt, max_w, max_size):
    size = max_size
    while size > 6:
        f = font(path, size * SS)
        if f.getlength(txt) <= max_w * SS:
            return f
        size -= 1
    return font(path, 6 * SS)


def spaced_text(d, txt, cx, cy, f, fill, spacing):
    widths = [f.getlength(c) for c in txt]
    total = sum(widths) + spacing * SS * (len(txt) - 1)
    x = s(cx) - total / 2
    for c, w in zip(txt, widths):
        d.text((x, s(cy)), c, font=f, fill=fill, anchor="lm")
        x += w + spacing * SS


def star(d, cx, cy, r_out, r_in, fill):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r_out if i % 2 == 0 else r_in
        pts.append((s(cx + math.cos(a) * r), s(cy + math.sin(a) * r)))
    d.polygon(pts, fill=fill)



def text_image(txt, path, max_w, h, fill):
    """Text in Zielhoehe h; ist er zu breit, wird er gestaucht (schmale Schrift). Rueckgabe in Supersampling-Aufloesung."""
    big = font(path, 200)
    l, t, r, b = big.getbbox(txt)
    tmp = Image.new("RGBA", (r - l, b - t), (0, 0, 0, 0))
    ImageDraw.Draw(tmp).text((-l, -t), txt, font=big, fill=fill)
    nw = min(max_w, (r - l) * h / (b - t))
    return tmp.resize((max(1, s(nw)), s(h)), Image.LANCZOS)


def paste_center(img, tmp, cx, cy):
    img.paste(tmp, (s(cx) - tmp.width // 2, s(cy) - tmp.height // 2), tmp)



REF = os.path.join(ROOT, "tools", "reference", "vorlage.png")
USE_REF = (not NEUTRAL) and os.path.exists(REF)


def crop_ref(box, scale=2):
    """Schneidet eine Ansicht aus dem Original-Screenshot aus und vergroessert sie."""
    im = Image.open(REF).convert("RGB").crop(box)
    im = im.resize((im.width * scale, im.height * scale), Image.LANCZOS)
    im = im.filter(ImageFilter.UnsharpMask(radius=1.4, percent=55, threshold=2))
    return im.convert("RGBA")


def glow(size, radius, color, strength):
    n = size
    yy, xx = np.mgrid[0:n, 0:n]
    d = np.hypot(xx - n / 2 + 0.5, yy - n / 2 + 0.5)
    a = np.clip(1 - d / radius, 0, 1) ** 2 * strength
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color
    arr[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def make_star():
    n = 32 * SS
    img = glow(n, 15.5 * SS, (255, 208, 70), 0.6)
    d = ImageDraw.Draw(img)
    c = n / 2
    pts = []
    for i in range(8):
        a = -math.pi / 2 + i * math.pi / 4
        r = 15 * SS if i % 2 == 0 else 2.6 * SS
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
    d.polygon(pts, fill=(255, 232, 130, 255))
    d.ellipse([c - 2.6 * SS, c - 2.6 * SS, c + 2.6 * SS, c + 2.6 * SS], fill=(255, 252, 235, 255))
    return img.resize((32, 32), Image.LANCZOS)


def make_mini_totem():
    n = 32 * SS
    img = glow(n, 16 * SS, (255, 200, 60), 0.45)
    d = ImageDraw.Draw(img)
    cell = n / 16
    rects = [(5, 1, 11, 5), (4, 5, 12, 10), (1, 5, 4, 7), (12, 5, 15, 7),
             (1, 3, 3, 5), (13, 3, 15, 5), (5, 10, 7, 14), (9, 10, 11, 14)]
    for x0, y0, x1, y1 in rects:        # dunkle Kontur
        d.rectangle([(x0 - .55) * cell, (y0 - .55) * cell, (x1 + .55) * cell, (y1 + .55) * cell], fill=(150, 98, 10, 255))
    for x0, y0, x1, y1 in rects:        # goldene Flaeche
        d.rectangle([x0 * cell, y0 * cell, x1 * cell, y1 * cell], fill=(255, 212, 66, 255))
        d.rectangle([x0 * cell, y0 * cell, x1 * cell, (y0 + .7) * cell], fill=(255, 238, 150, 255))
    for ex in (6.2, 8.8):               # Augen
        d.rectangle([ex * cell, 2.4 * cell, (ex + 1) * cell, 3.4 * cell], fill=(90, 56, 8, 255))
    return img.resize((32, 32), Image.LANCZOS)


def base_pack():
    img = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 255))
    vgrad(img, 0, LID, W, H, CREAM_T, CREAM_B)                    # Koerper
    vgrad(img, 0, 0, W, LID, GOLD_T, GOLD_B)                      # Deckel
    vgrad(img, 0, H - BAND, W, H, GOLD_T, GOLD_B)                 # Goldband
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, s(W), s(2)], fill=GOLD_L)                  # Glanzkante oben
    d.rectangle([0, s(LID - 3), s(W), s(LID)], fill=GOLD_D)       # Deckel-Naht
    d.rectangle([0, s(LID), s(W), s(LID + 1.5)], fill=(255, 240, 190))
    d.rectangle([0, s(H - BAND), s(W), s(H - BAND + 2)], fill=GOLD_D)
    spaced_text(d, "FILTER CIGARETTES", W / 2, 22, font(SANS_B, 11 * SS), BROWN, 0.6)
    return img, d


def finish(img):
    """Seitenkanten abdunkeln, herunterskalieren, leichtes Papierkorn."""
    arr = np.array(img).astype(np.float32)
    h, w = arr.shape[:2]
    xs = np.linspace(-1, 1, w)[None, :, None]
    vignette = 1 - 0.10 * np.clip(np.abs(xs) - 0.8, 0, 1) / 0.2
    arr[..., :3] *= vignette
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    out = out.resize((w // SS, h // SS), Image.LANCZOS)
    a = np.array(out).astype(np.float32)
    rng = np.random.default_rng(7)
    a[..., :3] += rng.normal(0, 1.6, a.shape[:2] + (1,))
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def make_front():
    img, d = base_pack()
    # goldene Ecken -> weisse "Dach"-Form
    d.polygon([(0, s(LID)), (s(52), s(LID)), (0, s(LID + 40))], fill=GOLD_T)
    d.polygon([(s(W), s(LID)), (s(W - 52), s(LID)), (s(W), s(LID + 40))], fill=GOLD_T)
    d.line([(s(52), s(LID)), (0, s(LID + 40))], fill=GOLD_D, width=s(1.2))
    d.line([(s(W - 52), s(LID)), (s(W), s(LID + 40))], fill=GOLD_D, width=s(1.2))
    # Wappen (eigene, allgemeine Gestaltung)
    cx, cy = W / 2, 77
    d.ellipse([s(cx - 15), s(cy - 15), s(cx + 15), s(cy + 15)], fill=GOLD_D)
    d.ellipse([s(cx - 13.5), s(cy - 13.5), s(cx + 13.5), s(cy + 13.5)], fill=GOLD_T)
    d.ellipse([s(cx - 10.5), s(cy - 10.5), s(cx + 10.5), s(cy + 10.5)], fill=RED)
    star(d, cx, cy + 0.5, 8, 3.4, GOLD_L)
    d.polygon([(s(cx - 7), s(cy - 15)), (s(cx - 7), s(cy - 21)), (s(cx - 3.5), s(cy - 17.5)),
               (s(cx), s(cy - 23)), (s(cx + 3.5), s(cy - 17.5)), (s(cx + 7), s(cy - 21)),
               (s(cx + 7), s(cy - 15))], fill=GOLD_D)
    # Schriftzug: grosse Marke + "GOLD"
    paste_center(img, text_image(BRAND1, SERIF_B, 122, 38, DARK), W / 2, 113)
    spaced_text(d, BRAND2, W / 2, 144, font(SANS_B, 22 * SS), (196, 146, 40), 6)
    return finish(img)


def make_back():
    img, d = base_pack()
    d.rectangle([s(10), s(58), s(W - 10), s(150)], fill=DARK)
    d.rectangle([s(13.5), s(61.5), s(W - 13.5), s(146.5)], fill=(250, 245, 228))
    for i, line in enumerate(["RAUCHEN", "KANN", "TÖDLICH", "SEIN"]):
        f = fit_font(SANS_B, line, 100, 17)
        d.text((s(W / 2), s(73 + i * 21)), line, font=f, fill=DARK, anchor="mm")
    return finish(img)


def make_side():
    img = Image.new("RGBA", (SW * SS, H * SS), (0, 0, 0, 255))
    vgrad(img, 0, 0, SW, H, GOLD_T, GOLD_B)
    d = ImageDraw.Draw(img)
    d.rectangle([0, s(LID - 3), s(SW), s(LID)], fill=GOLD_D)
    d.rounded_rectangle([s(14), s(60), s(SW - 14), s(H - 20)], radius=s(5), fill=(250, 244, 226))
    tmp = text_image(BRAND1, SERIF_B, 108, 24, DARK).rotate(-90, expand=True)
    paste_center(img, tmp, SW / 2, (60 + H - 20) / 2)
    return finish(img)


def make_top():
    img = Image.new("RGBA", (W * SS, SW * SS), (0, 0, 0, 255))
    vgrad(img, 0, 0, W, SW, GOLD_L, GOLD_B)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, s(W), s(2)], fill=GOLD_L)
    d.rectangle([0, s(SW - 3), s(W), s(SW)], fill=GOLD_D)
    d.rounded_rectangle([s(40), s(SW / 2 - 1.5), s(W - 40), s(SW / 2 + 1.5)], radius=s(1.5), fill=GOLD_D)
    return finish(img)


if USE_REF:
    # exakt die Ansichten aus dem Screenshot (Vorderseite, Rueckseite, Seite, Oben)
    front = crop_ref((678, 130, 812, 330))
    back = crop_ref((936, 128, 1058, 330))
    side = crop_ref((852, 128, 896, 331))
    top = crop_ref((1092, 188, 1226, 242))
else:
    front, back, side, top = make_front(), make_back(), make_side(), make_top()
front.save(os.path.join(ITEM_TEX, "pack_front.png"))
back.save(os.path.join(ITEM_TEX, "pack_back.png"))
side.save(os.path.join(ITEM_TEX, "pack_side.png"))
top.save(os.path.join(ITEM_TEX, "pack_top.png"))

# Kopien fuer die 3D-Schachtel in der Animation (weich gefiltert)
for name, im in (("front", front), ("back", back), ("side", side), ("top", top)):
    im.save(os.path.join(GUI_TEX, name + ".png"))
    with open(os.path.join(GUI_TEX, name + ".png.mcmeta"), "w") as fh:
        json.dump({"texture": {"blur": True}}, fh)

for old in ("body.png", "lid.png", "strip.png", "cigarette.png", "body.png.mcmeta", "lid.png.mcmeta", "strip.png.mcmeta"):
    path = os.path.join(GUI_TEX, old)
    if os.path.exists(path):
        os.remove(path)

make_star().save(os.path.join(GUI_TEX, "star.png"))
make_mini_totem().save(os.path.join(GUI_TEX, "mini_totem.png"))
for name in ("star", "mini_totem"):
    with open(os.path.join(GUI_TEX, name + ".png.mcmeta"), "w") as fh:
        json.dump({"texture": {"blur": True}}, fh)

icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
f = front.copy()
f.thumbnail((128, 128), Image.LANCZOS)
icon.paste(f, ((128 - f.width) // 2, (128 - f.height) // 2))
icon.save(os.path.join(ICON_DIR, "icon.png"))
print("HD-Texturen erzeugt (" + ("neutral" if NEUTRAL else "Marlboro Gold") + ").")
