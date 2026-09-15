package com.example.util.simpletimetracker.domain.record.interactor

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross screen one shot signal for the calendar drag edit mode.
 *
 * "Move" in the record quick actions normally opens the date time dialog. When
 * the popup was opened from the records calendar it instead closes and asks the
 * calendar to edit that record right on the timeline.
 */
@Singleton
class RecordsCalendarDragEditInteractor @Inject constructor() {

    val editRequested: SharedFlow<Long> get() = _editRequested.asSharedFlow()

    private val _editRequested = MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    suspend fun requestEdit(recordId: Long) {
        _editRequested.emit(recordId)
    }
}
