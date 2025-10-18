package com.hammumble.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.hammumble.data.VoxPreToneSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates an inaudible pre-transmission tone to trigger VOX on radio transmitters.
 * This ensures the VOX circuit is already open when speech begins, preventing
 * clipping of the first syllable.
 */
class VoxPreToneGenerator {
    
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 48000 // Hz
    
    /**
     * Play an inaudible tone before transmission starts
     * 
     * @param settings VOX pre-tone configuration
     */
    fun playPreTone(settings: VoxPreToneSettings) {
        if (!settings.enabled) return
        
        try {
            val duration = settings.durationMs / 1000.0 // Convert to seconds
            val frequency = settings.frequency // Hz (20kHz by default - inaudible)
            val numSamples = (duration * sampleRate).toInt()
            val generatedSound = ShortArray(numSamples)
            
            // Generate sine wave at specified frequency
            for (i in 0 until numSamples) {
                val angle = 2.0 * PI * i / (sampleRate / frequency)
                // Apply volume and convert to 16-bit PCM
                generatedSound[i] = (sin(angle) * Short.MAX_VALUE * settings.volume).toInt().toShort()
            }
            
            // Configure AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL, // Use voice call stream
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize.coerceAtLeast(numSamples * 2),
                AudioTrack.MODE_STATIC
            )
            
            // Load and play the tone
            audioTrack?.write(generatedSound, 0, numSamples)
            audioTrack?.play()
            
            // Wait for tone to complete
            Thread.sleep(settings.durationMs.toLong())
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stopAndRelease()
        }
    }
    
    /**
     * Play pre-tone asynchronously (non-blocking)
     */
    fun playPreToneAsync(settings: VoxPreToneSettings) {
        if (!settings.enabled) return
        
        Thread {
            playPreTone(settings)
        }.start()
    }
    
    /**
     * Stop and release audio resources
     */
    fun stopAndRelease() {
        audioTrack?.let { track ->
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioTrack = null
    }
    
    /**
     * Release resources
     */
    fun release() {
        stopAndRelease()
    }
}
