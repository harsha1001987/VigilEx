package com.extrive.vigilex.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json

/**
 * The session, encrypted with AES-256-GCM under a key generated inside the
 * Android Keystore. The key never leaves the Keystore, so the ciphertext in
 * app-private preferences is useless off this device (and the file is
 * excluded from backups). Anything that cannot be decrypted is discarded:
 * the user simply signs in again.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): StoredSession? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(iv)))
            json.decodeFromString(StoredSession.serializer(), String(cipher.doFinal(decode(data)), Charsets.UTF_8))
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override fun save(session: StoredSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(json.encodeToString(StoredSession.serializer(), session).toByteArray(Charsets.UTF_8))
        prefs.edit(commit = true) {
            putString(KEY_IV, encode(cipher.iv))
            putString(KEY_DATA, encode(data))
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        /** Matches the exclusions in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val PREFS = "vigilex_session"
        private const val KEY_IV = "iv"
        private const val KEY_DATA = "session"
        private const val KEY_ALIAS = "vigilex_session_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
