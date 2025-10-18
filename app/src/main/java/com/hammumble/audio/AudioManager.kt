package com.hammumble.audio

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.hammumble.data.AudioSettings
import com.hammumble.data.TransmissionMode
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

class AudioManager(private val context: Context) {
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    private var isDeafened = false
    private var isPushToTalkActive = false
    
    private var currentSettings = AudioSettings()
    private var vadThreshold = 0.5f
    
    // Audio format constants
    private val sampleRate = 48000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
    
    // Voice activity detection
    private val vadWindowSize = 320 // 20ms at 16kHz
    private val vadBuffer = FloatArray(vadWindowSize)
    private var vadIndex = 0
    
    init {
        initializeAudio()
    }
    
    private fun initializeAudio() {
        try {
            // Initialize AudioRecord for recording
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            // Initialize AudioTrack for playback
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            // Initialize audio effects
            setupAudioEffects()
            
        } catch (e: Exception) {
            // Handle initialization error
        }
    }
    
    private fun setupAudioEffects() {
        audioRecord?.let { record ->
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                    enabled = currentSettings.isEchoCancellationEnabled
                }
            }
            
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                    enabled = currentSettings.isNoiseSuppressionEnabled
                }
            }
        }
    }
    
    fun startRecording() {
        if (isRecording) return
        
        audioRecord?.let { record ->
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                record.startRecording()
                isRecording = true
                
                audioScope.launch {
                    recordingLoop()
                }
            }
        }
    }
    
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
    }
    
    fun startPlayback() {
        if (isPlaying) return
        
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                isPlaying = true
            }
        }
    }
    
    fun stopPlayback() {
        isPlaying = false
        audioTrack?.stop()
    }
    
    private suspend fun recordingLoop() {
        val buffer = ByteArray(bufferSize)
        
        while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            
            if (bytesRead > 0 && shouldTransmit(buffer, bytesRead)) {
                // Process and encode audio data
                processAudioData(buffer, bytesRead)
            }
            
            delay(10) // Small delay to prevent excessive CPU usage
        }
    }
    
    private fun shouldTransmit(buffer: ByteArray, length: Int): Boolean {
        if (isMuted || isDeafened) return false
        
        return when (currentSettings.transmissionMode) {
            TransmissionMode.CONTINUOUS -> true
            TransmissionMode.PUSH_TO_TALK -> isPushToTalkActive
            TransmissionMode.VOICE_ACTIVITY -> detectVoiceActivity(buffer, length)
        }
    }
    
    private fun detectVoiceActivity(buffer: ByteArray, length: Int): Boolean {
        // Convert bytes to float samples for VAD
        val samples = FloatArray(length / 2)
        val byteBuffer = ByteBuffer.wrap(buffer)
        
        for (i in samples.indices) {
            samples[i] = byteBuffer.short.toFloat() / Short.MAX_VALUE
        }
        
        // Calculate RMS energy
        var sum = 0.0
        for (sample in samples) {
            sum += sample * sample
        }
        val rms = sqrt(sum / samples.size).toFloat()
        
        // Simple VAD based on energy threshold
        return rms > vadThreshold
    }
    
    private fun processAudioData(_buffer: ByteArray, _length: Int) {
        // Here you would:
        // 1. Apply audio processing (noise reduction, etc.)
        // 2. Encode audio (Opus codec for Mumble)
        // 3. Send encoded data to network layer
        
        // For now, this is a placeholder
        // In a real implementation, you'd integrate with Opus codec
    }
    
    fun playAudioData(audioData: ByteArray) {
        if (!isDeafened && isPlaying) {
            audioTrack?.write(audioData, 0, audioData.size)
        }
    }
    
    fun setMuted(muted: Boolean) {
        this.isMuted = muted
        if (muted) {
            stopRecording()
        } else if (!isDeafened) {
            startRecording()
        }
    }
    
    fun setDeafened(deafened: Boolean) {
        this.isDeafened = deafened
        if (deafened) {
            stopRecording()
            stopPlayback()
            setMuted(true)
        } else {
            startRecording()
            startPlayback()
        }
    }
    
    fun setPushToTalk(active: Boolean) {
        this.isPushToTalkActive = active
    }
    
    fun updateSettings(settings: AudioSettings) {
        this.currentSettings = settings
        this.vadThreshold = settings.vadThreshold
        
        echoCanceler?.enabled = settings.isEchoCancellationEnabled
        noiseSuppressor?.enabled = settings.isNoiseSuppressionEnabled
        
        // Update bitrate and other encoding parameters
        // This would be handled by the audio encoder
    }
    
    fun release() {
        stopRecording()
        stopPlayback()
        
        echoCanceler?.release()
        noiseSuppressor?.release()
        audioRecord?.release()
        audioTrack?.release()
        
        audioScope.cancel()
    }
    
    fun getAudioLevels(): Pair<Float, Float> {
        // Return input and output audio levels for UI
        // This would be calculated during audio processing
        return Pair(0.0f, 0.0f)
    }
}