package com.extrive.vigilex.data.store

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Device-only state for backend assessments. The backend is the source of
 * truth for every assessment; it does not keep the video, so this remembers
 * which video on this device each assessment id was made from, letting
 * evidence be drawn over the real frames. Nothing else is stored here.
 *
 * [revision] changes whenever assessments are added or removed, so screens
 * showing backend data know to fetch it again.
 */
object AssessmentStore {
    private const val DIR = "assessments"
    // Lives beside index.json and <id>.json from when results were saved only on
    // the device. Those are left untouched: the backend has no copy of them.
    private const val VIDEOS = "videos.json"

    private var file: File? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var videos: Map<String, String> = emptyMap()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
        file = File(dir, VIDEOS)
        scope.launch { mutex.withLock { videos = read() } }
    }

    /** content:// URI of the video [assessmentId] was analyzed from, if it came from this device. */
    fun videoUri(assessmentId: String): String? = videos[assessmentId]

    /** Records a new backend assessment made from a video on this device. */
    suspend fun saved(assessmentId: String, videoUri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            videos = videos + (assessmentId to videoUri)
            write()
        }
        changed()
    }

    /** The assessment was deleted on the backend. */
    suspend fun deleted(assessmentId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            videos = videos - assessmentId
            write()
        }
        changed()
    }

    /** Every assessment was deleted on the backend. */
    suspend fun cleared() = withContext(Dispatchers.IO) {
        mutex.withLock {
            videos = emptyMap()
            write()
        }
        changed()
    }

    fun changed() = _revision.update { it + 1 }

    private fun read(): Map<String, String> = try {
        file?.takeIf { it.exists() }?.let { json.decodeFromString(serializer, it.readText()) }.orEmpty()
    } catch (e: Exception) {
        emptyMap()
    }

    private fun write() {
        val target = file ?: return
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, videos))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}
