package com.hammumble.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Manages audio feedback for PTT interactions
 * Based on Mumla's audio feedback system
 */
class AudioFeedbackManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tag = "AudioFeedbackManager"
    
    /**
     * Play a system sound effect for PTT press
     * Uses the standard keypress sound effect
     */
    fun playPttPressSound() {
        try {
            // Volume -1.0 means use system default volume
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
            Log.d(tag, "PTT press sound played")
        } catch (e: Exception) {
            Log.e(tag, "Failed to play PTT press sound", e)
        }
    }
    
    /**
     * Play a system sound effect for PTT release
     * Uses a slightly different sound to distinguish from press
     */
    fun playPttReleaseSound() {
        try {
            // Use return key sound for release (slightly different tone)
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, -1f)
            Log.d(tag, "PTT release sound played")
        } catch (e: Exception) {
            Log.e(tag, "Failed to play PTT release sound", e)
        }
    }
    
    /**
     * Play a generic notification sound
     * Useful for other feedback like mute/deafen toggles
     */
    fun playNotificationSound() {
        try {
            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, -1f)
            Log.d(tag, "Notification sound played")
        } catch (e: Exception) {
            Log.e(tag, "Failed to play notification sound", e)
        }
    }
}
