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

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val amount = EditText(this).apply {
            hint = "Bet amount (display only)"
            inputType = 2
            setText(prefs.getString("amount", "20"))
        }
        root.addView(amount)

        val target = EditText(this).apply {
            hint = "Target multiplier"
            inputType = 8194
            setText(prefs.getString("target", "2.00"))
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
                showAccessibilityDisclosure(force = true)
            }
        })

        root.addView(Button(this).apply {
            text = "SAVE SETTINGS"
            setOnClickListener {
                prefs.edit()
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
            text = "Accessibility access is optional and must be enabled manually in Android Settings. It is used only to read visible game text for this observe-only prototype."
            setPadding(0, 28, 0, 0)
        })

        root.addView(TextView(this).apply {
            text = "Observe-only prototype: it does not place bets, press cash-out, or execute wagering actions."
            setPadding(0, 20, 0, 0)
        })

        setContentView(root)
        updateMonitorStatus()
    }

    override fun onResume() {
        super.onResume()
        updateMonitorStatus()

        if (!isAccessibilityEnabled(this) &&
            !getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("accessibility_disclosure_shown", false)
        ) {
            showAccessibilityDisclosure(force = false)
        }
    }

    private fun updateMonitorStatus() {
        monitorStatus.text = if (isAccessibilityEnabled(this)) {
            "Monitor: ON — Accessibility access granted"
        } else {
            "Monitor: OFF — Accessibility access required"
        }
    }

    private fun showAccessibilityDisclosure(force: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        AlertDialog.Builder(this)
            .setTitle("Accessibility access")
            .setMessage(
                "CasinoMaster can read visible text from the active screen when Accessibility access is enabled. " +
                "This is used only for the app's observe-only monitoring feature. " +
                "The current prototype does not automatically place bets or press cash-out. " +
                "Android requires you to grant this access manually in Accessibility Settings."
            )
            .setPositiveButton("I UNDERSTAND — OPEN SETTINGS") { _, _ ->
                prefs.edit().putBoolean("accessibility_disclosure_shown", true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(if (force) "CANCEL" else "NOT NOW") { _, _ ->
                prefs.edit().putBoolean("accessibility_disclosure_shown", true).apply()
            }
            .setOnCancelListener {
                prefs.edit().putBoolean("accessibility_disclosure_shown", true).apply()
            }
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
