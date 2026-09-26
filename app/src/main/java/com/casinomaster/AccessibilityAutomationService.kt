package com.casinomaster

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityAutomationService : AccessibilityService() {
    private lateinit var store: AutomationProfileStore
    private val retryCounts = mutableMapOf<String, Int>()
    private val lastActionAt = mutableMapOf<String, Long>()
    private val waitingSince = mutableMapOf<String, Long>()
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (::store.isInitialized) store.appendLog(stamp() + " ACCESSIBILITY_HEARTBEAT")
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("accessibility_connected", true)
                .putLong("accessibility_heartbeat", SystemClock.elapsedRealtime())
                .putString("accessibility_active_package", activePackage ?: "")
                .apply()
            heartbeatHandler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = AutomationProfileStore(this)
        store.appendLog(stamp() + " SERVICE_CONNECTED")
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("accessibility_connected", true).apply()
        heartbeatHandler.post(heartbeat)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::store.isInitialized) store = AutomationProfileStore(this)
        val profile = store.load() ?: return
        if (!profile.enabled || !store.isRunning() || store.isPaused()) return
        val packageName = event?.packageName?.toString() ?: return
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("accessibility_active_package", packageName)
            .putLong("accessibility_heartbeat", SystemClock.elapsedRealtime())
            .apply()
        if (packageName != profile.targetPackage) return
        val root = rootInActiveWindow ?: return
        val snapshot = mutableListOf<String>()
        collectStrings(root, snapshot)
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("accessibility_package", packageName)
            .putString("accessibility_text", snapshot.joinToString(" "))
            .putLong("accessibility_heartbeat", SystemClock.elapsedRealtime())
            .putBoolean("accessibility_connected", true)
            .apply()
        if (containsSafetySensitiveContent(root)) {
            store.appendLog(stamp() + " SAFETY_GUARD blocked automation on " + packageName)
            return
        }
        profile.rules.forEach { rule ->
            if (!rule.action.equals("CLICK", true)) return@forEach
            val node = findMatch(root, rule)
            if (node == null) {
                val key = ruleKey(rule)
                val started = waitingSince.getOrPut(key) { SystemClock.elapsedRealtime() }
                if (SystemClock.elapsedRealtime() - started >= rule.timeoutMs) {
                    store.appendLog(stamp() + " TIMEOUT " + key + " after=" + rule.timeoutMs + "ms")
                    waitingSince.remove(key)
                }
                return@forEach
            }
            waitingSince.remove(ruleKey(rule))
            val key = ruleKey(rule)
            val now = SystemClock.elapsedRealtime()
            if (now - (lastActionAt[key] ?: 0L) < 750L) return@forEach
            val attempts = retryCounts[key] ?: 0
            if (attempts > rule.maxRetries) return@forEach
            val actionNode = clickableAncestor(node) ?: node
            if (!actionNode.isEnabled || !actionNode.isVisibleToUser || !actionNode.isClickable) {
                store.appendLog(stamp() + " SKIP " + describe(node))
                return@forEach
            }
            lastActionAt[key] = now
            val success = try { actionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            catch (e: Exception) {
                store.appendLog(stamp() + " ERROR " + ruleKey(rule) + " " + (e.message ?: "click failed"))
                false
            }
            if (success) {
                retryCounts[key] = 0
                store.appendLog(stamp() + " CLICK " + describe(actionNode))
            } else {
                retryCounts[key] = attempts + 1
                store.appendLog(stamp() + " RETRY " + ruleKey(rule) + " attempt=" + (attempts + 1) + "/" + (rule.maxRetries + 1))
            }
        }
    }

    override fun onInterrupt() {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("accessibility_connected", false).apply()
        heartbeatHandler.removeCallbacksAndMessages(null)
        if (::store.isInitialized) store.appendLog(stamp() + " SERVICE_INTERRUPTED")
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacksAndMessages(null)
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("accessibility_connected", false).apply()
        super.onDestroy()
    }

    private fun findMatch(root: AccessibilityNodeInfo, rule: AutomationRule): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        walk(root, candidates)
        return candidates.firstOrNull { matches(it, rule) }
    }

    private fun matches(node: AccessibilityNodeInfo, rule: AutomationRule): Boolean {
        val textOk = rule.text == null || node.text?.toString() == rule.text
        val descOk = rule.contentDescription == null || node.contentDescription?.toString() == rule.contentDescription
        val idOk = rule.viewId == null || node.viewIdResourceName == rule.viewId
        val classOk = rule.className == null || node.className?.toString() == rule.className
        return textOk && descOk && idOk && classOk
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out += node
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, out) }
    }

    private fun containsSafetySensitiveContent(root: AccessibilityNodeInfo): Boolean {
        val blocked = listOf("password","passcode","pin","verification","verify","authenticate","sign in","log in","consent","permission","allow","deny","purchase","buy","checkout","payment","pay","transfer","bank","wallet","bet","wager","cash out","withdraw")
        val values = mutableListOf<String>()
        collectStrings(root, values)
        return values.any { value -> blocked.any { token -> value.contains(token, ignoreCase = true) } }
    }

    private fun collectStrings(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectStrings(it, out) }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    private fun describe(node: AccessibilityNodeInfo) = "text=" + node.text + ",desc=" + node.contentDescription + ",id=" + node.viewIdResourceName
    private fun ruleKey(rule: AutomationRule) = listOf(rule.text, rule.contentDescription, rule.viewId, rule.className, rule.action).joinToString("|")
    private fun stamp(): String = "[" + System.currentTimeMillis() + "]"
}