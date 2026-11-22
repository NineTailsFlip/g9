package dev.g9

import org.junit.Assert.assertEquals
import org.junit.Test

class ScopeTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun trashExpiryInDays() {
        val now = 1_000_000_000_000L
        assertEquals("", trashExpiry(0, now))
        assertEquals("gone today", trashExpiry(now + day / 2, now))
        assertEquals("gone tomorrow", trashExpiry(now + day + 1, now))
        assertEquals("gone in 29 days", trashExpiry(now + 29 * day + 1, now))
    }

    @Test
    fun shareTypeNarrowsToWhatAppsCanTake() {
        assertEquals("image/jpeg", shareType(listOf("image/jpeg")))
        assertEquals("image/*", shareType(listOf("image/jpeg", "image/png")))
        assertEquals("video/*", shareType(listOf("video/mp4", "video/webm")))
        assertEquals("*/*", shareType(listOf("image/jpeg", "video/mp4")))
        assertEquals("*/*", shareType(emptyList()))
    }
}
