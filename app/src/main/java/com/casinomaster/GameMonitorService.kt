package com.casinomaster

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GameMonitorService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val values = ArrayList<String>()
        collectText(root, values)
        val countdown = values.asSequence().mapNotNull { CountdownDetector.parse(it) }.firstOrNull()
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("inspection_state", if (countdown != null) "COUNTDOWN DETECTED" else "WATCHING")
            .putString("countdown_value", countdown?.toString() ?: "not detected")
            .apply()
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() = Unit
}

object CountdownDetector {
    private val patterns = listOf(
        Regex("""^\s*(\d{1,3})\s*$"""),
        Regex("""^\s*(\d{1,3})\s*(?:s|sec|secs|second|seconds)\s*$""", RegexOption.IGNORE_CASE)
    )

    fun parse(value: String): Int? {
        for (pattern in patterns) {
            val match = pattern.matchEntire(value.trim()) ?: continue
            return match.groupValues[1].toIntOrNull()?.takeIf { it in 0..999 }
        }
        return null
    }
}