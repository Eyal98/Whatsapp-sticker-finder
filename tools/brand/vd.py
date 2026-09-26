"""Tiny shared model: shapes -> SVG (preview) and Android VectorDrawable."""


def P(d, fill=None, stroke=None, sw=None, cap=None, join=None, alpha=None):
    return {"d": d, "fill": fill, "stroke": stroke, "sw": sw, "cap": cap, "join": join, "alpha": alpha}


def G(children, rotate=0, px=0, py=0, tx=0, ty=0, sx=1, sy=1):
    return {"group": children, "rotate": rotate, "px": px, "py": py, "tx": tx, "ty": ty, "sx": sx, "sy": sy}


def circle(cx, cy, r):
    return f"M{cx - r},{cy}a{r},{r} 0,1 1,{2 * r},0a{r},{r} 0,1 1,{-2 * r},0z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy}a{rx},{ry} 0,1 1,{2 * rx},0a{rx},{ry} 0,1 1,{-2 * rx},0z"


def _argb(color, alpha):
    c = color.lstrip("#")
    if alpha is None:
        return "#" + c.upper()
    return "#%02X%s" % (round(alpha * 255), c.upper())


def svg(elements, vw, vh, background=None):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}">']
    if background:
        out.append(background)

    def emit(el):
        if "group" in el:
            t = f"translate({el['tx']},{el['ty']}) rotate({el['rotate']},{el['px']},{el['py']})"
            if el["sx"] != 1 or el["sy"] != 1:
                t += f" translate({el['px']},{el['py']}) scale({el['sx']},{el['sy']}) translate({-el['px']},{-el['py']})"
            out.append(f'<g transform="{t}">')
            for c in el["group"]:
                emit(c)
            out.append("</g>")
            return
        a = [f'd="{el["d"]}"', f'fill="{el["fill"] or "none"}"']
        if el["stroke"]:
            a += [f'stroke="{el["stroke"]}"', f'stroke-width="{el["sw"]}"']
            if el["cap"]:
                a.append(f'stroke-linecap="{el["cap"]}"')
            if el["join"]:
                a.append(f'stroke-linejoin="{el["join"]}"')
        if el["alpha"] is not None:
            a.append(f'opacity="{el["alpha"]}"')
        out.append(f"<path {' '.join(a)}/>")

    for e in elements:
        emit(e)
    out.append("</svg>")
    return "\n".join(out)


def vector(elements, vw, vh, dp_w, dp_h, comment=""):
    out = ['<?xml version="1.0" encoding="utf-8"?>']
    if comment:
        out.append(f"<!-- {comment} -->")
    out.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    out.append(f'    android:width="{dp_w}dp"\n    android:height="{dp_h}dp"')
    out.append(f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">')

    def emit(el, ind):
        sp = "    " * ind
        if "group" in el:
            attrs = []
            if el["rotate"]:
                attrs += [f'android:rotation="{el["rotate"]}"']
            if el["rotate"] or el["sx"] != 1 or el["sy"] != 1:
                attrs += [f'android:pivotX="{el["px"]}"', f'android:pivotY="{el["py"]}"']
            if el["sx"] != 1:
                attrs.append(f'android:scaleX="{el["sx"]}"')
            if el["sy"] != 1:
                attrs.append(f'android:scaleY="{el["sy"]}"')
            if el["tx"]:
                attrs.append(f'android:translateX="{el["tx"]}"')
            if el["ty"]:
                attrs.append(f'android:translateY="{el["ty"]}"')
            out.append(f"{sp}<group" + "".join(f"\n{sp}    {a}" for a in attrs) + ">")
            for c in el["group"]:
                emit(c, ind + 1)
            out.append(f"{sp}</group>")
            return
        attrs = [f'android:fillColor="{_argb(el["fill"], el["alpha"]) if el["fill"] else "#00000000"}"']
        if el["stroke"]:
            attrs.append(f'android:strokeColor="{_argb(el["stroke"], el["alpha"])}"')
            attrs.append(f'android:strokeWidth="{el["sw"]}"')
            if el["cap"]:
                attrs.append(f'android:strokeLineCap="{el["cap"]}"')
            if el["join"]:
                attrs.append(f'android:strokeLineJoin="{el["join"]}"')
        attrs.append(f'android:pathData="{el["d"]}"')
        out.append(f"{sp}<path" + "".join(f"\n{sp}    {a}" for a in attrs) + " />")

    for e in elements:
        emit(e, 1)
    out.append("</vector>")
    return "\n".join(out) + "\n"
