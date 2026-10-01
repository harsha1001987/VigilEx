package com.extrive.vigilex.data.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds the single Retrofit instance and the API interfaces the repositories use. */
object NetworkModule {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.EMULATOR_BASE_URL) // Legacy CRUD APIs; analysis uses the configured server.
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val siteApi: SiteApi = retrofit.create(SiteApi::class.java)
    val areaApi: AreaApi = retrofit.create(AreaApi::class.java)
    val taskApi: TaskApi = retrofit.create(TaskApi::class.java)
    val assessmentApi: AssessmentApi = retrofit.create(AssessmentApi::class.java)

    // Video analysis runs YOLO + ByteTrack before responding, which can take
    // minutes for longer clips, so it needs far longer timeouts than CRUD calls.
    // Retries are disabled so a dropped connection never uploads a video twice.
    private val analysisClient = okHttpClient.newBuilder()
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(15, TimeUnit.MINUTES)
        .retryOnConnectionFailure(false)
        .build()

    private val analysisApis = mutableMapOf<String, AnalysisApi>()

    /** The base URL is user-configurable (Settings), so APIs are built per URL. */
    @Synchronized
    fun analysisApi(baseUrl: String): AnalysisApi = analysisApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(analysisClient)
            .build()
            .create(AnalysisApi::class.java)
    }
}
