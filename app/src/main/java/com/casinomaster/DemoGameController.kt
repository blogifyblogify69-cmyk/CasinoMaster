package com.casinomaster

/**
 * Example API for a game owned by the developer.
 *
 * AutoTestEngine calls these methods directly for an end-to-end local test.
 * Nothing here searches for or clicks controls in another application.
 */
class DemoGameController(
    private val onAction: (String) -> Unit
) {
    var balance: Double = 1000.0
        private set

    var activeBet: Double = 0.0
        private set

    var collected: Boolean = false
        private set

    fun reset() {
        activeBet = 0.0
        collected = false
    }

    fun placeBet(amount: Double): Boolean {
        if (amount <= 0.0 || activeBet > 0.0 || amount > balance) return false
        balance -= amount
        activeBet = amount
        collected = false
        onAction("BET_EXECUTED: $amount")
        return true
    }

    fun collect(multiplier: Double): Double? {
        if (activeBet <= 0.0 || collected || multiplier < 1.0) return null
        val payout = activeBet * multiplier
        balance += payout
        activeBet = 0.0
        collected = true
        onAction("COLLECT_EXECUTED: %.2fx -> %.2f".format(multiplier, payout))
        return payout
    }
}
