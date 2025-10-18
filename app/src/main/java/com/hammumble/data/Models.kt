package com.hammumble.data

import org.json.JSONObject

data class ServerInfo(
    val address: String,
    val port: Int = 64738,
    val username: String,
    val password: String = "",
    val name: String = "",
    val autoConnect: Boolean = false, // Auto-connect to this server on app start
    val autoJoinChannel: String = "", // Channel name to auto-join after connecting (empty = no auto-join)
    val clientCertificatePath: String? = null, // Path to .p12 certificate file
    val clientCertificatePassword: String = "", // Password for the certificate
    val registerWithServer: Boolean = false // Automatically register with server after successful connection
)

data class User(
    val id: Int,
    val name: String,
    val channelId: Int,
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val isSpeaking: Boolean = false,
    val isSelfMuted: Boolean = false,
    val isSelfDeafened: Boolean = false,
    val userId: Int = -1  // User ID from server (-1 = not registered, >= 0 = registered)
)

data class Channel(
    val id: Int,
    val name: String,
    val parentId: Int? = null,
    val description: String = "",
    val users: List<User> = emptyList(),
    val subChannels: List<Channel> = emptyList()
)

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val channelId: Int? = null,
    val isPrivate: Boolean = false
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED,
    DISCONNECTING,
    ERROR
}

enum class TransmissionMode {
    CONTINUOUS,
    VOICE_ACTIVITY,
    PUSH_TO_TALK
}

enum class SerialPttPin {
    RTS,  // Request To Send
    DTR   // Data Terminal Ready
}

enum class RogerBeepStyle {
    CLASSIC_8_TONE,      // Classic ascending 8-tone sequence
    TWO_TONE_HIGH_LOW,   // High-low two-tone beep
    THREE_TONE_UP,       // Three ascending tones
    FOUR_TONE_DESCEND,   // Four descending tones
    SHORT_CHIRP,         // Quick single chirp
    LONG_BEEP,           // Single long tone
    CUSTOM,              // Custom audio file
    
    // Morse code alphabet
    MORSE_A,             // .-
    MORSE_B,             // -...
    MORSE_C,             // -.-.
    MORSE_D,             // -..
    MORSE_E,             // .
    MORSE_F,             // ..-.
    MORSE_G,             // --.
    MORSE_H,             // ....
    MORSE_I,             // ..
    MORSE_J,             // .---
    MORSE_K,             // .-.
    MORSE_L,             // .-..
    MORSE_M,             // --
    MORSE_N,             // -.
    MORSE_O,             // ---
    MORSE_P,             // .--.
    MORSE_Q,             // --.-
    MORSE_R,             // .-.
    MORSE_S,             // ...
    MORSE_T,             // -
    MORSE_U,             // ..-
    MORSE_V,             // ...-
    MORSE_W,             // .--
    MORSE_X,             // -..-
    MORSE_Y,             // -.--
    MORSE_Z              // --..
}

data class SerialPttSettings(
    val enabled: Boolean = false,
    val deviceName: String? = null,
    val pin: SerialPttPin = SerialPttPin.RTS,
    val baudRate: Int = 9600,
    val preDelay: Int = 50,    // milliseconds before PTT activates
    val postDelay: Int = 100   // milliseconds after PTT deactivates
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("deviceName", deviceName)
            put("pin", pin.name)
            put("baudRate", baudRate)
            put("preDelay", preDelay)
            put("postDelay", postDelay)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): SerialPttSettings {
            return SerialPttSettings(
                enabled = json.optBoolean("enabled", false),
                deviceName = json.optString("deviceName").takeIf { it.isNotEmpty() },
                pin = try {
                    SerialPttPin.valueOf(json.optString("pin", "RTS"))
                } catch (e: Exception) {
                    SerialPttPin.RTS
                },
                baudRate = json.optInt("baudRate", 9600),
                preDelay = json.optInt("preDelay", 50),
                postDelay = json.optInt("postDelay", 100)
            )
        }
    }
}

data class RogerBeepSettings(
    val enabled: Boolean = false, // Disabled by default - local playback only
    val style: RogerBeepStyle = RogerBeepStyle.CLASSIC_8_TONE,
    val volume: Float = 0.7f, // 0.0 to 1.0
    val toneDurationMs: Int = 40,
    val customAudioPath: String? = null // Path to custom audio file (WAV, MP3, etc.)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("style", style.name)
            put("volume", volume.toDouble())
            put("toneDurationMs", toneDurationMs)
            put("customAudioPath", customAudioPath)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): RogerBeepSettings {
            return RogerBeepSettings(
                enabled = json.optBoolean("enabled", false),
                style = try {
                    RogerBeepStyle.valueOf(json.optString("style", "CLASSIC_8_TONE"))
                } catch (e: Exception) {
                    RogerBeepStyle.CLASSIC_8_TONE
                },
                volume = json.optDouble("volume", 0.7).toFloat(),
                toneDurationMs = json.optInt("toneDurationMs", 40),
                customAudioPath = json.optString("customAudioPath").takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class MumbleRogerBeepSettings(
    val enabled: Boolean = false, // Transmit roger beep to Mumble when our voice hold timer ends
    val style: RogerBeepStyle = RogerBeepStyle.CLASSIC_8_TONE,
    val volume: Float = 0.7f, // 0.0 to 1.0
    val toneDurationMs: Int = 40,
    val customAudioPath: String? = null // Path to custom audio file (WAV, MP3, etc.)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("style", style.name)
            put("volume", volume.toDouble())
            put("toneDurationMs", toneDurationMs)
            put("customAudioPath", customAudioPath)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): MumbleRogerBeepSettings {
            return MumbleRogerBeepSettings(
                enabled = json.optBoolean("enabled", false),
                style = try {
                    RogerBeepStyle.valueOf(json.optString("style", "CLASSIC_8_TONE"))
                } catch (e: Exception) {
                    RogerBeepStyle.CLASSIC_8_TONE
                },
                volume = json.optDouble("volume", 0.7).toFloat(),
                toneDurationMs = json.optInt("toneDurationMs", 40),
                customAudioPath = json.optString("customAudioPath").takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class VoxPreToneSettings(
    val enabled: Boolean = false, // Enable VOX pre-transmission tone
    val frequency: Int = 20000,   // Hz - 20kHz (inaudible to humans, above speech range)
    val durationMs: Int = 100,    // Duration in milliseconds (default 100ms)
    val volume: Float = 0.3f      // Volume level (0.0 to 1.0, default 30%)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("frequency", frequency)
            put("durationMs", durationMs)
            put("volume", volume.toDouble())
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): VoxPreToneSettings {
            return VoxPreToneSettings(
                enabled = json.optBoolean("enabled", false),
                frequency = json.optInt("frequency", 20000),
                durationMs = json.optInt("durationMs", 100),
                volume = json.optDouble("volume", 0.3).toFloat()
            )
        }
    }
}

data class AudioSettings(
    val bitrate: Int = 64000,
    val framesPerPacket: Int = 2,
    val transmissionMode: TransmissionMode = TransmissionMode.PUSH_TO_TALK,
    val vadThreshold: Float = 0.5f,
    val isNoiseSuppressionEnabled: Boolean = true,
    val isEchoCancellationEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val voiceActivationThreshold: Float = 0.5f,
    val inputGain: Float = 1.0f, // Microphone gain multiplier (0.5x - 5.0x, default 1.0x = normal)
    val outputGain: Float = 1.0f, // Speaker gain multiplier (0.5x - 5.0x, default 1.0x = normal)
    val micBoost: Boolean = false, // Extra 2x pre-amplification for low-level microphones (portofoon/VOX)
    val autoMaxVolume: Boolean = true, // Automatically set system volume to maximum when connected
    val echoCancellation: Boolean = true,
    val noiseReduction: Boolean = true,
    val voiceHoldTime: Int = 500, // Milliseconds to keep transmitting after voice stops (default 500ms)
    val serialPtt: SerialPttSettings = SerialPttSettings(), // Hardware PTT via serial port
    val rogerBeep: RogerBeepSettings = RogerBeepSettings(), // 8-tone roger beep played locally on PTT release
    val mumbleRogerBeep: MumbleRogerBeepSettings = MumbleRogerBeepSettings(), // Roger beep transmitted to Mumble when our voice hold ends
    val voxPreTone: VoxPreToneSettings = VoxPreToneSettings() // Inaudible pre-transmission tone for VOX triggering
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("bitrate", bitrate)
            put("framesPerPacket", framesPerPacket)
            put("transmissionMode", transmissionMode.name)
            put("vadThreshold", vadThreshold.toDouble())
            put("isNoiseSuppressionEnabled", isNoiseSuppressionEnabled)
            put("isEchoCancellationEnabled", isEchoCancellationEnabled)
            put("isMuted", isMuted)
            put("isDeafened", isDeafened)
            put("voiceActivationThreshold", voiceActivationThreshold.toDouble())
            put("inputGain", inputGain.toDouble())
            put("outputGain", outputGain.toDouble())
            put("micBoost", micBoost)
            put("autoMaxVolume", autoMaxVolume)
            put("echoCancellation", echoCancellation)
            put("noiseReduction", noiseReduction)
            put("voiceHoldTime", voiceHoldTime)
            put("serialPtt", serialPtt.toJson())
            put("rogerBeep", rogerBeep.toJson())
            put("mumbleRogerBeep", mumbleRogerBeep.toJson())
            put("voxPreTone", voxPreTone.toJson())
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): AudioSettings {
            return AudioSettings(
                bitrate = json.optInt("bitrate", 64000),
                framesPerPacket = json.optInt("framesPerPacket", 2),
                transmissionMode = try {
                    TransmissionMode.valueOf(json.optString("transmissionMode", "PUSH_TO_TALK"))
                } catch (e: Exception) {
                    TransmissionMode.PUSH_TO_TALK
                },
                vadThreshold = json.optDouble("vadThreshold", 0.5).toFloat(),
                isNoiseSuppressionEnabled = json.optBoolean("isNoiseSuppressionEnabled", true),
                isEchoCancellationEnabled = json.optBoolean("isEchoCancellationEnabled", true),
                isMuted = json.optBoolean("isMuted", false),
                isDeafened = json.optBoolean("isDeafened", false),
                voiceActivationThreshold = json.optDouble("voiceActivationThreshold", 0.5).toFloat(),
                inputGain = json.optDouble("inputGain", 1.0).toFloat(),
                outputGain = json.optDouble("outputGain", 1.0).toFloat(),
                micBoost = json.optBoolean("micBoost", false),
                autoMaxVolume = json.optBoolean("autoMaxVolume", true),
                echoCancellation = json.optBoolean("echoCancellation", true),
                noiseReduction = json.optBoolean("noiseReduction", true),
                voiceHoldTime = json.optInt("voiceHoldTime", 500),
                serialPtt = json.optJSONObject("serialPtt")?.let { SerialPttSettings.fromJson(it) } ?: SerialPttSettings(),
                rogerBeep = json.optJSONObject("rogerBeep")?.let { RogerBeepSettings.fromJson(it) } ?: RogerBeepSettings(),
                mumbleRogerBeep = json.optJSONObject("mumbleRogerBeep")?.let { MumbleRogerBeepSettings.fromJson(it) } ?: MumbleRogerBeepSettings(),
                voxPreTone = json.optJSONObject("voxPreTone")?.let { VoxPreToneSettings.fromJson(it) } ?: VoxPreToneSettings()
            )
        }
    }
}