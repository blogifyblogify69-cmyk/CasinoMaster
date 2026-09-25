package com.casinomaster
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(32,40,32,32) }
        root.addView(TextView(this).apply { text="CASINOMASTER"; textSize=28f; setPadding(0,0,0,28) })
        val amount=EditText(this).apply { hint="Bet amount (display only)"; inputType=2; setText("20") }
        root.addView(amount)
        val target=EditText(this).apply { hint="Target multiplier"; inputType=8194; setText("2.00") }
        root.addView(target)
        root.addView(TextView(this).apply { text="Monitor: OFF"; textSize=18f; setPadding(0,24,0,24) })
        root.addView(Button(this).apply { text="OPEN ACCESSIBILITY SETTINGS"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(Button(this).apply { text="SAVE SETTINGS"; setOnClickListener {
            getSharedPreferences("settings",MODE_PRIVATE).edit().putString("amount",amount.text.toString()).putString("target",target.text.toString()).apply()
            Toast.makeText(this@MainActivity,"Settings saved",Toast.LENGTH_SHORT).show()
        }})
        root.addView(TextView(this).apply { text="Observe-only prototype: reads visible game text and reports changes. It does not place bets or press cash-out."; setPadding(0,28,0,0) })
        setContentView(root)
    }
}
