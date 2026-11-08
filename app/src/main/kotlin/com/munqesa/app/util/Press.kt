package com.munqesa.app.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** تأثير الضغط المرن (يصغر عند اللمس ثم يرتد) على طريقة iOS. */
object Press {
    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, pressedScale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(pressedScale).scaleY(pressedScale)
                        .setDuration(110).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(320).setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            false
        }
    }
}
