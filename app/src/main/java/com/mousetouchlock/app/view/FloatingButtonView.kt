package com.mousetouchlock.app.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.mousetouchlock.app.R
import com.mousetouchlock.app.util.PreferenceHelper

/**
 * Floating button rendered as a custom View with Nothing-OS monochrome aesthetic.
 * Handles drag-to-move and single/double-tap for lock/unlock.
 * Lives in its OWN WindowManager window, added AFTER LockOverlayView,
 * so it sits above the overlay in z-order and is always touchable.
 */
@SuppressLint("ViewConstructor")
class FloatingButtonView(
    context: Context,
    private val prefs: PreferenceHelper
) : View(context) {

    // ── Callbacks ──────────────────────────────────────────────────────────
    var onLockRequest: (() -> Unit)? = null
    var onUnlockRequest: (() -> Unit)? = null

    // ── State ──────────────────────────────────────────────────────────────
    private var isLocked = false

    // ── Paint / Drawing ────────────────────────────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var lockIcon: Bitmap? = null
    private var unlockIcon: Bitmap? = null

    // ── Drag ───────────────────────────────────────────────────────────────
    private var isDragging = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var winStartX = 0
    private var winStartY = 0
    private val dragThresholdPx by lazy { resources.displayMetrics.density * 6f }

    // ── Double-tap ─────────────────────────────────────────────────────────
    private var lastTapMs = 0L
    private var pendingTapCount = 0
    private val DOUBLE_TAP_MS = 450L

    // ── Press animation ────────────────────────────────────────────────────
    private var pressScale = 1f
        set(v) { field = v; invalidate() }

    init {
        alpha = prefs.buttonAlpha
        buildIcons()
    }

    // ── Icon Bitmaps ───────────────────────────────────────────────────────
    private fun buildIcons() {
        lockIcon   = vectorToBitmap(R.drawable.ic_lock_closed)
        unlockIcon = vectorToBitmap(R.drawable.ic_lock_open)
    }

    private fun vectorToBitmap(resId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        val size = (width.takeIf { it > 0 } ?: dpToPx(32)).coerceAtLeast(32)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    // ── Draw ───────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildIcons()   // rebuild at actual size
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r  = (width / 2f - 4f) * pressScale

        canvas.save()
        canvas.scale(pressScale, pressScale, cx, cy)

        // Background circle
        bgPaint.color = if (isLocked) Color.parseColor("#0A0A0A") else Color.WHITE
        canvas.drawCircle(cx, cy, r, bgPaint)

        // Border (always visible; wider when locked for emphasis)
        borderPaint.strokeWidth = if (isLocked) 3f else 1.5f
        borderPaint.color = if (isLocked) Color.WHITE else Color.parseColor("#CCCCCC")
        canvas.drawCircle(cx, cy, r - borderPaint.strokeWidth / 2f, borderPaint)

        // Icon
        val icon = if (isLocked) lockIcon else unlockIcon
        icon?.let {
            val iconPad = (width * 0.28f).toInt()
            val dstRect = Rect(iconPad, iconPad, width - iconPad, height - iconPad)
            iconPaint.colorFilter = PorterDuffColorFilter(
                if (isLocked) Color.WHITE else Color.parseColor("#0A0A0A"),
                PorterDuff.Mode.SRC_IN
            )
            canvas.drawBitmap(it, null, dstRect, iconPaint)
        }

        canvas.restore()
    }

    // ── State update ────────────────────────────────────────────────────────
    fun setLocked(locked: Boolean) {
        isLocked = locked
        pendingTapCount = 0
        lastTapMs = 0L
        animateStateChange()
        invalidate()
    }

    private fun animateStateChange() {
        val scaleDown = ObjectAnimator.ofFloat(this, "pressScale", 1f, 0.82f).apply { duration = 90 }
        val scaleUp   = ObjectAnimator.ofFloat(this, "pressScale", 0.82f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(2.0f)
        }
        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    fun updateAlpha(a: Float) { alpha = a }

    // ── Touch ───────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm     = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = layoutParams as? WindowManager.LayoutParams ?: return true

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                isDragging    = false
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                winStartX     = params.x
                winStartY     = params.y
                animatePress(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartRawX
                val dy = event.rawY - dragStartRawY

                if (!isDragging &&
                    (Math.abs(dx) > dragThresholdPx || Math.abs(dy) > dragThresholdPx)) {
                    isDragging = true
                }

                if (isDragging) {
                    val sw = resources.displayMetrics.widthPixels
                    val sh = resources.displayMetrics.heightPixels
                    params.x = (winStartX + dx.toInt()).coerceIn(0, sw - width)
                    params.y = (winStartY + dy.toInt()).coerceIn(0, sh - height)
                    try { wm.updateViewLayout(this, params) } catch (_: Exception) {}
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePress(false)
                if (!isDragging) {
                    handleTap()
                } else {
                    prefs.buttonX = params.x
                    prefs.buttonY = params.y
                }
                isDragging = false
                return true
            }
        }
        return true
    }

    // ── Tap logic ────────────────────────────────────────────────────────────
    private fun handleTap() {
        val now = SystemClock.elapsedRealtime()

        if (isLocked) {
            // Need double-tap to unlock (prevents accidental unlock)
            if (now - lastTapMs <= DOUBLE_TAP_MS) {
                pendingTapCount++
                if (pendingTapCount >= 2) {
                    pendingTapCount = 0
                    lastTapMs = 0L
                    onUnlockRequest?.invoke()
                }
            } else {
                pendingTapCount = 1
                lastTapMs = now
                animatePulse()   // visual hint: tap again to unlock
            }
        } else {
            // Single tap → lock
            pendingTapCount = 0
            lastTapMs = 0L
            onLockRequest?.invoke()
        }
    }

    // ── Animations ───────────────────────────────────────────────────────────
    private fun animatePress(pressed: Boolean) {
        ObjectAnimator.ofFloat(this, "pressScale", if (pressed) 0.92f else 1f).apply {
            duration     = 100
            interpolator = DecelerateInterpolator()
        }.start()
    }

    private fun animatePulse() {
        ObjectAnimator.ofFloat(this, "alpha", alpha, alpha * 0.35f, alpha).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
        }.start()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
