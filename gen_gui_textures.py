"""
Generates all 5 strap GUI textures for the Over Protected mod.
No external dependencies — uses only stdlib struct/zlib.

Slot layout mirrors StrapMenu.java exactly:
  STRAP_ROW_X=8, STRAP_ROW_Y=18, spacing=18
  playerInvY = 18 + strapRows*18 + 10
  hotbarY    = playerInvY + 58

Panel sizes:
  Leather/Iron/Golden/Diamond : 176 x 166
  Netherite (2 rows of 8)     : 176 x 184

Textures are 256x256 RGBA PNGs (panel in top-left corner).
"""

import struct, zlib, os

# ── PNG writer ────────────────────────────────────────────────────────────────

def _chunk(tag, data):
    payload = tag + data
    return struct.pack('>I', len(data)) + payload + struct.pack('>I', zlib.crc32(payload) & 0xffffffff)

def write_png(path, w, h, pixels):
    """pixels: flat list of (r,g,b,a) tuples, row-major."""
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)  # 8-bit RGBA
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter: None
        for x in range(w):
            raw.extend(pixels[y * w + x])
    data = (b'\x89PNG\r\n\x1a\n'
            + _chunk(b'IHDR', ihdr)
            + _chunk(b'IDAT', zlib.compress(bytes(raw), 9))
            + _chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(data)

# ── Palette ───────────────────────────────────────────────────────────────────

BG      = (198, 198, 198, 255)  # panel background
DARK    = ( 85,  85,  85, 255)  # outer dark border
LIGHT   = (255, 255, 255, 255)  # outer light border
SLOT_BG = (139, 139, 139, 255)  # slot interior
SLOT_D  = ( 55,  55,  55, 255)  # slot top/left shadow
SLOT_L  = (198, 198, 198, 255)  # slot bottom/right highlight
SEP     = (100, 100, 100, 255)  # separator line
TRANSP  = (  0,   0,   0,   0)

# Corner/accent color per tier
TIER_CFG = {
    #  name        slots  two_rows  R    G    B
    'leather':   (  2,   False,  131,  96,  57),
    'iron':      (  4,   False,  180, 180, 180),
    'golden':    (  6,   False,  243, 198,  24),
    'diamond':   (  8,   False,   79, 212, 234),
    'netherite': ( 16,   True,    72,  62,  73),
}

# ── Drawing helpers ───────────────────────────────────────────────────────────

W = 256  # texture width & height

def _idx(x, y): return y * W + x

def set_px(pixels, x, y, c):
    if 0 <= x < W and 0 <= y < W:
        pixels[_idx(x, y)] = c

def fill_rect(pixels, x0, y0, rw, rh, c):
    for dy in range(rh):
        for dx in range(rw):
            set_px(pixels, x0 + dx, y0 + dy, c)

def draw_panel(pixels, pw, ph):
    """Gray Minecraft-style panel with 3-D outer border."""
    fill_rect(pixels, 0, 0, pw, ph, BG)
    # Outer edge: dark top/left, light bottom/right
    for i in range(pw):
        set_px(pixels, i, 0,    DARK)
        set_px(pixels, i, ph-1, LIGHT)
    for i in range(ph):
        set_px(pixels, 0,    i, DARK)
        set_px(pixels, pw-1, i, LIGHT)
    # Inner edge: light top/left, dark bottom/right
    for i in range(1, pw-1):
        set_px(pixels, i, 1,    LIGHT)
        set_px(pixels, i, ph-2, DARK)
    for i in range(1, ph-1):
        set_px(pixels, 1,    i, LIGHT)
        set_px(pixels, pw-2, i, DARK)

def draw_slot(pixels, sx, sy):
    """18x18 recessed slot centered at (sx, sy)."""
    for i in range(18):
        set_px(pixels, sx + i, sy,      SLOT_D)   # top
        set_px(pixels, sx,     sy + i,  SLOT_D)   # left
        set_px(pixels, sx + i, sy + 17, SLOT_L)   # bottom
        set_px(pixels, sx + 17, sy + i, SLOT_L)   # right
    fill_rect(pixels, sx+1, sy+1, 16, 16, SLOT_BG)

def draw_corner_gem(pixels, cx, cy, r, g, b):
    """
    9x9 gem icon (diamond silhouette) with a highlight pixel.
    cx/cy = top-left corner of the 9x9 bounding box.
    """
    mid  = (r, g, b, 255)
    dark = (max(0,r-70), max(0,g-70), max(0,b-70), 255)
    hi   = (min(255,r+90), min(255,g+90), min(255,b+90), 255)

    # Diamond-shaped silhouette (row offsets from top)
    shape = [
        (3, 5),   # y=0: cols 3-5
        (1, 7),   # y=1
        (0, 8),   # y=2
        (0, 8),   # y=3
        (0, 8),   # y=4
        (1, 7),   # y=5
        (2, 6),   # y=6
        (3, 5),   # y=7
        (4, 4),   # y=8
    ]
    for dy, (x0, x1) in enumerate(shape):
        for dx in range(x0, x1+1):
            set_px(pixels, cx+dx, cy+dy, mid)

    # Dark lower-right shading
    for dy, (x0, x1) in enumerate(shape):
        set_px(pixels, cx+x1, cy+dy, dark)
    for dx in range(shape[-1][0], shape[-1][1]+1):
        set_px(pixels, cx+dx, cy+len(shape)-1, dark)

    # Bright highlight top-left
    set_px(pixels, cx+3, cy+1, hi)
    set_px(pixels, cx+2, cy+2, hi)
    set_px(pixels, cx+1, cy+3, hi)

def draw_tier_accents(pixels, pw, r, g, b):
    """
    Colored top stripe + gem icons in top-left and top-right of the panel.
    """
    # Thin stripe at y=2 and y=3 (inside the white inner border)
    stripe = (r, g, b, 255)
    fill_rect(pixels, 2, 2, pw-4, 3, stripe)

    # Gem in top-left corner (fits within the stripe area, just above it)
    draw_corner_gem(pixels,  3, 4, r, g, b)
    # Gem in top-right corner
    draw_corner_gem(pixels, pw-12, 4, r, g, b)

# ── Main ──────────────────────────────────────────────────────────────────────

OUT_DIR = os.path.join(os.path.dirname(__file__),
                       'src', 'main', 'resources', 'assets',
                       'overprotected', 'textures', 'gui')
os.makedirs(OUT_DIR, exist_ok=True)

for tier, (slots, two_rows, r, g, b) in TIER_CFG.items():
    panel_w    = 176
    spr        = 8 if two_rows else slots        # slots per row
    strap_rows = 2 if two_rows else 1

    player_y = 18 + strap_rows * 18 + 22     # matches StrapMenu.java (+22 gap)
    hotbar_y = player_y + 58
    panel_h  = hotbar_y + 24                 # slot height 18 + bottom padding 6

    pixels = [TRANSP] * (W * W)

    draw_panel(pixels, panel_w, panel_h)
    draw_tier_accents(pixels, panel_w, r, g, b)

    # Strap armor slots (centered horizontally)
    strap_start_x = (176 - spr * 18) // 2
    for i in range(slots):
        row, col = i // spr, i % spr
        draw_slot(pixels, strap_start_x - 1 + col * 18, 17 + row * 18)

    # Separator between strap area and player inventory
    sep_y = 18 + strap_rows * 18 + 5
    fill_rect(pixels, 7, sep_y, panel_w - 14, 1, SEP)

    # Player inventory (3 rows x 9 cols)
    for row in range(3):
        for col in range(9):
            draw_slot(pixels, 7 + col * 18, player_y - 1 + row * 18)

    # Separator between inventory rows and hotbar
    fill_rect(pixels, 7, hotbar_y - 4, panel_w - 14, 1, SEP)

    # Hotbar (9 cols)
    for col in range(9):
        draw_slot(pixels, 7 + col * 18, hotbar_y - 1)

    out_path = os.path.join(OUT_DIR, f'{tier}_strap_gui.png')
    write_png(out_path, W, W, pixels)
    print(f'Generated: {os.path.basename(out_path)}  (panel {panel_w}x{panel_h}, {slots} strap slots)')

print('\nAll done! Copy the PNGs into your textures/gui/ folder if not already there.')
