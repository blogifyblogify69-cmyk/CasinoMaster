package com.casinomaster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AutomationActivity : AppCompatActivity() {
    private lateinit var store: AutomationProfileStore
    private lateinit var targetPackage: EditText
    private lateinit var profileName: EditText
    private lateinit var rulesBox: LinearLayout
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AutomationProfileStore(this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 24) }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(TextView(this).apply { text = "UI Automation / Test Profiles"; textSize = 26f; gravity = Gravity.CENTER; setPadding(0,0,0,16) })
        root.addView(TextView(this).apply { text = "Only explicitly configured CLICK rules are executed, and only for the exact target package. Security/payment/consent screens are blocked."; setPadding(0,0,0,16) })

        profileName = field("Profile name", store.load()?.name ?: "Demo UI Flow")
        targetPackage = field("Target package", store.load()?.targetPackage ?: getSharedPreferences("settings", MODE_PRIVATE).getString("selected_package", "") ?: "")
        root.addView(profileName); root.addView(targetPackage)

        rulesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply { text = "Rules"; textSize = 20f; setPadding(0,16,0,8) })
        root.addView(rulesBox)

        val loaded = store.load()?.rules ?: listOf(
            AutomationRule(text = "START"),
            AutomationRule(text = "NEXT"),
            AutomationRule(text = "DONE")
        )
        loaded.forEach { addRuleRow(it) }

        root.addView(Button(this).apply { text = "+ ADD RULE"; setOnClickListener { addRuleRow(AutomationRule()) } })
        root.addView(Button(this).apply { text = "SAVE PROFILE"; setOnClickListener { saveProfile() } })

        val settings = Button(this).apply { text = "ENABLE ACCESSIBILITY SERVICE"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        root.addView(settings)

        val start = Button(this).apply { text = "START AUTOMATION"; setOnClickListener { saveProfile(); if (!isServiceEnabled()) { Toast.makeText(this@AutomationActivity, "Enable the Accessibility service first.", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return@setOnClickListener }; store.setPaused(false); store.setRunning(true); store.appendLog("START_AUTOMATION"); Toast.makeText(this@AutomationActivity, "Automation started", Toast.LENGTH_SHORT).show(); refreshLog() } }
        root.addView(start)
        root.addView(Button(this).apply { text = "PAUSE / RESUME"; setOnClickListener { val next = !store.isPaused(); store.setPaused(next); store.appendLog(if (next) "PAUSED" else "RESUMED"); refreshLog() } })
        root.addView(Button(this).apply { text = "STOP AUTOMATION"; setOnClickListener { store.setRunning(false); store.setPaused(false); store.appendLog("STOP_AUTOMATION"); refreshLog() } })
        root.addView(Button(this).apply { text = "CLEAR ACTION LOG"; setOnClickListener { store.clearLog(); refreshLog() } })
        logView = TextView(this).apply { setTextColor(Color.DKGRAY); setPadding(0,16,0,0); setTextIsSelectable(true) }
        root.addView(logView)
        refreshLog()
        setContentView(scroll)
    }

    private fun field(hint: String, value: String): EditText = EditText(this).apply { this.hint = hint; setText(value); setSingleLine(true) }

    private fun addRuleRow(rule: AutomationRule) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12); setBackgroundColor(Color.rgb(240,240,240)) }
        val textField = field("Visible text (optional)", rule.text ?: "")
        val desc = field("contentDescription (optional)", rule.contentDescription ?: "")
        val id = field("Resource/view ID (optional)", rule.viewId ?: "")
        val cls = field("Accessibility class (optional)", rule.className ?: "")
        val timeout = field("Timeout ms", rule.timeoutMs.toString())
        val retries = field("Max retries", rule.maxRetries.toString())
        card.addView(textField); card.addView(desc); card.addView(id); card.addView(cls); card.addView(timeout); card.addView(retries)
        card.tag = listOf(textField, desc, id, cls, timeout, retries)
        card.addView(Button(this).apply { text = "REMOVE RULE"; setOnClickListener { rulesBox.removeView(card) } })
        rulesBox.addView(card, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = 10 })
    }

    private fun saveProfile() {
        val rules = mutableListOf<AutomationRule>()
        for (i in 0 until rulesBox.childCount) {
            val card = rulesBox.getChildAt(i) as? LinearLayout ?: continue
            @Suppress("UNCHECKED_CAST") val fields = card.tag as? List<EditText> ?: continue
            rules += AutomationRule(
                text = fields[0].text.toString().trim().takeIf { it.isNotEmpty() },
                contentDescription = fields[1].text.toString().trim().takeIf { it.isNotEmpty() },
                viewId = fields[2].text.toString().trim().takeIf { it.isNotEmpty() },
                className = fields[3].text.toString().trim().takeIf { it.isNotEmpty() },
                timeoutMs = fields[4].text.toString().toLongOrNull()?.coerceIn(500L,60000L) ?: 5000L,
                maxRetries = fields[5].text.toString().toIntOrNull()?.coerceIn(0,5) ?: 2
            )
        }
        store.save(AutomationProfile(profileName.text.toString().ifBlank { "Automation profile" }, targetPackage.text.toString().trim(), true, rules))
        store.appendLog("PROFILE_SAVED rules=" + rules.size)
        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
        refreshLog()
    }

    private fun refreshLog() { if (::logView.isInitialized) logView.text = "Action log\n" + store.log() }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val expected = ComponentName(this, AccessibilityAutomationService::class.java).flattenToString()
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { s -> ComponentName(s.packageName, s.name).flattenToString() == expected } == true }
    }
}