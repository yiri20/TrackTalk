package com.trackvoice.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromoCodeRedemptionTest {
    @Test
    fun buildsPlayRedeemUrlAndNormalizesCode() {
        assertEquals(
            "https://play.google.com/redeem?code=FRIEND-2026",
            promoCodeRedeemUrl(" friend-2026 "),
        )
    }

    @Test
    fun rejectsBlankOrUnsafeCode() {
        assertNull(promoCodeRedeemUrl(""))
        assertNull(promoCodeRedeemUrl("abc"))
        assertNull(promoCodeRedeemUrl("FRIEND_CODE"))
        assertNull(promoCodeRedeemUrl("FRIEND?2026"))
        assertNull(promoCodeRedeemUrl("친구-2026"))
    }
}
