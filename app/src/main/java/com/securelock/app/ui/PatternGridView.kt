package com.securelock.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class PatternGridView(context: Context) : View(context) {

    var onComplete: ((String) -> Unit)? = null

    private val selected = mutableListOf<Int>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 7f
        style = Paint.Style.STROKE
    }

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private fun center(index: Int): Pair<Float, Float> {
        val size = min(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val gap = size / 4f

        return Pair(
            left + gap * ((index % 3) + 1),
            top + gap * ((index / 3) + 1)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val path = Path()

        selected.forEachIndexed { position, index ->
            val (x, y) = center(index)

            if (position == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        paint.color = Color.rgb(80, 190, 130)
        canvas.drawPath(path, paint)

        for (i in 0..8) {
            val (x, y) = center(i)

            dot.color = if (selected.contains(i)) {
                Color.rgb(80, 190, 130)
            } else {
                Color.WHITE
            }

            canvas.drawCircle(
                x,
                y,
                if (selected.contains(i)) 17f else 11f,
                dot
            )
        }
    }

    private fun addNearest(x: Float, y: Float) {
        val radius = min(width, height) / 8f

        for (index in 0..8) {
            if (selected.contains(index)) continue

            val (cx, cy) = center(index)

            val distance = sqrt(
                (x - cx) * (x - cx) +
                    (y - cy) * (y - cy)
            )

            if (distance <= radius) {
                selected.add(index)
                invalidate()
                return
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selected.clear()
                addNearest(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                addNearest(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val value = selected.joinToString("-")

                if (selected.distinct().size >= 4) {
                    onComplete?.invoke(value)
                }

                selected.clear()
                invalidate()

                return true
            }
        }

        return true
    }
}
