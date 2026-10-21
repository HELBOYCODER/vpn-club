package com.helboy.vpnclub.corex

import java.math.BigInteger
import java.security.SecureRandom

/**
 * X25519 (RFC 7748) برای کلیدهای WireGuard/WARP — بدون کتابخانه‌ی خارجی (minSdk 29 الگوریتم XDH ندارد).
 * فقط یک بار در ثبت WARP اجرا می‌شود؛ پیاده‌سازی BigInteger ساده و خوانا کافی است.
 * درستی با بردار آزمون RFC 7748 §5.2 و §6.1 تست شده (X25519Test).
 */
object X25519 {
    private val P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)

    /** کلید خصوصی ۳۲ بایتی تصادفی (clamp شده). */
    fun newPrivateKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it); clamp(it) }

    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, ByteArray(32).also { it[0] = 9 })

    private fun clamp(k: ByteArray) {
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = ((k[31].toInt() and 127) or 64).toByte()
    }

    private fun decodeLE(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun encodeLE(x: BigInteger): ByteArray {
        val be = x.mod(P).toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
        return (ByteArray(32 - be.size) + be).reversedArray()
    }

    /** ضرب اسکالر روی منحنی (نردبان مونتگومری، RFC 7748 §5). */
    fun scalarMult(scalar: ByteArray, u: ByteArray): ByteArray {
        val k = scalar.copyOf().also { clamp(it) }
        val kn = decodeLE(k)
        val uu = u.copyOf().also { it[31] = (it[31].toInt() and 127).toByte() }
        val x1 = decodeLE(uu)
        var x2 = BigInteger.ONE; var z2 = BigInteger.ZERO
        var x3 = x1; var z3 = BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val kt = if (kn.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap == 1) { val tx = x2; x2 = x3; x3 = tx; val tz = z2; z2 = z3; z3 = tz }
            swap = kt
            val a = x2.add(z2).mod(P); val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P); val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P); val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P); val cb = c.multiply(b).mod(P)
            x3 = da.add(cb).mod(P).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).mod(P).pow(2)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e))).mod(P)
        }
        if (swap == 1) { val tx = x2; x2 = x3; x3 = tx; val tz = z2; z2 = z3; z3 = tz }
        return encodeLE(x2.multiply(z2.modPow(P.subtract(BigInteger.TWO), P)).mod(P))
    }
}
