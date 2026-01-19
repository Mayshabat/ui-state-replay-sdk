package com.example.replaysdk.replay

import kotlinx.serialization.Serializable
import android.window.SplashScreen
import java.sql.Timestamp
@Serializable //אומר שמותר להפוך את זה לגייסון
data class Event(
    val type: String,
    val screen: String,
    val timestamp: Long

)
//מחלקה שמחזיקה נתונים בלבד