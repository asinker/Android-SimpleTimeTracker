package com.example.util.simpletimetracker.feature_records.customView

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RecordsCalendarDragMathTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val minute = TimeUnit.MINUTES.toMillis(1)

    @Test
    fun `time and y round trip in normal and reverse order with zoom and pan`() {
        listOf(false, true).forEach { reverse ->
            listOf(0L, 6 * hour, 12 * hour, 23 * hour + 45 * minute).forEach { time ->
                val y = RecordsCalendarDragMath.timeToY(
                    time = time,
                    chartTop = 24f,
                    chartHeight = 960f,
                    scale = 2.5f,
                    pan = -430f,
                    reverseOrder = reverse,
                )
                val restored = RecordsCalendarDragMath.yToTime(
                    y = y,
                    chartTop = 24f,
                    chartHeight = 960f,
                    scale = 2.5f,
                    pan = -430f,
                    reverseOrder = reverse,
                )
                assertTrue(abs(restored - time) < 100L)
            }
        }
    }

    @Test
    fun `snapping respects shifted start of day`() {
        val shift = 2 * hour
        assertEquals(0L, RecordsCalendarDragMath.snapToStep(7 * minute, shift, 15))
        assertEquals(15 * minute, RecordsCalendarDragMath.snapToStep(8 * minute, shift, 15))
    }

    @Test
    fun `column mapping supports multiple days and clamps outside chart`() {
        assertEquals(0, RecordsCalendarDragMath.columnIndex(-20f, 10f, 100f, 3))
        assertEquals(0, RecordsCalendarDragMath.columnIndex(50f, 10f, 100f, 3))
        assertEquals(1, RecordsCalendarDragMath.columnIndex(150f, 10f, 100f, 3))
        assertEquals(2, RecordsCalendarDragMath.columnIndex(999f, 10f, 100f, 3))
    }

    @Test
    fun `moving keeps duration and clamps to day edges`() {
        val resultAtStart = RecordsCalendarDragMath.moveRange(
            grabbedStart = 8 * hour,
            grabbedEnd = 10 * hour,
            anchor = 9 * hour,
            current = 0L,
        )
        assertEquals(0L, resultAtStart.start)
        assertEquals(2 * hour, resultAtStart.end)

        val resultAtEnd = RecordsCalendarDragMath.moveRange(
            grabbedStart = 8 * hour,
            grabbedEnd = 10 * hour,
            anchor = 9 * hour,
            current = 24 * hour,
        )
        assertEquals(22 * hour, resultAtEnd.start)
        assertEquals(24 * hour, resultAtEnd.end)
    }

    @Test
    fun `edge auto scroll accelerates toward viewport edges`() {
        assertEquals(0f, RecordsCalendarDragMath.autoScrollDelta(500f, 0f, 1000f, 100f, 12f))
        assertEquals(6f, RecordsCalendarDragMath.autoScrollDelta(50f, 0f, 1000f, 100f, 12f))
        assertEquals(-6f, RecordsCalendarDragMath.autoScrollDelta(950f, 0f, 1000f, 100f, 12f))
        assertEquals(12f, RecordsCalendarDragMath.autoScrollDelta(-20f, 0f, 1000f, 100f, 12f))
    }

    @Test
    fun `absolute mapping preserves wall clock and duration on spring DST day`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val (start, end) = dayRange(zone, 2025, Calendar.MARCH, 9)
        assertEquals(23 * hour, end - start)

        val result = RecordsCalendarDragMath.toAbsoluteRange(start, end, 10 * hour, 11 * hour, zone)
        assertLocalTime(zone, result.start, Calendar.MARCH, 9, 10)
        assertEquals(hour, result.end - result.start)
    }

    @Test
    fun `absolute mapping preserves wall clock and duration on fall DST day`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val (start, end) = dayRange(zone, 2025, Calendar.NOVEMBER, 2)
        assertEquals(25 * hour, end - start)

        val result = RecordsCalendarDragMath.toAbsoluteRange(start, end, 10 * hour, 11 * hour, zone)
        assertLocalTime(zone, result.start, Calendar.NOVEMBER, 2, 10)
        assertEquals(hour, result.end - result.start)
    }

    @Test
    fun `absolute mapping supports shifted day start and adjacent date`() {
        val zone = TimeZone.getTimeZone("Asia/Hong_Kong")
        val (firstStart, firstEnd) = dayRange(zone, 2026, Calendar.SEPTEMBER, 15, startHour = 2)
        val (nextStart, nextEnd) = dayRange(zone, 2026, Calendar.SEPTEMBER, 16, startHour = 2)

        val lateFirstDay = RecordsCalendarDragMath.toAbsoluteRange(
            firstStart,
            firstEnd,
            23 * hour,
            24 * hour,
            zone,
        )
        assertLocalTime(zone, lateFirstDay.start, Calendar.SEPTEMBER, 16, 1)

        val movedToNextColumn = RecordsCalendarDragMath.toAbsoluteRange(
            nextStart,
            nextEnd,
            8 * hour,
            9 * hour,
            zone,
        )
        assertLocalTime(zone, movedToNextColumn.start, Calendar.SEPTEMBER, 16, 10)
    }

    private fun dayRange(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        startHour: Int = 0,
    ): Pair<Long, Long> {
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, startHour, 0, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DATE, 1)
        return start to calendar.timeInMillis
    }

    private fun assertLocalTime(
        zone: TimeZone,
        timestamp: Long,
        expectedMonth: Int,
        expectedDay: Int,
        expectedHour: Int,
    ) {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
        assertEquals(expectedMonth, calendar.get(Calendar.MONTH))
        assertEquals(expectedDay, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(expectedHour, calendar.get(Calendar.HOUR_OF_DAY))
    }
}
