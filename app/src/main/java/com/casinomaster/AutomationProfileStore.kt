package com.casinomaster

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AutomationRule(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val action: String = "CLICK",
    val timeoutMs: Long = 5000L,
    val maxRetries: Int = 2
)

data class AutomationProfile(
    val name: String,
    val targetPackage: String,
    val enabled: Boolean,
    val rules: List<AutomationRule>
)

class AutomationProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("automation_profiles", Context.MODE_PRIVATE)

    fun save(profile: AutomationProfile) {
        val rules = JSONArray()
        profile.rules.forEach { r ->
            rules.put(JSONObject().apply {
                putOpt("text", r.text)
                putOpt("contentDescription", r.contentDescription)
                putOpt("viewId", r.viewId)
                putOpt("className", r.className)
                put("action", r.action)
                put("timeoutMs", r.timeoutMs.coerceIn(500L, 60_000L))
                put("maxRetries", r.maxRetries.coerceIn(0, 5))
            })
        }
        prefs.edit().putString("profile", JSONObject().apply {
            put("name", profile.name)
            put("targetPackage", profile.targetPackage)
            put("enabled", profile.enabled)
            put("rules", rules)
        }.toString()).apply()
    }

    fun load(): AutomationProfile? {
        val raw = prefs.getString("profile", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val rulesJson = o.optJSONArray("rules") ?: JSONArray()
            val rules = buildList {
                for (i in 0 until rulesJson.length()) {
                    val r = rulesJson.getJSONObject(i)
                    add(AutomationRule(
                        text = r.optString("text", "").takeIf { it.isNotBlank() },
                        contentDescription = r.optString("contentDescription", "").takeIf { it.isNotBlank() },
                        viewId = r.optString("viewId", "").takeIf { it.isNotBlank() },
                        className = r.optString("className", "").takeIf { it.isNotBlank() },
                        action = r.optString("action", "CLICK"),
                        timeoutMs = r.optLong("timeoutMs", 5000L).coerceIn(500L, 60_000L),
                        maxRetries = r.optInt("maxRetries", 2).coerceIn(0, 5)
                    ))
                }
            }
            AutomationProfile(o.optString("name", "Default profile"), o.optString("targetPackage"), o.optBoolean("enabled", true), rules)
        } catch (_: Exception) { null }
    }

    fun setRunning(running: Boolean) = prefs.edit().putBoolean("running", running).apply()
    fun isRunning(): Boolean = prefs.getBoolean("running", false)
    fun setPaused(paused: Boolean) = prefs.edit().putBoolean("paused", paused).apply()
    fun isPaused(): Boolean = prefs.getBoolean("paused", false)

    fun appendLog(message: String) {
        val old = prefs.getString("log", "") ?: ""
        val lines = (old.lines().filter { it.isNotBlank() } + message).takeLast(100)
        prefs.edit().putString("log", lines.joinToString("\n")).apply()
    }
    fun clearLog() = prefs.edit().remove("log").apply()
    fun log(): String = prefs.getString("log", "") ?: ""
}