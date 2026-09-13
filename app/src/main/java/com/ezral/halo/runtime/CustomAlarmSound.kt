package com.ezral.halo.runtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Provider reads and player operations are serialized off the UI thread. */
internal class CustomAlarmSound(private val context: Context) {
    private val handler = Handler(HandlerThread("HaloAlarmAudio").apply { start() }.looper)
    private val dispatcher = handler.asCoroutineDispatcher()
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var opening: CompletableDeferred<Unit>? = null
    private var finished: CompletableDeferred<Boolean>? = null
    private var focusHeld = false
    private val mutablePlaying = MutableStateFlow(false)
    val playing = mutablePlaying.asStateFlow()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener({ if (it < 0) stopOnAudioThread() }, handler).build()

    suspend fun play(uri: String, deadlineMs: Long): Boolean = withContext(dispatcher) {
        stopOnAudioThread()
        val media = MediaPlayer()
        player = media
        val prepared = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Boolean>()
        finished = done; opening = prepared
        try {
            media.setAudioAttributes(attributes)
            media.setOnPreparedListener { prepared.complete(Unit) }
            media.setOnCompletionListener { done.complete(true) }
            media.setOnErrorListener { _, _, _ ->
                prepared.completeExceptionally(IllegalStateException("Audio could not be opened"))
                done.complete(false); true
            }
            // setDataSource may read from a documents provider. Never do it on the main thread.
            media.setDataSource(context, Uri.parse(uri))
            media.prepareAsync()
            val opened = withTimeoutOrNull(minOf(10_000L, deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(1)) { prepared.await(); true } ?: false
            if (player !== media || SystemClock.elapsedRealtime() >= deadlineMs) return@withContext true
            if (!opened) return@withContext false
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@withContext true
            focusHeld = true
            media.start(); mutablePlaying.value = true
            withTimeoutOrNull((deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(1)) { done.await() } ?: true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { player !== media }
        finally { if (player === media) stopOnAudioThread() }
    }

    fun stop() { handler.post { stopOnAudioThread() } }
    private fun stopOnAudioThread() {
        val old = player; player = null
        old?.let { runCatching { it.release() } }
        opening?.completeExceptionally(IllegalStateException("Playback stopped")); opening = null
        finished?.complete(true); finished = null
        mutablePlaying.value = false
        if (focusHeld) { focusHeld = false; manager.abandonAudioFocusRequest(focus) }
    }
}
