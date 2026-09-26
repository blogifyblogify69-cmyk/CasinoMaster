package com.casinomaster

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Local end-to-end workflow simulation. It never clicks another app. */
class AutoTestEngine(context: Context, private val onEvent: (String, String) -> Unit) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var cycle = 0

    fun start() {
        if (running) return
        running = true
        cycle = 0
        runCycle()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        save("OFF", "Automatic end-to-end test stopped.")
    }

    private fun runCycle() {
        if (!running) return
        cycle++
        val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val amount = prefs.getString("amount", "20") ?: "20"
        val target = prefs.getString("target", "1.50") ?: "1.50"

        step(0, "COUNTDOWN", "Simulated countdown started at 30.")
        step(1000, "COUNTDOWN > 13", "Simulated countdown reached the >13 threshold.")
        step(1800, "BET_REQUESTED", "TEST EVENT: internal bet request for $amount.")
        step(3000, "ROUND_ACTIVE", "Simulated airplane/multiplier started.")
        step(5500, "MULTIPLIER_TARGET", "Simulated multiplier reached $target x.")
        step(5600, "COLLECT_REQUESTED", "TEST EVENT: internal collect request at $target x.")
        step(7000, "ROUND_ENDED", "Simulated round ended.")
        step(17000, "COOLDOWN_COMPLETE", "10-second cooldown completed.")
        step(17500, "NEXT_ROUND", "Starting next simulated cycle.") { runCycle() }
    }

    private fun step(delay: Long, state: String, detail: String, after: (() -> Unit)? = null) {
        handler.postDelayed({
            if (!running) return@postDelayed
            save(state, detail)
            onEvent(state, detail)
            after?.invoke()
        }, delay)
    }

    private fun save(state: String, detail: String) {
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("test_state", state)
            .putString("test_detail", detail)
            .apply()
    }
}
