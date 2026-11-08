package com.munqesa.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import android.view.animation.PathInterpolator
import com.munqesa.app.R

/**
 * iOS 26 Liquid Glass bottom navigation bar - refined & elegant.
 */
class LiquidGlassBottomNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class NavItem(val id: Int, val label: String, val iconRes: Int)

    private val items = mutableListOf<NavItem>()
    private var selectedId: Int = -1
    private var onItemSelectedListener: ((Int) -> Unit)? = null

    // Colors - neumorphism light palette
    private val colorActiveIcon = Color.parseColor("#243B64")
    private val colorInactiveIcon = Color.parseColor("#9AA6B5")
    private val colorActiveLabel = Color.parseColor("#243B64")
    private val colorInactiveLabel = Color.parseColor("#9AA6B5")
    private val colorActiveIndicator = Color.parseColor("#3157A4")
    private val colorActiveBorder = Color.parseColor("#19B9B5")
    private val colorGlassBg = Color.parseColor("#EDF1F6")
    private val colorGlassBorder = Color.parseColor("#C8D0DB")
    private val colorShadow = Color.parseColor("#C8D0DB")
    private val colorHighlight = Color.parseColor("#FFFFFF")
    private val colorHighlight2 = Color.argb(60, 255, 255, 255)

    private val density = resources.displayMetrics.density
    private val cornerRadius = 32f * density
    private val navHeight = 68f * density
    private val navMarginH = 12f * density
    private val navMarginBottom = 12f * density
    private val itemPaddingH = 6f * density
    private val itemPaddingV = 4f * density
    private val iconSize = 24f * density
    private val textSize = 11f * density
    private val indicatorCorner = 32f * density

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGlassBg
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGlassBorder
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorShadow
        maskFilter = BlurMaskFilter(20f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorActiveIndicator
        style = Paint.Style.FILL
    }
    private val indicatorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorActiveBorder
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorHighlight
        style = Paint.Style.FILL
    }
    private val highlightPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorHighlight2
        style = Paint.Style.FILL
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSize
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val itemRects = mutableListOf<RectF>()
    private var indicatorLeft = 0f
    private var indicatorTop = 0f
    private var indicatorWidth = 0f
    private var indicatorHeight = 0f
    private var indicatorAnimator: ValueAnimator? = null

    private val iconDrawables = mutableListOf<android.graphics.drawable.Drawable?>()

    // كائنات مُعاد استخدامها داخل onDraw (لا نُنشئ أي كائن أثناء الرسم)
    private val navRect = RectF()
    private val highlightRect = RectF()
    private val indicatorRect = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var shadowBitmap: Bitmap? = null

    init {
        // لا نستخدم LAYER_TYPE_SOFTWARE: الظل يُرسم مرة واحدة في Bitmap ثم يُنسخ بالمعالج الرسومي
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadowBitmap?.recycle()
        shadowBitmap = null
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val topMargin = (h - navHeight) / 2f
        val r = RectF(
            navMarginH, topMargin + 3 * density,
            w - navMarginH, topMargin + navHeight + 3 * density
        )
        Canvas(bmp).drawRoundRect(r, cornerRadius, cornerRadius, shadowPaint)
        shadowBitmap = bmp
    }

    override fun onDetachedFromWindow() {
        indicatorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    fun setItems(newItems: List<NavItem>) {
        items.clear()
        items.addAll(newItems)
        itemRects.clear()
        iconDrawables.clear()
        for (item in items) {
            iconDrawables.add(ContextCompat.getDrawable(context, item.iconRes))
        }
        if (items.isNotEmpty() && selectedId == -1) {
            selectedId = items[0].id
        }
        requestLayout()
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }

    fun setSelectedItem(id: Int) {
        if (id != selectedId) {
            selectedId = id
            updateSelection()
            invalidate()
        }
    }

    private fun selectItem(id: Int) {
        if (id == selectedId) return
        selectedId = id
        onItemSelectedListener?.invoke(id)
        updateSelection()
        invalidate()
    }

    private fun updateSelection() {
        val index = items.indexOfFirst { it.id == selectedId }
        if (index < 0) return

        if (itemRects.isNotEmpty() && index < itemRects.size) {
            // Clamp indicator within nav bar bounds
            val navLeft = navMarginH
            val navRight = width - navMarginH
            val rawLeft = itemRects[index].left + itemPaddingH
            val rawRight = itemRects[index].right - itemPaddingH
            val clampedLeft = maxOf(rawLeft, navLeft + itemPaddingH)
            val clampedRight = minOf(rawRight, navRight - itemPaddingH)
            val targetLeft = clampedLeft
            val targetWidth = clampedRight - clampedLeft

            val targetTop = (height - navHeight) / 2f + itemPaddingV
            val targetHeight = navHeight - itemPaddingV * 2

            val startLeft = indicatorLeft
            val startTop = indicatorTop
            val startWidth = indicatorWidth
            val startHeight = indicatorHeight

            indicatorAnimator?.cancel()
            indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 480
                // easeOutBack: ارتداد خفيف في نهاية الحركة كما في iOS
                interpolator = PathInterpolator(0.34f, 1.45f, 0.64f, 1f)
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    indicatorLeft = startLeft + (targetLeft - startLeft) * fraction
                    indicatorTop = startTop + (targetTop - startTop) * fraction
                    indicatorWidth = startWidth + (targetWidth - startWidth) * fraction
                    indicatorHeight = startHeight + (targetHeight - startHeight) * fraction
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (navHeight + navMarginBottom * 2).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = width.toFloat()
        val topMargin = (height - navHeight) / 2f
        val itemWidth = w / items.size

        itemRects.clear()
        // RTL: draw items from right to left
        for (i in items.indices) {
            val itemLeft = (items.size - 1 - i) * itemWidth
            itemRects.add(RectF(itemLeft, topMargin, itemLeft + itemWidth, topMargin + navHeight))
        }

        if (indicatorWidth == 0f && items.isNotEmpty()) {
            val index = items.indexOfFirst { it.id == selectedId }
            if (index >= 0 && index < itemRects.size) {
                val navLeft = navMarginH
                val navRight = width - navMarginH
                val rawLeft = itemRects[index].left + itemPaddingH
                val rawRight = itemRects[index].right - itemPaddingH
                indicatorLeft = maxOf(rawLeft, navLeft + itemPaddingH)
                indicatorWidth = minOf(rawRight, navRight - itemPaddingH) - indicatorLeft
                indicatorHeight = navHeight - itemPaddingV * 2
                indicatorTop = topMargin + itemPaddingV
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val topMargin = (h - navHeight) / 2f
        val navLeft = navMarginH
        val navRight = w - navMarginH
        val navTop = topMargin
        val navBottom = topMargin + navHeight
        navRect.set(navLeft, navTop, navRight, navBottom)

        // Shadow (مُخزَّن مسبقًا)
        shadowBitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }

        // Glass background
        canvas.drawRoundRect(navRect, cornerRadius, cornerRadius, glassPaint)
        canvas.drawRoundRect(navRect, cornerRadius, cornerRadius, borderPaint)

        // Subtle bottom highlight
        highlightRect.set(navLeft + 6 * density, navBottom - 10 * density, navRight - 6 * density, navBottom - 4 * density)
        canvas.drawRoundRect(highlightRect, 8 * density, 8 * density, highlightPaint2)

        // Active indicator
        if (indicatorWidth > 0) {
            indicatorRect.set(indicatorLeft, indicatorTop, indicatorLeft + indicatorWidth, indicatorTop + indicatorHeight)
            canvas.drawRoundRect(indicatorRect, indicatorCorner, indicatorCorner, indicatorPaint)
            canvas.drawRoundRect(indicatorRect, indicatorCorner, indicatorCorner, indicatorBorderPaint)
        }

        // Draw items
        for (i in items.indices) {
            if (i >= itemRects.size) break
            val rect = itemRects[i]
            val isActive = items[i].id == selectedId
            val centerX = rect.centerX()
            val iconY = rect.top + (rect.height() - iconSize) / 2f - 8 * density

            // Draw icon
            val drawable = iconDrawables.getOrNull(i)
            if (drawable != null) {
                val iconLeft = (centerX - iconSize / 2f).toInt()
                val iconTop = iconY.toInt()
                drawable.setBounds(iconLeft, iconTop, (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                drawable.setTint(if (isActive) colorActiveIcon else colorInactiveIcon)
                drawable.draw(canvas)
            }

            // Draw label
            val labelY = rect.bottom - 6 * density
            textPaint.color = if (isActive) colorActiveLabel else colorInactiveLabel
            textPaint.textSize = textSize
            canvas.drawText(items[i].label, centerX, labelY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                for (i in itemRects.indices) {
                    if (itemRects[i].contains(x, event.y)) {
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val x = event.x
                for (i in itemRects.indices) {
                    if (itemRects[i].contains(x, event.y)) {
                        selectItem(items[i].id)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
