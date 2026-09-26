package com.casinomaster

import org.junit.Assert.*
import org.junit.Test

class AutomationSafetyGateTest {
    @Test fun accessibilityDisconnectStopsAutomation() {
        val result = AutomationSafetyGate.check(true, 3001L, "demo", "demo", true)
        assertTrue(result is AutomationSafetyResult.Stop)
    }

    @Test fun mediaProjectionFailureStopsAutomation() {
        val result = AutomationSafetyGate.check(true, 10L, "demo", "demo", false)
        assertTrue(result is AutomationSafetyResult.Stop)
    }

    @Test fun targetPackageChangeStopsAutomation() {
        val result = AutomationSafetyGate.check(true, 10L, "other", "demo", true)
        assertTrue(result is AutomationSafetyResult.Stop)
    }

    @Test fun healthyAutomationContinues() {
        assertEquals(
            AutomationSafetyResult.Ok,
            AutomationSafetyGate.check(true, 10L, "demo", "demo", true)
        )
    }
}
