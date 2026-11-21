package com.youngwhite.gametranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

/**
 * Marco transparente arrastrable y redimensionable para seleccionar
 * únicamente la caja de diálogo del videojuego.
 */
class RegionSelectorView(context: Context) : View(context) {

    var selectedRect = Rect(120, 900, 960, 1250)
        private set

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#818CF8")
        style = Paint.Style.FILL
    }

    private var lastX = 0f
    private var lastY = 0f
    private var isResizing = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(selectedRect, borderPaint)
        // Tirador en esquina inferior derecha para redimensionar
        canvas.drawCircle(
            selectedRect.right.toFloat(),
            selectedRect.bottom.toFloat(),
            22f,
            handlePaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val nearCorner = kotlin.math.hypot(
                    (x - selectedRect.right).toDouble(),
                    (y - selectedRect.bottom).toDouble()
                ) < 60
                isResizing = nearCorner
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (x - lastX).toInt()
                val dy = (y - lastY).toInt()
                if (isResizing) {
                    selectedRect.right = (selectedRect.right + dx).coerceAtLeast(selectedRect.left + 150)
                    selectedRect.bottom = (selectedRect.bottom + dy).coerceAtLeast(selectedRect.top + 80)
                } else {
                    selectedRect.offset(dx, dy)
                }
                lastX = x
                lastY = y
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
