package com.example.util.simpletimetracker.feature_records.customView

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withTranslation
import com.example.util.simpletimetracker.core.utils.CalendarIntersectionCalculator
import com.example.util.simpletimetracker.domain.extension.orZero
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.record.RecordViewData
import com.example.util.simpletimetracker.feature_base_adapter.runningRecord.RunningRecordViewData
import com.example.util.simpletimetracker.feature_records.R
import com.example.util.simpletimetracker.feature_views.ColorUtils
import com.example.util.simpletimetracker.feature_views.IconView
import com.example.util.simpletimetracker.feature_views.ScaleDetector
import com.example.util.simpletimetracker.feature_views.SingleTapDetector
import com.example.util.simpletimetracker.feature_views.SwipeDetector
import com.example.util.simpletimetracker.feature_views.extension.dpToPx
import com.example.util.simpletimetracker.feature_views.extension.getBitmapFromView
import com.example.util.simpletimetracker.feature_views.extension.measureExactly
import com.example.util.simpletimetracker.feature_views.extension.setForegroundSpan
import com.example.util.simpletimetracker.feature_views.extension.toSpannableString
import com.example.util.simpletimetracker.feature_views.isHorizontal
import com.example.util.simpletimetracker.feature_views.viewData.RecordTypeIcon
import kotlinx.parcelize.Parcelize
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RecordsCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(
    context,
    attrs,
    defStyleAttr,
) {

    // Attrs
    private var nameTextSize: Float = 0f
    private var nameTextColor: Int = 0
    private var itemTagColor: Int = 0
    private var dragPreviewTextColor: Int = 0
    private var legendTextSize: Float = 0f
    private var legendTextColor: Int = 0
    private var legendLineColor: Int = 0
    private var legendLineSecondaryColor: Int = 0
    private var currentTimeLegendColor: Int = 0
    private var currentTimeLegendWidth: Float = 0f
    private var iconMaxSize: Int = 0
    private var reverseOrder: Boolean = false
    private var recordsCount: Int = 0
    private var columnsCount: Int = 0
    // End of attrs

    private var isScaling: Boolean = false
    private var isSwiping: Boolean = false
    private var scaleFactor: Float = 1f
    private var lastScaleFactor: Float = 1f
    private var panFactor: Float = 0f
    private var lastPanFactor: Float = 0f
    private var swipeStartOffset: Float = 0f
    private var swipeStartPanFactor: Float = 0f
    private var shouldRebaseSwipeAfterScaleStop: Boolean = false
    private var legendTextWidth: Float = 0f
    private var legendTextHeight: Float = 0f
    private var legendTopTextHeight: Float = 0f
    private var legendMinutesTextHeight: Float = 0f
    private var chartLeftBound: Float = 0f
    private var chartRightBound: Float = 0f
    private var chartTopBound: Float = 0f
    private var chartBottomBound: Float = 0f
    private var chartHeight: Float = 0f
    private var columnWidth: Float = 0f
    private val legendTextPadding: Float = 2.dpToPx().toFloat()
    private val legendTopTextPadding: Float = 4.dpToPx().toFloat()
    private val legendMinutesTextPadding: Float = 4.dpToPx().toFloat()
    private val recordCornerRadius: Float = 8.dpToPx().toFloat()
    private val recordVerticalPadding: Float = 2.dpToPx().toFloat()
    private val recordHorizontalPadding: Float = 4.dpToPx().toFloat()
    private val paddingBetweenDays: Float = 1.dpToPx().toFloat()
    private val multiSelectedRecordIndicatorWidth: Float = 8.dpToPx().toFloat()
    private val dayInMillis = TimeUnit.DAYS.toMillis(1)
    private val hourInMillis = TimeUnit.HOURS.toMillis(1)
    private var selectedRecord: RecordsCalendarViewData.Point.Data? = null
    private var selectedRecordColor: Int = 0
    private var isMilitary: Boolean = false

    // Hour number to full hour text, ex. 03 to 03:00 / 03 to 03 am
    private var hours: List<Pair<String, String>> = emptyList()

    private val recordPaint: Paint = Paint()
    private val legendTextPaint: Paint = Paint()
    private val legendTopTextPaint: Paint = Paint()
    private val legendMinutesTextPaint: Paint = Paint()
    private val linePaint: Paint = Paint()
    private val lineSecondaryPaint: Paint = Paint()
    private val currentTimelinePaint: Paint = Paint()

    private val bounds: Rect = Rect(0, 0, 0, 0)
    private val textBounds: Rect = Rect(0, 0, 0, 0)
    private val recordBounds: RectF = RectF(0f, 0f, 0f, 0f)
    private var data: List<Column> = emptyList()
    private val dataSize: Int get() = data.size.takeUnless { it == 0 } ?: 1
    private var shouldDrawTopLegends: Boolean = false
    private var currentTime: Long? = null
    private var startOfDayShift: Long = 0
    private val iconView: IconView = IconView(ContextThemeWrapper(context, R.style.AppTheme))
    private var clickListener: (ViewHolderType) -> Unit = {}
    private var longClickListener: (ViewHolderType) -> Unit = {}

    // Called when the user long pressed an empty calendar area and dragged out
    // a new time range. Both values are absolute timestamps, already resolved
    // to the day the drag happened on (including the calendar column, when
    // several days are shown at once).
    var onNewRecordSelectedListener: ((startTime: Long, endTime: Long) -> Unit)? = null

    // Called when the user confirms a new range for an existing record by
    // tapping anywhere after previewing the adjustment. Both values are
    // absolute timestamps of the day the record is shown on.
    var onRecordTimeAdjustedListener: ((recordId: Long, startTime: Long, endTime: Long) -> Unit)? = null

    // Drag on an empty area to create a new record.
    private var dragState: DragState = DragState.IDLE

    // Millis elapsed since the start of the day (0..dayInMillis), the same
    // units RecordsCalendarViewData.Point.start / Point.end use.
    // While editing they hold the previewed range.
    private var dragStartTime: Long = 0L
    private var dragEndTime: Long = 0L

    // Time of the long press, kept fixed while the other end follows the finger.
    private var dragAnchorTime: Long = 0L
    private var dragColumnIndex: Int = 0

    // Last snapped grid step, used to fire a haptic tick only on step changes.
    private var dragLastSnapTime: Long = 0L

    // Edit mode, entered through the "move" record quick action. Everything is
    // keyed by record id and never by an object reference, because setData()
    // rebuilds all Data every second on the "today" page.
    private var editRecordId: Long? = null

    // Last confirmed range of the edited record: the pending changes are the
    // difference between it and dragStartTime / dragEndTime.
    private var editSavedStart: Long = 0L
    private var editSavedEnd: Long = 0L
    private var editSavedColumnIndex: Int = 0

    // Previewed range captured on ACTION_DOWN, used as the drag baseline and
    // to roll the gesture back on ACTION_CANCEL.
    private var editGrabStart: Long = 0L
    private var editGrabEnd: Long = 0L
    private var editGrabX: Float = 0f
    private var editGrabY: Float = 0f
    private var isEditDragMoved: Boolean = false
    private var editDragStartedByLongPress: Boolean = false

    // A tap that grabbed the edited block must not also be reported as a
    // regular record click, otherwise editing would open the record screen.
    private var suppressTapForGesture: Boolean = false
    private val editHandleTouchSize: Float = 24.dpToPx().toFloat()
    private val editHandleHeight: Float = 6.dpToPx().toFloat()
    private val editHandleMinWidth: Float = 40.dpToPx().toFloat()
    private val editOutlineStrokeWidth: Float = 2.dpToPx().toFloat()
    private val editDragTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val editHandleBounds: RectF = RectF(0f, 0f, 0f, 0f)
    private val editHandlePaint: Paint = Paint()
    private val editOutlinePaint: Paint = Paint()
    private val editSelectionHaloPaint: Paint = Paint()
    private var editSelectionScale: Float = 1f
    private var editSelectionAnimator: ValueAnimator? = null

    private val edgeAutoScrollSize: Float = 48.dpToPx().toFloat()
    private val edgeAutoScrollMaxStep: Float = 10.dpToPx().toFloat()
    private var dragPointerX: Float = 0f
    private var dragPointerY: Float = 0f
    private var isEdgeAutoScrollRunning: Boolean = false
    private val edgeAutoScrollRunnable = object : Runnable {
        override fun run() = runEdgeAutoScrollFrame()
    }

    private val dragPreviewPaint: Paint = Paint()
    private val dragPreviewStrokePaint: Paint = Paint()
    private val dragPreviewTextPaint: Paint = Paint()
    private val dragPreviewStrokeWidth: Float = 2.dpToPx().toFloat()
    private val dragPreviewTextPadding: Float = 4.dpToPx().toFloat()
    private var dragPreviewLabel: String = ""

    // Read once during construction, so the drag gesture itself never has to
    // touch Context / Resources while the finger is moving.
    private val amPmTemplate: String = context.getString(R.string.separator_template)
    private val minuteInMillis: Long = TimeUnit.MINUTES.toMillis(1)
    private val minDragDurationInMillis: Long = TimeUnit.MINUTES.toMillis(15)
    private val adjacentRecordSnapThresholdInMillis: Long =
        TimeUnit.MINUTES.toMillis(ADJACENT_RECORD_SNAP_THRESHOLD_MINUTES)

    // True while the user is inside edit mode, with or without a finger down.
    private val isEditing: Boolean
        get() = dragState == DragState.EDIT_IDLE ||
            dragState == DragState.EDIT_DRAGGING_START ||
            dragState == DragState.EDIT_DRAGGING_END ||
            dragState == DragState.EDIT_DRAGGING_MOVE

    // True when the previewed range differs from the last confirmed one, which
    // is what the translucent "not saved yet" look is based on.
    private val hasPendingEdit: Boolean
        get() {
            val isRangeChanged = dragStartTime != editSavedStart ||
                dragEndTime != editSavedEnd ||
                dragColumnIndex != editSavedColumnIndex
            return editRecordId != null && isRangeChanged
        }

    private val nameTextView: AppCompatTextView by lazy {
        getTextView(
            textColor = nameTextColor,
            typeface = Typeface.DEFAULT_BOLD,
            widthLayoutParams = ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val durationTextView: AppCompatTextView by lazy {
        getTextView(
            textColor = itemTagColor,
            typeface = Typeface.DEFAULT_BOLD,
            widthLayoutParams = ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val timeTextView: AppCompatTextView by lazy {
        getTextView(
            textColor = itemTagColor,
            typeface = Typeface.DEFAULT,
            widthLayoutParams = ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val commentTextView: AppCompatTextView by lazy {
        getTextView(
            textColor = itemTagColor,
            typeface = Typeface.DEFAULT,
            widthLayoutParams = ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private val availableMinutesRanges: List<List<Int>> = listOf(
        (0..60).step(1),
        (0..60).step(5),
        (0..60).step(10),
        (0..60).step(15),
        (0..60).step(20),
        (0..60).step(30),
    ).map {
        it.toList().drop(1).dropLast(1)
    }

    private val singleTapDetector = SingleTapDetector(
        context = context,
        onSingleTap = ::onEventClick,
        onLongPress = ::onEventLongPress,
    )
    private val scaleDetector = ScaleDetector(
        context = context,
        onScaleStart = ::onEventScaleStart,
        onScaleChanged = ::onEventScaleChanged,
        onScaleStop = ::onEventScaleStop,
    )
    private val swipeDetector = SwipeDetector(
        context = context,
        onSlideStart = ::onEventSwipeStart,
        onSlide = ::onEventSwipe,
        onSlideStop = ::onEventSwipeStop,
    )

    init {
        initArgs(context, attrs, defStyleAttr)
        initPaint()
        initEditMode()
    }

    override fun onDetachedFromWindow() {
        // Never leave a half finished drag behind: it would keep the parent
        // from receiving touches and hold on to stale state.
        cancelDragCreate()
        exitEditMode()
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(
            superSavedState = superState,
            scaleFactor = scaleFactor,
            lastScaleFactor = lastScaleFactor,
            panFactor = panFactor,
            lastPanFactor = lastPanFactor,
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val savedState = state as? SavedState
        super.onRestoreInstanceState(savedState?.superSavedState ?: state)
        scaleFactor = savedState?.scaleFactor ?: 1f
        lastScaleFactor = savedState?.lastScaleFactor ?: 1f
        panFactor = savedState?.panFactor.orZero()
        lastPanFactor = savedState?.lastPanFactor.orZero()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(0, widthMeasureSpec)
        val h = resolveSize(w, heightMeasureSpec)

        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()

        calculateDimensions(w, h)
        drawTopLegend(canvas)
        drawSideLegend(canvas)
        data.forEachIndexed { index, column ->
            drawData(
                canvas = canvas,
                data = column.data,
                index = index,
            )
        }
        // Draw the draft being created on top of the existing blocks.
        if (dragState == DragState.DRAGGING_NEW) {
            drawNewRecordPreview(canvas)
        }
        // Edit mode overlay, drawn on top of everything else.
        if (isEditing) {
            drawEditOverlay(canvas)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchDown(event)
                handled = true
            }

            MotionEvent.ACTION_MOVE -> {
                when (dragState) {
                    DragState.DRAGGING_NEW -> {
                        onDragCreateMove(event)
                        handled = true
                    }
                    DragState.EDIT_DRAGGING_START,
                    DragState.EDIT_DRAGGING_END,
                    DragState.EDIT_DRAGGING_MOVE,
                    -> {
                        onEditDragMove(event)
                        handled = true
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                when (dragState) {
                    DragState.DRAGGING_NEW -> {
                        onDragCreateFinish()
                        handled = true
                    }
                    DragState.EDIT_DRAGGING_START,
                    DragState.EDIT_DRAGGING_END,
                    DragState.EDIT_DRAGGING_MOVE,
                    -> {
                        onEditDragFinish()
                        handled = true
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                when (dragState) {
                    DragState.DRAGGING_NEW -> {
                        cancelDragCreate()
                        handled = true
                    }
                    DragState.EDIT_DRAGGING_START,
                    DragState.EDIT_DRAGGING_END,
                    DragState.EDIT_DRAGGING_MOVE,
                    -> {
                        cancelEditDrag()
                        handled = true
                    }
                    else -> {}
                }
            }
        }

        return handled or
            singleTapDetector.onTouchEvent(event) or
            swipeDetector.onTouchEvent(event) or
            scaleDetector.onTouchEvent(event)
    }

    fun setClickListener(listener: (ViewHolderType) -> Unit) {
        this.clickListener = listener
    }

    fun setLongClickListener(listener: (ViewHolderType) -> Unit) {
        this.longClickListener = listener
    }

    fun isCreatingNewRecord(): Boolean = dragState == DragState.DRAGGING_NEW

    fun setData(viewData: RecordsCalendarViewData) {
        currentTime = viewData.currentTime
        startOfDayShift = viewData.startOfDayShift
        reverseOrder = viewData.reverseOrder
        shouldDrawTopLegends = viewData.shouldDrawTopLegends
        isMilitary = viewData.isMilitary
        data = viewData.points.map(::processData)
        calculateHoursData()
        syncEditMode()
        invalidate()
    }

    fun reset() {
        scaleFactor = 1f
        lastScaleFactor = 1f
        panFactor = 0f
        lastPanFactor = 0f
        resetDragState()
        invalidate()
    }

    /**
     * Enters edit mode for the given record: the block gets a highlighted
     * outline and a handle on both ends, which can be dragged to change the
     * time range. Nothing is saved until the user taps anywhere to finish.
     *
     * Returns false when the record cannot be edited right here, which is the
     * case when it is not visible on the currently shown days or when the day
     * boundary cuts it: only the visible part of a clipped record is known
     * here, so moving it would silently shorten it.
     */
    fun enterEditMode(recordId: Long): Boolean {
        val (index, target) = findEditTarget(recordId) ?: return false
        if (chartHeight <= 0f) return false
        if (isRecordClipped(index = index, target = target)) return false

        editRecordId = recordId
        editSavedStart = target.point.start
        editSavedEnd = target.point.end
        editSavedColumnIndex = index
        editGrabStart = editSavedStart
        editGrabEnd = editSavedEnd
        dragStartTime = editSavedStart
        dragEndTime = editSavedEnd
        dragAnchorTime = 0L
        dragLastSnapTime = dragStartTime
        dragColumnIndex = index
        suppressTapForGesture = false
        dragState = DragState.EDIT_IDLE
        updateDragPreviewLabel()
        animateEditSelection()
        invalidate()
        return true
    }

    /**
     * Leaves edit mode and drops unsaved changes. This remains an explicit
     * programmatic cancellation path; normal taps commit before leaving.
     */
    fun exitEditMode() {
        if (!isEditing) return
        resetDragState()
        invalidate()
    }

    fun isEditingRecord(): Boolean = isEditing

    /**
     * Re-syncs edit mode with freshly built data. Everything is looked up by
     * record id, so the "today" page rebuilding its data every second can never
     * leave the handles pointing at a stale block; when the record is gone,
     * edit mode ends.
     */
    private fun syncEditMode() {
        val recordId = editRecordId ?: return
        if (!isEditing) return

        val (index, target) = findEditTarget(recordId) ?: run {
            resetDragState()
            return
        }
        // With a pending change the preview owns the times: a background
        // refresh must not undo the user's unsaved drag.
        if (dragState != DragState.EDIT_IDLE || hasPendingEdit) return

        dragColumnIndex = index
        editSavedColumnIndex = index

        val start = target.point.start
        val end = target.point.end
        if (start == editSavedStart && end == editSavedEnd) return
        editSavedStart = start
        editSavedEnd = end
        editGrabStart = start
        editGrabEnd = end
        dragStartTime = start
        dragEndTime = end
        dragLastSnapTime = start
        updateDragPreviewLabel()
    }

    /**
     * Finds the record on the shown columns, returning its column index and the
     * laid out block. Only stored records are considered: untracked and running
     * entries have no id to write back to.
     */
    private fun findEditTarget(recordId: Long): Pair<Int, Data>? {
        data.forEachIndexed { index, column ->
            column.data.firstOrNull {
                (it.point.data.value as? RecordViewData.Tracked)?.id == recordId
            }?.let { return index to it }
        }
        return null
    }

    /**
     * A record starting before this day or ending after it only shows its
     * visible part, so its real range is unknown here.
     */
    private fun isRecordClipped(
        index: Int,
        target: Data,
    ): Boolean {
        val column = data.getOrNull(index) ?: return true
        val value = target.point.data.value as? RecordViewData.Tracked ?: return true
        return value.timeStartedTimestamp < column.rangeStart ||
            value.timeEndedTimestamp > column.rangeEnd
    }

    fun getScaleState(): ScaleState {
        return ScaleState(
            scaleFactor = scaleFactor,
            panFactor = panFactor,
        )
    }

    fun setScaleState(state: ScaleState) {
        scaleFactor = state.scaleFactor
        lastScaleFactor = state.scaleFactor
        panFactor = state.panFactor
        lastPanFactor = state.panFactor
        invalidate()
    }

    private fun initArgs(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) {
        context
            .withStyledAttributes(attrs, R.styleable.RecordsCalendarView, defStyleAttr, 0) {
                nameTextSize =
                    getDimensionPixelSize(R.styleable.RecordsCalendarView_calendarTextSize, 14).toFloat()
                nameTextColor =
                    getColor(R.styleable.RecordsCalendarView_calendarTextColor, Color.WHITE)
                itemTagColor =
                    getColor(R.styleable.RecordsCalendarView_calendarTagColor, Color.WHITE)
                dragPreviewTextColor =
                    getColor(R.styleable.RecordsCalendarView_calendarDragPreviewTextColor, nameTextColor)
                legendTextSize =
                    getDimensionPixelSize(R.styleable.RecordsCalendarView_calendarLegendTextSize, 14).toFloat()
                legendTextColor =
                    getColor(R.styleable.RecordsCalendarView_calendarLegendTextColor, Color.BLACK)
                legendLineColor =
                    getColor(R.styleable.RecordsCalendarView_calendarLegendLineColor, Color.BLACK)
                legendLineSecondaryColor =
                    getColor(R.styleable.RecordsCalendarView_calendarLegendLineSecondaryColor, Color.BLACK)
                currentTimeLegendColor =
                    getColor(R.styleable.RecordsCalendarView_calendarCurrentTimeLegendColor, Color.RED)
                currentTimeLegendWidth =
                    getDimensionPixelSize(R.styleable.RecordsCalendarView_calendarCurrentTimeLegendWidth, 0).toFloat()
                iconMaxSize =
                    getDimensionPixelSize(R.styleable.RecordsCalendarView_calendarIconMaxSize, 0)

                if (hasValue(R.styleable.RecordsCalendarView_calendarReverseOrder)) {
                    reverseOrder = getBoolean(R.styleable.RecordsCalendarView_calendarReverseOrder, false)
                }

                if (hasValue(R.styleable.RecordsCalendarView_calendarRecordsCount)) {
                    recordsCount = getInt(R.styleable.RecordsCalendarView_calendarRecordsCount, 0)
                }

                if (hasValue(R.styleable.RecordsCalendarView_calendarColumnsCount)) {
                    columnsCount = getInt(R.styleable.RecordsCalendarView_calendarColumnsCount, 0)
                }
            }
    }

    private fun initPaint() {
        recordPaint.apply {
            isAntiAlias = true
        }
        legendTextPaint.apply {
            isAntiAlias = true
            color = legendTextColor
            textSize = legendTextSize
        }
        legendTopTextPaint.apply {
            isAntiAlias = true
            color = legendTextColor
            textSize = legendTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        legendMinutesTextPaint.apply {
            isAntiAlias = true
            color = legendTextColor
            textSize = legendTextSize * 0.8f
        }
        linePaint.apply {
            isAntiAlias = true
            color = legendLineColor
        }
        lineSecondaryPaint.apply {
            isAntiAlias = true
            color = legendLineSecondaryColor
        }
        currentTimelinePaint.apply {
            isAntiAlias = true
            color = currentTimeLegendColor
            strokeWidth = currentTimeLegendWidth
        }
        dragPreviewPaint.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = currentTimeLegendColor
            alpha = DRAG_PREVIEW_ALPHA
        }
        dragPreviewStrokePaint.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = currentTimeLegendColor
            strokeWidth = dragPreviewStrokeWidth
        }
        dragPreviewTextPaint.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            color = dragPreviewTextColor
            textSize = nameTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        editHandlePaint.apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            // Handles can extend beyond the record block, so use the calendar
            // preview contrast color instead of the always-light record text.
            color = dragPreviewTextColor
        }
        editOutlinePaint.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = nameTextColor
            strokeWidth = editOutlineStrokeWidth
        }
        editSelectionHaloPaint.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = currentTimeLegendColor
            alpha = EDIT_SELECTION_HALO_ALPHA
            strokeWidth = 5.dpToPx().toFloat()
        }
    }

    private fun calculateDimensions(w: Float, h: Float) {
        val defaultLegendText = if (isMilitary) "00:00" else "00 am"
        legendTextWidth = legendTextPaint.measureText(defaultLegendText)

        legendTextPaint.getTextBounds(defaultLegendText, 0, defaultLegendText.length, textBounds)
        legendTextHeight = textBounds.height().toFloat()

        legendTopTextPaint.getTextBounds(defaultLegendText, 0, defaultLegendText.length, textBounds)
        legendTopTextHeight = textBounds.height().toFloat()

        legendMinutesTextPaint.getTextBounds(defaultLegendText, 0, defaultLegendText.length, textBounds)
        legendMinutesTextHeight = textBounds.height().toFloat()

        // Chart dimensions
        chartLeftBound = legendTextPadding
        chartRightBound = w - legendTextWidth - 2 * legendTextPadding
        columnWidth = (chartRightBound - chartLeftBound) / dataSize

        chartTopBound = if (shouldDrawTopLegends) {
            legendTopTextHeight + 2 * legendTopTextPadding
        } else {
            0f
        }
        chartBottomBound = h
        chartHeight = chartBottomBound - chartTopBound
    }

    private fun drawData(
        canvas: Canvas,
        data: List<Data>,
        index: Int,
    ) {
        val ellipsizedNameCutoff = iconMaxSize * 2

        var boxHeight: Float
        var boxShift: Float
        var boxWidth: Float
        var boxLeft: Float
        var boxRight: Float
        var boxTop: Float
        var boxBottom: Float
        var isSelectedViewWidth: Float

        var iconLeft: Int
        var iconRight: Int
        var iconTop: Int
        var iconBottom: Int

        var textWidth: Float
        var textHeight: Int
        var timesTextHeight: Int
        var textLeft: Float
        var textTop: Float
        var availableHeight: Float
        var availableWidth: Float
        var newMaxLines: Int

        data.forEach { item ->
            var iconDrawn = false
            var timesDrawn = false
            var durationInSeparateLine = false
            var nameIsEllipsized = false

            /************
             * Draw box *
             ************/
            boxHeight = chartHeight * (item.point.end - item.point.start) / dayInMillis
            boxShift = chartHeight * item.point.start / dayInMillis
            boxWidth = columnWidth / item.columnCount
            boxLeft = chartLeftBound +
                columnWidth * index +
                boxWidth * (item.columnNumber - 1)
            boxRight = boxLeft + boxWidth
            boxBottom = if (reverseOrder) {
                chartTopBound + (boxShift + boxHeight) * scaleFactor
            } else {
                chartTopBound + (chartHeight - boxShift) * scaleFactor
            }.let { it + panFactor }
            boxTop = boxBottom - boxHeight * scaleFactor

            // Save coordinates for click event.
            item.boxLeft = boxLeft
            item.boxTop = boxTop
            item.boxRight = boxRight
            item.boxBottom = boxBottom

            if (item.point.isSelected) {
                isSelectedViewWidth = multiSelectedRecordIndicatorWidth
                recordPaint.color = currentTimeLegendColor
                recordBounds.set(
                    boxLeft + (paddingBetweenDays / 2),
                    boxTop,
                    boxLeft + (paddingBetweenDays / 2) + isSelectedViewWidth - paddingBetweenDays,
                    boxBottom,
                )
                canvas.drawRoundRect(
                    recordBounds,
                    recordCornerRadius,
                    recordCornerRadius,
                    recordPaint,
                )
            } else {
                isSelectedViewWidth = 0f
            }
            recordPaint.color = if (selectedRecord == item.point.data) {
                selectedRecordColor
            } else {
                item.point.data.color
            }
            recordBounds.set(
                boxLeft + (paddingBetweenDays / 2) + isSelectedViewWidth,
                boxTop,
                boxRight - (paddingBetweenDays / 2),
                boxBottom,
            )
            canvas.drawRoundRect(
                recordBounds,
                recordCornerRadius,
                recordCornerRadius,
                recordPaint,
            )

            availableHeight = recordBounds.height() - 2 * recordVerticalPadding
            availableWidth = recordBounds.width() - 2 * recordHorizontalPadding

            /*************
             * Draw icon *
             *************/
            // If can fit into box.
            if (iconMaxSize < availableHeight && iconMaxSize < availableWidth) {
                iconLeft = (recordBounds.left + recordHorizontalPadding).toInt()
                iconRight = iconLeft + iconMaxSize
                iconTop = (recordBounds.top + recordVerticalPadding).toInt()
                iconBottom = iconTop + iconMaxSize

                bounds.set(
                    iconLeft, iconTop,
                    iconRight, iconBottom,
                )
                item.drawable?.bounds = bounds
                item.drawable?.draw(canvas)

                iconDrawn = true
                availableWidth = (availableWidth - iconMaxSize - recordHorizontalPadding)
                    .coerceAtLeast(0f)
                availableHeight = (availableHeight - iconMaxSize - recordVerticalPadding)
                    .coerceAtLeast(0f)
            }

            /*************
             * Draw name *
             *************/
            nameTextView.text = getItemName(item.point.data)
            nameTextView.measureText(
                width = 0,
                widthSpec = MeasureSpec.UNSPECIFIED,
            )
            if (nameTextView.measuredWidth > availableWidth) {
                nameIsEllipsized = true
                nameTextView.measureText(
                    width = availableWidth.toInt(),
                    widthSpec = MeasureSpec.EXACTLY,
                )
            }
            // If can fit into box.
            if (
                iconDrawn &&
                (nameTextView.measuredWidth > ellipsizedNameCutoff || !nameIsEllipsized) &&
                (nameTextView.measuredWidth < availableWidth || nameIsEllipsized)
            ) {
                textLeft = recordBounds.left + iconMaxSize + 2 * recordHorizontalPadding
                textTop = recordBounds.top + recordVerticalPadding

                canvas.withTranslation(textLeft, textTop) {
                    nameTextView.draw(this)
                }

                availableWidth = (availableWidth - nameTextView.measuredWidth - recordHorizontalPadding)
                    .coerceAtLeast(0f)
            }

            /*****************
             * Draw duration *
             *****************/
            durationTextView.text = item.point.data.duration
            durationTextView.measureText(
                width = 0,
                widthSpec = MeasureSpec.UNSPECIFIED,
            )
            textHeight = durationTextView.measuredHeight
            // If can fit into box.
            if (
                iconDrawn &&
                durationTextView.measuredWidth < availableWidth
            ) {
                textLeft = recordBounds.right - recordHorizontalPadding - durationTextView.measuredWidth
                textTop = recordBounds.top + recordVerticalPadding

                canvas.withTranslation(textLeft, textTop) {
                    durationTextView.draw(this)
                }
            } else if (
                iconDrawn &&
                textHeight < availableHeight
            ) {
                // Try to draw on separate line.
                val newAvailableWidth = recordBounds.width() - 2 * recordHorizontalPadding
                durationTextView.measureText(
                    width = 0,
                    widthSpec = MeasureSpec.UNSPECIFIED,
                )
                textHeight = durationTextView.measuredHeight
                if (durationTextView.measuredWidth < newAvailableWidth) {
                    durationInSeparateLine = true
                    textLeft = recordBounds.left + recordHorizontalPadding
                    textTop = recordBounds.top + recordVerticalPadding + iconMaxSize

                    canvas.withTranslation(textLeft, textTop) {
                        durationTextView.draw(this)
                    }

                    availableHeight = (availableHeight - textHeight - recordVerticalPadding)
                        .coerceAtLeast(0f)
                }
            }

            /**************
             * Draw times *
             **************/
            availableWidth = recordBounds.width() - 2 * recordHorizontalPadding
            timeTextView.text = getItemTimes(item.point.data)
            timeTextView.measureText(
                width = 0,
                widthSpec = MeasureSpec.UNSPECIFIED,
            )
            timesTextHeight = timeTextView.measuredHeight
            // If can fit into box.
            if (
                iconDrawn &&
                timeTextView.measuredWidth < availableWidth &&
                timesTextHeight < availableHeight
            ) {
                textLeft = recordBounds.left + recordHorizontalPadding
                textTop = recordBounds.top + recordVerticalPadding + iconMaxSize +
                    textHeight.takeIf { durationInSeparateLine }.orZero()

                canvas.withTranslation(textLeft, textTop) {
                    timeTextView.draw(this)
                }

                timesDrawn = true
                availableHeight = (availableHeight - timesTextHeight - recordVerticalPadding)
                    .coerceAtLeast(0f)
            }

            /****************
             * Draw comment *
             ****************/
            if (item.point.data.comment.isNotEmpty()) {
                textWidth = recordBounds.width() - 2 * recordHorizontalPadding
                commentTextView.text = item.point.data.comment
                commentTextView.apply { maxLines = 1 }.measureText(
                    width = textWidth.toInt(),
                    widthSpec = MeasureSpec.EXACTLY,
                    height = 0,
                    heightSpec = MeasureSpec.UNSPECIFIED,
                )
                newMaxLines = (availableHeight / commentTextView.measuredHeight).toInt()
                commentTextView.apply { maxLines = newMaxLines }.measureText(
                    width = textWidth.toInt(),
                    widthSpec = MeasureSpec.EXACTLY,
                    height = availableHeight.toInt(),
                    heightSpec = MeasureSpec.AT_MOST,
                )
                // If can fit into box.
                if (
                    iconDrawn &&
                    commentTextView.measuredHeight < availableHeight
                ) {
                    textLeft = recordBounds.left + recordHorizontalPadding
                    textTop = recordBounds.top + recordVerticalPadding + iconMaxSize +
                        textHeight.takeIf { durationInSeparateLine }.orZero() +
                        timesTextHeight.takeIf { timesDrawn }.orZero()

                    canvas.withTranslation(textLeft, textTop) {
                        commentTextView.draw(this)
                    }
                }
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun drawTopLegend(canvas: Canvas) {
        if (!shouldDrawTopLegends) return

        var currentText: String
        var textWidth: Float
        var textLeft: Float

        canvas.save()
        canvas.translate(0f, panFactor)

        data.forEachIndexed { index, column ->
            legendTopTextPaint.color = if (column.highlighted) {
                currentTimeLegendColor
            } else {
                legendTextColor
            }
            currentText = column.legend
            textWidth = legendTopTextPaint.measureText(currentText)
            textLeft = chartLeftBound +
                columnWidth * index +
                columnWidth / 2 -
                textWidth / 2

            canvas.drawText(
                currentText,
                textLeft,
                chartTopBound - legendTopTextPadding,
                legendTopTextPaint,
            )
        }

        canvas.restore()
    }

    @SuppressLint("UseKtx")
    private fun drawSideLegend(canvas: Canvas) {
        fun Float.checkReverse(): Float {
            return if (reverseOrder) {
                chartTopBound + chartHeight * scaleFactor - (this - chartTopBound)
            } else {
                this
            }
        }

        fun Float.checkOverdraw(): Float {
            // If goes over the end - draw on top, and otherwise.
            return when {
                this > chartTopBound + chartHeight * scaleFactor -> this - chartHeight * scaleFactor
                this < chartTopBound -> this + chartHeight * scaleFactor
                else -> this
            }
        }

        val lineStep = chartHeight / (hours.size - 1)

        val selectedMinutesRange = availableMinutesRanges.firstOrNull {
            (lineStep * scaleFactor / (it.size + 1)) > (legendMinutesTextHeight + 2 * legendMinutesTextPadding)
        }.orEmpty()
        val minuteLineStep = lineStep / (selectedMinutesRange.size + 1)

        val shift: Float = chartHeight * startOfDayShift / dayInMillis

        canvas.save()
        canvas.translate(0f, panFactor)

        // Draw current time
        currentTime?.let { currentTime ->
            val currentTimeY = (
                chartTopBound +
                    chartHeight * scaleFactor * (dayInMillis - currentTime) / dayInMillis
                ).checkOverdraw()

            canvas.drawLine(
                0f,
                currentTimeY.checkReverse(),
                chartRightBound + legendTextPadding,
                currentTimeY.checkReverse(),
                currentTimelinePaint,
            )
        }

        hours.forEachIndexed { index, hour ->
            val currentY = (
                chartTopBound +
                    index * lineStep * scaleFactor +
                    shift * scaleFactor
                ).checkOverdraw()

            // Draw hour line
            canvas.drawLine(
                chartLeftBound,
                currentY.checkReverse(),
                chartRightBound,
                currentY.checkReverse(),
                linePaint,
            )

            // Draw hour text
            val textCenterY: Float = (currentY.checkReverse() + legendTextHeight / 2)
                .coerceIn(
                    chartTopBound + legendTextHeight,
                    chartTopBound + chartHeight * scaleFactor,
                )
            canvas.drawText(
                hour.second,
                chartRightBound + legendTextPadding,
                textCenterY,
                legendTextPaint,
            )

            if (index == 0) return@forEachIndexed
            // Draw minutes
            selectedMinutesRange.forEachIndexed { minuteIndex, minute ->
                val minuteCurrentY = (currentY - (minuteIndex + 1) * minuteLineStep * scaleFactor).checkOverdraw()

                // Draw minute line
                canvas.drawLine(
                    chartLeftBound,
                    minuteCurrentY.checkReverse(),
                    chartRightBound,
                    minuteCurrentY.checkReverse(),
                    lineSecondaryPaint,
                )

                // Draw minute text
                val minuteTextCenterY: Float = (minuteCurrentY.checkReverse() + legendMinutesTextHeight / 2)
                    .coerceIn(
                        chartTopBound + legendMinutesTextHeight,
                        chartTopBound + chartHeight * scaleFactor,
                    )
                val minuteText = minute.toString()
                    .padStart(2, '0')
                val fullText = "${hour.first}:$minuteText"
                canvas.drawText(
                    fullText,
                    chartRightBound + legendTextPadding,
                    minuteTextCenterY,
                    legendMinutesTextPaint,
                )
            }
        }

        canvas.restore()
    }

    private fun initEditMode() {
        val records = recordsCount.takeIf { it != 0 } ?: 5
        val columns = columnsCount.takeIf { it != 0 } ?: 1
        if (isInEditMode) {
            var currentStart = 0L
            (0 until records)
                .map {
                    currentStart += hourInMillis * it
                    val start = currentStart
                    val end = currentStart + hourInMillis * (it + 1)
                    val record = RecordViewData.Tracked(
                        id = 1,
                        timeStartedTimestamp = start,
                        timeEndedTimestamp = end,
                        name = "Record $it",
                        tagName = "Tag $it",
                        timeStarted = "07:35",
                        timeFinished = "11:58",
                        duration = "5h 23m 3s",
                        durationTotal = "",
                        iconId = RecordTypeIcon.Image(R.drawable.unknown),
                        color = Color.RED,
                        comment = "Comment $it",
                    )
                    RecordsCalendarViewData.Point(
                        start = start,
                        end = end,
                        isSelected = it == 0,
                        data = RecordsCalendarViewData.Point.Data.RecordData(record),
                    )
                }.let {
                    val points = RecordsCalendarViewData.Points(
                        legend = "Sun",
                        highlighted = false,
                        data = it,
                        rangeStart = 0L,
                        rangeEnd = dayInMillis,
                    )
                    RecordsCalendarViewData(
                        currentTime = 18 * hourInMillis,
                        startOfDayShift = 0,
                        points = List(columns) { index ->
                            points.copy(highlighted = index == 0)
                        },
                        reverseOrder = reverseOrder,
                        shouldDrawTopLegends = true,
                        isMilitary = true,
                    )
                }.let(::setData)
        }
    }

    private fun processData(data: RecordsCalendarViewData.Points): Column {
        val res = mutableListOf<Data>()

        // Raw data.
        data.data.forEach { point ->
            res += Data(
                point = point,
                drawable = getIconDrawable(point.data.iconId),
            )
        }

        // Calculate intersections.
        res.map {
            CalendarIntersectionCalculator.Data(
                start = it.point.start,
                end = it.point.end,
                point = it,
            )
        }.let(
            CalendarIntersectionCalculator::execute,
        ).forEach {
            it.point.columnCount = it.columnCount
            it.point.columnNumber = it.columnNumber
        }

        return Column(
            legend = data.legend,
            highlighted = data.highlighted,
            data = res,
            rangeStart = data.rangeStart,
            rangeEnd = data.rangeEnd,
        )
    }

    private fun getIconDrawable(iconId: RecordTypeIcon): Drawable {
        return iconView
            .apply {
                itemIcon = iconId
                measureExactly(iconMaxSize)
            }
            .getBitmapFromView()
            .toDrawable(resources)
    }

    private fun getItemName(item: RecordsCalendarViewData.Point.Data): CharSequence {
        return if (item.tagName.isEmpty()) {
            item.name
        } else {
            val name = "${item.name} - ${item.tagName}"
            name.toSpannableString().setForegroundSpan(
                color = itemTagColor,
                start = item.name.length,
                length = name.length - item.name.length,
            )
        }
    }

    private fun getItemTimes(item: RecordsCalendarViewData.Point.Data): String {
        return when (val value = item.value) {
            is RecordViewData -> "${value.timeStarted} - ${value.timeFinished}"
            is RunningRecordViewData -> value.timeStarted
            else -> ""
        }
    }

    private fun onEventClick(event: MotionEvent) {
        // A tap that grabbed the edited block commits the edit instead of
        // opening the record, so it must never reach the click listener.
        if (suppressTapForGesture) return
        onClick(event)?.value?.let(clickListener)
    }

    private fun onEventLongClick(event: MotionEvent) {
        onClick(event)?.value?.let(longClickListener)
    }

    private fun onClick(event: MotionEvent): RecordsCalendarViewData.Point.Data? {
        val selected = findDataPoint(x = event.x, y = event.y)
            ?.point?.data
        selectedRecord = selected
        if (selected != null) animateSelectedRecord(selected)
        return selected
    }

    private fun onEventScaleStart() {
        // A second finger aborts an in progress drag creation.
        cancelDragCreate()
        // An in progress edit drag is rolled back, edit mode stays open.
        cancelEditDrag()
        isScaling = true
        if (isSwiping) lastPanFactor = panFactor
    }

    private fun onEventScaleChanged(newScale: Float) {
        parent.requestDisallowInterceptTouchEvent(true)
        scaleFactor *= newScale
        scaleFactor = scaleFactor.coerceAtLeast(1f)
        val currentScale = scaleFactor / lastScaleFactor
        panFactor = lastPanFactor * currentScale - (chartHeight * currentScale - chartHeight) / 2
        coercePan()
        invalidate()
    }

    private fun onEventScaleStop() {
        parent.requestDisallowInterceptTouchEvent(isSwiping)
        lastScaleFactor = scaleFactor
        lastPanFactor = panFactor
        isScaling = false
        shouldRebaseSwipeAfterScaleStop = isSwiping
    }

    private fun onEventSwipeStart() {
        if (dragState != DragState.IDLE) return
        isSwiping = true
        shouldRebaseSwipeAfterScaleStop = false
        swipeStartOffset = 0f
        swipeStartPanFactor = panFactor
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onEventSwipe(
        offset: Float,
        direction: SwipeDetector.Direction,
        event: MotionEvent,
    ) {
        // Vertical dragging is repurposed for creating a new record.
        if (dragState != DragState.IDLE) return
        if (direction.isHorizontal() || isScaling) return
        // Keep swipe baseline in sync with active pinch to avoid handoff jumps.
        if (shouldRebaseSwipeAfterScaleStop) {
            shouldRebaseSwipeAfterScaleStop = false
            swipeStartOffset = offset
            swipeStartPanFactor = panFactor
            return
        }
        parent.requestDisallowInterceptTouchEvent(true)
        panFactor = swipeStartPanFactor + (offset - swipeStartOffset)
        coercePan()
        invalidate()
    }

    private fun onEventSwipeStop() {
        if (dragState != DragState.IDLE) return
        isSwiping = false
        shouldRebaseSwipeAfterScaleStop = false
        if (isScaling) return
        parent.requestDisallowInterceptTouchEvent(false)
        lastPanFactor = panFactor
    }

    private fun coercePan() {
        val maxPanAvailable = chartHeight * scaleFactor - chartHeight
        panFactor = panFactor.coerceIn(-maxPanAvailable, 0f)
    }

    private fun updateEdgeAutoScroll() {
        val delta = calculateEdgeAutoScrollDelta()
        if (delta == 0f) {
            stopEdgeAutoScroll()
            return
        }
        if (!isEdgeAutoScrollRunning) {
            isEdgeAutoScrollRunning = true
            postOnAnimation(edgeAutoScrollRunnable)
        }
    }

    private fun runEdgeAutoScrollFrame() {
        if (!isEdgeAutoScrollRunning ||
            dragState == DragState.IDLE ||
            dragState == DragState.EDIT_IDLE
        ) {
            stopEdgeAutoScroll()
            return
        }

        val delta = calculateEdgeAutoScrollDelta()
        val previousPan = panFactor
        panFactor += delta
        coercePan()
        if (delta == 0f || panFactor == previousPan) {
            stopEdgeAutoScroll()
            return
        }

        lastPanFactor = panFactor
        when (dragState) {
            DragState.DRAGGING_NEW -> updateDragCreate(dragPointerY)
            DragState.EDIT_DRAGGING_START,
            DragState.EDIT_DRAGGING_END,
            DragState.EDIT_DRAGGING_MOVE,
            -> updateEditDrag(
                pointerX = dragPointerX,
                pointerY = dragPointerY,
                trackTouchSlop = false,
            )
            else -> {}
        }
        invalidate()
        postOnAnimation(edgeAutoScrollRunnable)
    }

    private fun calculateEdgeAutoScrollDelta(): Float {
        if (scaleFactor <= 1f) return 0f
        return RecordsCalendarDragMath.autoScrollDelta(
            pointerY = dragPointerY,
            viewportTop = chartTopBound,
            viewportBottom = chartBottomBound,
            edgeSize = edgeAutoScrollSize,
            maxStep = edgeAutoScrollMaxStep,
        )
    }

    private fun stopEdgeAutoScroll() {
        if (!isEdgeAutoScrollRunning) return
        isEdgeAutoScrollRunning = false
        removeCallbacks(edgeAutoScrollRunnable)
        lastPanFactor = panFactor
    }

    /**
     * Long press dispatcher. A movable tracked record immediately becomes an
     * edit drag, so the same held finger can move it. Unsupported records keep
     * the quick actions behaviour, while an empty area drafts a new record.
     */
    private fun onEventLongPress(event: MotionEvent) {
        if (dragState != DragState.IDLE) return

        val existingPoint = findDataPoint(x = event.x, y = event.y)
        if (existingPoint == null) {
            onDragCreateStart(event)
            return
        }

        val recordId = (existingPoint.point.data.value as? RecordViewData.Tracked)?.id
        if (recordId != null && enterEditMode(recordId)) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            beginEditDrag(
                event = event,
                grabbed = DragState.EDIT_DRAGGING_MOVE,
                startedByLongPress = true,
            )
            return
        }

        onEventLongClick(event)
    }

    private fun onDragCreateStart(event: MotionEvent) {
        if (data.isEmpty() || chartHeight <= 0f) return

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // Keep ViewPager2 and parent scroll containers from stealing the gesture.
        parent?.requestDisallowInterceptTouchEvent(true)

        dragColumnIndex = xToColumnIndex(event.x)
        dragPointerX = event.x
        dragPointerY = event.y
        // Anchor is snapped, so the draft always sits on the grid.
        dragAnchorTime = snapToStep(yToTime(event.y))
        dragStartTime = dragAnchorTime
        dragEndTime = dragAnchorTime
        dragLastSnapTime = dragAnchorTime
        dragState = DragState.DRAGGING_NEW
        updateDragPreviewLabel()
        invalidate()
    }

    private fun onDragCreateMove(event: MotionEvent) {
        dragPointerX = event.x
        dragPointerY = event.y
        updateDragCreate(event.y)
        updateEdgeAutoScroll()
    }

    private fun updateDragCreate(pointerY: Float) {
        val current = snapToStep(yToTime(pointerY))
        // Mechanical tick, but only when the snapped quarter hour step changes.
        if (current != dragLastSnapTime) {
            dragLastSnapTime = current
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        dragStartTime = if (current < dragAnchorTime) current else dragAnchorTime
        dragEndTime = if (current > dragAnchorTime) current else dragAnchorTime
        updateDragPreviewLabel()
        invalidate()
    }

    private fun onDragCreateFinish() {
        // Re-align to the grid, then enforce the minimum duration.
        var start = snapToStep(dragStartTime)
        var end = snapToStep(dragEndTime)
        if (end - start < minDragDurationInMillis) {
            end = (start + minDragDurationInMillis).coerceAtMost(dayInMillis)
            start = (end - minDragDurationInMillis).coerceAtLeast(0L)
        }

        // Restore absolute time of the day the drag happened on, then clamp it
        // to that whole day so nothing outside the day can ever be reported.
        val columnIndex = dragColumnIndex.coerceIn(0, dataSize - 1)
        val column = data.getOrNull(columnIndex) ?: return
        val absoluteRange = RecordsCalendarDragMath.toAbsoluteRange(
            rangeStart = column.rangeStart,
            rangeEnd = column.rangeEnd,
            startOffset = start,
            endOffset = end,
        )

        resetDragState()
        invalidate()

        // Never report an empty or inverted range.
        if (absoluteRange.end <= absoluteRange.start) return

        onNewRecordSelectedListener?.invoke(absoluteRange.start, absoluteRange.end)
    }

    private fun cancelDragCreate() {
        if (dragState != DragState.DRAGGING_NEW) return
        resetDragState()
        invalidate()
    }

    /**
     * ACTION_DOWN dispatcher. In edit mode it decides which part of the
     * highlighted block was grabbed: the handle on one end, the block itself,
     * or nothing, which commits the preview and leaves edit mode.
     */
    private fun onTouchDown(event: MotionEvent) {
        suppressTapForGesture = false
        if (dragState != DragState.EDIT_IDLE) return

        findEditTarget(editRecordId ?: return)?.second ?: run {
            exitEditMode()
            return
        }

        val startY = timeToY(dragStartTime)
        val endY = timeToY(dragEndTime)
        val bodyTop = minOf(startY, endY)
        val bodyBottom = maxOf(startY, endY)
        val bodyLeft = chartLeftBound + columnWidth * dragColumnIndex
        val bodyRight = bodyLeft + columnWidth
        val isOverHandleX = event.x >= bodyLeft - editHandleTouchSize &&
            event.x <= bodyRight + editHandleTouchSize

        val grabbed = when {
            isOverHandleX && abs(event.y - startY) <= editHandleTouchSize ->
                DragState.EDIT_DRAGGING_START

            isOverHandleX && abs(event.y - endY) <= editHandleTouchSize ->
                DragState.EDIT_DRAGGING_END

            bodyLeft < event.x && bodyRight > event.x &&
                bodyTop < event.y && bodyBottom > event.y ->
                DragState.EDIT_DRAGGING_MOVE

            else -> null
        }

        if (grabbed == null) {
            // A tap anywhere confirms the current preview. Consume this
            // gesture so the same tap cannot open another record underneath.
            suppressTapForGesture = true
            commitEdit()
            return
        }

        beginEditDrag(
            event = event,
            grabbed = grabbed,
            startedByLongPress = false,
        )
    }

    private fun beginEditDrag(
        event: MotionEvent,
        grabbed: DragState,
        startedByLongPress: Boolean,
    ) {
        editGrabStart = dragStartTime
        editGrabEnd = dragEndTime
        editGrabX = event.x
        editGrabY = event.y
        dragPointerX = event.x
        dragPointerY = event.y
        isEditDragMoved = false
        editDragStartedByLongPress = startedByLongPress
        dragAnchorTime = snapToStep(yToTime(event.y))
        dragLastSnapTime = dragAnchorTime
        parent?.requestDisallowInterceptTouchEvent(true)
        if (grabbed == DragState.EDIT_DRAGGING_MOVE) {
            // Moving or confirming an edit must not also open the record.
            suppressTapForGesture = true
        }
        dragState = grabbed
    }

    private fun resetDragState() {
        stopEdgeAutoScroll()
        editSelectionAnimator?.cancel()
        editSelectionAnimator = null
        editSelectionScale = 1f
        if (dragState != DragState.IDLE) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        dragState = DragState.IDLE
        dragAnchorTime = 0L
        dragStartTime = 0L
        dragEndTime = 0L
        dragLastSnapTime = 0L
        dragPreviewLabel = ""
        editRecordId = null
        editSavedStart = 0L
        editSavedEnd = 0L
        editSavedColumnIndex = 0
        editGrabStart = 0L
        editGrabEnd = 0L
        editGrabX = 0f
        editGrabY = 0f
        isEditDragMoved = false
        editDragStartedByLongPress = false
        // suppressTapForGesture is intentionally left alone here: it has to
        // survive until the next ACTION_DOWN, so the tap that committed an edit
        // (or the tap that left edit mode) never doubles as a record click.
    }

    /**
     * Drag on the handles or on the block itself. Everything is a preview: the
     * range is only reported to the listener when the user taps to finish.
     */
    private fun onEditDragMove(event: MotionEvent) {
        dragPointerX = event.x
        dragPointerY = event.y
        updateEditDrag(pointerX = event.x, pointerY = event.y, trackTouchSlop = true)
        updateEdgeAutoScroll()
    }

    private fun updateEditDrag(
        pointerX: Float,
        pointerY: Float,
        trackTouchSlop: Boolean,
    ) {
        val movedPastTouchSlop = abs(pointerX - editGrabX) > editDragTouchSlop ||
            abs(pointerY - editGrabY) > editDragTouchSlop
        if (trackTouchSlop && !isEditDragMoved && movedPastTouchSlop) {
            isEditDragMoved = true
        }
        val current = snapToStep(yToTime(pointerY))
        // Mechanical tick, but only when the snapped quarter hour step changes.
        if (current != dragLastSnapTime) {
            dragLastSnapTime = current
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        when (dragState) {
            DragState.EDIT_DRAGGING_START -> {
                // The end stays where it was, the start follows the finger.
                val candidate = current.coerceIn(
                    minimumValue = 0L,
                    maximumValue = editGrabEnd - minDragDurationInMillis,
                )
                dragStartTime = snapStartToPreviousRecordEnd(
                    candidate = candidate,
                    maximumValue = editGrabEnd - minDragDurationInMillis,
                )
            }
            DragState.EDIT_DRAGGING_END -> {
                // The start stays where it was, the end follows the finger.
                dragEndTime = current.coerceIn(
                    minimumValue = editGrabStart + minDragDurationInMillis,
                    maximumValue = dayInMillis,
                )
            }
            DragState.EDIT_DRAGGING_MOVE -> {
                // The whole block follows the finger in both axes: Y changes
                // time and X selects the adjacent visible day.
                val newColumnIndex = xToColumnIndex(pointerX)
                if (newColumnIndex != dragColumnIndex) {
                    dragColumnIndex = newColumnIndex
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                RecordsCalendarDragMath.moveRange(
                    grabbedStart = editGrabStart,
                    grabbedEnd = editGrabEnd,
                    anchor = dragAnchorTime,
                    current = current,
                ).let {
                    val duration = it.end - it.start
                    dragStartTime = snapStartToPreviousRecordEnd(
                        candidate = it.start,
                        maximumValue = dayInMillis - duration,
                    )
                    dragEndTime = dragStartTime + duration
                }
            }
            else -> {}
        }
        updateDragPreviewLabel()
        invalidate()
    }

    /**
     * A move started by long press is saved as soon as the finger is released,
     * matching calendar drag-and-drop behaviour. Existing handle editing keeps
     * its preview-and-tap-anywhere-to-confirm flow.
     */
    private fun onEditDragFinish() {
        if (editDragStartedByLongPress) {
            editDragStartedByLongPress = false
            if (isEditDragMoved) {
                snapEditRange()
                commitEdit()
            } else {
                finishEditDragWithoutCommit()
            }
            return
        }

        // A tap on the body or either handle confirms the pending preview.
        if (!isEditDragMoved) {
            commitEdit()
            return
        }

        snapEditRange()
        finishEditDragWithoutCommit()
    }

    private fun finishEditDragWithoutCommit() {
        stopEdgeAutoScroll()
        parent?.requestDisallowInterceptTouchEvent(false)
        dragAnchorTime = 0L
        dragLastSnapTime = dragStartTime
        dragState = DragState.EDIT_IDLE
        updateDragPreviewLabel()
        invalidate()
    }

    /**
     * Rolls an in progress drag back to the range it was started with, keeping
     * edit mode open.
     */
    private fun cancelEditDrag() {
        if (dragState != DragState.EDIT_DRAGGING_START &&
            dragState != DragState.EDIT_DRAGGING_END &&
            dragState != DragState.EDIT_DRAGGING_MOVE
        ) {
            return
        }
        dragStartTime = editGrabStart
        dragEndTime = editGrabEnd
        dragAnchorTime = 0L
        dragLastSnapTime = dragStartTime
        editDragStartedByLongPress = false
        stopEdgeAutoScroll()
        parent?.requestDisallowInterceptTouchEvent(false)
        dragState = DragState.EDIT_IDLE
        updateDragPreviewLabel()
        invalidate()
    }

    /**
     * Puts the previewed range back on the clock grid. A move keeps its exact
     * duration, a resize only snaps the end the user dragged.
     */
    private fun snapEditRange() {
        when (dragState) {
            DragState.EDIT_DRAGGING_START -> {
                val maximumValue = dragEndTime - minDragDurationInMillis
                val candidate = snapToStep(dragStartTime).coerceIn(
                    minimumValue = 0L,
                    maximumValue = maximumValue,
                )
                dragStartTime = snapStartToPreviousRecordEnd(
                    candidate = candidate,
                    maximumValue = maximumValue,
                )
            }
            DragState.EDIT_DRAGGING_END -> {
                dragEndTime = snapToStep(dragEndTime).coerceIn(
                    minimumValue = dragStartTime + minDragDurationInMillis,
                    maximumValue = dayInMillis,
                )
            }
            DragState.EDIT_DRAGGING_MOVE -> {
                val duration = dragEndTime - dragStartTime
                val maximumValue = dayInMillis - duration
                val candidate = snapToStep(dragStartTime).coerceIn(0L, maximumValue)
                dragStartTime = snapStartToPreviousRecordEnd(
                    candidate = candidate,
                    maximumValue = maximumValue,
                )
                dragEndTime = dragStartTime + duration
            }
            else -> {}
        }
    }

    /**
     * A finishing tap anywhere saves the previewed range, if it changed, and
     * ends edit mode.
     */
    private fun commitEdit() {
        val recordId = editRecordId
        val isChanged = hasPendingEdit
        val start = dragStartTime
        val end = dragEndTime
        val columnIndex = dragColumnIndex

        // Restore absolute time of the day the record is shown on, then clamp
        // it so nothing outside that day can ever be reported.
        val column = data.getOrNull(columnIndex)
        val absoluteRange = column?.let {
            RecordsCalendarDragMath.toAbsoluteRange(
                rangeStart = it.rangeStart,
                rangeEnd = it.rangeEnd,
                startOffset = start,
                endOffset = end,
            )
        }

        resetDragState()
        invalidate()

        if (recordId == null || !isChanged || absoluteRange == null) return
        // Never report an empty or inverted range.
        if (absoluteRange.end <= absoluteRange.start) return

        onRecordTimeAdjustedListener?.invoke(recordId, absoluteRange.start, absoluteRange.end)
    }

    /**
     * Millis since the start of the day (0..dayInMillis) to Y in pixels.
     * Exact inverse of [yToTime], mirroring the formula [drawData] uses:
     *
     *   boxBottom = chartTopBound + (chartHeight - boxShift) * scaleFactor + panFactor
     *   boxShift  = chartHeight * time / dayInMillis
     */
    private fun timeToY(time: Long): Float {
        return RecordsCalendarDragMath.timeToY(
            time = time,
            chartTop = chartTopBound,
            chartHeight = chartHeight,
            scale = scaleFactor,
            pan = panFactor,
            reverseOrder = reverseOrder,
        )
    }

    /**
     * Y in pixels to millis since the start of the day (0..dayInMillis).
     *
     * Touch coordinates are view local, so padding and the view own scroll
     * offset are removed first to land in the same space [Canvas] draws in.
     * This view has no padding, and panning is applied through [panFactor]
     * rather than scrolling, so both terms are 0 by default.
     */
    private fun yToTime(y: Float): Long {
        return RecordsCalendarDragMath.yToTime(
            y = y - paddingTop + scrollY,
            chartTop = chartTopBound,
            chartHeight = chartHeight,
            scale = scaleFactor,
            pan = panFactor,
            reverseOrder = reverseOrder,
        )
    }

    /**
     * Snaps to the clock grid drawn by the side legend. The legend lines are
     * offset by [startOfDayShift], so the shift is added before rounding and
     * removed afterwards, keeping snapping aligned with what the user sees.
     */
    private fun snapToStep(
        timeMillis: Long,
        stepMinutes: Int = DEFAULT_SNAP_STEP_MINUTES,
    ): Long {
        return RecordsCalendarDragMath.snapToStep(
            timeMillis = timeMillis,
            startOfDayShift = startOfDayShift,
            stepMinutes = stepMinutes,
        )
    }

    /**
     * Snaps the edited block's logical start to a nearby end boundary in the
     * target day. The edited record itself is excluded from the candidates.
     */
    private fun snapStartToPreviousRecordEnd(
        candidate: Long,
        maximumValue: Long,
    ): Long {
        val recordId = editRecordId
        val recordEnds = data.getOrNull(dragColumnIndex)
            ?.data
            .orEmpty()
            .asSequence()
            .filterNot {
                (it.point.data.value as? RecordViewData.Tracked)?.id == recordId
            }
            .map { it.point.end }
            .asIterable()

        return RecordsCalendarDragMath.snapToRecordEnd(
            timeMillis = candidate,
            recordEnds = recordEnds,
            thresholdMillis = adjacentRecordSnapThresholdInMillis,
            minimumValue = 0L,
            maximumValue = maximumValue,
        )
    }

    private fun xToColumnIndex(x: Float): Int {
        return RecordsCalendarDragMath.columnIndex(
            x = x,
            chartLeft = chartLeftBound,
            columnWidth = columnWidth,
            columnCount = dataSize,
        )
    }

    /**
     * Draws the ghost block for the record being dragged out: translucent
     * themed rounded rect, its stroke outline and a live start - end label.
     */
    private fun drawNewRecordPreview(canvas: Canvas) {
        if (chartHeight <= 0f) return

        val index = dragColumnIndex.coerceIn(0, dataSize - 1)
        val boxLeft = chartLeftBound + columnWidth * index + paddingBetweenDays / 2
        val boxRight = chartLeftBound + columnWidth * (index + 1) - paddingBetweenDays / 2
        if (boxRight - boxLeft <= 0f) return

        val startY = timeToY(dragStartTime)
        val endY = timeToY(dragEndTime)
        // Safe for both drag directions and for reverseOrder.
        val boxTop = minOf(startY, endY)
        var boxBottom = maxOf(startY, endY)

        // Right after the long press start is equal to end, keep a 15 minute hint visible.
        val minHeight = chartHeight * scaleFactor * minDragDurationInMillis / dayInMillis
        if (boxBottom - boxTop < minHeight) boxBottom = boxTop + minHeight

        recordBounds.set(boxLeft, boxTop, boxRight, boxBottom)
        // Translucent background.
        canvas.drawRoundRect(
            recordBounds,
            recordCornerRadius,
            recordCornerRadius,
            dragPreviewPaint,
        )
        // Outline.
        canvas.drawRoundRect(
            recordBounds,
            recordCornerRadius,
            recordCornerRadius,
            dragPreviewStrokePaint,
        )

        drawNewRecordPreviewLabel(
            canvas = canvas,
            boxLeft = boxLeft,
            boxRight = boxRight,
            boxTop = boxTop,
        )
    }

    /**
     * Draws the edit mode overlay on top of the record being edited.
     *
     * While a change is pending the block is shown as the same translucent
     * ghost used when dragging out a new record, which reads as "not saved
     * yet", together with its live start - end label. Without pending changes
     * only a highlighted outline marks the block. Both ends always carry a
     * capsule handle, whose position follows the previewed range, so it tracks
     * the finger while dragging and stays correct under reverseOrder, where the
     * start of the day is at the top.
     */
    private fun drawEditOverlay(canvas: Canvas) {
        if (chartHeight <= 0f) return
        if (editRecordId == null) return

        val index = dragColumnIndex.coerceIn(0, dataSize - 1)
        val boxLeft = chartLeftBound + columnWidth * index + paddingBetweenDays / 2
        val boxRight = chartLeftBound + columnWidth * (index + 1) - paddingBetweenDays / 2
        if (boxRight - boxLeft <= 0f) return

        val startY = timeToY(dragStartTime)
        val endY = timeToY(dragEndTime)
        val boxTop = minOf(startY, endY)
        var boxBottom = maxOf(startY, endY)
        // Keep the shortest possible range visible.
        val minHeight = chartHeight * scaleFactor * minDragDurationInMillis / dayInMillis
        if (boxBottom - boxTop < minHeight) boxBottom = boxTop + minHeight

        recordBounds.set(boxLeft, boxTop, boxRight, boxBottom)
        canvas.save()
        canvas.scale(
            editSelectionScale,
            editSelectionScale,
            recordBounds.centerX(),
            recordBounds.centerY(),
        )
        canvas.drawRoundRect(
            recordBounds,
            recordCornerRadius,
            recordCornerRadius,
            editSelectionHaloPaint,
        )
        if (hasPendingEdit) {
            canvas.drawRoundRect(
                recordBounds,
                recordCornerRadius,
                recordCornerRadius,
                dragPreviewPaint,
            )
            canvas.drawRoundRect(
                recordBounds,
                recordCornerRadius,
                recordCornerRadius,
                dragPreviewStrokePaint,
            )
            drawNewRecordPreviewLabel(
                canvas = canvas,
                boxLeft = boxLeft,
                boxRight = boxRight,
                boxTop = boxTop,
            )
        } else {
            canvas.drawRoundRect(
                recordBounds,
                recordCornerRadius,
                recordCornerRadius,
                editOutlinePaint,
            )
        }

        // Capsule handles, never narrower than the minimum touch friendly width.
        val columnWidthInPx = boxRight - boxLeft
        val handleWidth = columnWidthInPx.coerceAtLeast(editHandleMinWidth)
        val handleLeft = (boxLeft + boxRight) / 2f - handleWidth / 2f
        val handleRight = handleLeft + handleWidth
        val halfHandle = editHandleHeight / 2f

        editHandleBounds.set(handleLeft, startY - halfHandle, handleRight, startY + halfHandle)
        canvas.drawRoundRect(
            editHandleBounds,
            halfHandle,
            halfHandle,
            editHandlePaint,
        )

        editHandleBounds.set(handleLeft, endY - halfHandle, handleRight, endY + halfHandle)
        canvas.drawRoundRect(
            editHandleBounds,
            halfHandle,
            halfHandle,
            editHandlePaint,
        )
        canvas.restore()
    }

    private fun drawNewRecordPreviewLabel(
        canvas: Canvas,
        boxLeft: Float,
        boxRight: Float,
        boxTop: Float,
    ) {
        if (dragPreviewLabel.isEmpty()) return

        dragPreviewTextPaint.getTextBounds(
            dragPreviewLabel,
            0,
            dragPreviewLabel.length,
            textBounds,
        )
        val textWidth = dragPreviewTextPaint.measureText(dragPreviewLabel)
        val textHeight = textBounds.height().toFloat()
        val halfWidth = textWidth / 2

        val centerX = (boxLeft + boxRight) / 2
        val minCenterX = chartLeftBound + halfWidth
        val maxCenterX = chartRightBound - halfWidth
        val textCenterX = if (minCenterX <= maxCenterX) {
            centerX.coerceIn(minCenterX, maxCenterX)
        } else {
            centerX
        }

        // Prefer drawing above the block, otherwise pin it to the block top edge.
        val aboveBaseline = boxTop - dragPreviewTextPadding
        val baseline = if (aboveBaseline - textHeight >= chartTopBound) {
            aboveBaseline
        } else {
            boxTop + dragPreviewTextPadding + textHeight
        }

        canvas.drawText(dragPreviewLabel, textCenterX, baseline, dragPreviewTextPaint)
    }

    private fun updateDragPreviewLabel() {
        dragPreviewLabel = formatDragTimeText(dragStartTime) +
            DRAG_PREVIEW_TIME_SEPARATOR +
            formatDragTimeText(dragEndTime)
    }

    /**
     * [dayOffset] uses the same units as RecordsCalendarViewData.Point.start,
     * which is [startOfDayShift] behind the wall clock, so the shift is added
     * back before formatting. Honors the military time setting.
     *
     * Runs on every ACTION_MOVE, so it only touches in memory fields and never
     * performs a Context / Resources lookup.
     */
    private fun formatDragTimeText(dayOffset: Long): String {
        val clockMillis = (dayOffset + startOfDayShift).mod(dayInMillis)
        val totalMinutes = (clockMillis / minuteInMillis).toInt()
        val hour = totalMinutes / MINUTES_IN_HOUR
        val minute = totalMinutes % MINUTES_IN_HOUR
        val minuteText = minute.toString().padStart(2, '0')

        return if (isMilitary) {
            hour.toString().padStart(2, '0') + ":" + minuteText
        } else {
            val isAfterMidday = hour >= 12
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(
                amPmTemplate,
                hour12.toString().padStart(2, '0') + ":" + minuteText,
                if (isAfterMidday) PM_SUFFIX else AM_SUFFIX,
            )
        }
    }

    private fun findDataPoint(
        x: Float,
        y: Float,
    ): Data? {
        return data.map(Column::data).flatten().firstOrNull {
            it.boxLeft < x && it.boxTop < y && it.boxRight > x && it.boxBottom > y
        }
    }

    private fun getTextView(
        textColor: Int,
        typeface: Typeface,
        widthLayoutParams: Int,
    ): AppCompatTextView {
        return AppCompatTextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, nameTextSize)
            setTextColor(textColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            this.typeface = typeface
            layoutParams = ViewGroup.LayoutParams(
                widthLayoutParams, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun animateSelectedRecord(
        selectedRecord: RecordsCalendarViewData.Point.Data,
    ) {
        val from = selectedRecord.color
        val to = ColorUtils.normalizeLightness(
            color = selectedRecord.color,
            factor = 0.2f,
        )
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), from, to)

        animator.duration = CLICK_ANIMATION_DURATION_MS
        animator.repeatCount = 1
        animator.repeatMode = ValueAnimator.REVERSE
        animator.addUpdateListener {
            selectedRecordColor = it.animatedValue as? Int
                ?: return@addUpdateListener
            invalidate()
        }
        animator.start()
    }

    private fun animateEditSelection() {
        editSelectionAnimator?.cancel()
        editSelectionAnimator = ValueAnimator.ofFloat(editSelectionScale, EDIT_SELECTION_SCALE).apply {
            duration = EDIT_SELECTION_ANIMATION_DURATION_MS
            addUpdateListener {
                editSelectionScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun calculateHoursData() {
        val hoursNumbers = (24 downTo 0)
            .map { if (it == 24 && startOfDayShift != 0L) 0 else it }
        hours = if (isMilitary) {
            hoursNumbers.map { hour ->
                val hourText = hour
                    .toString().padStart(2, '0')
                val hourTextFull = "$hourText:00"
                hourText to hourTextFull
            }
        } else {
            hoursNumbers.map { hour ->
                val isAfterMidday = hour > 12
                val hourText = (if (isAfterMidday) hour - 12 else hour)
                    .toString().padStart(2, '0')
                val hourTextFull = context.getString(
                    R.string.separator_template,
                    hourText,
                    if (isAfterMidday) "pm" else "am",
                )
                hourText to hourTextFull
            }
        }
    }

    private fun View.measureText(
        width: Int,
        widthSpec: Int,
        height: Int = 0,
        heightSpec: Int = MeasureSpec.UNSPECIFIED,
    ) {
        val specWidth = MeasureSpec.makeMeasureSpec(width, widthSpec)
        val specHeight = MeasureSpec.makeMeasureSpec(height, heightSpec)
        measure(specWidth, specHeight)
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private inner class Column(
        val legend: String,
        val highlighted: Boolean,
        val data: List<Data>,
        val rangeStart: Long,
        val rangeEnd: Long,
    )

    private inner class Data(
        val point: RecordsCalendarViewData.Point,
        val drawable: Drawable? = null,
        // Set after the fact.
        var columnCount: Int = 1,
        var columnNumber: Int = 1,
        var boxLeft: Float = 0f,
        var boxTop: Float = 0f,
        var boxRight: Float = 0f,
        var boxBottom: Float = 0f,
    )

    @Parcelize
    private class SavedState(
        val superSavedState: Parcelable?,
        val scaleFactor: Float,
        val lastScaleFactor: Float,
        val panFactor: Float,
        val lastPanFactor: Float,
    ) : BaseSavedState(superSavedState)

    data class ScaleState(
        val scaleFactor: Float,
        val panFactor: Float,
    )

    enum class DragState {
        IDLE,

        // Dragging a brand new range out of an empty area.
        DRAGGING_NEW,

        // Edit mode with a handle on both ends, no finger down.
        EDIT_IDLE,

        // Edit mode, dragging the handle of one end.
        EDIT_DRAGGING_START,
        EDIT_DRAGGING_END,

        // Edit mode, dragging the block itself.
        EDIT_DRAGGING_MOVE,
    }

    companion object {
        private const val CLICK_ANIMATION_DURATION_MS: Long = 250L
        private const val DEFAULT_SNAP_STEP_MINUTES: Int = 15
        private const val ADJACENT_RECORD_SNAP_THRESHOLD_MINUTES: Long = 15L
        private const val DRAG_PREVIEW_ALPHA: Int = 90
        private const val EDIT_SELECTION_HALO_ALPHA: Int = 110
        private const val EDIT_SELECTION_SCALE: Float = 1.025f
        private const val EDIT_SELECTION_ANIMATION_DURATION_MS: Long = 140L
        private const val DRAG_PREVIEW_TIME_SEPARATOR: String = " - "
        private const val MINUTES_IN_HOUR: Int = 60
        private const val AM_SUFFIX: String = "am"
        private const val PM_SUFFIX: String = "pm"
    }
}
