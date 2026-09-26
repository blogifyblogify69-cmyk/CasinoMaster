package com.casinomaster

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Demo-only live coordinator. It never injects clicks into another application.
 * Test actions are executed only through DemoGameController.
 */
class DemoAutomationCoordinator(
    context: Context,
    private val onSnapshot: (AutomationSnapshot) -> Unit = {}
) {
    companion object {
        const val BET_COUNTDOWN_THRESHOLD = 15
        const val TEST_BET_AMOUNT = 20.0
        const val TARGET_MULTIPLIER = 1.50
        const val COOLDOWN_MS = 10_000L
        private const val PREFS = "settings"
        private const val OCR_MIN_INTERVAL_MS = 350L
        private const val END_SIGNAL_STABLE_MS = 900L
    }

    private val appContext = context.applicationContext
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val detector = GameStateDetector()
    private val busy = AtomicBoolean(false)
    private var lastOcrAt = 0L
    private var lastMultiplierAt = 0L
    private var activeSeenAt = 0L
    private var countdownSeenAt = 0L
    private var running = false

    private val logger = AutomationLogger { roundId, timestamp, state, countdown, multiplier, action, result, confidence, reason ->
        val line = buildString {
            append(timestamp)
            append(" ROUND_ID=").append(roundId)
            append(" state=").append(state)
            if (countdown != null) append(" countdown=").append(countdown)
            if (multiplier != null) append(" multiplier=").append("%.2f".format(java.util.Locale.US, multiplier))
            if (action != null) append(" action=").append(action)
            if (result != null) append(" result=").append(result)
            append(" confidence=").append("%.2f".format(java.util.Locale.US, confidence))
            append(" reason=").append(reason)
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("automation_last_log", line)
            .apply()
        AutomationProfileStore(appContext).appendLog(line)
    }

    private lateinit var machine: AutomationStateMachine
    private val controller = DemoGameController { action ->
        AutomationProfileStore(appContext).appendLog(
            System.currentTimeMillis().toString() + " GAME_ACTION " + action
        )
    }

    private fun buildMachine() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val threshold = prefs.getString("countdown_threshold", BET_COUNTDOWN_THRESHOLD.toString())?.toIntOrNull()
            ?.coerceIn(1, 300) ?: BET_COUNTDOWN_THRESHOLD
        val amount = prefs.getString("amount", TEST_BET_AMOUNT.toString())?.toDoubleOrNull()
            ?.takeIf { it > 0 } ?: TEST_BET_AMOUNT
        val target = prefs.getString("target", TARGET_MULTIPLIER.toString())?.toDoubleOrNull()
            ?.takeIf { it >= 1.0 } ?: TARGET_MULTIPLIER
        val cooldown = prefs.getString("cooldown_seconds", "10")?.toLongOrNull()
            ?.coerceIn(1, 300)?.times(1000L) ?: COOLDOWN_MS
        machine = AutomationStateMachine(
            betThreshold = threshold,
            betAmount = amount,
            targetMultiplier = target,
            cooldownMs = cooldown,
            logger = logger,
            controller = controller
        )
    }

    fun start() {
        buildMachine()
        running = true
        machine.start()
        publish()
    }

    fun stop(reason: String = "stopped by user") {
        running = false
        if (::machine.isInitialized) machine.stop()
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("automation_error", reason)
            .putBoolean("automation_running", false)
            .apply()
        publish()

    }

    fun isRunning(): Boolean = running

    fun onAccessibilitySnapshot(packageName: String, texts: List<String>) {
        if (!running) return
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = prefs.getString("selected_package", null)
        if (target.isNullOrBlank() || packageName != target) {
            stop("Target package changed or is not configured.")
            return
        }

        prefs.edit()
            .putLong("accessibility_heartbeat", SystemClock.elapsedRealtime())
            .putString("accessibility_package", packageName)
            .apply()

        val joined = texts.joinToString(" ")
        val multiplier = MultiplierDetector().parse(joined)
        val countdown = CountdownDetector().parse(joined)
        if (multiplier != null) lastMultiplierAt = SystemClock.elapsedRealtime()
        if (countdown != null) countdownSeenAt = SystemClock.elapsedRealtime()

        val explicitEnd = Regex("(?i)\\b(crash|crashed|blast|blasted|round ended|round over)\\b").containsMatchIn(joined)
        val roundActive = multiplier != null
        val roundEnded = explicitEnd || (
            machine.snapshot().state == AutomationState.ROUND_ACTIVE &&
                countdown != null &&
                SystemClock.elapsedRealtime() - lastMultiplierAt > END_SIGNAL_STABLE_MS
            )
        machine.observe(detector.fromAccessibility(texts, roundActive, roundEnded))
        publish()
    }

    fun onFrame(image: Image) {
        if (!running || busy.get()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOcrAt < OCR_MIN_INTERVAL_MS) return
        lastOcrAt = now
        if (!busy.compareAndSet(false, true)) return

        val left = cropRoi(image, 0.00f, 0.05f, 0.32f, 0.45f, 2)
        val center = cropRoi(image, 0.18f, 0.10f, 0.88f, 0.72f, 2)
        val controls = cropRoi(image, 0.62f, 0.58f, 1.00f, 1.00f, 2)
        if (left == null || center == null || controls == null) {
            busy.set(false)
            return
        }

        val leftTask = recognizer.process(InputImage.fromBitmap(left, 0))
        val centerTask = recognizer.process(InputImage.fromBitmap(center, 0))
        val controlsTask = recognizer.process(InputImage.fromBitmap(controls, 0))
        com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(leftTask, centerTask, controlsTask)
            .addOnSuccessListener { results ->
                if (!running) return@addOnSuccessListener
                val leftText = (results.getOrNull(0) as? com.google.mlkit.vision.text.Text)?.text.orEmpty()
                val centerText = (results.getOrNull(1) as? com.google.mlkit.vision.text.Text)?.text.orEmpty()
                val controlText = (results.getOrNull(2) as? com.google.mlkit.vision.text.Text)?.text.orEmpty()
                val all = listOf(leftText, centerText, controlText).filter { it.isNotBlank() }.joinToString(" ")
                val multiplier = MultiplierDetector().parse(centerText)
                val countdown = CountdownDetector().parse(leftText)
                val nowMs = SystemClock.elapsedRealtime()
                if (multiplier != null) {
                    lastMultiplierAt = nowMs
                    activeSeenAt = nowMs
                }
                if (countdown != null) countdownSeenAt = nowMs

                val explicitEnd = Regex("(?i)\\b(crash|crashed|blast|blasted|round ended|round over)\\b").containsMatchIn(all)
                val current = machine.snapshot()
                val roundEnded = explicitEnd ||
                    (current.state == AutomationState.ROUND_ACTIVE &&
                        multiplier == null &&
                        countdown != null &&
                        nowMs - lastMultiplierAt >= END_SIGNAL_STABLE_MS)

                val confidence = when {
                    multiplier != null || countdown != null -> 0.90f
                    controlText.isNotBlank() -> 0.70f
                    else -> 0.0f
                }
                val observation = GameObservation(
                    countdown = countdown,
                    multiplier = multiplier,
                    roundActive = multiplier != null || nowMs - activeSeenAt < 700L,
                    roundEnded = roundEnded,
                    confidence = confidence,
                    source = "ocr-roi"
                )
                machine.observe(observation)
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("automation_countdown", countdown?.toString() ?: "")
                    .putString("automation_multiplier", multiplier?.toString() ?: "")
                    .putString("automation_controls", controlText.take(160))
                    .putFloat("automation_confidence", confidence)
                    .apply()
                publish()
            }
            .addOnFailureListener {
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("automation_error", "OCR unavailable: " + (it.message ?: "unknown error"))
                    .putFloat("automation_confidence", 0f)
                    .apply()
            }
            .addOnCompleteListener {
                left.recycle()
                center.recycle()
                controls.recycle()
                busy.set(false)
            }
    }

    private fun publish() {
        if (!::machine.isInitialized) return
        val s = machine.snapshot()
        onSnapshot(s)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("automation_running", running)
            .putString("automation_state", s.state.name)
            .putString("automation_round_id", s.roundId)
            .putString("automation_last_action", s.lastAction ?: "")
            .putString("automation_error", s.error ?: "")
            .apply()
    }

    private fun cropRoi(image: Image, leftF: Float, topF: Float, rightF: Float, bottomF: Float, scale: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val left = (width * leftF).toInt().coerceIn(0, width - 1)
        val top = (height * topF).toInt().coerceIn(0, height - 1)
        val right = (width * rightF).toInt().coerceIn(left + 1, width)
        val bottom = (height * bottomF).toInt().coerceIn(top + 1, height)
        val outWidth = (right - left) * scale
        val outHeight = (bottom - top) * scale
        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val src: ByteBuffer = plane.buffer
        val stride = plane.rowStride
        val pixel = plane.pixelStride
        val pixels = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val sy = top + y / scale
            for (x in 0 until outWidth) {
                val sx = left + x / scale
                val pos = sy * stride + sx * pixel
                if (pos + 3 >= src.limit()) continue
                val r = src.get(pos).toInt() and 255
                val g = src.get(pos + 1).toInt() and 255
                val b = src.get(pos + 2).toInt() and 255
                val a = src.get(pos + 3).toInt() and 255
                pixels[y * outWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        out.setPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
        return out
    }
}
