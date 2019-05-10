/**
 *
 * @author Wojciech Zahradnik 2019-05-10
 * @version 1.0.0
 *
 * Based on
 * AigeStudio 2016-06-17
 * version 1.1.0
 * https://github.com/AigeStudio/WheelPicker
 *
 */


package com.wojciechzahradnik.wheelpicker

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller

class WheelPicker<T> constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), Runnable {

    private val mHandler = Handler()

    private var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.LINEAR_TEXT_FLAG)
    private val scroller: Scroller
    private var tracker: VelocityTracker? = null

    private var onItemSelectedListener: OnItemSelectedListener<T>? = null
    private var onWheelChangeListener: OnWheelChangeListener? = null

    private val rectDrawn: Rect
    private val rectIndicatorHead: Rect
    private val rectIndicatorFoot: Rect
    private val rectCurrentItem: Rect

    private val camera: Camera
    private val matrixRotate: Matrix
    private val matrixDepth: Matrix

    var data: ArrayList<T> = arrayListOf()
        set(value) {
            field = value

            if (selectedItemPosition > value.size - 1 || currentItemPosition > value.size - 1) {
                currentItemPosition = value.size - 1
                selectedItemPosition = currentItemPosition
            } else {
                selectedItemPosition = currentItemPosition
            }
            scrollOffsetY = 0
            computeTextSize()
            computeFlingLimitY()
            requestLayout()
            invalidate()
        }

    var maximumWidthText: String? = null
        set(value) {
            field = value
            if (value == null)
                return
            field = value
            computeTextSize()
            requestLayout()
            invalidate()
        }

    var visibleItemCount: Int = 0
        set(value) {
            field = value
            updateVisibleItemCount()
            requestLayout()
        }
    private var drawnItemCount: Int = 0

    private var halfDrawnItemCount: Int = 0

    private var textMaxWidth: Int = 0
    private var textMaxHeight: Int = 0

    var typeface: Typeface
        set(value) {
            paint.typeface = value
            computeTextSize()
            requestLayout()
            invalidate()
        }
        get() {
            return paint.typeface
        }

    var itemTextColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var selectedItemTextColor: Int = 0
        set(value) {
            field = value
            computeCurrentItemRect()
            invalidate()
        }

    var itemTextSize: Int = 0
        set(value) {
            field = value
            paint.textSize = value.toFloat()
            computeTextSize()
            requestLayout()
            invalidate()
        }

    var indicatorSize: Int = 0
        set(value) {
            field = value
            computeIndicatorRect()
            invalidate()
        }

    var indicatorColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var curtainColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var itemSpace: Int = 0
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var curveTo: Int = CURVE_TO_CENTER
        set(value) {
            field = value
            computeDrawnCenter()
            invalidate()
        }

    var itemGravity: Int = ITEM_GRAVITY_CENTER
        set(value) {
            field = value
            computeDrawnCenter()
            invalidate()
        }

    private var itemHeight: Int = 0
    private var halfItemHeight: Int = 0

    private var halfWheelHeight: Int = 0

    var selectedItemPosition: Int = 0
        set(value) {
            var position = value
            position = Math.min(position, data.size - 1)
            position = Math.max(position, 0)
            field = position
            currentItemPosition = position
            scrollOffsetY = 0
            computeFlingLimitY()
            requestLayout()
            invalidate()
        }

    var currentItemPosition: Int = 0

    private var minFlingY: Int = 0
    private var maxFlingY: Int = 0

    private var minimumVelocity = 50
    private var maximumVelocity = 8000

    private var wheelCenterX: Int = 0
    private var wheelCenterY: Int = 0

    private var drawnCenterX: Int = 0
    private var drawnCenterY: Int = 0

    private var scrollOffsetY: Int = 0

    var textMaxWidthPosition: Int = 0
        set(value) {
            field = value
            if (!isPosInRang(value))
                return
            computeTextSize()
            requestLayout()
            invalidate()
        }

    private var lastPointY: Int = 0

    private var downPointY: Int = 0

    private var touchSlop = 8

    var hasSameWidth: Boolean = false
        set(value) {
            field = value
            computeTextSize()
            requestLayout()
            invalidate()
        }

    var hasIndicator: Boolean = false
        set(value) {
            field = value
            computeIndicatorRect()
            invalidate()
        }

    var hasCurtain: Boolean = false
        set(value) {
            field = value
            computeCurrentItemRect()
            invalidate()
        }

    var hasAtmospheric: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isCyclic: Boolean = false
        set(value) {
            field = value
            computeFlingLimitY()
            invalidate()
        }

    var isCurved: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var isClick: Boolean = false

    private var isForceFinishScroll: Boolean = false

    var isDebug: Boolean = false

    init {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.WheelPicker)
        itemTextSize = attributes.getDimensionPixelSize(
            R.styleable.WheelPicker_wheel_item_text_size,
            resources.getDimensionPixelSize(R.dimen.WheelItemTextSize)
        )
        visibleItemCount = attributes.getInt(R.styleable.WheelPicker_wheel_visible_item_count, 7)
        selectedItemPosition = attributes.getInt(R.styleable.WheelPicker_wheel_selected_item_position, 0)
        hasSameWidth = attributes.getBoolean(R.styleable.WheelPicker_wheel_same_width, false)
        textMaxWidthPosition = attributes.getInt(R.styleable.WheelPicker_wheel_maximum_width_text_position, -1)
        maximumWidthText = attributes.getString(R.styleable.WheelPicker_wheel_maximum_width_text)
        selectedItemTextColor = attributes.getColor(R.styleable.WheelPicker_wheel_selected_item_text_color, -1)
        itemTextColor = attributes.getColor(R.styleable.WheelPicker_wheel_item_text_color, -0x777778)
        itemSpace = attributes.getDimensionPixelSize(
            R.styleable.WheelPicker_wheel_item_space,
            resources.getDimensionPixelSize(R.dimen.WheelItemSpace)
        )
        isCyclic = attributes.getBoolean(R.styleable.WheelPicker_wheel_cyclic, false)
        hasIndicator = attributes.getBoolean(R.styleable.WheelPicker_wheel_indicator, false)
        indicatorColor = attributes.getColor(R.styleable.WheelPicker_wheel_indicator_color, -0x11cccd)
        indicatorSize = attributes.getDimensionPixelSize(
            R.styleable.WheelPicker_wheel_indicator_size,
            resources.getDimensionPixelSize(R.dimen.WheelIndicatorSize)
        )
        hasCurtain = attributes.getBoolean(R.styleable.WheelPicker_wheel_curtain, false)
        curtainColor = attributes.getColor(R.styleable.WheelPicker_wheel_curtain_color, -0x77000001)
        hasAtmospheric = attributes.getBoolean(R.styleable.WheelPicker_wheel_atmospheric, false)
        isCurved = attributes.getBoolean(R.styleable.WheelPicker_wheel_curved, false)
        curveTo = attributes.getInt(R.styleable.WheelPicker_wheel_item_align, CURVE_TO_CENTER)
        itemGravity = attributes.getInt(R.styleable.WheelPicker_wheel_item_align, ITEM_GRAVITY_CENTER)
        attributes.recycle()

        updateVisibleItemCount()

        computeTextSize()

        scroller = Scroller(getContext())

        val conf = ViewConfiguration.get(getContext())
        minimumVelocity = conf.scaledMinimumFlingVelocity
        maximumVelocity = conf.scaledMaximumFlingVelocity
        touchSlop = conf.scaledTouchSlop

        rectDrawn = Rect()

        rectIndicatorHead = Rect()
        rectIndicatorFoot = Rect()

        rectCurrentItem = Rect()

        camera = Camera()

        matrixRotate = Matrix()
        matrixDepth = Matrix()
    }

    private fun updateVisibleItemCount() {
        if (visibleItemCount < 2)
            throw ArithmeticException("Wheel's visible item count can not be less than 2!")

        if (visibleItemCount % 2 == 0)
            visibleItemCount += 1
        drawnItemCount = visibleItemCount + 2
        halfDrawnItemCount = drawnItemCount / 2
    }

    private fun computeTextSize() {
        textMaxHeight = 0
        textMaxWidth = textMaxHeight
        if (hasSameWidth) {
            textMaxWidth = paint.measureText(data[0].toString()).toInt()
        } else if (isPosInRang(textMaxWidthPosition)) {
            textMaxWidth = paint.measureText(data[textMaxWidthPosition].toString()).toInt()
        } else if (!TextUtils.isEmpty(maximumWidthText)) {
            textMaxWidth = paint.measureText(maximumWidthText).toInt()
        } else {
            for (obj in data) {
                val text = obj.toString()
                val width = paint.measureText(text).toInt()
                textMaxWidth = Math.max(textMaxWidth, width)
            }
        }
        val metrics = paint.fontMetrics
        textMaxHeight = (metrics.bottom - metrics.top).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val modeWidth = MeasureSpec.getMode(widthMeasureSpec)
        val modeHeight = MeasureSpec.getMode(heightMeasureSpec)

        val sizeWidth = MeasureSpec.getSize(widthMeasureSpec)
        val sizeHeight = MeasureSpec.getSize(heightMeasureSpec)

        var resultWidth = textMaxWidth
        var resultHeight = textMaxHeight * visibleItemCount + itemSpace * (visibleItemCount - 1)

        if (isCurved) {
            resultHeight = (2 * resultHeight / Math.PI).toInt()
        }
        if (isDebug)
            Log.i(TAG, "Wheel's content size is ($resultWidth:$resultHeight)")

        resultWidth += paddingLeft + paddingRight
        resultHeight += paddingTop + paddingBottom
        if (isDebug)
            Log.i(TAG, "Wheel's size is ($resultWidth:$resultHeight)")

        resultWidth = measureSize(modeWidth, sizeWidth, resultWidth)
        resultHeight = measureSize(modeHeight, sizeHeight, resultHeight)

        setMeasuredDimension(resultWidth, resultHeight)
    }

    private fun measureSize(mode: Int, sizeExpect: Int, sizeActual: Int): Int {
        var realSize: Int
        if (mode == MeasureSpec.EXACTLY) {
            realSize = sizeExpect
        } else {
            realSize = sizeActual
            if (mode == MeasureSpec.AT_MOST)
                realSize = Math.min(realSize, sizeExpect)
        }
        return realSize
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        rectDrawn.set(
            paddingLeft, paddingTop, width - paddingRight,
            height - paddingBottom
        )
        if (isDebug)
            Log.i(
                TAG, "Wheel's drawn rect size is (" + rectDrawn.width() + ":" +
                        rectDrawn.height() + ") and location is (" + rectDrawn.left + ":" +
                        rectDrawn.top + ")"
            )

        wheelCenterX = rectDrawn.centerX()
        wheelCenterY = rectDrawn.centerY()

        computeDrawnCenter()

        halfWheelHeight = rectDrawn.height() / 2

        itemHeight = rectDrawn.height() / visibleItemCount
        halfItemHeight = itemHeight / 2

        computeFlingLimitY()

        computeIndicatorRect()

        computeCurrentItemRect()
    }

    private fun computeDrawnCenter() {
        drawnCenterX = when (itemGravity) {
            ITEM_GRAVITY_LEFT -> rectDrawn.left
            ITEM_GRAVITY_RIGHT -> rectDrawn.right
            else -> wheelCenterX
        }
        drawnCenterY = (wheelCenterY - (paint.ascent() + paint.descent()) / 2).toInt()
    }

    private fun computeFlingLimitY() {
        val currentItemOffset = selectedItemPosition * itemHeight
        minFlingY = if (isCyclic)
            Integer.MIN_VALUE
        else
            -itemHeight * (data.size - 1) + currentItemOffset
        maxFlingY = if (isCyclic) Integer.MAX_VALUE else currentItemOffset
    }

    private fun computeIndicatorRect() {
        if (!hasIndicator) return
        val halfIndicatorSize = indicatorSize / 2
        val indicatorHeadCenterY = wheelCenterY + halfItemHeight
        val indicatorFootCenterY = wheelCenterY - halfItemHeight
        rectIndicatorHead.set(
            rectDrawn.left, indicatorHeadCenterY - halfIndicatorSize,
            rectDrawn.right, indicatorHeadCenterY + halfIndicatorSize
        )
        rectIndicatorFoot.set(
            rectDrawn.left, indicatorFootCenterY - halfIndicatorSize,
            rectDrawn.right, indicatorFootCenterY + halfIndicatorSize
        )
    }

    private fun computeCurrentItemRect() {
        if (!hasCurtain && selectedItemTextColor == -1) return
        rectCurrentItem.set(
            rectDrawn.left, wheelCenterY - halfItemHeight, rectDrawn.right,
            wheelCenterY + halfItemHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        onWheelChangeListener?.onWheelScrolled(scrollOffsetY)
        val drawnDataStartPos = -scrollOffsetY / itemHeight - halfDrawnItemCount
        var drawnDataPos = drawnDataStartPos + selectedItemPosition
        var drawnOffsetPos = -halfDrawnItemCount
        while (drawnDataPos < drawnDataStartPos + selectedItemPosition + drawnItemCount) {
            var dataText = ""
            if (isCyclic) {
                var actualPos = drawnDataPos % data.size
                actualPos = if (actualPos < 0) actualPos + data.size else actualPos
                dataText = data[actualPos].toString()
            } else {
                if (isPosInRang(drawnDataPos))
                    dataText = data[drawnDataPos].toString()
            }
            val textWidth = paint.measureText(dataText)
            paint.color = itemTextColor
            paint.style = Paint.Style.FILL
            val drawnItemCenterY = drawnCenterY + drawnOffsetPos * itemHeight +
                    scrollOffsetY % itemHeight

            var distanceToCenter = 0
            if (isCurved) {
                val ratio = (drawnCenterY - Math.abs(drawnCenterY - drawnItemCenterY) -
                        rectDrawn.top) * 1.0f / (drawnCenterY - rectDrawn.top)

                var unit = 0
                if (drawnItemCenterY > drawnCenterY)
                    unit = 1
                else if (drawnItemCenterY < drawnCenterY)
                    unit = -1

                var degree = -(1 - ratio) * 90f * unit.toFloat()
                if (degree < -90) degree = -90f
                if (degree > 90) degree = 90f
                distanceToCenter = computeSpace(degree.toInt())

                var transX = wheelCenterX
                when (curveTo) {
                    CURVE_TO_LEFT -> transX = rectDrawn.left + textWidth.toInt()
                    CURVE_TO_RIGHT -> transX = rectDrawn.right - textWidth.toInt()
                }
                val transY = wheelCenterY - distanceToCenter

                camera.save()
                camera.rotateX(degree)
                camera.getMatrix(matrixRotate)
                camera.restore()
                matrixRotate.preTranslate((-transX).toFloat(), (-transY).toFloat())
                matrixRotate.postTranslate(transX.toFloat(), transY.toFloat())

                camera.save()
                camera.translate(0f, 0f, computeDepth(degree.toInt()).toFloat())
                camera.getMatrix(matrixDepth)
                camera.restore()
                matrixDepth.preTranslate((-transX).toFloat(), (-transY).toFloat())
                matrixDepth.postTranslate(transX.toFloat(), transY.toFloat())

                matrixRotate.postConcat(matrixDepth)
            }
            if (hasAtmospheric) {
                var alpha =
                    ((drawnCenterY - Math.abs(drawnCenterY - drawnItemCenterY)) * 1.0f / drawnCenterY * 255).toInt()
                alpha = if (alpha < 0) 0 else alpha
                paint.alpha = alpha
            }

            val drawnCenterY = if (isCurved) drawnCenterY - distanceToCenter else drawnItemCenterY
            var drawnCenterX = drawnCenterX.toFloat()
            when (itemGravity) {
                ITEM_GRAVITY_CENTER -> drawnCenterX -= textWidth / 2
                ITEM_GRAVITY_RIGHT -> drawnCenterX -= textWidth

            }

            if (selectedItemTextColor != -1) {
                canvas.save()
                if (isCurved) canvas.concat(matrixRotate)
                canvas.clipRect(rectCurrentItem, Region.Op.DIFFERENCE)
                canvas.drawText(dataText, drawnCenterX, drawnCenterY.toFloat(), paint)
                canvas.restore()

                paint.color = selectedItemTextColor
                canvas.save()
                if (isCurved) canvas.concat(matrixRotate)
                canvas.clipRect(rectCurrentItem)
                canvas.drawText(dataText, drawnCenterX, drawnCenterY.toFloat(), paint)
                canvas.restore()
            } else {
                canvas.save()
                canvas.clipRect(rectDrawn)
                if (isCurved) canvas.concat(matrixRotate)
                canvas.drawText(dataText, drawnCenterX, drawnCenterY.toFloat(), paint)
                canvas.restore()
            }
            if (isDebug) {
                canvas.save()
                canvas.clipRect(rectDrawn)
                paint.color = -0x11cccd
                val lineCenterY = wheelCenterY + drawnOffsetPos * itemHeight
                canvas.drawLine(
                    rectDrawn.left.toFloat(), lineCenterY.toFloat(), rectDrawn.right.toFloat(), lineCenterY.toFloat(),
                    paint
                )
                paint.color = -0xcccc12
                paint.style = Paint.Style.STROKE
                val top = lineCenterY - halfItemHeight
                canvas.drawRect(
                    rectDrawn.left.toFloat(),
                    top.toFloat(),
                    rectDrawn.right.toFloat(),
                    (top + itemHeight).toFloat(),
                    paint
                )
                canvas.restore()
            }
            drawnDataPos++
            drawnOffsetPos++
        }

        if (hasCurtain) {
            paint.color = curtainColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(rectCurrentItem, paint)
        }

        if (hasIndicator) {
            paint.color = indicatorColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(rectIndicatorHead, paint)
            canvas.drawRect(rectIndicatorFoot, paint)
        }
        if (isDebug) {
            paint.color = 0x4433EE33
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, paddingLeft.toFloat(), height.toFloat(), paint)
            canvas.drawRect(0f, 0f, width.toFloat(), paddingTop.toFloat(), paint)
            canvas.drawRect((width - paddingRight).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
            canvas.drawRect(0f, (height - paddingBottom).toFloat(), width.toFloat(), height.toFloat(), paint)
        }
    }

    private fun isPosInRang(position: Int): Boolean {
        return position >= 0 && position < data.size
    }

    private fun computeSpace(degree: Int): Int {
        return (Math.sin(Math.toRadians(degree.toDouble())) * halfWheelHeight).toInt()
    }

    private fun computeDepth(degree: Int): Int {
        return (halfWheelHeight - Math.cos(Math.toRadians(degree.toDouble())) * halfWheelHeight).toInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (null != parent)
                    parent.requestDisallowInterceptTouchEvent(true)
                if (null == tracker)
                    tracker = VelocityTracker.obtain()
                else
                    tracker!!.clear()
                tracker!!.addMovement(event)
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                    isForceFinishScroll = true
                }
                lastPointY = event.y.toInt()
                downPointY = lastPointY
            }
            MotionEvent.ACTION_MOVE -> {
                if (Math.abs(downPointY - event.y) < touchSlop) {
                    isClick = true
                    return true
                }
                isClick = false
                tracker!!.addMovement(event)
                if (null != onWheelChangeListener)
                    onWheelChangeListener!!.onWheelScrollStateChanged(SCROLL_STATE_DRAGGING)

                // 滚动内容
                // Scroll WheelPicker's content
                val move = event.y - lastPointY
                if (Math.abs(move) < 1) return true
                scrollOffsetY += move.toInt()
                lastPointY = event.y.toInt()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (null != parent)
                    parent.requestDisallowInterceptTouchEvent(false)
                if (isClick) return true
                tracker!!.addMovement(event)

                tracker!!.computeCurrentVelocity(1000, maximumVelocity.toFloat())

                isForceFinishScroll = false
                val velocity = tracker!!.yVelocity.toInt()
                if (Math.abs(velocity) > minimumVelocity) {
                    scroller.fling(0, scrollOffsetY, 0, velocity, 0, 0, minFlingY, maxFlingY)
                    scroller.finalY = (scroller.finalY + computeDistanceToEndPoint(scroller.finalY % itemHeight))
                } else {
                    scroller.startScroll(
                        0, scrollOffsetY, 0,
                        computeDistanceToEndPoint(scrollOffsetY % itemHeight)
                    )
                }

                if (!isCyclic)
                    if (scroller.finalY > maxFlingY)
                        scroller.finalY = maxFlingY
                    else if (scroller.finalY < minFlingY)
                        scroller.finalY = minFlingY
                mHandler.post(this)
                if (null != tracker) {
                    tracker!!.recycle()
                    tracker = null
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (null != parent)
                    parent.requestDisallowInterceptTouchEvent(false)
                if (null != tracker) {
                    tracker!!.recycle()
                    tracker = null
                }
            }
        }
        return true
    }

    private fun computeDistanceToEndPoint(remainder: Int): Int {
        return if (Math.abs(remainder) > halfItemHeight)
            if (scrollOffsetY < 0)
                -itemHeight - remainder
            else
                itemHeight - remainder
        else
            -remainder
    }

    override fun run() {
        if (data.isEmpty()) return
        if (scroller.isFinished && !isForceFinishScroll) {
            if (itemHeight == 0) return
            var position = (-scrollOffsetY / itemHeight + selectedItemPosition) % data.size
            position = if (position < 0) position + data.size else position
            if (isDebug)
                Log.i(TAG, (position).toString() + ":" + data[position] + ":" + scrollOffsetY)
            currentItemPosition = position
            onItemSelectedListener?.onItemSelected(this, data[position], position)

            onWheelChangeListener?.onWheelSelected(position)
            onWheelChangeListener?.onWheelScrollStateChanged(SCROLL_STATE_IDLE)

        }
        if (scroller.computeScrollOffset()) {
            onWheelChangeListener?.onWheelScrollStateChanged(SCROLL_STATE_SCROLLING)
            scrollOffsetY = scroller.currY
            postInvalidate()
            mHandler.postDelayed(this, 16)
        }


    }

    fun setOnWheelChangeListener(l: OnWheelChangeListener?) {
        onWheelChangeListener = l
    }

    fun setOnItemSelectedListener(l: (picker: WheelPicker<T>, data: T, position: Int) -> Unit) {
        onItemSelectedListener = object : OnItemSelectedListener<T> {
            override fun onItemSelected(picker: WheelPicker<T>, data: T, position: Int) = l(picker, data, position)

        }
    }

    interface OnItemSelectedListener<T> {
        fun onItemSelected(picker: WheelPicker<T>, data: T, position: Int)

    }

    interface OnWheelChangeListener {
        /**
         *
         * Invoke when WheelPicker scroll stopped
         * WheelPicker will return a distance offset which between current scroll position and
         * initial position, this offset is a positive or a negative, positive means WheelPicker is
         * scrolling from bottom to top, negative means WheelPicker is scrolling from top to bottom
         *
         * @param offset
         *
         *
         * Distance offset which between current scroll position and initial position
         */
        fun onWheelScrolled(offset: Int)

        /**
         *
         * Invoke when WheelPicker scroll stopped
         * This method will be called when WheelPicker stop and return current selected item data's
         * position in list
         *
         * @param position
         *
         *
         * Current selected item data's position in list
         */
        fun onWheelSelected(position: Int)

        /**
         *
         * Invoke when WheelPicker's scroll state changed
         * The state of WheelPicker always between idle, dragging, and scrolling, this method will
         * be called when they switch
         *
         * @param state State of WheelPicker, only one of the following
         * [WheelPicker.SCROLL_STATE_IDLE]
         * Express WheelPicker in state of idle
         * [WheelPicker.SCROLL_STATE_DRAGGING]
         * Express WheelPicker in state of dragging
         * [WheelPicker.SCROLL_STATE_SCROLLING]
         * Express WheelPicker in state of scrolling
         */
        fun onWheelScrollStateChanged(state: Int)
    }

    companion object {
        /**
         * @see OnWheelChangeListener.onWheelScrollStateChanged
         */
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SCROLLING = 2

        /**
         * @see curveTo
         */
        const val CURVE_TO_CENTER = 0
        const val CURVE_TO_LEFT = 1
        const val CURVE_TO_RIGHT = 2

        /**
         * @see itemGravity
         */
        const val ITEM_GRAVITY_CENTER = 0
        const val ITEM_GRAVITY_LEFT = 1
        const val ITEM_GRAVITY_RIGHT = 2

        private val TAG = WheelPicker::class.java.simpleName
    }
}