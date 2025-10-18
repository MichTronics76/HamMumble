package com.hammumble.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CustomAudioLoader {
    private const val TAG = "CustomAudioLoader"
    private const val TARGET_SAMPLE_RATE = 48000
    
    fun loadAudioFile(context: Context, audioUri: Uri, maxDurationMs: Int = 5000): ShortArray? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        
        try {
            Log.d(TAG, "Starting to load audio file: $audioUri")
            
            // Check if we can access the URI
            try {
                val inputStream = context.contentResolver.openInputStream(audioUri)
                if (inputStream == null) {
                    Log.e(TAG, "Cannot open input stream for URI - permission denied or file not found")
                    return null
                }
                inputStream.close()
                Log.d(TAG, "URI is accessible")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to access URI: ${e.message}", e)
                return null
            }
            
            extractor = MediaExtractor()
            extractor.setDataSource(context, audioUri, null)
            
            Log.d(TAG, "MediaExtractor created, track count: ${extractor.trackCount}")
            
            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                Log.d(TAG, "Track $i: mime=$mime")
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            
            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.e(TAG, "No audio track found in file")
                return null
            }
            
            Log.d(TAG, "Found audio track at index: $audioTrackIndex")
            extractor.selectTrack(audioTrackIndex)
            
            // Get audio properties
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            Log.d(TAG, "Audio format: mime=$mime, sampleRate=$sampleRate, channels=$channelCount")
            
            // Create decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()
            
            Log.d(TAG, "MediaCodec decoder started")
            
            val samples = mutableListOf<Short>()
            val maxSamples = ((maxDurationMs / 1000.0) * sampleRate * channelCount).toInt()
            val info = MediaCodec.BufferInfo()
            
            var sawInputEOS = false
            var sawOutputEOS = false
            
            while (!sawOutputEOS && samples.size < maxSamples) {
                // Feed input to decoder
                if (!sawInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIndex)
                        if (inputBuf != null) {
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            
                            if (sampleSize < 0) {
                                Log.d(TAG, "Saw input EOS")
                                codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                
                // Get decoded output
                val outputBufIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputBufIndex >= 0) {
                    val outputBuf = codec.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null) {
                        outputBuf.position(info.offset)
                        outputBuf.limit(info.offset + info.size)
                        
                        // Convert decoded PCM bytes to shorts
                        val pcmBytes = ByteArray(info.size)
                        outputBuf.get(pcmBytes)
                        
                        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shortBuffer.hasRemaining() && samples.size < maxSamples) {
                            samples.add(shortBuffer.get())
                        }
                        
                        codec.releaseOutputBuffer(outputBufIndex, false)
                        
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "Saw output EOS")
                            sawOutputEOS = true
                        }
                    }
                }
            }
            
            Log.d(TAG, "Decoded ${samples.size} samples (sampleRate=$sampleRate, channels=$channelCount)")
            
            // Resample if needed
            val result = if (sampleRate != TARGET_SAMPLE_RATE) {
                Log.d(TAG, "Resampling from $sampleRate Hz to $TARGET_SAMPLE_RATE Hz")
                resample(samples.toShortArray(), sampleRate, TARGET_SAMPLE_RATE, channelCount)
            } else {
                samples.toShortArray()
            }
            
            Log.d(TAG, "Final audio: ${result.size} samples at $TARGET_SAMPLE_RATE Hz")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom audio file", e)
            return null
        } finally {
            codec?.stop()
            codec?.release()
            extractor?.release()
        }
    }
    
    /**
     * Simple linear interpolation resampling
     */
    private fun resample(input: ShortArray, inputRate: Int, outputRate: Int, @Suppress("UNUSED_PARAMETER") channels: Int): ShortArray {
        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outputLength = (input.size / ratio).toInt()
        val output = ShortArray(outputLength)
        
        for (i in output.indices) {
            val srcIndex = i * ratio
            val index1 = srcIndex.toInt().coerceIn(0, input.size - 1)
            val index2 = (index1 + 1).coerceIn(0, input.size - 1)
            val fraction = srcIndex - index1
            
            output[i] = (input[index1] * (1 - fraction) + input[index2] * fraction).toInt().toShort()
        }
        
        return output
    }
}
