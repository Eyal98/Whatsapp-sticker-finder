"""Peel-It flat logo: a white sticker, corner peeling, with a flat side-view elephant on it."""
from vd import P, G, circle, ellipse

VIOLET = "#5B4BDB"; PINK = "#FF6FA8"; INK = "#2D2A4A"; WHITE = "#FFFFFF"; FOLD = "#DDD8FA"

def elephant(col=VIOLET, ear=PINK, eye=WHITE, mono=False):
    """Side view facing right, in a 100x100 box."""
    c = col
    parts = [
        # tail
        P("M20,50q-6,4 -5,12", stroke=c, sw=3.5, cap="round"),
        # legs (back pair slightly apart)
        P("M24,58h13v24a3,3 0,0 1,-3,3h-7a3,3 0,0 1,-3,-3z", fill=c),
        P("M55,58h13v24a3,3 0,0 1,-3,3h-7a3,3 0,0 1,-3,-3z", fill=c),
        # body
        P(ellipse(46, 52, 28, 21), fill=c),
        # head
        P(circle(72, 40, 16), fill=c),
        # trunk raised and curled: the cheerful "lucky elephant" pose
        P("M80,48C90,48 95,40 94,30C93.5,24 88,22 86,26", stroke=c, sw=9, cap="round", join="round"),
    ]
    if mono:
        return parts
    return parts + [
        P("M63,31c-8,2 -12,12 -8,20c3,6 10,8 15,4c2,-6 1,-18 -7,-24z", fill=ear),
        P(circle(77, 36, 3), fill=eye),
        P(circle(77.8, 36.2, 1.6), fill=INK),
        # bow on top of the head
        P("M71,24.5l-7,-5q-2,5 1,9.5z", fill=PINK),
        P("M71,24.5l7,-5q2,5 -1,9.5z", fill=PINK),
        P(circle(71, 24.5, 2.3), fill="#E0407E"),
    ]

def sticker(inner):
    """Rounded white square, bottom-right corner folded up; `inner` drawn on it."""
    body = "M28,24h52a6,6 0,0 1,6,6v36l-20,20h-38a6,6 0,0 1,-6,-6v-50a6,6 0,0 1,6,-6z"
    return G([
        P(body, fill="#000000", alpha=0.18),
    ], ty=2.2) , [
        P(body, fill=WHITE),
        G(inner, sx=0.64, sy=0.64, px=0, py=0, tx=18, ty=21),
        # the fold: the peeled corner, lifted back over the sticker
        P("M86,66l-20,20v-14a6,6 0,0 1,6,-6z", fill=FOLD),
        P("M86,66l-20,20", stroke="#C9C2F2", sw=0.8),
    ]

def logo(k="color"):
    if k == "mono":
        return [G(elephant(mono=True), sx=0.62, sy=0.62, px=0, py=0, tx=23, ty=22)]
    shadow, top = sticker(elephant())
    # Scaled so the tilted sticker's corners stay inside the 66dp safe circle of adaptive icons.
    return [G([G([shadow] + top, rotate=-8, px=54, py=54)], sx=0.8, sy=0.8, px=54, py=54)]

BG = ('<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6C5CE7"/>'
      '<stop offset="1" stop-color="#00B894"/></linearGradient></defs><rect width="108" height="108" fill="url(#g)"/>')


def mini_sticker():
    """A small sticker with a heart and a peeled corner, centred on 0,0."""
    body = "M-15,-17h30a4,4 0,0 1,4,4v18l-12,12h-22a4,4 0,0 1,-4,-4v-26a4,4 0,0 1,4,-4z"
    return [
        G([P(body, fill="#000000", alpha=0.14)], ty=1.8),
        P(body, fill=WHITE, stroke="#D6D0F5", sw=1.2, join="round"),
        P("M0,-3c-2.4,-4.8 -9.6,-4.3 -9.6,1c0,4.8 9.6,10 9.6,10c0,0 9.6,-5.2 9.6,-10c0,-5.3 -7.2,-5.8 -9.6,-1z", fill=PINK),
        P("M19,5l-12,12v-8a4,4 0,0 1,4,-4z", fill=FOLD),
    ]


def mascot():
    """The logo's elephant, full size, lifting a sticker with her curled trunk (200x200)."""
    return [
        # soft ground shadow
        P(ellipse(92, 184, 62, 7), fill="#000000", alpha=0.08),
        G(elephant(), sx=1.9, sy=1.9, px=0, py=0, tx=-6, ty=25),
        G(mini_sticker(), rotate=12, tx=160, ty=55),
    ]
