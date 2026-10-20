/**
 * Narcic Gateway - NRG Short Link Relay Worker
 * 
 * راهنمای نصب و راه‌اندازی در کلودفلر (کاملاً رایگان):
 * ۱. وارد پنل Cloudflare شوید و به بخش Workers & Pages > KV بروید.
 * ۲. یک KV Namespace بسازید به نام NRG_KV.
 * ۳. یک Worker جدید بسازید (Workers & Pages > Create application > Create Worker).
 * ۴. کد زیر را کپی کرده و جایگزین محتوای Worker کنید.
 * ۵. در تنظیمات Worker (Settings > Variables > KV Namespace Bindings):
 *    یک Binding اضافه کنید:
 *    - Variable name: NRG_KV
 *    - KV namespace: NRG_KV (همانی که در مرحله ۲ ساختید)
 * ۶. (اختیاری) اگر می‌خواهید فقط خودتان بتوانید کانفیگ بسازید، در همان بخش Variables یک Environment Variable به نام AUTH_TOKEN بسازید و رمز دلخواه بگذارید.
 * ۷. دکمه Save and Deploy را بزنید. آدرس Worker (مثلاً https://nrg-relay.yourname.workers.dev) را در برنامه وارد کنید.
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, X-NRG-Token, User-Agent",
        },
      });
    }

    // GET /get/:id -> دریافت کانفیگ رمزشده
    if (request.method === "GET" && url.pathname.startsWith("/get/")) {
      const id = url.pathname.substring("/get/".length).trim();
      if (!id || id.length < 6 || id.length > 64) {
        return new Response("شناسه نامعتبر است", { status: 400 });
      }

      if (!env.NRG_KV) {
        return new Response("NRG_KV binding is missing", { status: 500 });
      }

      const blob = await env.NRG_KV.get(id);
      if (!blob) {
        return new Response("این لینک پیدا نشد یا منقضی شده است", { status: 404 });
      }

      return new Response(blob, {
        status: 200,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "public, max-age=300",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    // POST /put -> ذخیره کانفیگ رمزشده و دریافت شناسه کوتاه
    if (request.method === "POST" && url.pathname === "/put") {
      // بررسی توکن اختیاری
      if (env.AUTH_TOKEN) {
        const clientToken = request.headers.get("X-NRG-Token");
        if (!clientToken || clientToken !== env.AUTH_TOKEN) {
          return new Response("Unauthorized", { status: 401 });
        }
      }

      if (!env.NRG_KV) {
        return new Response("NRG_KV binding is missing", { status: 500 });
      }

      const body = await request.text();
      if (!body || body.length < 16 || body.length > 6_000_000) {
        return new Response("داده ارسالی نامعتبر یا بیش از حد بزرگ است", { status: 400 });
      }

      // تولید شناسه تصادفی ۱۰ کاراکتری ایمن برای URL
      const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
      const randomValues = new Uint8Array(10);
      crypto.getRandomValues(randomValues);
      let id = "";
      for (let i = 0; i < 10; i++) {
        id += chars[randomValues[i] % chars.length];
      }

      // مدت زمان اعتبار (پیش‌فرض ۳۰ روز یا بر اساس پارامتر ttl)
      const ttlParam = parseInt(url.searchParams.get("ttl") || "0", 10);
      const ttlSeconds = (ttlParam >= 60) ? Math.min(ttlParam, 365 * 24 * 3600) : 30 * 24 * 3600;

      await env.NRG_KV.put(id, body, { expirationTtl: ttlSeconds });

      return new Response(JSON.stringify({ id }), {
        status: 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    return new Response("Narcic Gateway NRG Relay Worker is active.", {
      status: 200,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  },
};
