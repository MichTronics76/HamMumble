package com.hammumble.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.util.Log
import com.hammumble.data.RogerBeepStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates and plays multiple roger beep styles
 * Plays locally through speaker, NOT transmitted to Mumble
 * 
 * Supports various roger beep styles used in ham radio equipment
 */
class RogerBeepGenerator(private val context: Context) {
    private val tag = "RogerBeepGenerator"
    private var audioTrack: AudioTrack? = null
    private var currentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    
    companion object {
        private const val SAMPLE_RATE = 48000
        
        // Morse code timing constants (in milliseconds)
        private const val MORSE_DIT = 60      // Short beep (dot)
        private const val MORSE_DAH = 180     // Long beep (dash) = 3x dit
        private const val MORSE_GAP = 60      // Gap between dit/dah within letter
        private const val MORSE_FREQ = 1000f  // Frequency for morse tones
        
        // Helper function to create morse sequences
        private fun morse(pattern: String): BeepDefinition {
            val frequencies = mutableListOf<Float>()
            val durations = mutableListOf<Int>()
            
            pattern.forEach { char ->
                when (char) {
                    '.' -> { // Dit
                        frequencies.add(MORSE_FREQ)
                        durations.add(MORSE_DIT)
                        frequencies.add(0f) // Gap
                        durations.add(MORSE_GAP)
                    }
                    '-' -> { // Dah
                        frequencies.add(MORSE_FREQ)
                        durations.add(MORSE_DAH)
                        frequencies.add(0f) // Gap
                        durations.add(MORSE_GAP)
                    }
                }
            }
            
            // Remove last gap
            if (frequencies.isNotEmpty()) {
                frequencies.removeAt(frequencies.size - 1)
                durations.removeAt(durations.size - 1)
            }
            
            return BeepDefinition(
                frequencies = frequencies.toFloatArray(),
                durations = durations.toIntArray(),
                description = "Morse code '$pattern'"
            )
        }
        
        // Roger beep style definitions
        private val BEEP_STYLES = mapOf(
            RogerBeepStyle.CLASSIC_8_TONE to BeepDefinition(
                frequencies = floatArrayOf(1047f, 1109f, 1175f, 1245f, 1319f, 1397f, 1480f, 1568f),
                durations = intArrayOf(40, 40, 40, 40, 40, 40, 40, 40),
                description = "Classic 8-tone ascending"
            ),
            RogerBeepStyle.TWO_TONE_HIGH_LOW to BeepDefinition(
                frequencies = floatArrayOf(1800f, 1200f),
                durations = intArrayOf(100, 100),
                description = "Two-tone high-low"
            ),
            RogerBeepStyle.THREE_TONE_UP to BeepDefinition(
                frequencies = floatArrayOf(800f, 1200f, 1600f),
                durations = intArrayOf(80, 80, 120),
                description = "Three ascending tones"
            ),
            RogerBeepStyle.FOUR_TONE_DESCEND to BeepDefinition(
                frequencies = floatArrayOf(2000f, 1600f, 1200f, 800f),
                durations = intArrayOf(60, 60, 60, 80),
                description = "Four descending tones"
            ),
            RogerBeepStyle.LONG_BEEP to BeepDefinition(
                frequencies = floatArrayOf(1000f),
                durations = intArrayOf(250),
                description = "Single long tone"
                ),
                    
            RogerBeepStyle.SHORT_CHIRP to BeepDefinition(
                frequencies = floatArrayOf(1500f),
                durations = intArrayOf(80),
                description = "Quick chirp"
            ),
            
            // Morse code alphabet
            RogerBeepStyle.MORSE_A to morse(".-"),
            RogerBeepStyle.MORSE_B to morse("-..."),
            RogerBeepStyle.MORSE_C to morse("-.-."),
            RogerBeepStyle.MORSE_D to morse("-.."),
            RogerBeepStyle.MORSE_E to morse("."),
            RogerBeepStyle.MORSE_F to morse("..-."),
            RogerBeepStyle.MORSE_G to morse("--."),
            RogerBeepStyle.MORSE_H to morse("...."),
            RogerBeepStyle.MORSE_I to morse(".."),
            RogerBeepStyle.MORSE_J to morse(".---"),
            RogerBeepStyle.MORSE_K to morse("-.-"),
            RogerBeepStyle.MORSE_L to morse(".-.."),
            RogerBeepStyle.MORSE_M to morse("--"),
            RogerBeepStyle.MORSE_N to morse("-."),
            RogerBeepStyle.MORSE_O to morse("---"),
            RogerBeepStyle.MORSE_P to morse(".--."),
            RogerBeepStyle.MORSE_Q to morse("--.-"),
            RogerBeepStyle.MORSE_R to morse(".-."),
            RogerBeepStyle.MORSE_S to morse("..."),
            RogerBeepStyle.MORSE_T to morse("-"),
            RogerBeepStyle.MORSE_U to morse("..-"),
            RogerBeepStyle.MORSE_V to morse("...-"),
            RogerBeepStyle.MORSE_W to morse(".--"),
            RogerBeepStyle.MORSE_X to morse("-..-"),
            RogerBeepStyle.MORSE_Y to morse("-.--"),
            RogerBeepStyle.MORSE_Z to morse("--.."),
        )
        
        data class BeepDefinition(
            val frequencies: FloatArray,
            val durations: IntArray,
            val description: String
        )
    }
    
    data class RogerBeepSettings(
        val enabled: Boolean = true,
        val style: RogerBeepStyle = RogerBeepStyle.CLASSIC_8_TONE,
        val volume: Float = 0.7f, // 0.0 to 1.0
        val toneDurationMs: Int = 40,
        val customAudioPath: String? = null // Path to custom audio file
    )
    
    private var settings = RogerBeepSettings()
    
    fun updateSettings(newSettings: RogerBeepSettings) {
        settings = newSettings
        Log.d(tag, "Roger beep settings updated: style=${newSettings.style}, enabled=${newSettings.enabled}, volume=${newSettings.volume}, customAudioPath=${newSettings.customAudioPath}")
    }
    
    /**
     * Play the selected roger beep style
     * This is played locally and NOT transmitted to Mumble
     */
    fun playRogerBeep() {
        if (!settings.enabled) {
            Log.d(tag, "Roger beep disabled, skipping")
            return
        }
        
        Log.d(tag, "playRogerBeep called - style=${settings.style}, customAudioPath=${settings.customAudioPath}")
        
        // Cancel any existing playback
        currentJob?.cancel()
        
        currentJob = scope.launch {
            try {
                // Check if custom audio should be used
                if (settings.style == RogerBeepStyle.CUSTOM && settings.customAudioPath != null) {
                    Log.d(tag, "Playing custom roger beep from: ${settings.customAudioPath}")
                    playCustomAudio(settings.customAudioPath!!)
                } else if (settings.style == RogerBeepStyle.CUSTOM) {
                    Log.e(tag, "CUSTOM style selected but customAudioPath is null!")
                } else {
                    val beepDef = BEEP_STYLES[settings.style]
                    if (beepDef != null) {
                        Log.d(tag, "Playing roger beep: ${beepDef.description}")
                        playBeepSequence(beepDef)
                        Log.d(tag, "Roger beep playback completed")
                    } else {
                        Log.e(tag, "Unknown roger beep style: ${settings.style}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error playing roger beep", e)
            }
        }
    }
    
    /**
     * Play custom audio file
     */
    private fun playCustomAudio(audioPath: String) {
        try {
            Log.d(tag, "playCustomAudio called with path: $audioPath")
            val uri = Uri.parse(audioPath)
            Log.d(tag, "Parsed URI: $uri")
            
            val audioData = CustomAudioLoader.loadAudioFile(context, uri) ?: run {
                Log.e(tag, "Failed to load custom audio file - loadAudioFile returned null")
                return
            }
            
            Log.d(tag, "Loaded audio data: ${audioData.size} samples")
            
            // Normalize audio to maximum amplitude first
            val normalizedData = normalizeAudio(audioData)
            Log.d(tag, "Audio normalized")
            
            // Then apply user volume setting
            val amplifiedData = ShortArray(normalizedData.size)
            for (i in normalizedData.indices) {
                amplifiedData[i] = (normalizedData[i] * settings.volume).toInt().toShort()
            }
            
            Log.d(tag, "Creating AudioTrack for playback...")
            // Create AudioTrack and play
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(amplifiedData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            
            Log.d(tag, "AudioTrack created, writing ${amplifiedData.size} samples...")
            audioTrack.write(amplifiedData, 0, amplifiedData.size)
            Log.d(tag, "Data written, starting playback...")
            audioTrack.play()
            Log.d(tag, "AudioTrack.play() called, waiting for completion...")
            
            // Wait for playback to finish
            val durationMs = (amplifiedData.size * 1000L) / SAMPLE_RATE
            Log.d(tag, "Waiting ${durationMs}ms for playback to complete...")
            Thread.sleep(durationMs)
            
            audioTrack.stop()
            audioTrack.release()
            
            Log.d(tag, "Custom roger beep playback completed")
        } catch (e: Exception) {
            Log.e(tag, "Error playing custom audio", e)
        }
    }
    
    /**
     * Normalize audio to maximum amplitude without clipping
     */
    private fun normalizeAudio(audioData: ShortArray): ShortArray {
        // Find peak amplitude
        var maxAmplitude = 0
        for (sample in audioData) {
            val absSample = kotlin.math.abs(sample.toInt())
            if (absSample > maxAmplitude) {
                maxAmplitude = absSample
            }
        }
        
        if (maxAmplitude == 0) {
            Log.w(tag, "Audio data is silent (max amplitude = 0)")
            return audioData
        }
        
        // Calculate normalization factor to reach ~90% of max (32767 * 0.9 = 29490)
        // This gives headroom to prevent clipping while maximizing volume
        val targetAmplitude = 29490.0
        val normalizationFactor = targetAmplitude / maxAmplitude
        
        Log.d(tag, "Normalizing audio: maxAmplitude=$maxAmplitude, factor=$normalizationFactor")
        
        // Apply normalization
        val normalizedData = ShortArray(audioData.size)
        for (i in audioData.indices) {
            val normalized = (audioData[i] * normalizationFactor).toInt()
            // Clamp to prevent overflow
            normalizedData[i] = normalized.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return normalizedData
    }
    
    private fun playBeepSequence(beepDef: BeepDefinition) {
        // Calculate total buffer size needed
        var totalSamples = 0
        for (duration in beepDef.durations) {
            totalSamples += (SAMPLE_RATE * duration) / 1000
        }
        
        // Create AudioTrack for local playback (NOT for Mumble transmission)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(totalSamples * 2) // 16-bit = 2 bytes per sample
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        
        try {
            // Generate the complete beep sequence based on style
            val audioData = generateBeepSequence(beepDef)
            
            // Calculate total duration for sleep
            val totalDuration = beepDef.durations.sum()
            
            // Write data to AudioTrack
            audioTrack.write(audioData, 0, audioData.size)
            
            // Set volume to max (volume is already applied to samples)
            audioTrack.setVolume(1.0f)
            
            // Play the beep
            audioTrack.play()
            
            // Wait for playback to complete
            Thread.sleep(totalDuration.toLong())
            
        } finally {
            audioTrack.stop()
            audioTrack.release()
        }
    }
    
    private fun generateBeepSequence(beepDef: BeepDefinition): ShortArray {
        // Calculate total samples needed
        var totalSamples = 0
        for (duration in beepDef.durations) {
            totalSamples += (SAMPLE_RATE * duration) / 1000
        }
        
        val audioData = ShortArray(totalSamples)
        var offset = 0
        
        // Generate each tone in the sequence
        for (i in beepDef.frequencies.indices) {
            val frequency = beepDef.frequencies[i]
            val duration = beepDef.durations[i]
            val numSamples = (SAMPLE_RATE * duration) / 1000
            
            if (frequency > 0) {
                // Generate actual tone
                generateTone(frequency, numSamples, audioData, offset)
            } else {
                // Silence (frequency = 0)
                // Audio data is already initialized to zeros, so just skip
            }
            
            offset += numSamples
        }
        
        return audioData
    }
    
    private fun generateTone(frequency: Float, numSamples: Int, buffer: ShortArray, offset: Int) {
        // Apply volume setting to amplitude
        // Use 0.8 as base amplitude to avoid clipping, then multiply by user's volume setting
        val amplitude = 32767 * 0.8 * settings.volume
        
        for (i in 0 until numSamples) {
            val sample = (amplitude * sin(2.0 * PI * frequency * i / SAMPLE_RATE)).toInt().toShort()
            buffer[offset + i] = sample
        }
    }
    
    /**
     * Stop any currently playing roger beep
     */
    fun stop() {
        currentJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stop()
    }
}
