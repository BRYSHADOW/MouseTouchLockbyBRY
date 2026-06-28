package com.mousetouchlock.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Transparent full-screen overlay that intercepts and blocks finger touches
 * while attempting to pass through mouse (USB OTG) pointer events.
 *
 * Placed BELOW FloatingButtonView in the WindowManager z-order.
 * Active only when touch is locked (FLAG_NOT_TOUCHABLE removed).
 *
 * Note: Mouse pass-through relies on the OS forwarding unhandled events
 * to the next window (FLAG_NOT_TOUCH_MODAL behaviour). Results may vary
 * by device/ROM. Keyboard OTG always works because FLAG_NOT_FOCUSABLE
 * keeps keyboard focus on the background app.
 */
class LockOverlayView(context: Context) : View(context) {

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable  = false
        isHapticFeedbackEnabled = false
    }

    /**
     * Intercept every touch event first.
     * - TOOL_TYPE_MOUSE  → return false (not consumed → system may forward to app behind)
     * - TOOL_TYPE_FINGER / STYLUS → return true  (consumed = blocked)
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val toolType = ev.getToolType(0)

        return when (toolType) {
            MotionEvent.TOOL_TYPE_MOUSE -> false   // pass mouse clicks through

            MotionEvent.TOOL_TYPE_FINGER,
            MotionEvent.TOOL_TYPE_STYLUS -> true   // block finger / stylus

            else -> {
                // Fallback: check InputDevice source flags
                val isMouseSource =
                    (ev.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                !isMouseSource          // block if NOT from mouse
            }
        }
    }

    /**
     * Generic motion events (hover moves, mouse scroll wheel, joystick, etc.)
     * Mouse events → not consumed → forwarded to the window/app below.
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val isMousePointer =
            (ev.source and InputDevice.SOURCE_CLASS_POINTER) != 0 &&
            ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
        return if (isMousePointer) false else super.dispatchGenericMotionEvent(ev)
    }

    /** Nothing to draw – completely transparent */
    override fun onDraw(canvas: Canvas) { /* intentionally empty */ }
}
