package com.hammumble.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hammumble.data.MumbleRogerBeepSettings
import com.hammumble.data.RogerBeepStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates roger beep audio and transmits it to Mumble
 * This is different from RogerBeepGenerator which plays locally
 * 
 * This generator creates audio data that gets sent through Mumble's audio input
 * to be transmitted to other users when our voice hold timer ends
 */
class MumbleRogerBeepGenerator(private val context: Context) {
    private val tag = "MumbleRogerBeepGenerator"
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
                frequencies = floatArrayOf(1047f, 0f, 1109f, 0f, 1175f, 0f, 1245f, 0f, 1319f, 0f, 1397f, 0f, 1480f, 0f, 1568f),
                durations = intArrayOf(40, 10, 40, 10, 40, 10, 40, 10, 40, 10, 40, 10, 40, 10, 40),
                description = "Classic 8-tone ascending"
            ),
            RogerBeepStyle.TWO_TONE_HIGH_LOW to BeepDefinition(
                frequencies = floatArrayOf(1800f, 0f, 1200f),
                durations = intArrayOf(100, 20, 100),
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
            RogerBeepStyle.MORSE_K to morse("-.-"),
            RogerBeepStyle.SHORT_CHIRP to BeepDefinition(
                frequencies = floatArrayOf(1500f),
                durations = intArrayOf(80),
                description = "Quick chirp"
            ),
            RogerBeepStyle.LONG_BEEP to BeepDefinition(
                frequencies = floatArrayOf(1000f),
                durations = intArrayOf(250),
                description = "Single long tone"
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
    
    private var settings = MumbleRogerBeepSettings()
    private var audioCallback: ((ShortArray) -> Unit)? = null
    
    fun updateSettings(newSettings: MumbleRogerBeepSettings) {
        settings = newSettings
        Log.d(tag, "Mumble roger beep settings updated: style=${newSettings.style}, enabled=${newSettings.enabled}, volume=${newSettings.volume}")
    }
    
    /**
     * Set callback to receive generated audio data
     * This audio should be fed into Mumble's audio input
     */
    fun setAudioCallback(callback: (ShortArray) -> Unit) {
        audioCallback = callback
    }
    
    /**
     * Generate and send roger beep audio to Mumble
     * This is transmitted to other users when our voice hold timer ends
     */
    fun playRogerBeep() {
        if (!settings.enabled) {
            Log.d(tag, "Mumble roger beep disabled, skipping")
            return
        }
        
        // Cancel any existing playback
        currentJob?.cancel()
        
        // Generate synchronously (not in a coroutine) to ensure it completes before callback returns
        try {
            // Check if custom audio should be used
            val audioData = if (settings.style == RogerBeepStyle.CUSTOM && settings.customAudioPath != null) {
                Log.d(tag, "Loading custom Mumble roger beep from: ${settings.customAudioPath}")
                loadCustomAudio(settings.customAudioPath!!)
            } else {
                val beepDef = BEEP_STYLES[settings.style]
                if (beepDef != null) {
                    Log.d(tag, "Generating Mumble roger beep: ${beepDef.description}")
                    generateBeepSequence(beepDef)
                } else {
                    Log.e(tag, "Unknown roger beep style: ${settings.style}")
                    return
                }
            }
            
            if (audioData != null) {
                // Send audio data through callback (to be transmitted via Mumble)
                audioCallback?.invoke(audioData)
                Log.d(tag, "Mumble roger beep sent (${audioData.size} samples)")
            } else {
                Log.e(tag, "Failed to generate/load Mumble roger beep audio")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error generating Mumble roger beep", e)
        }
    }
    
    /**
     * Load custom audio file and return PCM data
     */
    private fun loadCustomAudio(audioPath: String): ShortArray? {
        try {
            val uri = Uri.parse(audioPath)
            val audioData = CustomAudioLoader.loadAudioFile(context, uri) ?: return null
            
            // Normalize audio to maximum amplitude first
            val normalizedData = normalizeAudio(audioData)
            
            // Then apply user volume setting
            val amplifiedData = ShortArray(normalizedData.size)
            for (i in normalizedData.indices) {
                amplifiedData[i] = (normalizedData[i] * settings.volume).toInt().toShort()
            }
            
            return amplifiedData
        } catch (e: Exception) {
            Log.e(tag, "Error loading custom audio", e)
            return null
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
    
    private fun generateBeepSequence(beepDef: BeepDefinition): ShortArray {
        Log.d(tag, "Generating beep sequence: ${beepDef.frequencies.size} tones, ${beepDef.durations.size} durations")
        
        // Calculate total samples needed
        var totalSamples = 0
        for (duration in beepDef.durations) {
            totalSamples += (SAMPLE_RATE * duration) / 1000
        }
        
        Log.d(tag, "Total samples needed: $totalSamples (${totalSamples / 48}ms)")
        
        val audioData = ShortArray(totalSamples)
        var offset = 0
        
        // Generate each tone in the sequence
        for (i in beepDef.frequencies.indices) {
            val frequency = beepDef.frequencies[i]
            val duration = beepDef.durations[i]
            val numSamples = (SAMPLE_RATE * duration) / 1000
            
            Log.d(tag, "Tone $i: ${frequency}Hz for ${duration}ms (${numSamples} samples) at offset $offset")
            
            if (frequency > 0) {
                // Generate actual tone
                generateTone(frequency, numSamples, audioData, offset)
            } else {
                // Silence (frequency = 0)
                // Audio data is already initialized to zeros, so just skip
            }
            
            offset += numSamples
        }
        
        Log.d(tag, "Generated ${audioData.size} total samples")
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
     * Stop any currently generating roger beep
     */
    fun stop() {
        currentJob?.cancel()
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stop()
        audioCallback = null
    }
}
