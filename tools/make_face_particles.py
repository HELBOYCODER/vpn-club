#!/usr/bin/env python3
"""
ساخت assets/hellboy_particles.bin از ۱ تا ۳ عکس هم‌قاب (۴۶۰×۴۶۰ یا هر اندازه‌ی مربعی):
  python3 tools/make_face_particles.py neutral.jpg [smile.jpg] [sad.jpg] out.bin

ذرات روی یک شبکه‌ی ثابت نمونه‌برداری می‌شوند؛ هر ذره سه رنگ دارد (خنثی/لبخند/ناراحت)،
پس در برنامه چهره بدون جابه‌جایی ناهماهنگ از یک حالت به حالت دیگر «مورف» می‌شود.
فرمت: b"HBX1" | u16 n | n × (u8 x, u8 y, 3 × (u8 r, g, b))
"""
import random, struct, sys
from PIL import Image

args = sys.argv[1:]
out = args[-1]; srcs = args[:-1]
if not 1 <= len(srcs) <= 3:
    sys.exit("usage: make_face_particles.py neutral [smile] [sad] out.bin")
N = 460; STEP = 3; MAX = 12000
ims = [Image.open(p).convert('RGB').resize((N, N), Image.LANCZOS) for p in srcs]
while len(ims) < 3: ims.append(ims[0])

def keep(px):
    r, g, b = px
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    # پوست/ریش قرمز-قهوه‌ای (حتی در سایه) + شعله و هایلایت‌ها؛ دیوار خاکستری حذف می‌شود
    return (r > 45 and r > g * 1.18 and r > b * 1.18) or lum > 120

pts = []
for y in range(0, N, STEP):
    for x in range(0, N, STEP):
        if (x - N / 2) ** 2 + (y - N / 2) ** 2 > (N * 0.47) ** 2: continue
        cols = [im.getpixel((x, y)) for im in ims]
        if any(keep(c) for c in cols):   # اجتماع: ذره‌ای که در یکی از حالت‌ها پیداست
            pts.append((x, y, cols))
random.seed(7)
# شبکه‌ی یکنواخت؛ فقط اگر از سقف بیشتر شد نمونه‌گیری (حفره‌ی تصادفی در صورت ایجاد نشود)
if len(pts) > MAX: pts = random.sample(pts, MAX)
with open(out, 'wb') as f:
    f.write(b'HBX1'); f.write(struct.pack('>H', len(pts)))
    for x, y, cols in pts:
        f.write(bytes([int(x / N * 255), int(y / N * 255)]))
        for c in cols: f.write(bytes(c))
print(f"{len(pts)} particles, {len(srcs)} expression image(s) -> {out}")
