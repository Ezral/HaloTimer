package com.ezral.halo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
data class Preferences(val theme: String = "System", val reducedMotion: Boolean = false, val volume: Boolean = false, val dismissAllOnMenu: Boolean = false)
class PreferenceStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val motionKey = booleanPreferencesKey("reduced_motion")
    private val dismissKey = booleanPreferencesKey("dismiss_all_on_menu")
    private val volumeKey = booleanPreferencesKey("local_volume")
    val flow = context.settings.data.map { Preferences(it[themeKey] ?: "System", it[motionKey] ?: false, it[volumeKey] ?: false, it[dismissKey] ?: false) }
    suspend fun theme(value: String) { context.settings.edit { it[themeKey] = value } }
    suspend fun motion(value: Boolean) { context.settings.edit { it[motionKey] = value } }
    suspend fun dismissAllOnMenu(value: Boolean) { context.settings.edit { it[dismissKey] = value } }
    suspend fun volume(value: Boolean) { context.settings.edit { it[volumeKey] = value } }
}
