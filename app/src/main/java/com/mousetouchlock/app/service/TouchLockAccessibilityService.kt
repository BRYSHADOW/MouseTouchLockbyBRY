package com.mousetouchlock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional AccessibilityService.
 *
 * Enables the user to grant the "Accessibility" permission for enhanced
 * integration. When connected, it automatically starts the FloatingButtonService
 * if it is not already running. The core touch-block functionality works
 * WITHOUT this service; enabling it is entirely optional.
 *
 * To enable: Settings → Accessibility → Mouse Touch Lock → ON
 */
class TouchLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Auto-start the floating button service when accessibility is toggled on
        if (!FloatingButtonService.isRunning) {
            try {
                startForegroundService(Intent(this, FloatingButtonService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used – we only need the service connection hook
    }

    override fun onInterrupt() {
        // No-op
    }
}
