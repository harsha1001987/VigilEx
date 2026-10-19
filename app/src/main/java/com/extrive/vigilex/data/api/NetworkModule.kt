package com.extrive.vigilex.data.api

import com.extrive.vigilex.data.model.AnalysisJson
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

    // Through NetLog, so request lines reach Logcat only in debuggable builds.
    private val loggingInterceptor = HttpLoggingInterceptor { message -> NetLog.i(message) }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader(AuthInterceptor.HEADER)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val siteApis = mutableMapOf<String, SiteApi>()
    private val areaApis = mutableMapOf<String, AreaApi>()
    private val taskApis = mutableMapOf<String, TaskApi>()

    @Synchronized
    fun siteApi(baseUrl: String): SiteApi = siteApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SiteApi::class.java)
    }

    @Synchronized
    fun areaApi(baseUrl: String): AreaApi = areaApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AreaApi::class.java)
    }

    @Synchronized
    fun taskApi(baseUrl: String): TaskApi = taskApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TaskApi::class.java)
    }

    // Video analysis runs YOLO + ByteTrack before responding, which can take
    // minutes for longer clips, so it needs far longer timeouts than CRUD calls.
    private val analysisClient = okHttpClient.newBuilder()
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(15, TimeUnit.MINUTES)
        .retryOnConnectionFailure(false)
        .build()

    private val analysisApis = mutableMapOf<String, AnalysisApi>()

    @Synchronized
    fun analysisApi(baseUrl: String): AnalysisApi = analysisApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(analysisClient)
            .build()
            .create(AnalysisApi::class.java)
    }

    private val assessmentClient = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val assessmentApis = mutableMapOf<String, AssessmentApi>()
    private val authApis = mutableMapOf<String, AuthApi>()

    @Synchronized
    fun authApi(baseUrl: String): AuthApi = authApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }

    @Synchronized
    fun assessmentApi(baseUrl: String): AssessmentApi = assessmentApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(assessmentClient)
            .addConverterFactory(AnalysisJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AssessmentApi::class.java)
    }
}
