package com.trackvoice.ui

import com.trackvoice.data.AnnouncementReadField
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentReadOrderKeyTest {
    @Test
    fun togglingSegmentChangesKeySoLazyRowDoesNotAnchorToMovedChip() {
        val activeKey = contentReadFieldItemKey(AnnouncementReadField.ALBUM, active = true)
        val inactiveKey = contentReadFieldItemKey(AnnouncementReadField.ALBUM, active = false)

        assertEquals("active:ALBUM", activeKey)
        assertEquals("inactive:ALBUM", inactiveKey)
        assertNotEquals(activeKey, inactiveKey)
    }
}
