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
    val fullScreenMode: Boolean = false, val fullScreenMask: Int = 7, val keepScreenOn: Boolean = false,
    val fullScreenLayout: String = "Pizza", val fullScreenControls: Boolean = true,
    val fullScreenLineDp: Int = 4, val fullScreenInsetDp: Int = 4,
    val fullScreenGapDp: Int = 3, val fullScreenAutoCorners: Boolean = true, val fullScreenCornerDp: Int = 28,
    val fullScreenNumberPercent: Int = 100, val fullScreenNames: Boolean = true, val fullScreenSpeedPercent: Int = 100)
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
    private val fullScreenLayoutKey = stringPreferencesKey("full_screen_layout")
    private val fullScreenControlsKey = booleanPreferencesKey("full_screen_controls")
    private val fullScreenLineKey = intPreferencesKey("full_screen_line_dp")
    private val fullScreenInsetKey = intPreferencesKey("full_screen_inset_dp")
    private val fullScreenGapKey = intPreferencesKey("full_screen_gap_dp")
    private val fullScreenCornerKey = intPreferencesKey("full_screen_corner_dp")
    private val fullScreenNumberKey = intPreferencesKey("full_screen_number_percent")
    private val fullScreenSpeedKey = intPreferencesKey("full_screen_speed_percent")
    private val fullScreenAutoCornersKey = booleanPreferencesKey("full_screen_auto_corners")
    private val fullScreenNamesKey = booleanPreferencesKey("full_screen_names")
    val flow = context.settings.data.map { Preferences(it[themeKey] ?: "System", false, it[volumeKey] ?: false, it[dismissKey] ?: false,
        it[completionKey] ?: true, (it[secondsKey] ?: 4).coerceIn(1,30),
        (it[textSizeKey] ?: 28).coerceIn(18,48), it[boldKey] ?: true, it[alignKey] ?: "Center", it[dockTextKey] ?: !(it[motionKey] ?: false),
        it[fullScreenKey] ?: false, (it[fullScreenMaskKey] ?: 7).coerceIn(1,7), it[keepScreenKey] ?: false,
        it[fullScreenLayoutKey] ?: "Pizza", it[fullScreenControlsKey] ?: true,
        (it[fullScreenLineKey] ?: 4).coerceIn(2,10), (it[fullScreenInsetKey] ?: 4).coerceIn(0,24),
        (it[fullScreenGapKey] ?: 3).coerceIn(0,16), it[fullScreenAutoCornersKey] ?: true, (it[fullScreenCornerKey] ?: 28).coerceIn(0,64),
        (it[fullScreenNumberKey] ?: 100).coerceIn(75,150), it[fullScreenNamesKey] ?: true, (it[fullScreenSpeedKey] ?: 100).coerceIn(50,200)) }
    suspend fun fullScreenGapDp(value: Int) { context.settings.edit { it[fullScreenGapKey] = value.coerceIn(0,16) } }
    suspend fun fullScreenCornerDp(value: Int) { context.settings.edit { it[fullScreenCornerKey] = value.coerceIn(0,64) } }
    suspend fun fullScreenNumberPercent(value: Int) { context.settings.edit { it[fullScreenNumberKey] = value.coerceIn(75,150) } }
    suspend fun fullScreenSpeedPercent(value: Int) { context.settings.edit { it[fullScreenSpeedKey] = value.coerceIn(50,200) } }
    suspend fun fullScreenAutoCorners(value: Boolean) { context.settings.edit { it[fullScreenAutoCornersKey] = value } }
    suspend fun fullScreenNames(value: Boolean) { context.settings.edit { it[fullScreenNamesKey] = value } }
    suspend fun fullScreenMode(value: Boolean) { context.settings.edit { it[fullScreenKey] = value } }
    suspend fun fullScreenMask(value: Int) { context.settings.edit { it[fullScreenMaskKey] = value.coerceIn(1,7) } }
    suspend fun keepScreenOn(value: Boolean) { context.settings.edit { it[keepScreenKey] = value } }
    suspend fun fullScreenLayout(value: String) { context.settings.edit { it[fullScreenLayoutKey] = if(value == "Split") "Split" else "Pizza" } }
    suspend fun fullScreenControls(value: Boolean) { context.settings.edit { it[fullScreenControlsKey] = value } }
    suspend fun fullScreenLineDp(value: Int) { context.settings.edit { it[fullScreenLineKey] = value.coerceIn(2,10) } }
    suspend fun fullScreenInsetDp(value: Int) { context.settings.edit { it[fullScreenInsetKey] = value.coerceIn(0,24) } }
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
