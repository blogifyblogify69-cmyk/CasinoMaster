package com.casinomaster
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GameMonitorService : AccessibilityService() {
    private var lastSignature = ""
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collectText(root,texts)
        val signature = texts.joinToString(" | ")
        if (signature.isNotBlank() && signature != lastSignature) {
            lastSignature = signature
            val interesting = texts.filter { it.contains("x",true) || it.contains("bet",true) || it.contains("collect",true) || it.contains("balance",true) }.take(8)
            if (interesting.isNotEmpty()) Toast.makeText(this,"Detected: " + interesting.joinToString(" • "),Toast.LENGTH_SHORT).show()
        }
    }
    private fun collectText(node: AccessibilityNodeInfo,out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> collectText(child,out); child.recycle() }
    }
    override fun onInterrupt() = Unit
}
