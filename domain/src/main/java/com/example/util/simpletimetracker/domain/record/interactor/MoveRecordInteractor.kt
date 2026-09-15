package com.example.util.simpletimetracker.domain.record.interactor

import javax.inject.Inject

/**
 * Overwrites the time range of an already tracked record.
 *
 * Used by the calendar drag edit mode: once the user confirms the previewed
 * range, the record keeps its id, activity, comment and tags and only its
 * times are replaced. The write goes through [AddRecordMediator], so widgets,
 * the notification switch and the external views are refreshed exactly like
 * after any other record change.
 */
class MoveRecordInteractor @Inject constructor(
    private val recordInteractor: RecordInteractor,
    private val addRecordMediator: AddRecordMediator,
) {

    suspend fun setTime(
        recordId: Long,
        timeStarted: Long,
        timeEnded: Long,
    ): TimeChange? {
        val record = recordInteractor.get(recordId) ?: return null
        if (record.timeStarted == timeStarted && record.timeEnded == timeEnded) return null
        val change = TimeChange(
            recordId = recordId,
            previousTimeStarted = record.timeStarted,
            previousTimeEnded = record.timeEnded,
            newTimeStarted = timeStarted,
            newTimeEnded = timeEnded,
        )
        addRecordMediator.add(
            record.copy(
                timeStarted = timeStarted,
                timeEnded = timeEnded,
            ),
        )
        return change
    }

    /**
     * Reverts only the exact change represented by [change]. If the record was
     * edited again while the Snackbar was visible, the newer value wins.
     */
    suspend fun undo(change: TimeChange): Boolean {
        val record = recordInteractor.get(change.recordId) ?: return false
        if (record.timeStarted != change.newTimeStarted ||
            record.timeEnded != change.newTimeEnded
        ) {
            return false
        }
        addRecordMediator.add(
            record.copy(
                timeStarted = change.previousTimeStarted,
                timeEnded = change.previousTimeEnded,
            ),
        )
        return true
    }

    data class TimeChange(
        val recordId: Long,
        val previousTimeStarted: Long,
        val previousTimeEnded: Long,
        val newTimeStarted: Long,
        val newTimeEnded: Long,
    )
}
