package com.ezral.halo.runtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.ezral.halo.core.AlertTone

/** Owns only Halo's alarm audio; never changes the user's volume or DND settings. */
internal class PatternSound(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var track: AudioTrack? = null
    private var focusHeld = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener { if (it < 0) stop() }.build()
    fun play(samples: ShortArray) {
        stop()
        if (samples.isEmpty() || manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focusHeld = true
        try {
            val player = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AlertTone.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            track = player
            if (player.write(samples, 0, samples.size) != samples.size) { stop(); return }
            player.play()
        } catch (_: RuntimeException) { stop() }
    }
    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }; track = null
        if (focusHeld) { focusHeld = false; manager.abandonAudioFocusRequest(focus) }
    }
}
