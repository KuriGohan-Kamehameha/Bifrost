package com.moonbench.bifrost.revive

import org.json.JSONArray
import org.json.JSONObject

enum class ReviveEventKind(val label: String) {
    /** The previous process ended (from ApplicationExitInfo). */
    EXIT("Ended"),
    /** An uncaught exception was caught in-process just before dying. */
    CRASH("Crashed"),
    REVIVED("Revived"),
    /** Android refused to start the service from the background. */
    BLOCKED("Revive blocked"),
    GAVE_UP("Gave up"),
    /** Revive skipped because the user had stopped Bifrost. */
    RESPECTED_STOP("Not revived")
}

data class ReviveEvent(val at: Long, val kind: ReviveEventKind, val detail: String)

/** Bounded, newest-last history of exits and revives. Pure; JSON-backed. */
object ReviveHistory {

    const val MAX_EVENTS = 12
    private const val MAX_DETAIL = 160

    fun append(events: List<ReviveEvent>, event: ReviveEvent): List<ReviveEvent> =
        (events + event.copy(detail = event.detail.take(MAX_DETAIL)))
            .sortedBy { it.at }
            .takeLast(MAX_EVENTS)

    fun toJson(events: List<ReviveEvent>): String {
        val array = JSONArray()
        events.forEach {
            array.put(JSONObject().put("at", it.at).put("kind", it.kind.name).put("detail", it.detail))
        }
        return array.toString()
    }

    fun fromJson(raw: String?): List<ReviveEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = runCatching { ReviveEventKind.valueOf(obj.getString("kind")) }.getOrNull()
                    ?: return@mapNotNull null
                ReviveEvent(obj.getLong("at"), kind, obj.optString("detail"))
            }.sortedBy { it.at }.takeLast(MAX_EVENTS)
        }.getOrDefault(emptyList())
    }

    /** Newest first, one line per event. */
    fun format(events: List<ReviveEvent>, formatTime: (Long) -> String): String =
        events.sortedByDescending { it.at }.joinToString("\n") { event ->
            buildString {
                append(formatTime(event.at)).append("  ").append(event.kind.label)
                if (event.detail.isNotBlank()) append(": ").append(event.detail)
            }
        }
}
