package com.ps2gamepad.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ps2gamepad.model.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Raw persisted text. The DataStore implementation is used in the app; tests use an in-memory one. */
interface TextStorage {
    suspend fun read(): String?
    suspend fun write(text: String)
}

/** Non-persistent storage (tests, and a fallback when no Context is available). */
class InMemoryTextStorage(@Volatile var text: String? = null) : TextStorage {
    @Volatile var writes = 0
    override suspend fun read(): String? = text
    override suspend fun write(text: String) { this.text = text; writes++ }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "controller_settings")

class DataStoreTextStorage(context: Context) : TextStorage {
    private val store = context.applicationContext.settingsDataStore
    private val key = stringPreferencesKey("profiles_v1")
    override suspend fun read(): String? = store.data.map { it[key] }.first()
    override suspend fun write(text: String) { store.edit { it[key] = text } }
}

/**
 * Holds the profile store in memory (instant UI updates) and persists it with a short debounce,
 * so dragging a slider does not write to disk on every frame. [flush] writes immediately and is
 * called when the app stops.
 */
class SettingsRepository(
    private val storage: TextStorage,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 400,
) {
    private val _store = MutableStateFlow(DefaultProfiles.store())
    val store: StateFlow<ProfileStore> = _store.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var saveJob: Job? = null
    private var dirty = false
    /** Edits made before loading finished; replayed on top of the loaded data (never lost, never clobbering it). */
    private val pending = mutableListOf<(ProfileStore) -> ProfileStore>()
    private var flushRequested = false

    init {
        scope.launch {
            val text = runCatching { storage.read() }.getOrNull()
            _store.value = pending.fold(ProfileCodec.decode(text)) { acc, f -> f(acc) }
            val hadPending = pending.isNotEmpty()
            pending.clear()
            _loaded.value = true
            if (hadPending) {
                dirty = true
                // flush() was requested while loading: write now instead of after the debounce.
                if (flushRequested) { flushRequested = false; save() } else scheduleSave()
            }
        }
    }

    /** Call from the main thread (UI / ViewModel). */
    fun update(f: (ProfileStore) -> ProfileStore) {
        _store.update(f)
        if (!_loaded.value) { pending += f; return }
        scheduleSave()
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch { delay(debounceMs); save() }
    }

    fun flush() {
        if (!_loaded.value) { flushRequested = true; return }
        if (!dirty) return
        saveJob?.cancel()
        saveJob = scope.launch { save() }
    }

    private suspend fun save() {
        val snapshot = _store.value
        runCatching { storage.write(ProfileCodec.encode(snapshot)) }
            .onSuccess { if (_store.value == snapshot) dirty = false }
    }
}
