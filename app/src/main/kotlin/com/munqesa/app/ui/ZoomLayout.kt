package com.munqesa.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * Pinch-to-zoom container. When scale == 1 the child (ScrollView) handles
 * scrolling normally; when zoomed in, this layout intercepts touch events
 * and allows panning + pinch zoom.
 */
class ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ScaleGestureDetector.OnScaleGestureListener {

    private var scaleFactor = 1f
    private var posX = 0f
    private var posY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private val detector = ScaleGestureDetector(context, this)

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = scaleFactor.coerceIn(1f, 5f)
        if (scaleFactor <= 1.02f) {
            scaleFactor = 1f
            posX = 0f
            posY = 0f
        }
        invalidate()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = scaleFactor > 1.02f

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    val dx = ev.getX(pointerIndex) - lastTouchX
                    val dy = ev.getY(pointerIndex) - lastTouchY
                    posX += dx
                    posY += dy
                    lastTouchX = ev.getX(pointerIndex)
                    lastTouchY = ev.getY(pointerIndex)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                activePointerId = MotionEvent.INVALID_POINTER_ID
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        canvas.translate(posX / scaleFactor, posY / scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
