"""Writes the app icon drawables and docs/brand SVGs from one geometry (docs/DESIGN.md §7).

Run from the repo root: python3 docs/brand/generate.py
Then re-export the PNG: rsvg-convert -w 512 -h 512 docs/brand/icon-full-bleed.svg -o docs/brand/icon-512.png
"""
import pathlib
root = pathlib.Path(__file__).resolve().parents[2]
BARS = [(30, 54, 14), (38, 44, 22), (46, 42, 28), (54, 54, 30), (62, 66, 28), (70, 64, 22), (78, 54, 14)]
RAMP = ["FFFFFF", "DDE7FF", "BBCFFE", "98B6FE", "769EFD", "5487FC", "3574FC"]
GROUND, DISC = "0C0C0C", "171C29"
RINGS = (26, 21, 16)

def pill(x, cy, length, w=5.5):
    r = w / 2
    top = cy - length / 2
    return f"M{x - r:g},{top + r:g} a{r:g},{r:g} 0 0,1 {w:g},0 v{length - w:g} a{r:g},{r:g} 0 0,1 {-w:g},0 z"

def circle(r, cx=54, cy=54):
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,1 {2 * r:g},0 a{r:g},{r:g} 0 1,1 {-2 * r:g},0 z"

def vector(comment, body, size=108, viewport=108, ignore=None):
    tools = f'\n    xmlns:tools="http://schemas.android.com/tools"\n    tools:ignore="{ignore}"' if ignore else ""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{tools}
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{viewport}"
    android:viewportHeight="{viewport}">
{body}</vector>
'''

def vpath(d, fill=None, alpha=None, stroke=None, stroke_alpha=None, indent="    "):
    attrs = []
    if fill: attrs.append(f'android:fillColor="#FF{fill}"')
    if alpha is not None: attrs.append(f'android:fillAlpha="{alpha:g}"')
    if stroke:
        attrs += [f'android:strokeColor="#FF{stroke}"', 'android:strokeWidth="1"']
    if stroke_alpha is not None: attrs.append(f'android:strokeAlpha="{stroke_alpha:g}"')
    attrs.append(f'android:pathData="{d}"')
    sep = "\n" + indent + "    "
    return f"{indent}<path{sep}{sep.join(attrs)} />\n"

def launcher_layer(mono):
    out = vpath(circle(31), "FFFFFF" if mono else DISC, 0.16 if mono else None)
    for r in RINGS:
        out += vpath(circle(r), stroke="FFFFFF", stroke_alpha=0.2 if mono else 0.06)
    for (x, cy, n), c in zip(BARS, RAMP):
        out += vpath(pill(x, cy, n), "FFFFFF" if mono else c)
    return out

res = root / "app/src/main/res/drawable"
(res / "ic_launcher_foreground.xml").write_text(vector(
    "Wave on vinyl: a grooved record with a waveform. Spec in docs/DESIGN.md §7.", launcher_layer(False)))
(res / "ic_launcher_monochrome.xml").write_text(vector(
    "Themed-icon layer: the foreground as alpha only; the launcher picks the colour.", launcher_layer(True)))

bars = "".join(vpath(pill(x, cy, n), "FFFFFF", indent="        ") for x, cy, n in BARS)
(root / "app/src/main/res/drawable/media3_notification_small_icon.xml").write_text(vector(
    "Replaces Media3's default status-bar icon with the launcher icon's waveform. White; the system tints it.",
    f'    <group\n        android:translateX="-24"\n        android:translateY="-24">\n{bars}    </group>\n',
    size=24, viewport=60, ignore="UnusedResources"))

def svg_layers():
    out = f'  <circle cx="54" cy="54" r="31" fill="#{DISC}"/>\n'
    for r in RINGS:
        out += f'  <circle cx="54" cy="54" r="{r}" fill="none" stroke="#FFFFFF" stroke-opacity="0.06"/>\n'
    for (x, cy, n), c in zip(BARS, RAMP):
        out += f'  <path fill="#{c}" d="{pill(x, cy, n)}"/>\n'
    return out

brand = root / "docs/brand"
(brand / "icon-full-bleed.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">\n'
    f'  <rect width="108" height="108" fill="#{GROUND}"/>\n{svg_layers()}</svg>\n')
(brand / "icon.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="256" height="256">\n'
    f'  <circle cx="54" cy="54" r="36" fill="#{GROUND}"/>\n{svg_layers()}</svg>\n')
