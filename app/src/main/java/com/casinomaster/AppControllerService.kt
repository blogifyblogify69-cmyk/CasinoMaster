package com.casinomaster

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class AppControllerService : Service() {
    companion object {
        const val ACTION_SHOW = "com.casinomaster.SHOW"
        private const val CHANNEL = "controller"
        private const val PREFS = "settings"
    }
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Floating controller", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(910, Notification.Builder(this, CHANNEL)
            .setContentTitle("CasinoMaster")
            .setContentText("Floating controller active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) showBubble()
        return START_NOT_STICKY
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return
        bubble = TextView(this).apply {
            text = "CM"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45,45,55))
            setOnClickListener { togglePanel() }
        }
        val p = WindowManager.LayoutParams(
            64,64,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.START; x=20; y=220 }
        wm.addView(bubble,p)
    }

    private fun togglePanel() {
        if (panel != null) { removePanel(); return }
        val selected = prefs().getString("selected_label","Selected app") ?: "Selected app"
        panel = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18,18,18,18)
            setBackgroundColor(Color.WHITE)
        }
        panel!!.addView(TextView(this).apply {
            text="CasinoMaster\n"+selected
            textSize=18f
            setTextColor(Color.BLACK)
        })
        panel!!.addView(Button(this).apply {
            text="ACTIVE"
            setOnClickListener {
                prefs().edit().putBoolean("controller_active",true).putString("controller_state","ACTIVE").apply()
                Toast.makeText(this@AppControllerService,"Active: countdown inspection is running; A/B are manual.",Toast.LENGTH_SHORT).show()
                addTargets()
            }
        })
        panel!!.addView(Button(this).apply {
            text="INSPECTION STATUS"
            setOnClickListener {
                val p=prefs()
                Toast.makeText(this@AppControllerService,
                    "Countdown: "+p.getString("countdown_value","not detected")+"\n"+
                    "State: "+p.getString("inspection_state","OFF"),
                    Toast.LENGTH_LONG).show()
            }
        })
        panel!!.addView(Button(this).apply {
            text="STOP"
            setOnClickListener {
                prefs().edit().putBoolean("controller_active",false).putString("controller_state","STOPPED").apply()
                stopSelf()
            }
        })
        val wp=WindowManager.LayoutParams(
            minOf(360,resources.displayMetrics.widthPixels-24),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.END; x=12; y=150 }
        wm.addView(panel,wp)
    }

    private fun addTargets() {
        if (panel?.findViewWithTag<View>("A") != null) return
        panel?.addView(Button(this).apply {
            tag="A"; text="TARGET A"
            setOnClickListener {
                prefs().edit().putString("last_manual_target","A").apply()
                Toast.makeText(this@AppControllerService,"Target A selected manually.",Toast.LENGTH_SHORT).show()
            }
        })
        panel?.addView(Button(this).apply {
            tag="B"; text="TARGET B"
            setOnClickListener {
                prefs().edit().putString("last_manual_target","B").apply()
                Toast.makeText(this@AppControllerService,"Target B selected manually.",Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun removePanel() {
        panel?.let { try { wm.removeView(it) } catch (_:Exception){} }
        panel=null
    }

    override fun onDestroy() {
        removePanel()
        bubble?.let { try { wm.removeView(it) } catch (_:Exception){} }
        bubble=null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?)=null
}