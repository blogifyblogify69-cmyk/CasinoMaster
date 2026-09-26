package com.casinomaster

import java.util.Locale
import java.util.UUID

enum class AutomationState {
    IDLE,
    WAITING_FOR_COUNTDOWN,
    BET_WINDOW,
    TEST_BET_PLACED,
    WAITING_FOR_ROUND,
    ROUND_ACTIVE,
    TARGET_REACHED,
    TEST_COLLECTED,
    ROUND_ENDED,
    COOLDOWN_10_SECONDS
}

data class GameObservation(
    val countdown: Int? = null,
    val multiplier: Double? = null,
    val roundActive: Boolean = false,
    val roundEnded: Boolean = false,
    val confidence: Float = 0f,
    val source: String = "unknown"
)

data class AutomationSnapshot(
    val roundId: String,
    val state: AutomationState,
    val enteredAtMs: Long,
    val timeoutMs: Long,
    val confidence: Float,
    val countdown: Int?,
    val multiplier: Double?,
    val transitionReason: String,
    val lastAction: String?,
    val error: String?
)

fun interface AutomationLogger {
    fun log(roundId: String, timestampMs: Long, state: AutomationState, countdown: Int?, multiplier: Double?, action: String?, result: String?, confidence: Float, reason: String)
}

class CountdownDetector {
    private val pattern = Regex("""(?<![\d.])(\d{1,3})(?:\s*s)?(?![\d.])""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Int? =
        pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..999 }
}

class MultiplierDetector {
    private val pattern = Regex("""(?<![\d.])(\d{1,4}(?:[.,]\d{1,3})?)\s*x\b""", RegexOption.IGNORE_CASE)
    private val decimalPattern = Regex("""(?<![\d.])(\d+[.,]\d{1,3})(?![\d.])""")

    fun parse(text: String): Double? {
        val explicit = pattern.find(text)?.groupValues?.getOrNull(1)
        val value = (explicit ?: decimalPattern.find(text)?.groupValues?.getOrNull(1))
            ?.replace(',', '.')?.toDoubleOrNull()
        return value?.takeIf { it.isFinite() && it >= 1.0 }
    }
}

class AutomationStateMachine(
    private val betThreshold: Int = 15,
    private val betAmount: Double = 20.0,
    private val targetMultiplier: Double = 1.50,
    private val cooldownMs: Long = 10_000L,
    private val stateTimeoutMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val logger: AutomationLogger,
    private val controller: DemoGameController
) {
    private var state = AutomationState.IDLE
    private var roundId = newRoundId()
    private var enteredAt = clock()
    private var previousCountdown: Int? = null
    private var betAttempted = false
    private var collectAttempted = false
    private var lastObservation = GameObservation()
    private var lastAction: String? = null
    private var lastError: String? = null

    fun start() {
        if (state != AutomationState.IDLE) return
        transition(AutomationState.WAITING_FOR_COUNTDOWN, "automation started")
    }

    fun stop() {
        state = AutomationState.IDLE
        lastAction = "STOP"
        log("STOP", "stopped")
    }

    fun snapshot(): AutomationSnapshot = AutomationSnapshot(
        roundId, state, enteredAt, stateTimeoutMs, lastObservation.confidence,
        lastObservation.countdown, lastObservation.multiplier,
        "current", lastAction, lastError
    )

    fun observe(observation: GameObservation) {
        if (state == AutomationState.IDLE) return
        lastObservation = observation
        val now = clock()

        if (state != AutomationState.COOLDOWN_10_SECONDS && now - enteredAt > stateTimeoutMs) {
            lastError = "State timeout: $state"
            log("TIMEOUT", lastError)
            transition(AutomationState.WAITING_FOR_COUNTDOWN, "timeout recovery")
            resetRoundGuard()
        }

        when (state) {
            AutomationState.WAITING_FOR_COUNTDOWN -> {
                val countdown = observation.countdown
                if (countdown != null && crossedIntoThreshold(countdown)) {
                    transition(AutomationState.BET_WINDOW, "countdown reached $countdown")
                    placeTestBet()
                }
            }
            AutomationState.BET_WINDOW -> {
                if (!betAttempted) placeTestBet()
            }
            AutomationState.TEST_BET_PLACED -> {
                if (observation.roundActive) transition(AutomationState.ROUND_ACTIVE, "airplane round detected")
            }
            AutomationState.WAITING_FOR_ROUND -> {
                if (observation.roundActive) transition(AutomationState.ROUND_ACTIVE, "airplane round detected")
            }
            AutomationState.ROUND_ACTIVE -> {
                val multiplier = observation.multiplier
                if (multiplier != null && multiplier >= targetMultiplier && !collectAttempted) {
                    transition(AutomationState.TARGET_REACHED, "multiplier reached %.2fx".format(Locale.US, multiplier))
                    collectTest(multiplier)
                } else if (observation.roundEnded) {
                    transition(AutomationState.ROUND_ENDED, "round end signal")
                    beginCooldown()
                }
            }
            AutomationState.TARGET_REACHED -> {
                if (!collectAttempted && observation.multiplier != null) collectTest(observation.multiplier)
            }
            AutomationState.TEST_COLLECTED -> {
                if (observation.roundEnded) {
                    transition(AutomationState.ROUND_ENDED, "round ended after collection")
                    beginCooldown()
                }
            }
            AutomationState.ROUND_ENDED -> beginCooldown()
            AutomationState.COOLDOWN_10_SECONDS -> {
                if (now - enteredAt >= cooldownMs) {
                    transition(AutomationState.WAITING_FOR_COUNTDOWN, "10-second cooldown complete")
                    resetRoundGuard()
                }
            }
            AutomationState.IDLE -> Unit
        }

        previousCountdown = observation.countdown ?: previousCountdown
    }

    private fun crossedIntoThreshold(countdown: Int): Boolean {
        val previous = previousCountdown
        return countdown <= betThreshold && (previous == null || previous > betThreshold)
    }

    private fun placeTestBet() {
        if (betAttempted) return
        betAttempted = true
        lastAction = "TEST_BET"
        val ok = controller.placeBet(betAmount)
        if (ok) {
            log("TEST_BET", "amount=%.2f".format(Locale.US, betAmount))
            transition(AutomationState.TEST_BET_PLACED, "owned demo placeBet() succeeded")
            transition(AutomationState.WAITING_FOR_ROUND, "waiting for round start")
        } else {
            lastError = "DemoGameController.placeBet() rejected the test bet"
            log("TEST_BET", "FAILED: $lastError")
            transition(AutomationState.TEST_BET_PLACED, "bet attempt completed with failure")
        }
    }

    private fun collectTest(multiplier: Double) {
        if (collectAttempted) return
        collectAttempted = true
        lastAction = "TEST_COLLECT"
        val payout = controller.collect(multiplier)
        if (payout != null) {
            log("TEST_COLLECT", "target=%.2fx payout=%.2f".format(Locale.US, multiplier, payout))
            transition(AutomationState.TEST_COLLECTED, "owned demo collect() succeeded")
        } else {
            lastError = "DemoGameController.collect() rejected the test collect"
            log("TEST_COLLECT", "FAILED: $lastError")
            transition(AutomationState.TEST_COLLECTED, "collect attempt completed with failure")
        }
    }

    private fun beginCooldown() {
        if (state != AutomationState.COOLDOWN_10_SECONDS) transition(AutomationState.COOLDOWN_10_SECONDS, "cooldown started")
    }

    private fun resetRoundGuard() {
        roundId = newRoundId()
        betAttempted = false
        collectAttempted = false
        previousCountdown = null
        lastError = null
        lastAction = null
        controller.reset()
    }

    private fun transition(next: AutomationState, reason: String) {
        state = next
        enteredAt = clock()
        log("TRANSITION", reason)
    }

    private fun log(action: String?, result: String?) {
        logger.log(roundId, clock(), state, lastObservation.countdown, lastObservation.multiplier, action, result, lastObservation.confidence, result ?: "state update")
    }

    private fun newRoundId(): String = UUID.randomUUID().toString().take(8)
}

class GameStateDetector {
    private val countdownDetector = CountdownDetector()
    private val multiplierDetector = MultiplierDetector()

    fun fromAccessibility(texts: List<String>, roundActive: Boolean, roundEnded: Boolean, confidence: Float = 0.95f): GameObservation {
        val joined = texts.joinToString(" ")
        return GameObservation(
            countdown = countdownDetector.parse(joined),
            multiplier = multiplierDetector.parse(joined),
            roundActive = roundActive,
            roundEnded = roundEnded,
            confidence = confidence,
            source = "accessibility"
        )
    }

    fun fromOcr(text: String, roundActive: Boolean, roundEnded: Boolean, confidence: Float): GameObservation =
        GameObservation(
            countdown = countdownDetector.parse(text),
            multiplier = multiplierDetector.parse(text),
            roundActive = roundActive,
            roundEnded = roundEnded,
            confidence = confidence,
            source = "ocr"
        )
}
