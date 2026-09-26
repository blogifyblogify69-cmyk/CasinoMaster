package com.casinomaster

import android.content.Context
import java.util.Locale

/**
 * Compatibility adapter for the developer-owned demo test engine.
 * It consumes real observations; it does not synthesize countdowns or multipliers.
 */
class AutoTestEngine(
    context: Context,
    private val onEvent: (String, String) -> Unit
) {
    private val appContext = context.applicationContext
    private var running = false
    private lateinit var machine: AutomationStateMachine
    private val controller = DemoGameController { action -> onEvent("GAME_ACTION", action) }

    fun start() {
        if (running) return
        val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val amount = prefs.getString("amount", "20")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 20.0
        val threshold = prefs.getString("countdown_threshold", "15")?.toIntOrNull()?.coerceIn(1, 300) ?: 15
        val target = prefs.getString("target", "1.50")?.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 1.50
        val cooldown = prefs.getString("cooldown_seconds", "10")?.toLongOrNull()?.coerceIn(1, 300)?.times(1000L) ?: 10_000L
        machine = AutomationStateMachine(
            betThreshold = threshold,
            betAmount = amount,
            targetMultiplier = target,
            cooldownMs = cooldown,
            logger = AutomationLogger { roundId, timestamp, state, countdown, multiplier, action, result, confidence, reason ->
                val line = buildString {
                    append(timestamp).append(" ROUND_ID=").append(roundId)
                    append(" state=").append(state)
                    if (countdown != null) append(" countdown=").append(countdown)
                    if (multiplier != null) append(" multiplier=").append("%.2f".format(Locale.US, multiplier))
                    if (action != null) append(" action=").append(action)
                    if (result != null) append(" result=").append(result)
                    append(" confidence=").append("%.2f".format(Locale.US, confidence))
                    append(" reason=").append(reason)
                }
                AutomationProfileStore(appContext).appendLog(line)
                onEvent("AUTOMATION", line)
            },
            controller = controller
        )
        running = true
        machine.start()
    }

    fun observe(observation: GameObservation) {
        if (running) machine.observe(observation)
    }

    fun stop() {
        if (running) machine.stop()
        running = false
        onEvent("AUTOMATION", "Stopped.")
    }

    fun snapshot(): AutomationSnapshot? = if (::machine.isInitialized) machine.snapshot() else null
}
