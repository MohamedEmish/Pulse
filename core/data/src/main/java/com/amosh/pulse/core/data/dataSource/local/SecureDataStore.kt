package com.amosh.pulse.core.data.dataSource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amosh.pulse.core.domain.constants.Constants.APP_DATE_STORE_NAME
import com.amosh.pulse.core.domain.model.UserData
import com.amosh.pulse.core.domain.utils.EncryptionUtils
import com.amosh.pulse.core.domain.utils.toGson
import com.amosh.pulse.core.domain.utils.toObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context._dataStore: DataStore<Preferences> by preferencesDataStore(name = APP_DATE_STORE_NAME)
    private val dataStore: DataStore<Preferences> = context._dataStore
    private val prefToKeep = listOf<Preferences.Key<String>>()

    val userData = secureMap(USER_DATA_KEY)
        .map { it.toObject(UserData::class.java) ?: UserData() }

    suspend fun setUserData(user: UserData) =
        secureEdit(USER_DATA_KEY, user.toGson())

    private suspend fun secureEdit(
        key: Preferences.Key<String>,
        value: Any,
    ) {
        dataStore.edit { it[key] = encryptValue(value) }
    }

    private fun secureMap(
        key: Preferences.Key<String>,
        defaultValue: String = "",
    ): Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map {
            val value = it[key]
            if (value != null) EncryptionUtils.decrypt(value) else defaultValue
        }
        .catch { emit(defaultValue) }
        .distinctUntilChanged()

    private fun encryptValue(value: Any): String = value.toString().runCatching {
        EncryptionUtils.encrypt(this)
    }.getOrDefault("")

    /*
    * clear all data
    * */
    suspend fun clearData() {
        dataStore.data.first().asMap().keys.forEach { key ->
            if (key !in prefToKeep) {
                dataStore.edit { it.remove(key) }
            }
        }
    }

    companion object {
        private val USER_DATA_KEY = stringPreferencesKey("pref_user_data")
    }

}