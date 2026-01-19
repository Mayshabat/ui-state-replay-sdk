package com.example.replaysdk.replay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object Replay {

    private var recording = false // האם מקליטים עכשיו
    private var startedAt: Long = 0L
    private val events = mutableListOf<Event>() //רשימת אירועים שומרים מה שהוקלט
    private val jsonParser = Json {
        ignoreUnknownKeys = true
    }
    fun start() {
        recording = true // מתחילים להקליט
        startedAt = System.currentTimeMillis()
        events.clear() //מנקים אירועים ישנים
    }

    fun log(type: String, screen: String) {
        if (!recording) return

        events.add(
            Event(
                type = type,
                screen = screen,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun stop(): Session {
        recording = false
        return Session(
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            events = events.toList()
        )
    }

    fun toJson(session: Session): String {
        return Json.encodeToString(session)
    }

    fun fromJson(json: String): Session =
        jsonParser.decodeFromString(Session.serializer(), json)

    fun init(baseUrl: String) {
        ApiClient.init(baseUrl)
    }

    suspend fun upload(session: Session): String {
        val json = toJson(session)
        val body = ApiClient.jsonBody(json)
        return ApiClient.service().postSession(body)
    }
    // מבטיח שהאירועים יהיו בסדר נכון גם אם לא נשמר לפי הסדר
    fun eventsOf(session: Session): List<Event> {
        return session.events.sortedBy { it.timestamp }
    }

    suspend fun fetch(sessionId: String): Session {
        val json = ApiClient.service().getSession(sessionId)
        return fromJson(json)
    }
    suspend fun replay(
        session: Session,
        delayMs: Long = 500,
        onEvent: (Event) -> Unit
    ) {
        // 1) מסדרים לפי זמן כדי לנגן נכון
        val ordered = session.events.sortedBy { it.timestamp }

        // 2) מריצים אירוע-אירוע עם השהייה
        for (e in ordered) {
            onEvent(e)
            delay(delayMs)
        }
    }

}
