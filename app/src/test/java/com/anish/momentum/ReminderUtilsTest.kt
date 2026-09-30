package com.anish.momentum

import com.anish.momentum.utils.ReminderUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderUtilsTest {

    @Test
    fun `afternoon time uses PM`() {
        assertEquals("2:30 PM", ReminderUtils.formatTo12Hour("14:30"))
    }

    @Test
    fun `midnight is 12 AM`() {
        assertEquals("12:05 AM", ReminderUtils.formatTo12Hour("00:05"))
    }

    @Test
    fun `noon is 12 PM`() {
        assertEquals("12:00 PM", ReminderUtils.formatTo12Hour("12:00"))
    }

    @Test
    fun `morning time uses AM`() {
        assertEquals("9:07 AM", ReminderUtils.formatTo12Hour("09:07"))
    }

    @Test
    fun `garbage in comes back unchanged`() {
        assertEquals("25:99", ReminderUtils.formatTo12Hour("25:99"))
        assertEquals("", ReminderUtils.formatTo12Hour(""))
        assertEquals("nope", ReminderUtils.formatTo12Hour("nope"))
    }
}
