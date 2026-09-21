package com.waynejiang.linefeed.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceCardSlotsTest {
    private val slots = ServiceCardSlots(firstAfter = 3, every = 6)

    @Test
    fun `no slot before firstAfter`() {
        assertNull(slots.slotBefore(0))
        assertNull(slots.slotBefore(1))
        assertNull(slots.slotBefore(2))
    }

    @Test
    fun `first slot lands exactly at firstAfter`() {
        assertEquals(0, slots.slotBefore(3))
    }

    @Test
    fun `subsequent slots repeat every N articles`() {
        assertEquals(1, slots.slotBefore(9))
        assertEquals(2, slots.slotBefore(15))
    }

    @Test
    fun `no slot between placements`() {
        assertNull(slots.slotBefore(4))
        assertNull(slots.slotBefore(8))
        assertNull(slots.slotBefore(10))
    }

    @Test
    fun `custom spacing`() {
        val custom = ServiceCardSlots(firstAfter = 0, every = 2)
        assertEquals(0, custom.slotBefore(0))
        assertNull(custom.slotBefore(1))
        assertEquals(1, custom.slotBefore(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `firstAfter must not be negative`() {
        ServiceCardSlots(firstAfter = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `every must be positive`() {
        ServiceCardSlots(every = 0)
    }
}
