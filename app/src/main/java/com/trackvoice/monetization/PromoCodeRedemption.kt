package com.trackvoice.monetization

import java.util.Locale

private const val PLAY_REDEEM_URL = "https://play.google.com/redeem?code="

fun promoCodeRedeemUrl(rawCode: String): String? {
    val code = rawCode
        .filterNot(Char::isWhitespace)
        .uppercase(Locale.ROOT)
    if (code.length !in 4..64 || code.any { it !in 'A'..'Z' && it !in '0'..'9' && it != '-' }) return null
    return PLAY_REDEEM_URL + code
}
