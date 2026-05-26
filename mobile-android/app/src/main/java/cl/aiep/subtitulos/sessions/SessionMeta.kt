package cl.aiep.subtitulos.sessions

import cl.aiep.subtitulos.CaptureMode
import org.json.JSONArray
import org.json.JSONObject

data class SessionMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val captionCount: Int,
    val lastSnippet: String,
    val durationMs: Long,
    val mode: CaptureMode,
    val relaySessionId: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("captionCount", captionCount)
        put("lastSnippet", lastSnippet)
        put("durationMs", durationMs)
        put("mode", mode.queryValue)
        put("relaySessionId", relaySessionId ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(obj: JSONObject): SessionMeta? = runCatching {
            SessionMeta(
                id = obj.getString("id"),
                name = obj.optString("name", "Sesión"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                captionCount = obj.optInt("captionCount", 0),
                lastSnippet = obj.optString("lastSnippet", ""),
                durationMs = obj.optLong("durationMs", 0L),
                mode = CaptureMode.fromQueryValue(obj.optString("mode", "speech")),
                relaySessionId = if (obj.isNull("relaySessionId")) null else obj.optString("relaySessionId", null),
            )
        }.getOrNull()

        fun listToJson(metas: List<SessionMeta>): String {
            val arr = JSONArray()
            metas.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<SessionMeta> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        fromJson(obj)?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
