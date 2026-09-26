package com.casinomaster

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(248, 248, 248))
        }

        root.addView(TextView(this).apply {
            text = "CasinoMaster — Safe Simulator"
            textSize = 26f
            setTextColor(Color.BLACK)
        })

        root.addView(TextView(this).apply {
            text = "Local simulation only. This build does not read, control, or click other apps and does not use Accessibility or overlay permissions."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 12, 0, 22)
        })

        root.addView(Button(this).apply {
            text = "OPEN LOCAL AUTOMATION SIMULATOR"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AutomationSimulatorActivity::class.java))
            }
        })

        root.addView(TextView(this).apply {
            text = "Security status\n• No Accessibility Service\n• No overlay permission\n• No foreground service\n• No external-app input injection\n• No sensitive runtime permissions"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 24, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
