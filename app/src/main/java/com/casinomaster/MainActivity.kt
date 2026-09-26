package com.casinomaster

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private data class AppEntry(val label: String, val packageName: String)
    private lateinit var spinner: Spinner
    private var apps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }
    override fun onResume() {
        super.onResume()
        if (::spinner.isInitialized) loadApps()
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(248, 248, 248))
        }
        root.addView(TextView(this).apply {
            text = "CasinoMaster"; textSize = 28f; setTextColor(Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "Add an installed app to monitor. Countdown inspection is observation-only; A/B actions remain manual."
            textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, 10, 0, 20)
        })
        spinner = Spinner(this)
        root.addView(spinner, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply { text = "REFRESH APP LIST"; setOnClickListener { loadApps() } })
        root.addView(Button(this).apply {
            text = "ADD / SELECT APP"
            setOnClickListener {
                val selected = apps.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("selected_package", selected.packageName)
                    .putString("selected_label", selected.label).apply()
                Toast.makeText(this@MainActivity, selected.label + " added", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "OPEN ADDED APP + FLOATING CONTROL"; setOnClickListener { openSelectedApp() }
        })
        root.addView(Button(this).apply {
            text = "ALLOW FLOATING ICON"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity))
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
                else Toast.makeText(this@MainActivity, "Floating icon permission is enabled.", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Inspection: countdown observation only\nExternal-app automatic clicking: disabled"
            textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, 24, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
        loadApps()
    }
    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull {
                val pkg = it.activityInfo.packageName
                if (pkg == this.packageName) return@mapNotNull null
                val label = it.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) null else AppEntry(label, pkg)
            }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps.map { it.label + " (" + it.packageName + ")" })
        val selectedPackage = getSharedPreferences("settings", MODE_PRIVATE).getString("selected_package", null)
        val index = apps.indexOfFirst { it.packageName == selectedPackage }
        if (index >= 0) spinner.setSelection(index)
    }
    private fun openSelectedApp() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val pkg = prefs.getString("selected_package", null)
        if (pkg.isNullOrBlank()) { Toast.makeText(this, "Select and add an app first.", Toast.LENGTH_SHORT).show(); return }
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Allow the floating icon permission first.", Toast.LENGTH_LONG).show(); return }
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent == null) { Toast.makeText(this, "The selected app cannot be opened.", Toast.LENGTH_SHORT).show(); return }
        startService(Intent(this, AppControllerService::class.java).apply { action = AppControllerService.ACTION_SHOW })
        startActivity(launchIntent)
    }
}