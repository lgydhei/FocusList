package com.bistu.focuslist.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.bistu.focuslist.R
import kotlin.math.max
import kotlin.math.min

/** 统计页使用的轻量 7 天专注趋势柱状图。 */
class WeeklyFocusChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<DailyFocusUi> = emptyList()
    private val barRect = RectF()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val barBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_variant)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_dark)
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
        isFakeBoldText = true
    }

    fun setData(data: List<DailyFocusUi>) {
        items = data
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(150f).toInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val left = paddingLeft + dp(6f)
        val right = width - paddingRight - dp(6f)
        val top = paddingTop + dp(18f)
        val bottom = height - paddingBottom - dp(28f)
        val chartHeight = max(dp(36f), bottom - top)
        val slotWidth = (right - left) / items.size
        val barWidth = min(dp(26f), slotWidth * 0.45f)
        val maxMinutes = items.maxOf { it.minutes }.coerceAtLeast(1)
        val radius = dp(6f)

        items.forEachIndexed { index, item ->
            val centerX = left + slotWidth * index + slotWidth / 2f
            val bgLeft = centerX - barWidth / 2f
            val bgRight = centerX + barWidth / 2f

            barRect.set(bgLeft, top, bgRight, bottom)
            canvas.drawRoundRect(barRect, radius, radius, barBackgroundPaint)

            if (item.minutes > 0) {
                val barHeight = max(dp(8f), chartHeight * item.minutes / maxMinutes)
                barRect.set(bgLeft, bottom - barHeight, bgRight, bottom)
                canvas.drawRoundRect(barRect, radius, radius, barPaint)
                canvas.drawText("${item.minutes}", centerX, bottom - barHeight - dp(5f), valuePaint)
            }

            canvas.drawText(item.label, centerX, height - paddingBottom - dp(7f), labelPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
