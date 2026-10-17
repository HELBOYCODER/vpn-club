#!/usr/bin/env python3
"""
بافت بتن تیره‌ی بی‌درز (seamless) — هم‌رنگ دیوار پشت عکس هلبوی — برای پس‌زمینه‌ی برنامه.
  python3 tools/make_concrete.py out_dir
نویز دوره‌ای در حوزه‌ی فرکانس ساخته می‌شود، پس کاشی‌ها بدون درز کنار هم می‌نشینند.
"""
import sys
import numpy as np
from PIL import Image

out = sys.argv[1]
S = 512
rng = np.random.default_rng(42)

def periodic_noise(sigma):
    """نویز گاوسی فیلترشده در فرکانس → ذاتاً تکرارشونده (بی‌درز)."""
    white = rng.standard_normal((S, S))
    fx = np.fft.fftfreq(S)[:, None]; fy = np.fft.fftfreq(S)[None, :]
    g = np.exp(-(fx ** 2 + fy ** 2) * (2 * np.pi * sigma) ** 2 / 2)
    n = np.real(np.fft.ifft2(np.fft.fft2(white) * g))
    return (n - n.mean()) / (n.std() + 1e-9)

def concrete(base, light_patch, pit, grain_amp):
    big = periodic_noise(48)      # لکه‌های بزرگ روشن/تیره (مثل دیوار عکس)
    mid = periodic_noise(10)
    fine = periodic_noise(1.2)
    t = 0.55 * big + 0.30 * mid + 0.15 * fine
    t = (t - t.min()) / (t.max() - t.min())
    base = np.array(base, float); light = np.array(light_patch, float)
    img = base + (light - base) * (t[..., None] ** 1.6)
    # دانه‌ی ریز
    img += rng.normal(0, grain_amp, (S, S, 1))
    # حفره‌های ریز بتن (seamless با wrap)
    for _ in range(900):
        x, y = rng.integers(0, S, 2); r = rng.integers(1, 3)
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r:
                    img[(y + dy) % S, (x + dx) % S] *= pit
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))

concrete((34, 41, 41), (78, 87, 82), 0.62, 5).save(f"{out}/bg_concrete_dark.webp", "WEBP", quality=82, method=6)
concrete((168, 170, 165), (214, 214, 208), 0.80, 6).save(f"{out}/bg_concrete_light.webp", "WEBP", quality=82, method=6)
print("ok")
