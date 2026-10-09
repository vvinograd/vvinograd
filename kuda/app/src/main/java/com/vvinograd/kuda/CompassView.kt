package com.vvinograd.kuda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

/** Круглый циферблат: стрелка к цели и метка «С» (север). */
class CompassView(context: Context) : View(context) {

    /** Угол стрелки к цели, null — направление неизвестно. */
    var arrowAngle: Float? = null
        set(value) { field = value; invalidate() }

    /** Куда сейчас север относительно верха телефона, null — компаса нет. */
    var northAngle: Float? = null
        set(value) { field = value; invalidate() }

    var arrived = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6 * density
        color = Color.parseColor("#2B3E63")
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2 * density
        color = Color.parseColor("#3D5A80")
    }
    private val north = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E63946")
        textSize = 20 * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FCA311") }
    private val arrowShade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C77D00") }
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8DA2C0")
        textSize = 64 * density
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, (320 * density).toInt())
        setMeasuredDimension(w, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 16 * density

        canvas.drawCircle(cx, cy, r, ring)

        // Деления и «С» поворачиваются вместе со сторонами света
        val n = northAngle
        if (n != null) {
            canvas.save()
            canvas.rotate(n, cx, cy)
            for (i in 0 until 12) {
                canvas.drawLine(cx, cy - r + 6 * density, cx, cy - r + 18 * density, tick)
                canvas.rotate(30f, cx, cy)
            }
            canvas.drawText("С", cx, cy - r + 42 * density, north)
            canvas.restore()
        }

        if (arrived) {
            canvas.drawText("🎉", cx, cy + 22 * density, placeholder)
            return
        }

        val a = arrowAngle
        if (a == null) {
            canvas.drawText("?", cx, cy + 22 * density, placeholder)
            return
        }

        val len = r * 0.72f
        val half = r * 0.30f
        canvas.save()
        canvas.rotate(a, cx, cy)
        // левая половина стрелки светлее, правая темнее — получается объём
        path.reset()
        path.moveTo(cx, cy - len)
        path.lineTo(cx - half, cy + len * 0.55f)
        path.lineTo(cx, cy + len * 0.25f)
        path.close()
        canvas.drawPath(path, arrow)
        path.reset()
        path.moveTo(cx, cy - len)
        path.lineTo(cx + half, cy + len * 0.55f)
        path.lineTo(cx, cy + len * 0.25f)
        path.close()
        canvas.drawPath(path, arrowShade)
        canvas.restore()
    }
}
