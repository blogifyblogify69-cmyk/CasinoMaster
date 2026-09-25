package com.casinomaster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var monitorStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 40, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "CASINOMASTER"
            textSize = 28f
            setPadding(0, 0, 0, 28)
        })

        val amount = EditText(this).apply {
            hint = "Bet amount (display only)"
            inputType = 2
            setText(getSharedPreferences("settings", MODE_PRIVATE).getString("amount", "20"))
        }
        root.addView(amount)

        val target = EditText(this).apply {
            hint = "Target multiplier"
            inputType = 8194
            setText(getSharedPreferences("settings", MODE_PRIVATE).getString("target", "2.00"))
        }
        root.addView(target)

        monitorStatus = TextView(this).apply {
            textSize = 18f
            setPadding(0, 24, 0, 24)
        }
        root.addView(monitorStatus)

        root.addView(Button(this).apply {
            text = "ALLOW ACCESSIBILITY ACCESS"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "SAVE SETTINGS"
            setOnClickListener {
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("amount", amount.text.toString())
                    .putString("target", target.text.toString())
                    .apply()
                Toast.makeText(
                    this@MainActivity,
                    "Settings saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        root.addView(TextView(this).apply {
            text = "Permission note: Android does not provide a normal runtime popup for Accessibility access. The button above opens Android Settings, where you must enable CasinoMaster manually."
            setPadding(0, 28, 0, 0)
        })

        root.addView(TextView(this).apply {
            text = "Observe-only prototype: reads visible game text and reports changes. It does not place bets or press cash-out."
            setPadding(0, 20, 0, 0)
        })

        setContentView(root)
        updateMonitorStatus()
    }

    override fun onResume() {
        super.onResume()
        updateMonitorStatus()
        if (!isAccessibilityEnabled(this)) {
            showAccessibilityOnboarding()
        }
    }

    private fun updateMonitorStatus() {
        monitorStatus.text = if (isAccessibilityEnabled(this)) {
            "Monitor: ON — Accessibility access granted"
        } else {
            "Monitor: OFF — Accessibility access required"
        }
    }

    private fun showAccessibilityOnboarding() {
        AlertDialog.Builder(this)
            .setTitle("Allow CasinoMaster access")
            .setMessage(
                "To monitor the visible game screen, CasinoMaster needs Accessibility access. " +
                "Android will open its Accessibility settings. Select CasinoMaster and turn it ON, then return to the app."
            )
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("NOT NOW", null)
            .show()
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GameMonitorService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
