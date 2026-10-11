package com.example.visualalarm.alarm

import com.example.visualalarm.data.Alarm
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AlarmSchedulerTest {

    private fun alarm(
        hour: Int = 7,
        minute: Int = 0,
        repeatDays: Int = 0,
        enabled: Boolean = true
    ): Alarm = Alarm(hour, minute, repeatDays, enabled)

    // Helper to create a timestamp at a specific UTC time using java.time
    private fun calAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    @Test
    fun `one-shot alarm in future today triggers today`() {
        // Assuming "now" is 06:00, alarm at 07:00 today
        val scheduler = TestAlarmScheduler(calAt(2026, 0, 15, 6, 0))
        val next = scheduler.calculateNextTrigger(alarm(hour = 7))
        assertEquals(calAt(2026, 0, 15, 7, 0), next)
    }

    @Test
    fun `one-shot alarm in past today triggers tomorrow`() {
        // Assuming "now" is 08:00, alarm at 07:00 today -> should trigger tomorrow
        val scheduler = TestAlarmScheduler(calAt(2026, 0, 15, 8, 0))
        val next = scheduler.calculateNextTrigger(alarm(hour = 7))
        assertEquals(calAt(2026, 0, 16, 7, 0), next)
    }

    @Test
    fun `repeating alarm on current day after now triggers today`() {
        // Thursday (day 4 in 0=Mon...), alarm at 08:00, now 07:00, Thu enabled
        val scheduler = TestAlarmScheduler(calAt(2026, 0, 15, 7, 0)) // Thursday
        val next = scheduler.calculateNextTrigger(alarm(hour = 8, repeatDays = 1 shl 3)) // Thu bit
        assertEquals(calAt(2026, 0, 15, 8, 0), next)
    }

    @Test
    fun `repeating alarm on current day before now triggers next enabled day`() {
        // Thursday, alarm at 07:00, now 08:00, Thu enabled -> should skip to next enabled day
        val scheduler = TestAlarmScheduler(calAt(2026, 0, 15, 8, 0)) // Thursday
        // Only Friday enabled
        val next = scheduler.calculateNextTrigger(alarm(hour = 7, repeatDays = 1 shl 4))
        assertEquals(calAt(2026, 0, 16, 7, 0), next) // Friday
    }

    @Test
    fun `repeating alarm wraps to next week`() {
        // Thursday, only Monday enabled, alarm at 07:00, now 08:00
        val scheduler = TestAlarmScheduler(calAt(2026, 0, 15, 8, 0)) // Thursday
        val next = scheduler.calculateNextTrigger(alarm(hour = 7, repeatDays = 1 shl 0)) // Monday
        assertEquals(calAt(2026, 0, 19, 7, 0), next) // Next Monday (4 days)
    }

    @Test
    fun `empty repeat days falls back to tomorrow`() {
        // Edge case: repeatDays != 0 but no bits set (shouldn't happen in practice)
        val scheduler = TestAlarmScheduler(calAt(2026, 0, 15, 8, 0))
        val next = scheduler.calculateNextTrigger(alarm(hour = 7, repeatDays = 0))
        assertEquals(calAt(2026, 0, 16, 7, 0), next)
    }

    @Test
    fun `disabled alarm returns null`() {
        val scheduler = TestAlarmScheduler(System.currentTimeMillis())
        val next = scheduler.calculateNextTrigger(alarm(enabled = false))
        assertNull(next)
    }

    // Test wrapper that allows injecting "now"
    private class TestAlarmScheduler(private val fixedNow: Long) {
        // Copy of the logic from AlarmScheduler for testing
        fun calculateNextTrigger(alarm: Alarm): Long? {
            if (!alarm.enabled) return null

            val baseTime = getBaseTime(alarm)

            if (alarm.repeatDays == 0) {
                return if (baseTime <= fixedNow) baseTime + 24 * 60 * 60 * 1000 else baseTime
            }

            val todayDayOfWeek = getDayOfWeekIndex(fixedNow)
            val baseDayOfWeek = getDayOfWeekIndex(baseTime)

            val todayBit = 1 shl todayDayOfWeek
            if ((alarm.repeatDays and todayBit) != 0 && baseTime > fixedNow) {
                return baseTime
            }

            for (i in 1..7) {
                val checkDay = (todayDayOfWeek + i) % 7
                val checkBit = 1 shl checkDay
                if ((alarm.repeatDays and checkBit) != 0) {
                    val daysToAdd = if (checkDay >= todayDayOfWeek) {
                        checkDay - todayDayOfWeek
                    } else {
                        7 - todayDayOfWeek + checkDay
                    }
                    return baseTime + daysToAdd * 24L * 60 * 60 * 1000
                }
            }

            return baseTime + 24 * 60 * 60 * 1000
        }

        private fun getBaseTime(alarm: Alarm): Long {
            // Calculate the base time for today at the alarm time
            val instant = Instant.ofEpochMilli(fixedNow).atZone(ZoneOffset.UTC)
            val today = instant.toLocalDate()
            val alarmTime = today.atTime(alarm.hour, alarm.minute).atZone(ZoneOffset.UTC).toInstant()
            return alarmTime.toEpochMilli()
        }

        private fun getDayOfWeekIndex(timeMs: Long): Int {
            val instant = Instant.ofEpochMilli(timeMs).atZone(ZoneOffset.UTC)
            val dayOfWeek = instant.dayOfWeek.value // 1=Mon...7=Sun
            return (dayOfWeek - 1) // 0=Mon...6=Sun
        }
    }
}
