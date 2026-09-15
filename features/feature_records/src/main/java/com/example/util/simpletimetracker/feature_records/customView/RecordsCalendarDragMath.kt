package com.example.util.simpletimetracker.feature_records.customView

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Pure calculations used by calendar dragging and covered by JVM tests. */
internal object RecordsCalendarDragMath {

    val dayInMillis: Long = TimeUnit.DAYS.toMillis(1)
    private val minuteInMillis: Long = TimeUnit.MINUTES.toMillis(1)
    private const val MINUTES_PER_DAY: Long = 24L * 60L

    fun timeToY(
        time: Long,
        chartTop: Float,
        chartHeight: Float,
        scale: Float,
        pan: Float,
        reverseOrder: Boolean,
    ): Float {
        if (chartHeight <= 0f) return chartTop + pan
        val fraction = (time.toFloat() / dayInMillis).coerceIn(0f, 1f)
        val offset = if (reverseOrder) chartHeight * fraction else chartHeight * (1f - fraction)
        return chartTop + pan + offset * scale
    }

    fun yToTime(
        y: Float,
        chartTop: Float,
        chartHeight: Float,
        scale: Float,
        pan: Float,
        reverseOrder: Boolean,
    ): Long {
        if (chartHeight <= 0f || scale <= 0f) return 0L
        val visibleTop = chartTop + pan
        val visibleBottom = visibleTop + chartHeight * scale
        val chartY = y.coerceIn(visibleTop, visibleBottom)
        val offset = (chartY - chartTop - pan) / scale
        val fraction = if (reverseOrder) offset / chartHeight else (chartHeight - offset) / chartHeight
        return (fraction * dayInMillis).toLong().coerceIn(0L, dayInMillis)
    }

    fun snapToStep(
        timeMillis: Long,
        startOfDayShift: Long,
        stepMinutes: Int,
    ): Long {
        val step = TimeUnit.MINUTES.toMillis(stepMinutes.toLong())
        if (step <= 0L) return timeMillis.coerceIn(0L, dayInMillis)
        val shifted = timeMillis + startOfDayShift
        val remainder = shifted.mod(step)
        val rounded = if (remainder * 2 >= step) shifted - remainder + step else shifted - remainder
        return (rounded - startOfDayShift).coerceIn(0L, dayInMillis)
    }

    /**
     * Magnetically snaps a boundary to the closest record end while it is
     * within [thresholdMillis]. Targets outside the allowed range are ignored.
     */
    fun snapToRecordEnd(
        timeMillis: Long,
        recordEnds: Iterable<Long>,
        thresholdMillis: Long,
        minimumValue: Long,
        maximumValue: Long,
    ): Long {
        val safeMinimum = minimumValue.coerceAtMost(maximumValue)
        val safeMaximum = maximumValue.coerceAtLeast(minimumValue)
        val time = timeMillis.coerceIn(safeMinimum, safeMaximum)
        if (thresholdMillis < 0L) return time

        return recordEnds
            .asSequence()
            .filter { it in safeMinimum..safeMaximum }
            .map { target -> target to abs(target - time) }
            .filter { (_, distance) -> distance <= thresholdMillis }
            .minWithOrNull(compareBy<Pair<Long, Long>> { it.second }.thenBy { it.first })
            ?.first
            ?: time
    }

    fun columnIndex(
        x: Float,
        chartLeft: Float,
        columnWidth: Float,
        columnCount: Int,
    ): Int {
        if (columnWidth <= 0f || columnCount <= 1) return 0
        return ((x - chartLeft) / columnWidth).toInt().coerceIn(0, columnCount - 1)
    }

    fun moveRange(
        grabbedStart: Long,
        grabbedEnd: Long,
        anchor: Long,
        current: Long,
    ): OffsetRange {
        val duration = (grabbedEnd - grabbedStart).coerceIn(0L, dayInMillis)
        val start = (grabbedStart + current - anchor).coerceIn(0L, dayInMillis - duration)
        return OffsetRange(start = start, end = start + duration)
    }

    /**
     * Returns a per-frame pan delta. Positive values reveal content above the
     * viewport, negative values reveal content below it.
     */
    fun autoScrollDelta(
        pointerY: Float,
        viewportTop: Float,
        viewportBottom: Float,
        edgeSize: Float,
        maxStep: Float,
    ): Float {
        if (edgeSize <= 0f || maxStep <= 0f) return 0f
        return when {
            pointerY < viewportTop + edgeSize -> {
                val strength = ((viewportTop + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
                maxStep * strength
            }
            pointerY > viewportBottom - edgeSize -> {
                val strength = ((pointerY - (viewportBottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
                -maxStep * strength
            }
            else -> 0f
        }
    }

    /**
     * Maps a displayed wall-clock offset to an absolute timestamp. Calendar
     * fields are used instead of adding milliseconds so 10:00 remains 10:00
     * on 23- and 25-hour daylight-saving days.
     */
    fun wallClockOffsetToTimestamp(
        rangeStart: Long,
        offset: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val safeOffset = offset.coerceIn(0L, dayInMillis)
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = rangeStart }
        val baseMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60L + calendar.get(Calendar.MINUTE)
        val totalMinutes = baseMinuteOfDay + safeOffset / minuteInMillis
        val dayOffset = totalMinutes / MINUTES_PER_DAY
        val minuteOfDay = totalMinutes % MINUTES_PER_DAY
        val remainder = safeOffset % minuteInMillis

        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DATE, dayOffset.toInt())
        calendar.set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60L).toInt())
        calendar.set(Calendar.MINUTE, (minuteOfDay % 60L).toInt())
        return calendar.timeInMillis + remainder
    }

    /** Keeps the displayed duration while clamping the result to the target day. */
    fun toAbsoluteRange(
        rangeStart: Long,
        rangeEnd: Long,
        startOffset: Long,
        endOffset: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): AbsoluteRange {
        val available = (rangeEnd - rangeStart).coerceAtLeast(0L)
        val duration = (endOffset - startOffset).coerceIn(0L, available)
        val candidate = wallClockOffsetToTimestamp(rangeStart, startOffset, timeZone)
        val start = candidate.coerceIn(rangeStart, rangeEnd - duration)
        return AbsoluteRange(start = start, end = start + duration)
    }

    data class OffsetRange(val start: Long, val end: Long)
    data class AbsoluteRange(val start: Long, val end: Long)
}
