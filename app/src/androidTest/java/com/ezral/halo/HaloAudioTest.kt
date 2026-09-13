package com.ezral.halo

import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.ezral.halo.core.*
import com.ezral.halo.data.HaloStore
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class HaloAudioTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as HaloApplication).coordinator
    private var downloaded: Uri? = null
    @Before fun prepare() {
        rule.waitUntil(10000) { c.ready.value }
        reset()
    }
    @After fun reset() {
        runBlocking { withContext(Dispatchers.Main) {
            c.execute(Command.StopAll)
            c.state.value.presets.forEach { c.execute(Command.DeletePreset(it.id)) }
            (0..2).forEach { c.execute(Command.Activate(it, it == 0)); c.execute(Command.Edit(Definition(it))) }
            c.preferences.fullScreenMode(false); c.error.value = null
        } }
        downloaded?.let { rule.activity.contentResolver.delete(it, null, null) }; downloaded = null
    }
    private fun execute(command: Command) {
        assertTrue(runBlocking { withContext(Dispatchers.Main) { c.execute(command) } })
    }
    private fun wave(seconds: Int): ByteArray {
        val samples = 16000 * seconds
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        b.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { b.putShort((kotlin.math.sin(it * 2 * Math.PI * 440 / 16000) * 1500).toInt().toShort()) }
        return b.array()
    }
    @Test fun audioFilePickerPersistsSelectionAndPresetAndPreviewCanStop() {
        val resolver = rule.activity.contentResolver
        val title = "Halo test tone.mp3"
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, title)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })!!
        downloaded = uri
        val mp3 = InstrumentationRegistry.getInstrumentation().context.assets.open("alarm-test.mp3.b64").use {
            android.util.Base64.decode(it.readBytes(), android.util.Base64.DEFAULT)
        }
        resolver.openOutputStream(uri)!!.use { it.write(mp3) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        rule.onNodeWithContentDescription("Sound").performScrollTo().performClick()
        rule.onNodeWithText("Choose audio file").performScrollTo().performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // UiObject resolves its selector for each action; the picker replaces drawer
        // nodes during its opening transition, so cached UiObject2 handles can go stale.
        val file = device.findObject(UiSelector().text(title))
        if (!file.waitForExists(5000)) {
            val roots = device.findObject(UiSelector().description("Show roots"))
            assertTrue("System picker navigation is available", roots.waitForExists(5000))
            roots.click(); device.waitForIdle(2000)
            val downloads = device.findObject(UiSelector().text("Downloads"))
            assertTrue("Downloads is available", downloads.waitForExists(5000))
            downloads.click(); device.waitForIdle(2000)
        }
        assertTrue("Audio file must be selectable in the system picker", file.waitForExists(5000))
        file.click()
        rule.waitUntil(10000) { c.state.value.tracks[0].definition.soundUri != null }
        val selected = c.state.value.tracks[0].definition
        assertEquals(title, selected.soundName)
        assertTrue(resolver.persistedUriPermissions.any { it.uri.toString() == selected.soundUri && it.isReadPermission })
        execute(Command.SavePreset(0, "Audio timer"))
        execute(Command.Edit(selected.copy(soundUri = null, soundName = null)))
        execute(Command.LoadPreset(c.state.value.presets.single().id, 0))
        assertEquals(selected.soundUri, c.state.value.tracks[0].definition.soundUri)
        assertEquals(selected.soundUri, runBlocking { HaloStore(rule.activity).load()!!.tracks[0].definition.soundUri })
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Test sound once").performScrollTo().performClick()
        rule.waitUntil(5000) { c.haptics.customSoundPlaying.value }
        rule.onNodeWithText("Stop sound preview").performScrollTo().performClick()
        rule.waitUntil(5000) { !c.haptics.customSoundPlaying.value }
        rule.onNodeWithContentDescription("Selected alarm sound").performScrollTo().assert(hasText(title))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/halo-qa", "screencap -p /sdcard/Download/halo-qa/25-custom-audio.png").forEach {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
        }
        rule.onNodeWithText("Use soft tone").performScrollTo().performClick()
        rule.waitUntil(5000) { c.state.value.tracks[0].definition.soundUri == null }
        assertEquals(selected.soundUri, c.state.value.presets.single().definition.soundUri)
    }
    @Test fun customCompletionHonorsTimedBudgetAndMissingAudioFallsBack() {
        val audio = java.io.File(rule.activity.cacheDir, "halo-test.wav").apply { writeBytes(wave(6)) }
        try {
            execute(Command.Edit(Definition(0, durationMs = 1000, soundEnabled = true, vibrationEnabled = false,
                soundUri = Uri.fromFile(audio).toString(), soundName = audio.name,
                hapticRepeat = HapticRepeat.TIMED, repeatDurationMs = 1000)))
            execute(Command.Start(setOf(0)))
            rule.waitUntil(5000) { c.haptics.customSoundPlaying.value }
            rule.waitUntil(4000) { !c.haptics.customSoundPlaying.value }
            assertEquals(Status.COMPLETED, c.state.value.tracks[0].session?.status)
            execute(Command.Reset(0))
            c.error.value = null
            runBlocking { withContext(Dispatchers.Main) {
                c.haptics.preview(Definition(0, soundEnabled = true, vibrationEnabled = false,
                    soundUri = "content://com.ezral.halo.missing/audio.mp3"))
            } }
            rule.waitUntil(5000) { c.error.value?.contains("Custom sound unavailable") == true }
            assertFalse(c.haptics.customSoundPlaying.value)
        } finally { audio.delete() }
    }
}
