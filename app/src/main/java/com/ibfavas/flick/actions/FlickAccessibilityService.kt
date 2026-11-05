package com.ibfavas.flick.actions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Flick's accessibility service. It exists for exactly two actions that
 * Android offers no other way to do: locking the screen and taking a
 * screenshot. Both are performed with performGlobalAction, which only an
 * enabled accessibility service can call.
 *
 * The service is OPTIONAL: Flick works without it, and the lock-screen
 * and screenshot actions guide the user to enable it when it is off.
 * Nothing is observed: onAccessibilityEvent is a no-op, and the service
 * declares no feedback type and no event types in its config, so it
 * receives no events at all.
 *
 * The system binds this service, so the app cannot call it directly.
 * Instead the service keeps a static reference to itself (set in
 * onServiceConnected, cleared in onDestroy), and ActionExecutor uses
 * [performAction] through it.
 */
class FlickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: FlickAccessibilityService? = null

        /** True when the user has enabled Flick's accessibility service. */
        fun isEnabled(context: Context): Boolean {
            val manager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            ).any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
        }

        /**
         * Run a global action (lock screen / screenshot) if the service
         * is enabled and bound. Returns false when it is not, so the
         * caller can guide the user to enable it instead.
         */
        fun performAction(action: Int): Boolean {
            val service = instance ?: return false
            return try {
                service.performGlobalAction(action)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: Flick does not observe anything.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }
}
