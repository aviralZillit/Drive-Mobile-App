package com.zillit.drive.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zillit.drive.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zillit_session")

data class UserSession(
    val userId: String,
    val projectId: String,
    val deviceId: String,
    val scannerDeviceId: String = "",
    val encryptionKey: String,
    val encryptionIv: String,
    val environment: String,
    val userName: String = "",
    val userEmail: String = ""
)

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // In-memory cache to avoid DataStore reads on every network call
    @Volatile
    private var cachedSession: UserSession? = null

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_PROJECT_ID = stringPreferencesKey("project_id")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_SCANNER_DEVICE_ID = stringPreferencesKey("scanner_device_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    }

    /** Fast synchronous access for interceptors — returns cached session without IO */
    fun getCachedSession(): UserSession? = cachedSession

    suspend fun saveSession(session: UserSession) {
        cachedSession = session
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = session.userId
            prefs[KEY_PROJECT_ID] = session.projectId
            prefs[KEY_DEVICE_ID] = session.deviceId
            prefs[KEY_SCANNER_DEVICE_ID] = session.scannerDeviceId
            prefs[KEY_USER_NAME] = session.userName
            prefs[KEY_USER_EMAIL] = session.userEmail
        }
    }

    suspend fun getSession(): UserSession? {
        cachedSession?.let { return it }

        val prefs = dataStore.data.first()
        val userId = prefs[KEY_USER_ID] ?: return null
        val projectId = prefs[KEY_PROJECT_ID] ?: return null
        val deviceId = prefs[KEY_DEVICE_ID] ?: return null

        val session = UserSession(
            userId = userId,
            projectId = projectId,
            deviceId = deviceId,
            scannerDeviceId = prefs[KEY_SCANNER_DEVICE_ID] ?: "",
            encryptionKey = BuildConfig.ENCRYPTION_KEY,
            encryptionIv = BuildConfig.ENCRYPTION_IV,
            environment = BuildConfig.ENVIRONMENT,
            userName = prefs[KEY_USER_NAME] ?: "",
            userEmail = prefs[KEY_USER_EMAIL] ?: ""
        )
        cachedSession = session
        return session
    }

    suspend fun updateProjectId(projectId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PROJECT_ID] = projectId
        }
    }

    suspend fun clearSession() {
        cachedSession = null
        dataStore.edit { it.clear() }
    }

    fun isLoggedIn() = dataStore.data.map { prefs ->
        prefs[KEY_USER_ID] != null
    }
}
