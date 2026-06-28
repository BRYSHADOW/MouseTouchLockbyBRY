package com.mousetouchlock.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mousetouchlock.app.MainActivity
import com.mousetouchlock.app.R
import com.mousetouchlock.app.util.PreferenceHelper
import com.mousetouchlock.app.view.FloatingButtonView
import com.mousetouchlock.app.view.LockOverlayView

class FloatingButtonService : Service() {

    // ── Companion ──────────────────────────────────────────────────────────
    companion object {
        const val CHANNEL_ID      = "mtl_service_channel"
        const val NOTIF_ID        = 1001
        const val ACTION_STOP     = "com.mousetouchlock.ACTION_STOP"
        const val ACTION_LOCK     = "com.mousetouchlock.ACTION_LOCK"
        const val ACTION_UNLOCK   = "com.mousetouchlock.ACTION_UNLOCK"

        @Volatile var isRunning = false
        @Volatile var isLocked  = false

        /** Weak live reference – null when service is dead */
        @Volatile var instance: FloatingButtonService? = null
    }

    // ── Fields ─────────────────────────────────────────────────────────────
    private lateinit var wm: WindowManager
    private lateinit var prefs: PreferenceHelper

    private var fabView: FloatingButtonView? = null
    private var overlayView: LockOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true

        wm    = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PreferenceHelper(this)

        createNotificationChannel()
        startForegroundCompat()

        // Order matters: overlay added FIRST (lower z-order), FAB added SECOND (higher)
        setupLockOverlay()
        setupFloatingButton()

        prefs.isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP   -> stopSelf()
            ACTION_LOCK   -> lock()
            ACTION_UNLOCK -> unlock()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance  = null
        isRunning = false
        isLocked  = false
        teardown()
        prefs.isServiceRunning = false
    }

    // ── Window setup ───────────────────────────────────────────────────────
    private fun setupLockOverlay() {
        val view = LockOverlayView(this).also { overlayView = it }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_TOUCHABLE → completely transparent to input (unlocked state)
            // FLAG_NOT_FOCUSABLE → keyboard always goes to background app
            // FLAG_NOT_TOUCH_MODAL → unlocked events that escape the overlay reach other windows
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).also { overlayParams = it }

        try { wm.addView(view, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupFloatingButton() {
        val sizePx = dpToPx(prefs.buttonSize)
        val sw     = resources.displayMetrics.widthPixels
        val sh     = resources.displayMetrics.heightPixels

        val x = if (prefs.buttonX >= 0) prefs.buttonX else sw - sizePx - dpToPx(16)
        val y = if (prefs.buttonY >= 0) prefs.buttonY else sh / 3

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity  = Gravity.TOP or Gravity.START
            this.x   = x
            this.y   = y
        }

        val view = FloatingButtonView(this, prefs).also { fabView = it }
        view.onLockRequest   = { lock() }
        view.onUnlockRequest = { unlock() }

        try { wm.addView(view, params) } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Lock / Unlock ──────────────────────────────────────────────────────
    fun lock() {
        if (isLocked) return
        isLocked = true

        // Remove FLAG_NOT_TOUCHABLE so overlay intercepts finger touches
        overlayParams?.let { p ->
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            overlayView?.let { v ->
                try { wm.updateViewLayout(v, p) } catch (e: Exception) { e.printStackTrace() }
            }
        }

        fabView?.setLocked(true)
        vibrate(locking = true)
        updateNotification()
        showToast(getString(R.string.toast_locked))
    }

    fun unlock() {
        if (!isLocked) return
        isLocked = false

        // Restore FLAG_NOT_TOUCHABLE → overlay is pass-through again
        overlayParams?.let { p ->
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            overlayView?.let { v ->
                try { wm.updateViewLayout(v, p) } catch (e: Exception) { e.printStackTrace() }
            }
        }

        fabView?.setLocked(false)
        vibrate(locking = false)
        updateNotification()
        showToast(getString(R.string.toast_unlocked))
    }

    // ── Public setters (called from SettingsActivity) ──────────────────────
    fun applyButtonSize(sizeDp: Int) {
        prefs.buttonSize = sizeDp
        val sizePx = dpToPx(sizeDp)
        val view   = fabView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.width  = sizePx
        params.height = sizePx
        try { wm.updateViewLayout(view, params) } catch (e: Exception) { e.printStackTrace() }
    }

    fun applyButtonAlpha(alpha: Float) {
        prefs.buttonAlpha = alpha
        fabView?.updateAlpha(alpha)
    }

    fun resetButtonPosition() {
        prefs.resetPosition()
        val view   = fabView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val sw     = resources.displayMetrics.widthPixels
        val sh     = resources.displayMetrics.heightPixels
        params.x   = sw - params.width - dpToPx(16)
        params.y   = sh / 3
        try { wm.updateViewLayout(view, params) } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Notification ───────────────────────────────────────────────────────
    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val mainPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingButtonService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (isLocked) getString(R.string.notif_locked)
                else getString(R.string.notif_running)
            )
            .setContentIntent(mainPi)
            .addAction(R.drawable.ic_notif_lock, getString(R.string.action_stop), stopPi)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    // ── Haptics ────────────────────────────────────────────────────────────
    private fun vibrate(locking: Boolean) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val effect = if (locking) {
            // Double pulse for lock
            VibrationEffect.createWaveform(longArrayOf(0, 55, 45, 55), -1)
        } else {
            // Single softer pulse for unlock
            VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    // ── Toast (main-thread safe) ────────────────────────────────────────────
    private fun showToast(msg: String) {
        android.os.Handler(mainLooper).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────
    private fun teardown() {
        fabView?.let     { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        fabView     = null
        overlayView = null
    }

    // ── Utilities ────────────────────────────────────────────────────────────
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density + 0.5f).toInt()
}
