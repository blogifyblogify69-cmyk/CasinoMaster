package com.casinomaster

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }

        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(TextView(this).apply {
            text = "CasinoMaster"
            textSize = 30f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Android-safe observe-only prototype"
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 28)
        })

        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 21f
            setPadding(0, 0, 0, 12)
        })

        val amount = EditText(this).apply {
            hint = "Display amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getString("amount", "20"))
            setSingleLine(true)
        }
        root.addView(amount, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        val target = EditText(this).apply {
            hint = "Target multiplier"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getString("target", "2.00"))
            setSingleLine(true)
        }
        root.addView(target, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        root.addView(Button(this).apply {
            text = "SAVE SETTINGS"
            setOnClickListener {
                prefs.edit()
                    .putString("amount", amount.text.toString())
                    .putString("target", target.text.toString())
                    .apply()
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 22 })

        root.addView(TextView(this).apply {
            text = "Monitoring status"
            textSize = 21f
            setPadding(0, 0, 0, 10)
        })

        root.addView(TextView(this).apply {
            text = "● READY — No sensitive Android permissions are requested by this build."
            textSize = 17f
            setPadding(0, 0, 0, 18)
        })

        root.addView(TextView(this).apply {
            text = "This version intentionally does not request Accessibility, camera, microphone, location, contacts, SMS, storage, overlay, or network permissions."
            setPadding(0, 0, 0, 18)
        })

        root.addView(TextView(this).apply {
            text = "Observe-only scope: this app does not place bets, press cash-out, control another app, or execute wagering actions."
            setPadding(0, 0, 0, 18)
        })

        root.addView(TextView(this).apply {
            text = "Screen monitoring is not enabled in this install-safe build. A future monitoring feature should use an explicit Android user-consent flow and the narrowest appropriate API."
            setPadding(0, 0, 0, 18)
        })

        setContentView(scroll)
    }
}
