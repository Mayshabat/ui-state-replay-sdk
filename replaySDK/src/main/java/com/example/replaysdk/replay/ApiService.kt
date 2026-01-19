package com.example.replaysdk.replay

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    // Upload session -> returns { "sessionId": "..." }
    @POST("sessions")
    suspend fun postSession(@Body body: RequestBody): String

    // Fetch session JSON by id -> returns the JSON of the session
    @GET("sessions/{id}")
    suspend fun getSession(@Path("id") id: String): String
}
