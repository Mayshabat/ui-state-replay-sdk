package com.example.replaysdk.replay

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object ApiClient {

    private const val JSON = "application/json; charset=utf-8"

    private var baseUrl: String = "http://10.0.2.2:5000/" // ברירת מחדל לאמולטור
    private var api: ApiService? = null

    fun init(baseUrl: String) {
        this.baseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        //יוצר לקוח HTTP יודע להחזיר מחרוזת מחבר לAPISERVIS
        api = Retrofit.Builder()
            .baseUrl(this.baseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun jsonBody(json: String) = json.toRequestBody(JSON.toMediaType())

    fun service(): ApiService {
        return api ?: error("ApiClient not initialized. Call ApiClient.init(baseUrl) first.")
    }
}
