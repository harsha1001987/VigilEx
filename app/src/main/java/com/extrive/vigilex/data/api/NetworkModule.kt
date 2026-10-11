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

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        // BASIC logs no headers; redacting keeps a token out of Logcat if the level is ever raised.
        redactHeader(AuthInterceptor.HEADER)
    }

    // The session token goes on every request to the server that issued it.
    // The logger is added after it so it sees the final request.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
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

    // Saved assessments, overview and reports. Report generation renders a PDF
    // on the server, so reads get more time than the CRUD default.
    private val assessmentClient = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val assessmentApis = mutableMapOf<String, AssessmentApi>()
    private val authApis = mutableMapOf<String, AuthApi>()

    /** Accounts, on the configured server. */
    @Synchronized
    fun authApi(baseUrl: String): AuthApi = authApis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }

    /** Same server as analysis (Settings → Developer options), built per URL. */
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
