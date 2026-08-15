package com.trackvoice.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementReadFieldsTest {
    private val albumFields = listOf(
        AnnouncementReadField.ALBUM,
        AnnouncementReadField.TRACK_NUMBER,
        AnnouncementReadField.TITLE,
        AnnouncementReadField.ARTIST,
    )

    @Test
    fun emptyOrInvalidSelectionFallsBackToTheContentDefault() {
        assertEquals(
            albumFields,
            normalizeAnnouncementReadFields(
                fields = emptyList(),
                allowedFields = albumFields,
                fallbackFields = albumFields,
            ),
        )
    }

    @Test
    fun disablingTheLastActiveFieldKeepsOneFieldActive() {
        assertEquals(
            listOf(AnnouncementReadField.TITLE),
            toggleAnnouncementReadField(
                fields = listOf(AnnouncementReadField.TITLE),
                field = AnnouncementReadField.TITLE,
                enabled = false,
                allowedFields = listOf(AnnouncementReadField.TITLE, AnnouncementReadField.ARTIST),
            ),
        )
    }

    @Test
    fun dragReorderingChangesOnlyTheActiveOrder() {
        val fields = listOf(
            AnnouncementReadField.TITLE,
            AnnouncementReadField.ARTIST,
            AnnouncementReadField.ALBUM,
        )

        assertEquals(
            listOf(
                AnnouncementReadField.ALBUM,
                AnnouncementReadField.TITLE,
                AnnouncementReadField.ARTIST,
            ),
            reorderAnnouncementReadField(
                fields = fields,
                field = AnnouncementReadField.ALBUM,
                targetIndex = 0,
                allowedFields = fields,
            ),
        )
    }

    @Test
    fun legacyDefaultSetRecoversTheCanonicalDefaultOrder() {
        assertEquals(
            DEFAULT_ALBUM_READ_FIELDS,
            orderedFieldsFromStorage(
                storedOrder = null,
                legacyFields = DEFAULT_ALBUM_READ_FIELDS.map(AnnouncementReadField::name).toSet(),
                allowedFields = DEFAULT_ALBUM_READ_FIELDS,
                fallbackFields = DEFAULT_ALBUM_READ_FIELDS,
                legacyOrder = AnnouncementOrder.DEFAULT,
            ),
        )
    }
}
