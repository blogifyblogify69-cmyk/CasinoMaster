package com.casinomaster

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CAPTURE = 4001
        private const val REQUEST_OVERLAY = 4002
        private const val PREFS = "settings"
    }

    private lateinit var prefs: android.content.SharedPreferences
    private var pendingCaptureAfterOverlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        showHome()
    }

    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(TextView(this).apply {
            text = "CasinoMaster"
            textSize = 30f
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.addView(TextView(this).apply {
            text = "App monitor • offline observe-only"
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 6, 0, 24)
        })

        val selectedPackage = prefs.getString("selected_package", null)
        val selectedLabel = prefs.getString("selected_label", null)

        root.addView(TextView(this).apply {
            text = if (selectedPackage != null) {
                "Selected installed app: " + selectedLabel + "\n" + selectedPackage
            } else {
                val apk = prefs.getString("selected_apk_uri", null)
                if (apk != null) "Selected APK: " + selectedLabel else "No app or APK selected"
            }
            textSize = 17f
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        root.addView(Button(this).apply {
            text = "+ ADD APP"
            setOnClickListener { showAppPicker() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        root.addView(Button(this).apply {
            text = "OPEN SELECTED APP + FLOATING CONTROL"
            isEnabled = selectedPackage != null
            setOnClickListener { openWithOverlay(selectedPackage!!) }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        root.addView(Button(this).apply {
            text = "START SCREEN INSPECTION"
            isEnabled = selectedPackage != null
            setOnClickListener { startCaptureFlow() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        root.addView(TextView(this).apply {
            text = "Display settings"
            textSize = 21f
            setPadding(0, 8, 0, 10)
        })

        val amount = EditText(this).apply {
            hint = "Display bet amount, e.g. 20"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getString("amount", "20"))
            setSingleLine(true)
        }
        root.addView(amount)

        val target = EditText(this).apply {
            hint = "Display collect target, e.g. 1.50"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getString("target", "1.50"))
            setSingleLine(true)
        }
        root.addView(target)

        root.addView(Button(this).apply {
            text = "SAVE SETTINGS"
            setOnClickListener {
                prefs.edit()
                    .putString("amount", amount.text.toString())
                    .putString("target", target.text.toString())
                    .apply()
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        root.addView(TextView(this).apply {
            text = "Workflow:\n1. Add a launchable app.\n2. Save display settings.\n3. Open it with the floating CM button.\n4. Start screen inspection and approve Android's MediaProjection dialog.\n5. Move the manual BET and COLLECT markers to the locations you want to remember.\n\nThe monitor only observes screen changes. It never taps, places bets, or presses cash-out."
            setPadding(0, 0, 0, 18)
        })

        root.addView(TextView(this).apply {
            text = "Add App now has two sections: Installed Apps shows installed packages, and APK Files lets you add APK files from the phone. APK files are kept as selectable file entries; installing an APK still uses Android's own package installer and confirmation."
            setPadding(0, 0, 0, 18)
        })

        setContentView(scroll)
    }

    private fun showAppPicker() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        val search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine(true)
        }
        val list = ListView(this)
        box.addView(search)
        box.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        val dialog = android.app.Dialog(this)
        dialog.setTitle("Add app")
        dialog.setContentView(box)

        fun filtered(): List<ApplicationInfo> {
            val q = search.text.toString()
            return apps.filter {
                it.loadLabel(pm).toString().contains(q, true) || it.packageName.contains(q, true)
            }
        }

        fun refresh() {
            val items = filtered()
            list.adapter = object : ArrayAdapter<ApplicationInfo>(
                this, android.R.layout.simple_list_item_1, items
            ) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    val app = getItem(position)!!
                    view.text = app.loadLabel(pm).toString()
                    view.setCompoundDrawablesWithIntrinsicBounds(app.loadIcon(pm), null, null, null)
                    view.compoundDrawablePadding = 18
                    return view
                }
            }
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        list.setOnItemClickListener { _, _, position, _ ->
            val app = filtered()[position]
            prefs.edit()
                .putString("selected_package", app.packageName)
                .putString("selected_label", app.loadLabel(pm).toString())
                .apply()
            dialog.dismiss()
            showHome()
        }

        refresh()
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.86f).toInt()
        )
    }

    private fun openWithOverlay(packageName: String) {
        if (!Settings.canDrawOverlays(this)) {
            pendingCaptureAfterOverlay = false
            requestOverlayPermission()
            Toast.makeText(this, "Enable floating-window access, then press Open again.", Toast.LENGTH_LONG).show()
            return
        }
        startMonitorService(showOverlay = true)
        openTarget(packageName)
    }

    private fun startCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) {
            pendingCaptureAfterOverlay = true
            requestOverlayPermission()
            return
        }
        requestScreenCapture()
    }

    private fun requestOverlayPermission() {
        try {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)),
                REQUEST_OVERLAY
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    private fun startMonitorService(showOverlay: Boolean, resultCode: Int? = null, data: Intent? = null) {
        val serviceIntent = Intent(this, MonitorService::class.java)
            .putExtra(MonitorService.EXTRA_SHOW_OVERLAY, showOverlay)
        if (resultCode != null && data != null) {
            serviceIntent.putExtra(MonitorService.EXTRA_RESULT_CODE, resultCode)
            serviceIntent.putExtra(MonitorService.EXTRA_RESULT_DATA, data)
        }
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun openTarget(packageName: String) {
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) {
                Toast.makeText(this, "Selected app cannot be launched.", Toast.LENGTH_SHORT).show()
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Could not open selected app.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        // Closing the main CasinoMaster activity also stops its monitor service,
        // projection session, floating bubble, markers and panel.
        stopService(Intent(this, MonitorService::class.java))
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this) && pendingCaptureAfterOverlay) {
                pendingCaptureAfterOverlay = false
                requestScreenCapture()
            }
        } else if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startMonitorService(true, resultCode, data)
                prefs.getString("selected_package", null)?.let { openTarget(it) }
            } else {
                Toast.makeText(this, "Screen inspection was cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
