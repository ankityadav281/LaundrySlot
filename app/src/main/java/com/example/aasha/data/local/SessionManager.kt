package com.example.aasha.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hosteller_session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private val encryptedPrefs: SharedPreferences? by lazy {
        createEncryptedPrefsWithRecovery()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun createEncryptedPrefsWithRecovery(): SharedPreferences? {
        return try {
            createEncryptedPrefs()
        } catch (_: Exception) {
            Log.w("SessionManager", "Encrypted store corrupted; resetting secure credential store")
            runCatching {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                context.deleteSharedPreferences("secure_mpin_prefs_fallback")
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
            }
            try {
                createEncryptedPrefs()
            } catch (_: Exception) {
                Log.e("SessionManager", "Secure credential store unavailable after reset")
                null
            }
        }
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "secure_mpin_prefs"

        private val HOSTELLER_ID = stringPreferencesKey("hosteller_id")
        private val LAST_HOSTELLER_ID = stringPreferencesKey("last_hosteller_id")
        private val NAME = stringPreferencesKey("name")
        private val EMAIL = stringPreferencesKey("email")
        private val ROOM_NUMBER = stringPreferencesKey("room_number")
        private val HOSTEL_BLOCK = stringPreferencesKey("hostel_block")
        private val FLOOR = intPreferencesKey("floor")
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val LANGUAGE = stringPreferencesKey("language")
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")

        private const val MPIN_HASH_PREFIX = "mpin_hash_"
        private const val PASSWORD_HASH_PREFIX = "password_hash_"
        private const val EMAIL_PREFIX = "email_"
    }

    val hostellerId: Flow<String?> = dataStore.data.map { it[HOSTELLER_ID] }
    val lastHostellerId: Flow<String?> = dataStore.data.map { it[LAST_HOSTELLER_ID] }
    val name: Flow<String?> = dataStore.data.map { it[NAME] ?: "Hosteller" }
    val email: Flow<String?> = dataStore.data.map { it[EMAIL] }
    val roomNumber: Flow<String> = dataStore.data.map { it[ROOM_NUMBER] ?: "101" }
    val hostelBlock: Flow<String> = dataStore.data.map { it[HOSTEL_BLOCK] ?: "Block A" }
    val floor: Flow<Int> = dataStore.data.map { it[FLOOR] ?: 1 }
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { it[IS_LOGGED_IN] ?: false }
    val language: Flow<String> = dataStore.data.map { it[LANGUAGE] ?: "en" }
    val lastSyncTime: Flow<Long> = dataStore.data.map { it[LAST_SYNC_TIME] ?: 0L }
    val notificationEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_ENABLED] ?: true }

    fun hasMpin(id: String): Boolean {
        return encryptedPrefs?.getString(MPIN_HASH_PREFIX + id, null) != null
    }

    fun verifyMpin(pin: String, id: String): Boolean {
        val savedHash = encryptedPrefs?.getString(MPIN_HASH_PREFIX + id, null) ?: return false
        return MessageDigest.isEqual(
            hashString(pin).toByteArray(Charsets.UTF_8),
            savedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun hasPasswordHash(id: String): Boolean {
        return encryptedPrefs?.getString(PASSWORD_HASH_PREFIX + id, null) != null
    }

    fun verifyPassword(password: String, id: String): Boolean {
        val savedHash = encryptedPrefs?.getString(PASSWORD_HASH_PREFIX + id, null) ?: return false
        return MessageDigest.isEqual(
            hashString(password).toByteArray(Charsets.UTF_8),
            savedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun getUserEmail(id: String): String? {
        return encryptedPrefs?.getString(EMAIL_PREFIX + id, null)
    }

    private fun hashString(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    suspend fun saveSession(
        hostellerId: String,
        name: String,
        email: String,
        roomNumber: String,
        hostelBlock: String = "Block A",
        floor: Int = 1
    ) {
        dataStore.edit { preferences ->
            preferences[HOSTELLER_ID] = hostellerId
            preferences[LAST_HOSTELLER_ID] = hostellerId
            preferences[NAME] = name
            preferences[EMAIL] = email
            preferences[ROOM_NUMBER] = roomNumber
            preferences[HOSTEL_BLOCK] = hostelBlock
            preferences[FLOOR] = floor
            preferences[IS_LOGGED_IN] = true
        }
    }

    suspend fun updateHostelDetails(roomNumber: String, hostelBlock: String, floor: Int) {
        dataStore.edit { preferences ->
            preferences[ROOM_NUMBER] = roomNumber
            preferences[HOSTEL_BLOCK] = hostelBlock
            preferences[FLOOR] = floor
        }
    }

    suspend fun saveLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = language
        }
    }

    suspend fun updateLastSyncTime(time: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME] = time
        }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED] = enabled
        }
    }

    fun saveMpin(mpin: String, id: String) {
        val hashedMpin = hashString(mpin)
        encryptedPrefs?.edit()?.putString(MPIN_HASH_PREFIX + id, hashedMpin)?.apply()
    }

    fun savePasswordHash(password: String, id: String) {
        val hash = hashString(password)
        encryptedPrefs?.edit()?.putString(PASSWORD_HASH_PREFIX + id, hash)?.apply()
    }

    fun saveUserEmail(email: String, id: String) {
        encryptedPrefs?.edit()?.putString(EMAIL_PREFIX + id, email)?.apply()
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = loggedIn
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = false
            preferences.remove(HOSTELLER_ID)
            preferences.remove(NAME)
            preferences.remove(EMAIL)
            preferences.remove(ROOM_NUMBER)
            preferences.remove(HOSTEL_BLOCK)
            preferences.remove(FLOOR)
            preferences.remove(LAST_SYNC_TIME)
        }
    }
}
