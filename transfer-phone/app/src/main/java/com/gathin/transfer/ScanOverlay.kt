package com.gathin.transfer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Dims the preview and leaves a rounded square where the QR should sit. */
class ScanOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3101419.toInt() }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6C57A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
    }
    private val hole = RectF()

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height) * 0.68f
        val left = (width - side) / 2f
        val top = (height - side) / 2f - 24f * resources.displayMetrics.density
        hole.set(left, top, left + side, top + side)
        val radius = 28f * resources.displayMetrics.density
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        canvas.drawRoundRect(hole, radius, radius, clear)
        canvas.restoreToCount(layer)
        canvas.drawRoundRect(hole, radius, radius, stroke)
    }
}
