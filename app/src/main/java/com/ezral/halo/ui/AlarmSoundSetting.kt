package com.ezral.halo.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezral.halo.core.Command
import com.ezral.halo.runtime.TimerCoordinator
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmSoundSetting(c: TimerCoordinator, selected: Int) {
    val snapshot by c.state.collectAsStateWithLifecycle()
    val d = snapshot.tracks[selected].definition
    val context = LocalContext.current
    var pickerTarget by rememberSaveable { mutableIntStateOf(selected) }
    var opening by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            opening = true; failure = null
            val target = pickerTarget
            c.scope.launch {
                try {
                    val displayName = withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        val metadata = MediaMetadataRetriever()
                        try {
                            metadata.setDataSource(context, uri)
                            require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                        } finally { metadata.release() }
                        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "Custom audio"
                    }
                    // Use the latest definition: picking a file must not revert other edits.
                    c.haptics.cancel(-1)
                    val latest = c.state.value.tracks[target].definition
                    if (!c.execute(Command.Edit(latest.copy(soundUri = uri.toString(), soundName = displayName)))) {
                        failure = c.error.value ?: "The sound could not be saved."
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failure = "Could not open this audio file. Try a local MP3, M4A, OGG or WAV file." }
                finally { opening = false }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(d.soundName ?: "Soft tone", fontSize = 14.sp,
            modifier = Modifier.semantics { contentDescription = "Selected alarm sound" })
        Text("Uses alarm volume. Custom audio plays once per alert repeat; previews stop after 10 seconds.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !opening, onClick = {
                pickerTarget = selected; failure = null
                try { launcher.launch(arrayOf("audio/*")) }
                catch (_: android.content.ActivityNotFoundException) { failure = "No audio file picker is available on this device." }
            }) { Text(if (opening) "Opening audio…" else "Choose audio file") }
            if (d.soundUri != null) TextButton(enabled = !opening, onClick = {
                c.haptics.cancel(-1)
                c.submit(Command.Edit(d.copy(soundUri = null, soundName = null)))
                failure = null
            }) { Text("Use soft tone") }
        }
        failure?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
}
