package com.example.util.simpletimetracker.navigation.params.screen

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecordQuickActionsParams(
    val type: Type,
    val preview: Preview,
    val from: From = From.Other,
) : ScreenParams, Parcelable {

    sealed interface Type : Parcelable {

        @Parcelize
        data class RecordTracked(
            val id: Long,
        ) : Type

        @Parcelize
        data class RecordUntracked(
            val timeStarted: Long,
            val timeEnded: Long,
        ) : Type

        @Parcelize
        data class RecordRunning(
            val id: Long,
        ) : Type
    }

    @Parcelize
    data class Preview(
        val name: String,
        val iconId: RecordTypeIconParams,
        val color: Int,
    ) : Parcelable

    /**
     * Where the popup was opened from. The records calendar reuses "move" to
     * enter its own drag edit mode, everywhere else keeps the date time dialog.
     */
    sealed class From : Parcelable {
        @Parcelize
        object RecordsCalendar : From()

        @Parcelize
        object Other : From()
    }

    companion object {
        val Empty = RecordQuickActionsParams(
            type = Type.RecordTracked(0),
            preview = Preview(
                name = "",
                iconId = RecordTypeIconParams.Text(""),
                color = 0,
            ),
            from = From.Other,
        )
    }
}