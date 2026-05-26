package com.aicontrol.android.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 单轴虚拟摇杆控件
 * axis=vertical: 只能上下拖动（控制前后）
 * axis=horizontal: 只能左右拖动（控制左右转向）
 *
 * 回调 onChange(percent: Float):
 *   vertical: -1.0(最上/前进) ~ 0.0(中间) ~ 1.0(最下/后退)
 *   horizontal: -1.0(最左) ~ 0.0(中间) ~ 1.0(最右)
 */
class SingleAxisJoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Axis { VERTICAL, HORIZONTAL }

    var axis: Axis = Axis.VERTICAL
    var onMove: ((percent: Float) -> Unit)? = null
    var onRelease: (() -> Unit)? = null

    // 外圈参数
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 摇杆球参数
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 方向指示文字
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var trackRadius = 0f
    private var thumbRadius = 0f

    private var currentPercent = 0f  // -1.0 ~ 1.0

    init {
        // 读取自定义属性
        val ta = context.obtainStyledAttributes(attrs, intArrayOf(
            android.R.attr.layout_width,
            android.R.attr.layout_height
        ))
        ta.recycle()

        // 外圈填充 — 深色半透明
        trackFillPaint.color = Color.parseColor("#1e293b")
        trackFillPaint.style = Paint.Style.FILL

        // 外圈描边
        trackStrokePaint.color = Color.parseColor("#334155")
        trackStrokePaint.style = Paint.Style.STROKE
        trackStrokePaint.strokeWidth = 4f

        // 摇杆球体 — 渐变模拟3D
        thumbPaint.style = Paint.Style.FILL

        // 方向文字
        labelPaint.color = Color.parseColor("#64748b")
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.typeface = Typeface.DEFAULT_BOLD
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        trackRadius = Math.min(w, h) / 2f - 16f
        thumbRadius = trackRadius * 0.35f
        labelPaint.textSize = thumbRadius * 0.7f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 绘制轨道活动区域指示线（轴线）
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        linePaint.color = Color.parseColor("#1e3a5f")
        linePaint.strokeWidth = 6f
        linePaint.strokeCap = Paint.Cap.ROUND

        if (axis == Axis.VERTICAL) {
            // 画竖直轨道线
            canvas.drawLine(centerX, centerY - trackRadius * 0.8f, centerX, centerY + trackRadius * 0.8f, linePaint)
        } else {
            // 画水平轨道线
            canvas.drawLine(centerX - trackRadius * 0.8f, centerY, centerX + trackRadius * 0.8f, centerY, linePaint)
        }

        // 2. 绘制外圈
        canvas.drawCircle(centerX, centerY, trackRadius, trackFillPaint)
        canvas.drawCircle(centerX, centerY, trackRadius, trackStrokePaint)

        // 3. 计算摇杆球位置
        val thumbX: Float
        val thumbY: Float

        if (axis == Axis.VERTICAL) {
            thumbX = centerX
            thumbY = centerY - currentPercent * trackRadius * 0.8f
        } else {
            thumbX = centerX + currentPercent * trackRadius * 0.8f
            thumbY = centerY
        }

        // 4. 绘制摇杆球阴影
        thumbShadowPaint.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(
            thumbX - thumbRadius + 4f,
            thumbY - thumbRadius + 6f,
            thumbX + thumbRadius + 4f,
            thumbY + thumbRadius + 6f,
            thumbShadowPaint
        )

        // 5. 绘制摇杆球体（3D渐变）
        val gradient = RadialGradient(
            thumbX - thumbRadius * 0.3f,
            thumbY - thumbRadius * 0.3f,
            thumbRadius * 0.1f,
            thumbX,
            thumbY,
            thumbRadius,
            longArrayOf(
                Color.parseColor("#60a5fa").toLong(),  // 高光
                Color.parseColor("#3b82f6").toLong(),  // 主色
                Color.parseColor("#1e40af").toLong()   // 阴影
            ),
            null,
            Shader.TileMode.CLAMP
        )
        thumbPaint.shader = gradient
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

        // 6. 绘制方向标签
        if (axis == Axis.VERTICAL) {
            canvas.drawText("F", centerX, centerY - trackRadius * 0.65f, labelPaint)
            canvas.drawText("B", centerX, centerY + trackRadius * 0.75f, labelPaint)
        } else {
            canvas.drawText("L", centerX - trackRadius * 0.7f, centerY + thumbRadius * 0.25f, labelPaint)
            canvas.drawText("R", centerX + trackRadius * 0.7f, centerY + thumbRadius * 0.25f, labelPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY

                if (axis == Axis.VERTICAL) {
                    // 纵向：向上为负(前进)，向下为正(后退)
                    val raw = -dy / (trackRadius * 0.8f)
                    currentPercent = raw.coerceIn(-1f, 1f)
                } else {
                    // 横向：向左为负，向右为正
                    val raw = dx / (trackRadius * 0.8f)
                    currentPercent = raw.coerceIn(-1f, 1f)
                }

                // 死区过滤：小于 5% 的偏移归零
                if (Math.abs(currentPercent) < 0.05f) currentPercent = 0f

                onMove?.invoke(currentPercent)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                // 回弹动画
                animateReturn()
                onRelease?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 松手后摇杆弹回中心
     */
    private fun animateReturn() {
        val startPercent = currentPercent
        val startTime = System.currentTimeMillis()
        val duration = 150L

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                // ease-out
                val ease = 1f - (1f - t) * (1f - t)
                currentPercent = startPercent * (1f - ease)
                if (t >= 1f) {
                    currentPercent = 0f
                    invalidate()
                    onMove?.invoke(0f)
                    return
                }
                onMove?.invoke(currentPercent)
                invalidate()
                handler.postDelayed(this, 16)
            }
        })
    }
}
