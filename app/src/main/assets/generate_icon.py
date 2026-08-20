import math
import struct
import zlib

def make_png(width, height, get_pixel):
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0) # Filter type 0 (None)
        for x in range(width):
            r, g, b, a = get_pixel(x, y, width, height)
            raw_data.extend([int(r), int(g), int(b), int(a)])

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) # 8-bit RGBA
    png += chunk(b'IDAT', zlib.compress(raw_data, 9))
    png += chunk(b'IEND', b'')
    return png

def distance_to_segment(px, py, x1, y1, x2, y2):
    dx = x2 - x1
    dy = y2 - y1
    if dx == 0 and dy == 0:
        return math.hypot(px - x1, py - y1)
    t = max(0, min(1, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
    proj_x = x1 + t * dx
    proj_y = y1 + t * dy
    return math.hypot(px - proj_x, py - proj_y)

def point_in_triangle(px, py, x1, y1, x2, y2, x3, y3):
    def sign(p1x, p1y, p2x, p2y, p3x, p3y):
        return (p1x - p3x) * (p2y - p3y) - (p2x - p3x) * (p1y - p3y)
    d1 = sign(px, py, x1, y1, x2, y2)
    d2 = sign(px, py, x2, y2, x3, y3)
    d3 = sign(px, py, x3, y3, x1, y1)
    has_neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
    has_pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
    return not (has_neg and has_pos)

def render_pixel(x, y, w, h):
    cx, cy = w / 2.0, h / 2.0
    dx, dy = x - cx, y - cy
    dist = math.hypot(dx, dy)
    
    # Base Background Linear Gradient: (42, 8, 69) -> (13, 0, 23)
    t_bg = (x + y) / (w + h)
    r_bg = 42 * (1 - t_bg) + 13 * t_bg
    g_bg = 8 * (1 - t_bg) + 0 * t_bg
    b_bg = 69 * (1 - t_bg) + 23 * t_bg
    
    # Radial ambient purple glow in center
    if dist < 220:
        glow = math.cos(dist / 220 * math.pi / 2) ** 1.5 * 0.4
        r_bg = min(255, r_bg + 80 * glow)
        g_bg = min(255, g_bg + 20 * glow)
        b_bg = min(255, b_bg + 140 * glow)

    # Groove rings
    for ring_r in [180, 140]:
        diff = abs(dist - ring_r)
        if diff < 1.5:
            alpha = (1.5 - diff) / 1.5 * 0.35
            r_bg = r_bg * (1 - alpha) + 79 * alpha
            g_bg = g_bg * (1 - alpha) + 55 * alpha
            b_bg = b_bg * (1 - alpha) + 139 * alpha

    # Core Disc at dist <= 75
    if dist <= 75:
        disc_t = (x - 180 + y - 180) / 150.0
        disc_t = max(0, min(1, disc_t))
        dr = 49 * (1 - disc_t) + 26 * disc_t
        dg = 17 * (1 - disc_t) + 4 * disc_t
        db = 84 * (1 - disc_t) + 43 * disc_t
        if abs(dist - 75) < 2.0:
            border_a = (2.0 - abs(dist - 75)) / 2.0
            dr = dr * (1 - border_a) + 124 * border_a
            dg = dg * (1 - border_a) + 77 * border_a
            db = db * (1 - border_a) + 255 * border_a
        r_bg, g_bg, b_bg = dr, dg, db

    # Glowing Loop Ring Arc (Radius ~105, width ~20)
    angle = math.atan2(dy, dx)
    norm_angle = (angle + 2 * math.pi) % (2 * math.pi)
    in_arc_angle = (norm_angle >= 2.0 or norm_angle <= 0.1 or norm_angle >= 5.8)
    arc_diff = abs(dist - 105)
    if arc_diff < 12 and in_arc_angle:
        arc_factor = (12 - arc_diff) / 12
        arc_t = (x + y - 150) / 300.0
        arc_t = max(0, min(1, arc_t))
        if arc_t < 0.5:
            st = arc_t / 0.5
            cr = 0 * (1 - st) + 124 * st
            cg = 229 * (1 - st) + 77 * st
            cb = 255
        else:
            st = (arc_t - 0.5) / 0.5
            cr = 124 * (1 - st) + 224 * st
            cg = 77 * (1 - st) + 64 * st
            cb = 255 * (1 - st) + 251 * st
        
        r_bg = r_bg * (1 - arc_factor) + cr * arc_factor
        g_bg = g_bg * (1 - arc_factor) + cg * arc_factor
        b_bg = b_bg * (1 - arc_factor) + cb * arc_factor

    # Arrowhead Top-Right: (345, 145), (395, 205), (325, 215)
    if point_in_triangle(x, y, 345, 145, 395, 205, 325, 215):
        r_bg, g_bg, b_bg = 224, 64, 251

    # Arrowhead Bottom-Left: (170, 335), (120, 310), (155, 270)
    if point_in_triangle(x, y, 170, 335, 120, 310, 155, 270):
        r_bg, g_bg, b_bg = 0, 229, 255

    # Soundwave bars
    # Left bar: (205, 230) to (205, 282)
    d_l = distance_to_segment(x, y, 205, 230, 205, 282)
    if d_l < 5.5:
        a_l = (5.5 - d_l) / 5.5
        r_bg = r_bg * (1 - a_l) + 0 * a_l
        g_bg = g_bg * (1 - a_l) + 229 * a_l
        b_bg = b_bg * (1 - a_l) + 255 * a_l

    # Right bar: (307, 225) to (307, 287)
    d_r = distance_to_segment(x, y, 307, 225, 307, 287)
    if d_r < 5.5:
        a_r = (5.5 - d_r) / 5.5
        r_bg = r_bg * (1 - a_r) + 224 * a_r
        g_bg = g_bg * (1 - a_r) + 64 * a_r
        b_bg = b_bg * (1 - a_r) + 251 * a_r

    # Center Playhead Triangle: (240, 215), (290, 256), (240, 297)
    if point_in_triangle(x, y, 240, 215, 290, 256, 240, 297):
        pt = (y - 215) / 82.0
        r_bg = 255 * (1 - pt) + 234 * pt
        g_bg = 255 * (1 - pt) + 221 * pt
        b_bg = 255
        
    # Center Spindle Micro Dot
    if dist < 6:
        r_bg, g_bg, b_bg = 56, 30, 114

    return (r_bg, g_bg, b_bg, 255)

if __name__ == '__main__':
    print("Generating original 512x512 PNG app icon...")
    png_bytes = make_png(512, 512, render_pixel)
    with open('app/src/main/assets/loopcount_app_icon_512.png', 'wb') as f:
        f.write(png_bytes)
    print("Done! Saved original app/src/main/assets/loopcount_app_icon_512.png")
