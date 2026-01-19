package com.example.replaysdk.replay
import kotlinx.serialization.Serializable
//קובץ הקלטה אחד  מתי התחיל מתי נגמר וכל האירועים באמצע
@Serializable
data class Session(
    val startedAt: Long,
    val endedAt: Long,
    val events: List<Event>
)
