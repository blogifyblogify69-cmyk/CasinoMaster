package com.casinomaster

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MonitorService : Service() {
    companion object {
        const val EXTRA_SHOW_OVERLAY = "show_overlay"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "casino_monitor"
        private const val NOTIFICATION_ID = 77
        private const val PREFS = "settings"
    }

    private lateinit var wm: WindowManager
    private var windowContext: Context? = null
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var betMarker: TextView? = null
    private var collectMarker: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastHash = 0L
    private var stable = 0
    private var changed = 0

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CasinoMaster monitor")
            .setContentText("Floating observe-only control is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        thread = HandlerThread("CasinoMasterMonitor").also {
            it.start()
            handler = Handler(it.looper)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_SHOW_OVERLAY, false) == true) showBubble()
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (code != Int.MIN_VALUE && data != null && projection == null) startProjection(code, data)
        return START_STICKY
    }

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return
        val primary = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        windowContext = if (Build.VERSION.SDK_INT >= 30) {
            createDisplayContext(primary).createWindowContext(overlayType(), null)
        } else this

        bubble = TextView(windowContext).apply {
            text = "CM"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setPadding(8, 8, 8, 8)
            setOnClickListener { togglePanel() }
            setOnTouchListener(BubbleTouchListener())
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        bubbleParams = WindowManager.LayoutParams(
            58, 58, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("bubble_x", 24)
            y = prefs.getInt("bubble_y", 220)
        }
        wm.addView(bubble, bubbleParams)
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downX = 0
        private var downY = 0
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = bubbleParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX.toInt()
                    downY = e.rawY.toInt()
                    startX = p.x
                    startY = p.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX.toInt() - downX
                    val dy = e.rawY.toInt() - downY
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    p.x = startX + dx
                    p.y = startY + dy
                    wm.updateViewLayout(v, p)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) v.performClick()
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt("bubble_x", p.x).putInt("bubble_y", p.y).apply()
                    return true
                }
            }
            return false
        }
    }

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
            return
        }
        val ctx = windowContext ?: this
        panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }
        panel!!.addView(TextView(ctx).apply {
            text = "CasinoMaster • Manual Control"
            textSize = 18f
            setTextColor(Color.BLACK)
        })

        val amount = EditText(ctx).apply {
            hint = "Bet amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("amount", "20"))
            setSingleLine(true)
        }
        panel!!.addView(amount)

        val target = EditText(ctx).apply {
            hint = "Collect target"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("target", "1.50"))
            setSingleLine(true)
        }
        panel!!.addView(target)

        panel!!.addView(Button(ctx).apply {
            text = "SAVE DISPLAY SETTINGS"
            setOnClickListener {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("amount", amount.text.toString())
                    .putString("target", target.text.toString()).apply()
                Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        })

        panel!!.addView(Button(ctx).apply {
            text = "SHOW / MOVE MANUAL MARKERS"
            setOnClickListener { showMarkers() }
        })

        panel!!.addView(Button(ctx).apply {
            text = "INSPECTION STATUS"
            setOnClickListener {
                val p = getSharedPreferences(PREFS, MODE_PRIVATE)
                Toast.makeText(
                    ctx,
                    (p.getString("monitor_state", "OFF") ?: "OFF") + "\n" +
                        (p.getString("monitor_detail", "") ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        panel!!.addView(Button(ctx).apply {
            text = "CLOSE PANEL"
            setOnClickListener { removePanel() }
        })

        panel!!.addView(Button(ctx).apply {
            text = "STOP WORKING"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(190, 35, 35))
            setOnClickListener {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("monitor_state", "OFF")
                    .putString("monitor_detail", "Stopped by user.")
                    .apply()
                stopSelf()
            }
        })


        val params = WindowManager.LayoutParams(
            min(340, resources.displayMetrics.widthPixels - 24),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 150
        }
        wm.addView(panel, params)
    }

    private fun removePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private fun showMarkers() {
        if (betMarker != null || collectMarker != null) return
        val ctx = windowContext ?: this
        betMarker = marker(ctx, "BET")
        collectMarker = marker(ctx, "COLLECT")
        addMarker(betMarker!!, "bet_marker_x", "bet_marker_y", 40, 520)
        addMarker(collectMarker!!, "collect_marker_x", "collect_marker_y", 180, 520)
    }

    private fun marker(ctx: Context, label: String) = TextView(ctx).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(if (label == "BET") Color.rgb(30, 110, 210) else Color.rgb(30, 150, 80))
    }

    private fun addMarker(view: TextView, xKey: String, yKey: String, defaultX: Int, defaultY: Int) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            90, 52, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(xKey, defaultX)
            y = prefs.getInt(yKey, defaultY)
        }
        var downX = 0
        var downY = 0
        var startX = 0
        var startY = 0
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX.toInt()
                    downY = e.rawY.toInt()
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + e.rawX.toInt() - downX
                    params.y = startY + e.rawY.toInt() - downY
                    wm.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt(xKey, params.x).putInt(yKey, params.y).apply()
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
    }

    private fun startProjection(code: Int, data: Intent) {
        if (Build.VERSION.SDK_INT >= 29) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CasinoMaster monitor")
                .setContentText("Screen inspection is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(code, data)
        val metrics = resources.displayMetrics
        val width = max(320, metrics.widthPixels)
        val height = max(480, metrics.heightPixels)

        reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopProjection() }
        }, handler)

        reader?.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { updateVisualState(hashImage(image)) } finally { image.close() }
        }, handler)

        display = projection?.createVirtualDisplay(
            "CasinoMasterMonitor", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("monitor_state", "LEARNING")
            .putString("monitor_detail", "Local visual baseline is being learned.")
            .apply()
    }

    private fun hashImage(image: android.media.Image): Long {
        val plane = image.planes.firstOrNull() ?: return 0L
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val step = max(8, image.width / 24)
        var hash = 1469598103934665603L
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val pos = y * rowStride + x * pixelStride
                if (pos + 2 < buffer.limit()) {
                    hash = hash xor (buffer.get(pos).toLong() and 255L)
                    hash *= 1099511628211L
                    hash = hash xor ((buffer.get(pos + 1).toLong() and 255L) * 3L)
                    hash *= 1099511628211L
                    hash = hash xor ((buffer.get(pos + 2).toLong() and 255L) * 7L)
                    hash *= 1099511628211L
                }
                x += step
            }
            y += step
        }
        return hash
    }

    private fun updateVisualState(hash: Long) {
        if (lastHash == 0L) {
            lastHash = hash
            stable = 1
            return
        }
        val delta = abs(hash - lastHash)
        lastHash = hash
        if (delta > 1000000L) {
            changed++
            stable = 0
        } else {
            stable++
            changed = 0
        }

        val state: String
        val detail: String
        when {
            changed >= 3 -> {
                state = "SCREEN CHANGE"
                detail = "Visual change detected locally. Heuristic only; no action is executed."
            }
            stable >= 5 -> {
                state = "WAITING / STABLE"
                detail = "Screen is visually stable. Ready to observe another change."
            }
            else -> {
                state = "LEARNING"
                detail = "Sampling the selected screen locally."
            }
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("monitor_state", state)
            .putString("monitor_detail", detail)
            .apply()
    }

    private fun stopProjection() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        projection = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("monitor_state", "OFF")
            .putString("monitor_detail", "Screen capture session ended.")
            .apply()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "CasinoMaster monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // If the user removes CasinoMaster from the recent-apps/task list,
        // stop the monitor service and all floating windows as well.
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        removePanel()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        betMarker?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        collectMarker?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        betMarker = null
        collectMarker = null
        stopProjection()
        thread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
