package com.trackvoice.monetization

import java.security.MessageDigest
import java.util.Locale

private const val PLAY_REDEEM_URL = "https://play.google.com/redeem?code="
private const val LOCAL_PLUS_CODE_SHA256 = "3039493f7ec836f610ce5b8452c589d655978cdc7a4c966380e7a0e239b221d6"

private fun normalizePromoCode(rawCode: String): String = rawCode
    .filterNot(Char::isWhitespace)
    .uppercase(Locale.ROOT)

private fun isSafePromoCode(code: String): Boolean =
    code.length in 4..64 && code.all { it in 'A'..'Z' || it in '0'..'9' || it == '-' }

fun isLocalPlusPromoCode(rawCode: String): Boolean {
    val code = normalizePromoCode(rawCode)
    if (!isSafePromoCode(code)) return false
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(code.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
    return digest == LOCAL_PLUS_CODE_SHA256
}

fun promoCodeRedeemUrl(rawCode: String): String? {
    val code = normalizePromoCode(rawCode)
    if (!isSafePromoCode(code)) return null
    return PLAY_REDEEM_URL + code
}
