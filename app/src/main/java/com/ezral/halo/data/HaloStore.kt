package com.ezral.halo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import com.ezral.halo.core.Snapshot
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Entity(tableName = "checkpoints")
data class Checkpoint(@PrimaryKey val id: Int = 0, val payload: String)
@Dao interface CheckpointDao {
    @Query("SELECT * FROM checkpoints WHERE id = 0") suspend fun read(): Checkpoint?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(value: Checkpoint)
}
@Database(entities = [Checkpoint::class], version = 1, exportSchema = true)
abstract class HaloDatabase : RoomDatabase() { abstract fun checkpoints(): CheckpointDao }

/** Three tiny tracks plus their event outbox are one atomic, versioned checkpoint. */
class HaloStore(context: Context) {
    private val db = Room.databaseBuilder(context, HaloDatabase::class.java, "halo.db").build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun load(): Snapshot? = db.checkpoints().read()?.let { json.decodeFromString<Snapshot>(it.payload) }
    suspend fun save(snapshot: Snapshot) = db.checkpoints().write(Checkpoint(payload = json.encodeToString(Snapshot.serializer(), snapshot)))
}
private val Context.settings by preferencesDataStore("halo_preferences")
data class Preferences(val theme: String = "System", val reducedMotion: Boolean = false, val volume: Boolean = false, val dismissAllOnMenu: Boolean = false,
    val completionEnabled: Boolean = true, val completionSeconds: Int = 4,
    val completionTextSp: Int = 28, val completionBold: Boolean = true,
    val completionAlignment: String = "Center", val dockTextMotion: Boolean = true,
    val fullScreenMode: Boolean = false, val fullScreenMask: Int = 7, val keepScreenOn: Boolean = false)
class PreferenceStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val motionKey = booleanPreferencesKey("reduced_motion")
    private val dockTextKey = booleanPreferencesKey("dock_text_motion")
    private val dismissKey = booleanPreferencesKey("dismiss_all_on_menu")
    private val volumeKey = booleanPreferencesKey("local_volume")
    private val completionKey = booleanPreferencesKey("completion_enabled")
    private val secondsKey = intPreferencesKey("completion_seconds")
    private val textSizeKey = intPreferencesKey("completion_text_sp")
    private val boldKey = booleanPreferencesKey("completion_bold")
    private val alignKey = stringPreferencesKey("completion_alignment")
    private val fullScreenKey = booleanPreferencesKey("full_screen_mode")
    private val fullScreenMaskKey = intPreferencesKey("full_screen_mask")
    private val keepScreenKey = booleanPreferencesKey("full_screen_keep_awake")
    val flow = context.settings.data.map { Preferences(it[themeKey] ?: "System", false, it[volumeKey] ?: false, it[dismissKey] ?: false,
        it[completionKey] ?: true, (it[secondsKey] ?: 4).coerceIn(1,30),
        (it[textSizeKey] ?: 28).coerceIn(18,48), it[boldKey] ?: true, it[alignKey] ?: "Center", it[dockTextKey] ?: !(it[motionKey] ?: false),
        it[fullScreenKey] ?: false, (it[fullScreenMaskKey] ?: 7).coerceIn(1,7), it[keepScreenKey] ?: false) }
    suspend fun fullScreenMode(value: Boolean) { context.settings.edit { it[fullScreenKey] = value } }
    suspend fun fullScreenMask(value: Int) { context.settings.edit { it[fullScreenMaskKey] = value.coerceIn(1,7) } }
    suspend fun keepScreenOn(value: Boolean) { context.settings.edit { it[keepScreenKey] = value } }
    suspend fun completionEnabled(value: Boolean) { context.settings.edit { it[completionKey] = value } }
    suspend fun completionSeconds(value: Int) { context.settings.edit { it[secondsKey] = value.coerceIn(1,30) } }
    suspend fun completionTextSp(value: Int) { context.settings.edit { it[textSizeKey] = value.coerceIn(18,48) } }
    suspend fun completionBold(value: Boolean) { context.settings.edit { it[boldKey] = value } }
    suspend fun completionAlignment(value: String) { context.settings.edit { it[alignKey] = if(value == "Left") "Left" else "Center" } }
    suspend fun theme(value: String) { context.settings.edit { it[themeKey] = value } }
    suspend fun dockTextMotion(value: Boolean) { context.settings.edit { it[dockTextKey] = value } }
    suspend fun motion(value: Boolean) { context.settings.edit { it[motionKey] = value } }
    suspend fun dismissAllOnMenu(value: Boolean) { context.settings.edit { it[dismissKey] = value } }
    suspend fun volume(value: Boolean) { context.settings.edit { it[volumeKey] = value } }
}
