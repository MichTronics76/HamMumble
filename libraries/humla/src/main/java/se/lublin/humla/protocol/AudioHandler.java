/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.humla.protocol;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import se.lublin.humla.R;
import se.lublin.humla.audio.AudioInput;
import se.lublin.humla.audio.AudioOutput;
import se.lublin.humla.audio.encoder.CELT11Encoder;
import se.lublin.humla.audio.encoder.CELT7Encoder;
import se.lublin.humla.audio.encoder.IEncoder;
import se.lublin.humla.audio.encoder.OpusEncoder;
import se.lublin.humla.audio.encoder.PreprocessingEncoder;
import se.lublin.humla.audio.encoder.ResamplingEncoder;
import se.lublin.humla.audio.inputmode.IInputMode;
import se.lublin.humla.exception.AudioException;
import se.lublin.humla.exception.AudioInitializationException;
import se.lublin.humla.exception.NativeAudioException;
import se.lublin.humla.model.User;
import se.lublin.humla.net.HumlaConnection;
import se.lublin.humla.net.HumlaUDPMessageType;
import se.lublin.humla.net.PacketBuffer;
import se.lublin.humla.protobuf.Mumble;
import se.lublin.humla.util.HumlaLogger;
import se.lublin.humla.util.HumlaNetworkListener;

/**
 * Bridges the protocol's audio messages to our input and output threads.
 * A useful intermediate for reducing code coupling.
 * Audio playback and recording is exclusively controlled by the protocol.
 * Changes to input/output instance vars after the audio threads have been initialized will recreate
 * them in most cases (they're immutable for the purpose of avoiding threading issues).
 * Calling shutdown() will cleanup both input and output threads. It is safe to restart after.
 * Created by andrew on 23/04/14.
 */
public class AudioHandler extends HumlaNetworkListener implements AudioInput.AudioInputListener {
    private static final String TAG = AudioHandler.class.getName();

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = SAMPLE_RATE/100;
    public static final int MAX_BUFFER_SIZE = 960;

    private final Context mContext;
    private final HumlaLogger mLogger;
    private final AudioManager mAudioManager;
    private final AudioInput mInput;
    private final AudioOutput mOutput;
    private AudioOutput.AudioOutputListener mOutputListener;
    private AudioEncodeListener mEncodeListener;

    private int mSession;
    private HumlaUDPMessageType mCodec;
    private IEncoder mEncoder;
    private int mFrameCounter;

    private final int mAudioStream;
    private final int mAudioSource;
    private int mSampleRate;
    private int mBitrate;
    private int mFramesPerPacket;
    private final IInputMode mInputMode;
    private final float mAmplitudeBoost;

    private boolean mInitialized;
    /** True if the user is muted on the server. */
    private boolean mMuted;
    private boolean mBluetoothOn;
    private boolean mHalfDuplex;
    private boolean mPreprocessorEnabled;
    /** The last observed talking state. False if muted, or the input mode is not active. */
    private boolean mTalking;
    /** Flag to bypass preprocessing for injected audio (e.g., roger beeps) */
    private boolean mBypassPreprocessing = false;
    /** Flag to block normal audio input processing during audio injection */
    private boolean mIsInjectingAudio = false;
    
    // Gain controls for real-time volume adjustment
    private float mInputGainMultiplier = 2.5f;  // Start with 2.5x
    private float mOutputGainMultiplier = 2.5f; // Start with 2.5x

    private final Object mEncoderLock;
    private byte mTargetId;

    public AudioHandler(Context context, HumlaLogger logger, int audioStream, int audioSource,
                        int sampleRate, int targetBitrate, int targetFramesPerPacket,
                        IInputMode inputMode, byte targetId, float amplitudeBoost,
                        boolean bluetoothEnabled, boolean halfDuplexEnabled,
                        boolean preprocessorEnabled, AudioEncodeListener encodeListener,
                        AudioOutput.AudioOutputListener outputListener) throws AudioInitializationException, NativeAudioException {
        mContext = context;
        mLogger = logger;
        mAudioStream = audioStream;
        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mBitrate = targetBitrate;
        mFramesPerPacket = targetFramesPerPacket;
        mInputMode = inputMode;
        mAmplitudeBoost = amplitudeBoost;
        mBluetoothOn = bluetoothEnabled;
        mHalfDuplex = halfDuplexEnabled;
        mPreprocessorEnabled = preprocessorEnabled;
        mEncodeListener = encodeListener;
        mOutputListener = outputListener;
        mTalking = false;
        mTargetId = targetId;

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mEncoderLock = new Object();

        mInput = new AudioInput(this, mAudioSource, mSampleRate);
        mOutput = new AudioOutput(mOutputListener);
        
        // Set initial normal gain (1.0x = no amplification)
        mInput.setInputGain(1.0f);
        mOutput.setOutputGain(1.0f);
        Log.i(TAG, "AudioHandler created with initial gain 1.0x");
    }

    /**
     * Starts the audio output and input threads.
     * Will create both the input and output modules if they haven't been created yet.
     */
    public synchronized void initialize(User self, int maxBandwidth, HumlaUDPMessageType codec) throws AudioException {
        if(mInitialized) return;
        mSession = self.getSession();

        setMaxBandwidth(maxBandwidth);
        setCodec(codec);
        setServerMuted(self.isMuted() || self.isLocalMuted() || self.isSuppressed());
        startRecording();
        // Ensure that if a bluetooth SCO connection is active, we use the VOICE_CALL stream.
        // This is required by Android for compatibility with SCO.
        mOutput.startPlaying(mBluetoothOn ? AudioManager.STREAM_VOICE_CALL : mAudioStream);

        mInitialized = true;
    }

    /**
     * Starts a recording AudioInput thread.
     * @throws AudioException if the input thread failed to initialize, or if a thread was already
     *                        recording.
     */
    private void startRecording() throws AudioException {
        synchronized (mInput) {
            if (!mInput.isRecording()) {
                mInput.startRecording();
            } else {
                throw new AudioException("Attempted to start recording while recording!");
            }
        }
    }

    /**
     * Stops the recording AudioInput thread.
     * @throws AudioException if there was no thread recording.
     */
    private void stopRecording() throws AudioException {
        synchronized (mInput) {
            if (mInput.isRecording()) {
                mInput.stopRecording();
            } else {
                throw new AudioException("Attempted to stop recording while not recording!");
            }
        }
    }

    /**
     * Sets whether or not the server wants the client muted.
     * @param muted Whether the user is muted on the server.
     */
    private void setServerMuted(boolean muted) throws AudioException {
        mMuted = muted;
    }

    /**
     * Returns whether or not the handler has been initialized.
     * @return true if the handler is ready to play and record audio.
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isPlaying() {
        synchronized (mOutput) {
            return mOutput.isPlaying();
        }
    }

    /**
     * Inject external audio data (e.g., roger beep) to be transmitted to Mumble.
     * This bypasses the microphone input and directly encodes and sends the audio.
     * 
     * @param audioData The PCM audio samples to transmit (16-bit signed, 48kHz)
     * @return true if the audio was successfully queued for transmission
     */
    public boolean injectAudioData(short[] audioData) {
        if (!mInitialized || audioData == null || audioData.length == 0) {
            Log.w(TAG, "Cannot inject audio: initialized=" + mInitialized + ", audioData=" + (audioData == null ? "null" : audioData.length + " samples"));
            return false;
        }

        Log.i(TAG, "Starting audio injection: " + audioData.length + " samples (" + (audioData.length / 48) + "ms at 48kHz)");

        // Save the original talking state to restore it later
        boolean wasTalking = mTalking;
        
        // Set flag to block normal audio input processing
        mIsInjectingAudio = true;
        
        try {
            // Force talking state for injection
            if (!mTalking) {
                mTalking = true;
                mEncodeListener.onTalkingStateChanged(true);
                Log.d(TAG, "Set talking state to TRUE for audio injection (was: " + wasTalking + ")");
            }
            
            // Bypass preprocessing for injected audio (pure tones shouldn't be filtered)
            mBypassPreprocessing = true;
            if (mEncoder instanceof se.lublin.humla.audio.encoder.PreprocessingEncoder) {
                ((se.lublin.humla.audio.encoder.PreprocessingEncoder) mEncoder).setBypass(true);
                Log.i(TAG, "Disabled audio preprocessing for clean tone transmission");
            }
            
            // Process audio in frames matching the encoder's frame size
            // FRAME_SIZE = 480 samples = 10ms of audio at 48kHz
            int offset = 0;
            int frameCount = 0;
            final int FRAME_DURATION_MS = 10; // Each frame is 10ms
            
            while (offset < audioData.length) {
                long frameStartTime = System.currentTimeMillis();
                
                int remaining = audioData.length - offset;
                int currentFrameSize = Math.min(FRAME_SIZE, remaining);
                
                // Create frame buffer (pad with silence if needed)
                short[] frame = new short[FRAME_SIZE];
                System.arraycopy(audioData, offset, frame, 0, currentFrameSize);
                
                // Encode the frame (preprocessing will be bypassed)
                synchronized (mEncoderLock) {
                    if (mEncoder != null) {
                        try {
                            mEncoder.encode(frame, currentFrameSize);
                            mFrameCounter++; // Increment frame counter for packet sequencing
                            frameCount++;
                            
                            // Check if encoder is ready to send (buffer full)
                            if (mEncoder.isReady()) {
                                sendEncodedAudio();
                                Log.d(TAG, "Sent encoded audio packet after frame " + frameCount);
                            }
                        } catch (NativeAudioException e) {
                            Log.e(TAG, "Error encoding injected audio frame", e);
                            mBypassPreprocessing = false;
                            return false;
                        }
                    } else {
                        Log.e(TAG, "Encoder is null, cannot encode frame");
                        mBypassPreprocessing = false;
                        return false;
                    }
                }
                
                offset += currentFrameSize;
                
                // Pace the frames - sleep to match real-time audio transmission
                // Only pace if we have more frames to send
                if (offset < audioData.length) {
                    long frameProcessTime = System.currentTimeMillis() - frameStartTime;
                    long sleepTime = FRAME_DURATION_MS - frameProcessTime;
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Frame pacing interrupted", e);
                            Thread.currentThread().interrupt();
                            mBypassPreprocessing = false;
                            return false;
                        }
                    }
                }
            }
            
            // Send any remaining buffered audio
            synchronized (mEncoderLock) {
                if (mEncoder != null && mEncoder.getBufferedFrames() > 0) {
                    try {
                        mEncoder.terminate();
                        sendEncodedAudio();
                        Log.d(TAG, "Sent final buffered audio");
                    } catch (NativeAudioException e) {
                        Log.e(TAG, "Error sending final buffered audio", e);
                    }
                }
            }
            
            // Re-enable preprocessing
            mBypassPreprocessing = false;
            if (mEncoder instanceof se.lublin.humla.audio.encoder.PreprocessingEncoder) {
                ((se.lublin.humla.audio.encoder.PreprocessingEncoder) mEncoder).setBypass(false);
                Log.i(TAG, "Re-enabled audio preprocessing");
            }
            
            // Clear injection flag to allow normal audio input processing
            mIsInjectingAudio = false;
            
            // Restore original talking state
            if (wasTalking != mTalking) {
                mTalking = wasTalking;
                mEncodeListener.onTalkingStateChanged(wasTalking);
                Log.d(TAG, "Restored talking state to: " + wasTalking);
            }
            
            Log.i(TAG, "Successfully injected " + audioData.length + " audio samples in " + frameCount + " frames");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error injecting audio data", e);
            
            // Re-enable preprocessing on error
            mBypassPreprocessing = false;
            if (mEncoder instanceof se.lublin.humla.audio.encoder.PreprocessingEncoder) {
                ((se.lublin.humla.audio.encoder.PreprocessingEncoder) mEncoder).setBypass(false);
                Log.i(TAG, "Re-enabled audio preprocessing after error");
            }
            
            // Clear injection flag on error
            mIsInjectingAudio = false;
            
            // Restore original talking state on error
            if (wasTalking != mTalking) {
                mTalking = wasTalking;
                mEncodeListener.onTalkingStateChanged(wasTalking);
                Log.d(TAG, "Restored talking state to: " + wasTalking + " after error");
            }
            
            return false;
        }
    }

    public HumlaUDPMessageType getCodec() {
        return mCodec;
    }

    public void recreateEncoder() throws NativeAudioException {
        setCodec(mCodec);
    }

    public void setCodec(HumlaUDPMessageType codec) throws NativeAudioException {
        mCodec = codec;

        if (mEncoder != null) {
            mEncoder.destroy();
            mEncoder = null;
        }

        if (codec == null) {
            Log.w(TAG, "setCodec(null) Input disabled.");
            return;
        }

        IEncoder encoder;
        switch (codec) {
            case UDPVoiceCELTAlpha:
                encoder = new CELT7Encoder(SAMPLE_RATE, AudioHandler.FRAME_SIZE, 1,
                        mFramesPerPacket, mBitrate, MAX_BUFFER_SIZE);
                break;
            case UDPVoiceCELTBeta:
                encoder = new CELT11Encoder(SAMPLE_RATE, 1, mFramesPerPacket);
                break;
            case UDPVoiceOpus:
                encoder = new OpusEncoder(SAMPLE_RATE, 1, FRAME_SIZE, mFramesPerPacket, mBitrate,
                        MAX_BUFFER_SIZE);
                break;
            default:
                Log.w(TAG, "Unsupported codec, input disabled.");
                return;
        }

        if (mPreprocessorEnabled) {
            encoder = new PreprocessingEncoder(encoder, FRAME_SIZE, SAMPLE_RATE);
        }

        if (mInput.getSampleRate() != SAMPLE_RATE) {
            encoder = new ResamplingEncoder(encoder, 1, mInput.getSampleRate(), FRAME_SIZE, SAMPLE_RATE);
        }

        mEncoder = encoder;
    }

    public int getAudioStream() {
        return mAudioStream;
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getBitrate() {
        return mBitrate;
    }

    /**
     * Sets the maximum bandwidth available for audio input as obtained from the server.
     * Adjusts the bitrate and frames per packet accordingly to meet the server's requirement.
     * @param maxBandwidth The server-reported maximum bandwidth, in bps.
     */
    private void setMaxBandwidth(int maxBandwidth) throws AudioException {
        if (maxBandwidth == -1) {
            return;
        }
        int bitrate = mBitrate;
        int framesPerPacket = mFramesPerPacket;
        // Logic as per desktop Mumble's AudioInput::adjustBandwidth for consistency.
        if (HumlaConnection.calculateAudioBandwidth(bitrate, framesPerPacket) > maxBandwidth) {
            if (framesPerPacket <= 4 && maxBandwidth <= 32000) {
                framesPerPacket = 4;
            } else if (framesPerPacket == 1 && maxBandwidth <= 64000) {
                framesPerPacket = 2;
            } else if (framesPerPacket == 2 && maxBandwidth <= 48000) {
                framesPerPacket = 4;
            }
            while (HumlaConnection.calculateAudioBandwidth(bitrate, framesPerPacket)
                    > maxBandwidth && bitrate > 8000) {
                bitrate -= 1000;
            }
        }
        bitrate = Math.max(8000, bitrate);

        if (bitrate != mBitrate ||
                framesPerPacket != mFramesPerPacket) {
            mBitrate = bitrate;
            mFramesPerPacket = framesPerPacket;

            mLogger.logInfo(mContext.getString(R.string.audio_max_bandwidth,
                    maxBandwidth/1000, maxBandwidth/1000, framesPerPacket * 10));
        }
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    /**
     * Returns whether or not the audio handler is operating in half duplex mode, muting outgoing
     * audio when incoming audio is received.
     * @return true if the handler is in half duplex mode.
     */
    public boolean isHalfDuplex() {
        return mHalfDuplex;
    }

    public int getCurrentBandwidth() {
        return HumlaConnection.calculateAudioBandwidth(mBitrate, mFramesPerPacket);
    }

    /**
     * Shuts down the audio handler, halting input and output.
     */
    public synchronized void shutdown() {
        synchronized (mInput) {
            mInput.shutdown();
        }
        synchronized (mOutput) {
            mOutput.stopPlaying();
        }
        synchronized (mEncoderLock) {
            if (mEncoder != null) {
                mEncoder.destroy();
                mEncoder = null;
            }
        }
        mInitialized = false;
        mBluetoothOn = false;

        mEncodeListener.onTalkingStateChanged(false);
    }


    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
        if (!mInitialized)
            return; // Only listen to change events in this handler.

        HumlaUDPMessageType codec;
        if (msg.hasOpus() && msg.getOpus()) {
            codec = HumlaUDPMessageType.UDPVoiceOpus;
        } else if (msg.hasBeta() && !msg.getPreferAlpha()) {
            codec = HumlaUDPMessageType.UDPVoiceCELTBeta;
        } else {
            codec = HumlaUDPMessageType.UDPVoiceCELTAlpha;
        }

        if (codec != mCodec) {
            try {
                synchronized (mEncoderLock) {
                    setCodec(codec);
                }
            } catch (NativeAudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {
        try {
            setMaxBandwidth(msg.hasMaxBandwidth() ? msg.getMaxBandwidth() : -1);
        } catch (AudioException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        if (!mInitialized)
            return; // We shouldn't initialize on UserState- wait for ServerSync.

        // Stop audio input if the user is muted, and resume if the user has set talking enabled.
        if (msg.hasSession() && msg.getSession() == mSession &&
                (msg.hasMute() || msg.hasSelfMute() || msg.hasSuppress())) {
            try {
                setServerMuted(msg.getMute() || msg.getSelfMute() || msg.getSuppress());
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageVoiceData(byte[] data, HumlaUDPMessageType messageType) {
        synchronized (mOutput) {
            mOutput.queueVoiceData(data, messageType);
        }
    }

    @Override
    public void onAudioInputReceived(short[] frame, int frameSize) {
        // Block normal audio input processing during roger beep injection
        if (mIsInjectingAudio) {
            // Silently drop mic input during injection to prevent audio mixing
            return;
        }
        
        boolean talking = mInputMode.shouldTransmit(frame, frameSize);
        talking &= !mMuted;

        if (mTalking ^ talking) {
            mEncodeListener.onTalkingStateChanged(talking);
            if (mHalfDuplex) {
                mAudioManager.setStreamMute(getAudioStream(), talking);
            }

            synchronized (mEncoderLock) {
                // Terminate encoding when talking stops.
                if (!talking && mEncoder != null) {
                    try {
                        mEncoder.terminate();
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (talking) {
            // Apply input gain first (user-controlled volume) - THIS IS THE REAL-TIME CONTROL!
            if (mInputGainMultiplier != 1.0f) {
                for (int i = 0; i < frameSize; i++) {
                    float val = frame[i] * mInputGainMultiplier;
                    if (val > Short.MAX_VALUE) {
                        val = Short.MAX_VALUE;
                    } else if (val < Short.MIN_VALUE) {
                        val = Short.MIN_VALUE;
                    }
                    frame[i] = (short) val;
                }
                // Log occasionally
                if (mFrameCounter % 100 == 0) {
                    Log.i(TAG, "Applied INPUT gain: " + mInputGainMultiplier + "x");
                }
            }
            
            // Boost/reduce amplitude based on user preference
            // TODO: perhaps amplify to the largest value that does not result in clipping.
            if (mAmplitudeBoost != 1.0f) {
                for (int i = 0; i < frameSize; i++) {
                    // Java only guarantees the bounded preservation of sign in a narrowing
                    // primitive conversion from float -> int, not float -> int -> short.
                    float val = frame[i] * mAmplitudeBoost;
                    if (val > Short.MAX_VALUE) {
                        val = Short.MAX_VALUE;
                    } else if (val < Short.MIN_VALUE) {
                        val = Short.MIN_VALUE;
                    }
                    frame[i] = (short) val;
                }
            }

            synchronized (mEncoderLock) {
                if (mEncoder != null) {
                    try {
                        mEncoder.encode(frame, frameSize);
                        mFrameCounter++;
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        synchronized (mEncoderLock) {
            if (mEncoder != null && mEncoder.isReady()) {
                sendEncodedAudio();
            }
        }

        mTalking = talking;
        if (!talking) {
            mInputMode.waitForInput();
        }
    }

    public void setVoiceTargetId(byte id) {
        mTargetId = id;
    }

    public void clearVoiceTarget() {
        // A target ID of 0 indicates normal talking.
        mTargetId = 0;
    }
    
    /**
     * Sets the input gain (microphone gain multiplier).
     * @param gain The gain multiplier (0.5 to 5.0 recommended).
     */
    public void setInputGain(float gain) {
        mInputGainMultiplier = Math.max(0.1f, Math.min(gain, 5.0f));
        if (mInput != null) {
            mInput.setInputGain(gain);
        }
    }
    
    public void setMicBoost(boolean enabled) {
        if (mInput != null) {
            mInput.setMicBoost(enabled);
        }
    }
    
    public void setOutputGain(float gain) {
        mOutputGainMultiplier = Math.max(0.1f, Math.min(gain, 5.0f));
        if (mOutput != null) {
            mOutput.setOutputGain(gain);
        }
    }

    /**
     * Fetches the buffered audio from the current encoder and sends it to the server.
     */
    private void sendEncodedAudio() {
        int frames = mEncoder.getBufferedFrames();

        int flags = 0;
        flags |= mCodec.ordinal() << 5;
        flags |= mTargetId & 0x1F;

        final byte[] packetBuffer = new byte[1024];
        packetBuffer[0] = (byte) (flags & 0xFF);

        PacketBuffer ds = new PacketBuffer(packetBuffer, 1024);
        ds.skip(1);
        ds.writeLong(mFrameCounter - frames);
        mEncoder.getEncodedData(ds);
        int length = ds.size();
        ds.rewind();

        byte[] packet = ds.dataBlock(length);
        mEncodeListener.onAudioEncoded(packet, length);
    }

    public interface AudioEncodeListener {
        void onAudioEncoded(byte[] data, int length);
        void onTalkingStateChanged(boolean talking);
    }

    /**
     * A builder to configure and instantiate the audio protocol handler.
     */
    public static class Builder {
        private Context mContext;
        private HumlaLogger mLogger;
        private int mAudioStream;
        private int mAudioSource;
        private int mTargetBitrate;
        private int mTargetFramesPerPacket;
        private int mInputSampleRate;
        private float mAmplitudeBoost;
        private boolean mBluetoothEnabled;
        private boolean mHalfDuplexEnabled;
        private boolean mPreprocessorEnabled;
        private IInputMode mInputMode;
        private AudioEncodeListener mEncodeListener;
        private AudioOutput.AudioOutputListener mTalkingListener;

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public Builder setLogger(HumlaLogger logger) {
            mLogger = logger;
            return this;
        }

        public Builder setAudioStream(int audioStream) {
            mAudioStream = audioStream;
            return this;
        }

        public Builder setAudioSource(int audioSource) {
            mAudioSource = audioSource;
            return this;
        }

        public Builder setTargetBitrate(int targetBitrate) {
            mTargetBitrate = targetBitrate;
            return this;
        }

        public Builder setTargetFramesPerPacket(int targetFramesPerPacket) {
            mTargetFramesPerPacket = targetFramesPerPacket;
            return this;
        }

        public Builder setInputSampleRate(int inputSampleRate) {
            mInputSampleRate = inputSampleRate;
            return this;
        }

        public Builder setAmplitudeBoost(float amplitudeBoost) {
            mAmplitudeBoost = amplitudeBoost;
            return this;
        }

        public Builder setBluetoothEnabled(boolean bluetoothEnabled) {
            mBluetoothEnabled = bluetoothEnabled;
            return this;
        }

        public Builder setHalfDuplexEnabled(boolean halfDuplexEnabled) {
            mHalfDuplexEnabled = halfDuplexEnabled;
            return this;
        }

        public Builder setPreprocessorEnabled(boolean preprocessorEnabled) {
            mPreprocessorEnabled = preprocessorEnabled;
            return this;
        }

        public Builder setEncodeListener(AudioEncodeListener encodeListener) {
            mEncodeListener = encodeListener;
            return this;
        }

        public Builder setTalkingListener(AudioOutput.AudioOutputListener talkingListener) {
            mTalkingListener = talkingListener; // TODO: remove user dependency from AudioOutput
            return this;
        }

        public Builder setInputMode(IInputMode inputMode) {
            mInputMode = inputMode;
            return this;
        }

        /**
         * Creates a new AudioHandler for the given session and begins managing input/output.
         * @return An initialized audio handler.
         */
        public AudioHandler initialize(User self, int maxBandwidth, HumlaUDPMessageType codec, byte targetId) throws AudioException {
            AudioHandler handler = new AudioHandler(mContext, mLogger, mAudioStream, mAudioSource,
                    mInputSampleRate, mTargetBitrate, mTargetFramesPerPacket, mInputMode, targetId,
                    mAmplitudeBoost, mBluetoothEnabled, mHalfDuplexEnabled,
                    mPreprocessorEnabled, mEncodeListener, mTalkingListener);
            handler.initialize(self, maxBandwidth, codec);
            return handler;
        }
    }
}
