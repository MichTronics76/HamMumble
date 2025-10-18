package com.hammumble.data

import org.json.JSONObject

/**
 * App-wide settings based on Mumla's comprehensive settings
 */
data class AppSettings(
    // Audio input settings
    val inputMethod: InputMethod = InputMethod.VOICE_ACTIVITY,
    val vadThreshold: Float = 0.5f,
    
    // Push to Talk settings
    val pttSoundEnabled: Boolean = true,  // Play sound on PTT
    
    // Audio output settings
    val useSpeakerphone: Boolean = false  // true = speaker (luidspreker), false = earpiece (oortje)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("inputMethod", inputMethod.name)
            put("vadThreshold", vadThreshold.toDouble())
            put("pttSoundEnabled", pttSoundEnabled)
            put("useSpeakerphone", useSpeakerphone)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): AppSettings {
            return AppSettings(
                inputMethod = InputMethod.valueOf(json.optString("inputMethod", InputMethod.VOICE_ACTIVITY.name)),
                vadThreshold = json.optDouble("vadThreshold", 0.5).toFloat(),
                pttSoundEnabled = json.optBoolean("pttSoundEnabled", true),
                useSpeakerphone = json.optBoolean("useSpeakerphone", false)
            )
        }
    }
}

enum class InputMethod {
    VOICE_ACTIVITY,
    PUSH_TO_TALK,
    CONTINUOUS
}
