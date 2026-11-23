package com.example.dualpane

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/** ImageView with pinch-zoom, drag when zoomed and double-tap to reset. Works inside a ViewPager2. */
class ZoomImageView(ctx: Context) : ImageView(ctx) {

    private var scale = 1f
    private var lx = 0f
    private var ly = 0f

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            scale = (scale * d.scaleFactor).coerceIn(1f, 8f)
            scaleX = scale
            scaleY = scale
            if (scale == 1f) {
                translationX = 0f
                translationY = 0f
            }
            return true
        }
    })

    private val tapDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            reset()
            return true
        }
    })

    fun reset() {
        scale = 1f
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        tapDetector.onTouchEvent(e)
        // While zoomed (or pinching) keep the pager from stealing the gesture.
        parent?.requestDisallowInterceptTouchEvent(scale > 1f || e.pointerCount > 1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lx = e.rawX; ly = e.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && scale > 1f && e.pointerCount == 1) {
                    translationX += e.rawX - lx
                    translationY += e.rawY - ly
                }
                lx = e.rawX; ly = e.rawY
            }
        }
        return true
    }
}
