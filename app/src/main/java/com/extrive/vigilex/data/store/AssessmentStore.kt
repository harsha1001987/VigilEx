package com.extrive.vigilex.data.store

import android.content.Context
import com.extrive.vigilex.data.model.AnalysisJson
import com.extrive.vigilex.data.model.AnalysisResult
import com.extrive.vigilex.data.model.AssessmentRecord
import com.extrive.vigilex.data.model.parseAnalysisResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Completed analyses saved on this device. The backend's analyze-video
 * endpoint is stateless, so this is the only record of past assessments:
 * an index of summaries plus the untouched server JSON for each assessment.
 */
object AssessmentStore {
    private const val DIR = "assessments"
    private const val INDEX = "index.json"

    private lateinit var dir: File
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexSerializer = ListSerializer(AssessmentRecord.serializer())

    private val _records = MutableStateFlow<List<AssessmentRecord>>(emptyList())
    /** Newest first. */
    val records: StateFlow<List<AssessmentRecord>> = _records.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** False until the index has been read, so screens don't flash an empty state. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
        scope.launch {
            mutex.withLock { _records.value = readIndex() }
            _loaded.value = true
        }
    }

    suspend fun save(record: AssessmentRecord, rawJson: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeAtomically(File(dir, "${record.id}.json"), rawJson)
            val updated = (listOf(record) + _records.value.filter { it.id != record.id })
                .sortedByDescending { it.createdAtMillis }
            writeIndex(updated)
            _records.value = updated
        }
    }

    suspend fun loadResult(id: String): AnalysisResult? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            parseAnalysisResult(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun record(id: String): AssessmentRecord? = _records.value.firstOrNull { it.id == id }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(dir, "$id.json").delete()
            val updated = _records.value.filter { it.id != id }
            writeIndex(updated)
            _records.value = updated
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
            _records.value = emptyList()
        }
    }

    private fun readIndex(): List<AssessmentRecord> {
        val file = File(dir, INDEX)
        if (!file.exists()) return emptyList()
        return try {
            AnalysisJson.decodeFromString(indexSerializer, file.readText())
                .sortedByDescending { it.createdAtMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(records: List<AssessmentRecord>) {
        writeAtomically(File(dir, INDEX), AnalysisJson.encodeToString(indexSerializer, records))
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}
