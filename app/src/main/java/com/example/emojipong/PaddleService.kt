package com.example.emojipong

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/** Step 4: move the paddle by dragging a finger along the paddle row. */
class PaddleService : AccessibilityService() {
    companion object { @Volatile var instance: PaddleService? = null }

    private var busy = false
    private var curX = -1f

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(i: android.content.Intent?): Boolean { instance = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun dragTo(x: Float, y: Float) {
        if (busy) return
        if (curX < 0) curX = x
        if (abs(x - curX) < 10f) return
        val path = Path().apply { moveTo(curX, y); lineTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 40)).build()
        busy = true
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { curX = x; busy = false }
            override fun onCancelled(d: GestureDescription?) { busy = false }
        }, null)
    }
}
