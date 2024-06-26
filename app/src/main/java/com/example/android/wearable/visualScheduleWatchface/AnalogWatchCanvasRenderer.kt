/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.visualScheduleWatchface

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.graphics.toRectF
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.example.android.wearable.visualScheduleWatchface.calendar.Calendar
import com.example.android.wearable.visualScheduleWatchface.calendar.CalendarRequester
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val SIXTY_FPS_FRAME_PERIOD_MS: Long = 256
private const val BATTERY_SAVING_FRAME_PERIOD_MS: Long = 16

class AnalogWatchCanvasRenderer(
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
    private val calendar: Calendar,
    private val calendarRequester: CalendarRequester
) : Renderer.CanvasRenderer2<AnalogWatchCanvasRenderer.AnalogSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    BATTERY_SAVING_FRAME_PERIOD_MS,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class AnalogSharedAssets : SharedAssets {
        override fun onDestroy() {
        }
    }

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val progressBarStrokeWidth = 25F
    private val textPaintSize = 25F

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = textPaintSize
        color = Color.parseColor("#EEEBDD")
    }

    private val timePaint = Paint().apply {
        isAntiAlias = true
        textSize = 60.toFloat()
        color = Color.parseColor("#EEEBDD")
    }

    private val emojiPaint = Paint().apply {
        isAntiAlias = true
        textSize = 120.toFloat()
        color = Color.WHITE
    }

    private val circlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#F2F2F2")
        style = Paint.Style.STROKE
        strokeWidth = progressBarStrokeWidth
    }

    private val progressPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#0aa1a4")
        style = Paint.Style.STROKE
        strokeWidth = progressBarStrokeWidth
    }

    init {
        scope.launch {
            calendar.getCalendarInfo()
        }
    }

    override suspend fun createSharedAssets(): AnalogSharedAssets {
        return AnalogSharedAssets()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        scope.cancel("AnalogWatchCanvasRenderer scope clear() request")
        super.onDestroy()
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        val percentage = this.calendar.getPercentage(zonedDateTime)
        if (percentage >= 100) {
            this.calendar.getCalendarInfo()
        }

        this.interactiveDrawModeUpdateDelayMillis = if(this.calendarRequester.requestInProgress) SIXTY_FPS_FRAME_PERIOD_MS else BATTERY_SAVING_FRAME_PERIOD_MS

        canvas.drawColor(Color.parseColor("#000000"))
        this.displayWatchFaceElements(canvas, bounds, zonedDateTime, percentage)
    }

    private fun displayWatchFaceElements(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, percentage: Float){
        this.displayProgressBar(canvas, bounds, percentage)
        this.displayEmoji(canvas, bounds)
        this.displaySummaryText(canvas, bounds)
        this.displayEndText(canvas, bounds)
        this.displayTime(canvas, bounds, zonedDateTime)
        this.displayDate(canvas, bounds, zonedDateTime)
    }

    private fun displayProgressBar(canvas: Canvas, bounds: Rect, percentage: Float){
        val radius = (bounds.width() / 2).toFloat() - this.progressBarStrokeWidth/2F
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, this.circlePaint)
        canvas.drawArc(this.getProgressBarBounds(bounds), 270F, 360 * (percentage / 100), false, progressPaint)
    }

    private fun getProgressBarBounds(bounds: Rect): RectF {
        val progressBounds = bounds.toRectF()
        progressBounds.right = progressBounds.right - this.progressBarStrokeWidth/2F
        progressBounds.left = progressBounds.left + this.progressBarStrokeWidth/2F
        progressBounds.bottom = progressBounds.bottom - this.progressBarStrokeWidth/2F
        progressBounds.top = progressBounds.top + this.progressBarStrokeWidth/2F
        return progressBounds
    }

    private fun displayEmoji(canvas: Canvas, bounds: Rect){
        // Emoji is not set relatively because the bounds function doesn't work correctly
        val width = bounds.exactCenterX() - 70
        val height = bounds.exactCenterY() + 40
        canvas.drawText(this.calendar.emoji, width, height, emojiPaint)
    }

    private fun displayEndText(canvas: Canvas, bounds: Rect){
        val textBounds = this.getTextBounds(this.calendar.endText, textPaint)
        val width = this.getCenterXCoordinate(bounds, textBounds)
        val height = bounds.bottom - textBounds.height() - this.progressBarStrokeWidth * 2
        canvas.drawText(this.calendar.endText, width, height, textPaint)
    }

    private fun displaySummaryText(canvas: Canvas, bounds: Rect){
        val textBounds = this.getTextBounds(this.calendar.summaryText, textPaint)
        val width = this.getCenterXCoordinate(bounds, textBounds)
        val height = bounds.bottom - textBounds.height() - this.progressBarStrokeWidth * 2 - this.textPaintSize - 20F
        canvas.drawText(this.calendar.summaryText, width, height, textPaint)
    }


    private fun displayDate(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime){
        val timeFormatter = DateTimeFormatter.ofPattern("E, MMM d")
        val dateText = zonedDateTime.format(timeFormatter)
        val textBounds = this.getTextBounds(dateText, textPaint)
        val width = this.getCenterXCoordinate(bounds, textBounds)
        val height = bounds.top + textBounds.height() + this.progressBarStrokeWidth * 2
        canvas.drawText(dateText, width, height, textPaint)
    }

    private fun displayTime(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime){
        val timeFormatter = DateTimeFormatter.ofPattern("H:mm")
        val time = zonedDateTime.format(timeFormatter)
        val textBounds = this.getTextBounds(time, timePaint)
        val width = this.getCenterXCoordinate(bounds, textBounds)
        val margin = 20F
        val height = bounds.top + textBounds.height() + this.progressBarStrokeWidth * 2 + this.textPaintSize + margin
        canvas.drawText(time, width, height, timePaint)
    }

    private fun getTextBounds(text: String, paint: Paint): Rect {
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        return textBounds
    }

    private fun getCenterXCoordinate(bounds: Rect, textBounds: Rect): Float {
        return bounds.exactCenterX() - textBounds.width() / 2
    }

    companion object {
        private const val TAG = "AnalogWatchCanvasRenderer"
    }
}
