package com.mousetouchlock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mousetouchlock.app.databinding.ActivityMainBinding
import com.mousetouchlock.app.service.FloatingButtonService
import com.mousetouchlock.app.util.PreferenceHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceHelper

    // ── Permission launchers ───────────────────────────────────────────────
    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUi() }

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUi() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshUi() }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    // ── UI refresh ──────────────────────────────────────────────────────────
    private fun refreshUi() {
        val serviceRunning  = FloatingButtonService.isRunning
        val isLocked        = FloatingButtonService.isLocked
        val hasOverlay      = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityEnabled()
        val hasNotification  = hasNotificationPermission()

        // ── Service status ────────────────────────────────────────────────
        with(binding) {
            dotService.setBackgroundResource(
                if (serviceRunning) R.drawable.dot_active else R.drawable.dot_inactive
            )
            tvServiceStatus.text = if (serviceRunning)
                getString(R.string.status_service_running)
            else
                getString(R.string.status_service_stopped)

            dotTouch.setBackgroundResource(
                if (isLocked) R.drawable.dot_locked else R.drawable.dot_inactive
            )
            tvTouchStatus.text = if (isLocked)
                getString(R.string.status_touch_locked)
            else
                getString(R.string.status_touch_unlocked)

            // ── Permissions ───────────────────────────────────────────────
            ivOverlayStatus.setImageResource(
                if (hasOverlay) R.drawable.ic_check else R.drawable.ic_cross
            )
            ivOverlayStatus.setColorFilter(
                ContextCompat.getColor(this@MainActivity,
                    if (hasOverlay) R.color.perm_granted else R.color.perm_denied)
            )
            btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

            ivAccessibilityStatus.setImageResource(
                if (hasAccessibility) R.drawable.ic_check else R.drawable.ic_optional
            )
            ivAccessibilityStatus.setColorFilter(
                ContextCompat.getColor(this@MainActivity,
                    if (hasAccessibility) R.color.perm_granted else R.color.text_secondary)
            )
            tvAccessibilityHint.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
            btnGrantAccessibility.text = if (hasAccessibility)
                getString(R.string.perm_accessibility_enabled)
            else
                getString(R.string.btn_enable)

            // Notification – only show on Android 13+
            rowNotification.visibility =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ivNotifStatus.setImageResource(
                    if (hasNotification) R.drawable.ic_check else R.drawable.ic_cross
                )
                ivNotifStatus.setColorFilter(
                    ContextCompat.getColor(this@MainActivity,
                        if (hasNotification) R.color.perm_granted else R.color.perm_denied)
                )
                btnGrantNotif.visibility = if (hasNotification) View.GONE else View.VISIBLE
            }

            // ── Main action button ────────────────────────────────────────
            btnStartStop.text = if (serviceRunning)
                getString(R.string.btn_stop_service)
            else
                getString(R.string.btn_start_service)
            btnStartStop.isEnabled = hasOverlay
            if (!hasOverlay) {
                btnStartStop.alpha = 0.38f
            } else {
                btnStartStop.alpha = 1f
            }
        }
    }

    // ── Click listeners ─────────────────────────────────────────────────────
    private fun setupClickListeners() {
        with(binding) {

            btnGrantOverlay.setOnClickListener {
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }

            btnGrantAccessibility.setOnClickListener {
                accessibilityLauncher.launch(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }

            btnGrantNotif.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            btnStartStop.setOnClickListener {
                val serviceIntent = Intent(this@MainActivity, FloatingButtonService::class.java)
                if (FloatingButtonService.isRunning) {
                    serviceIntent.action = FloatingButtonService.ACTION_STOP
                    startService(serviceIntent)
                } else {
                    startForegroundService(serviceIntent)
                }
                // Delay refresh so service has time to start/stop
                binding.root.postDelayed({ refreshUi() }, 400)
            }

            btnSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }

            tvVersion.setOnClickListener {
                // Triple-tap Easter egg: show debug info
            }
        }
    }

    // ── Permission helpers ──────────────────────────────────────────────────
    private fun isAccessibilityEnabled(): Boolean {
        val flat = Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return flat.contains(
            "${packageName}/.service.TouchLockAccessibilityService",
            ignoreCase = true
        )
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }
}
