package com.casinomaster

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class AutomationSimulatorActivity : Activity() {
    private enum class Phase { WAITING_FOR_15, WAITING_FOR_A }
    private var phase = Phase.WAITING_FOR_15
    private var simulatedTimer = 30
    private var secondsSinceB = 0
    private var lastAction = "None"
    private var cycle = 0
    private var running = false

    private lateinit var timerText: TextView
    private lateinit var phaseText: TextView
    private lateinit var actionText: TextView
    private lateinit var waitText: TextView
    private lateinit var logText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            advanceOneSecond()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateUi()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(248, 248, 248))
        }

        root.addView(TextView(this).apply {
            text = "Auto Clicker — Local Simulator"
            textSize = 25f
            setTextColor(Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "Safe test mode: A/B are simulated actions only. No external app is clicked."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 18)
        })

        timerText = label("SIMULATED TIMER")
        timerText.textSize = 42f
        timerText.gravity = Gravity.CENTER
        root.addView(timerText, LinearLayout.LayoutParams(-1, 90))

        phaseText = label("")
        root.addView(phaseText)
        actionText = label("")
        root.addView(actionText)
        waitText = label("")
        root.addView(waitText)

        root.addView(Button(this).apply {
            text = "SET TIMER TO 15"
            setOnClickListener { setTimer(15) }
        })
        root.addView(Button(this).apply {
            text = "ADVANCE 1 SECOND"
            setOnClickListener { advanceOneSecond() }
        })
        root.addView(Button(this).apply {
            text = "START / PAUSE AUTO TIMER"
            setOnClickListener { toggleRunning() }
        })
        root.addView(Button(this).apply {
            text = "RESET SIMULATOR"
            setOnClickListener { reset() }
        })

        root.addView(TextView(this).apply {
            text = "Event log"
            textSize = 18f
            setTextColor(Color.BLACK)
            setPadding(0, 18, 0, 6)
        })
        logText = label("")
        logText.minLines = 7
        root.addView(logText)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 16f
        setTextColor(Color.DKGRAY)
        setPadding(0, 5, 0, 5)
    }

    private fun setTimer(value: Int) {
        simulatedTimer = value
        processState()
        updateUi()
    }

    private fun advanceOneSecond() {
        if (phase == Phase.WAITING_FOR_A) {
            secondsSinceB++
            if (secondsSinceB >= 22) {
                triggerA()
            }
        } else {
            if (simulatedTimer > 0) simulatedTimer--
            processState()
        }
        updateUi()
    }

    private fun processState() {
        if (phase == Phase.WAITING_FOR_15 && simulatedTimer == 15) {
            triggerB()
        }
    }

    private fun triggerB() {
        phase = Phase.WAITING_FOR_A
        secondsSinceB = 0
        cycle++
        lastAction = "B"
        appendLog("Cycle $cycle: Timer reached 15 → TARGET B triggered once.")
    }

    private fun triggerA() {
        phase = Phase.WAITING_FOR_15
        secondsSinceB = 0
        lastAction = "A"
        appendLog("Cycle $cycle: 22 seconds elapsed → TARGET A triggered once.")
    }

    private fun toggleRunning() {
        running = !running
        if (running) handler.post(tick) else handler.removeCallbacks(tick)
        updateUi()
    }

    private fun reset() {
        running = false
        handler.removeCallbacks(tick)
        phase = Phase.WAITING_FOR_15
        simulatedTimer = 30
        secondsSinceB = 0
        lastAction = "None"
        cycle = 0
        logText.text = "Simulator reset. Waiting for timer = 15."
        updateUi()
    }

    private fun appendLog(message: String) {
        val old = logText.text?.toString().orEmpty()
        logText.text = (if (old.isBlank()) message else "$old\n$message").takeLast(1800)
    }

    private fun updateUi() {
        timerText.text = simulatedTimer.toString()
        phaseText.text = when (phase) {
            Phase.WAITING_FOR_15 -> "STATE: WAITING FOR TIMER = 15"
            Phase.WAITING_FOR_A -> "STATE: B TRIGGERED — WAITING 22 SECONDS"
        }
        actionText.text = "LAST SIMULATED ACTION: $lastAction"
        waitText.text = if (phase == Phase.WAITING_FOR_A)
            "WAIT: $secondsSinceB / 22 seconds"
        else
            "WAIT: 0 / 22 seconds"
    }
}
