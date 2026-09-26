package com.casinomaster

sealed class AutomationSafetyResult {
    data object Ok : AutomationSafetyResult()
    data class Stop(val reason: String) : AutomationSafetyResult()
}

object AutomationSafetyGate {
    fun check(
        accessibilityConnected: Boolean,
        heartbeatAgeMs: Long,
        activePackage: String?,
        targetPackage: String?,
        projectionActive: Boolean,
        maxHeartbeatAgeMs: Long = 2500L
    ): AutomationSafetyResult {
        if (!projectionActive) return AutomationSafetyResult.Stop("MediaProjection stopped.")
        if (!accessibilityConnected || heartbeatAgeMs > maxHeartbeatAgeMs) {
            return AutomationSafetyResult.Stop("AccessibilityService disconnected.")
        }
        if (targetPackage.isNullOrBlank() || activePackage != targetPackage) {
            return AutomationSafetyResult.Stop("Target package changed.")
        }
        return AutomationSafetyResult.Ok
    }
}
