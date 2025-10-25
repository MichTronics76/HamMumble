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

package se.lublin.humla.audio;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import se.lublin.humla.exception.AudioInitializationException;
import se.lublin.humla.exception.NativeAudioException;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.model.User;
import se.lublin.humla.net.HumlaUDPMessageType;
import se.lublin.humla.net.PacketBuffer;
import se.lublin.humla.protocol.AudioHandler;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutput implements Runnable, AudioOutputSpeech.TalkStateListener {
    private static final String TAG = AudioOutput.class.getName();

    private Map<Integer, AudioOutputSpeech> mAudioOutputs = new HashMap<>();
    private AudioTrack mAudioTrack;
    private int mBufferSize;
    private Thread mThread;
    private final Object mInactiveLock = new Object();
    private final Lock mPacketLock;
    private boolean mRunning = false;
    private Handler mMainHandler;
    private AudioOutputListener mListener;
    private final IAudioMixer<float[], short[]> mMixer;
    private ExecutorService mDecodeExecutorService;
    private float mOutputGain = 1.0f;

    public AudioOutput(AudioOutputListener listener) {
        mListener = listener;
        mMainHandler = new Handler(Looper.getMainLooper());
        mDecodeExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mPacketLock = new ReentrantLock();
        mMixer = new BasicClippingShortMixer();
    }

    public Thread startPlaying(int audioStream) throws AudioInitializationException {
        if (mThread != null || mRunning)
            return null;

        int minBufferSize = AudioTrack.getMinBufferSize(AudioHandler.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mBufferSize = Math.min(minBufferSize, AudioHandler.FRAME_SIZE * 12);
        Log.v(TAG, "Using buffer size " + mBufferSize + ", system's min buffer size: " + minBufferSize);

        try {
            mAudioTrack = new AudioTrack(audioStream,
                    AudioHandler.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    mBufferSize,
                    AudioTrack.MODE_STREAM);
        } catch (IllegalArgumentException e) {
            throw new AudioInitializationException(e);
        }

        mThread = new Thread(this);
        mThread.start();
        return mThread;
    }

    public void stopPlaying() {
        if(!mRunning)
            return;

        mRunning = false;
        synchronized (mInactiveLock) {
            mInactiveLock.notify(); // Wake inactive lock if active
        }
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mThread = null;

        mPacketLock.lock();
        for(AudioOutputSpeech speech : mAudioOutputs.values()) {
            speech.destroy();
        }
        mPacketLock.unlock();

        mAudioOutputs.clear();
        mAudioTrack.release();
        mAudioTrack = null;
    }

    public boolean isPlaying() {
        return mRunning;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        mRunning = true;
        mAudioTrack.play();

        final short[] mix = new short[mBufferSize];

        while(mRunning) {
            if(fetchAudio(mix, 0, mBufferSize)) {
                mAudioTrack.write(mix, 0, mBufferSize);
            } else {
                synchronized (mInactiveLock) {
                    mAudioTrack.flush();
                    mAudioTrack.pause();

                    try {
                        mInactiveLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    mAudioTrack.play();
                }
            }
        }

        mAudioTrack.flush();
        mAudioTrack.stop();
    }

    /**
     * Fetches audio data from registered audio output users and mixes them into the given buffer.
     * TODO: add priority speaker support.
     * @param buffer The buffer to mix output data into.
     * @param bufferOffset The offset of the
     * @param bufferSize The size of the buffer.
     * @return true if the buffer contains audio data.
     */
    private boolean fetchAudio(short[] buffer, int bufferOffset, int bufferSize) {
        Arrays.fill(buffer, bufferOffset, bufferOffset + bufferSize, (short) 0);
        final List<IAudioMixerSource<float[]>> sources = new ArrayList<>();
        try {
            mPacketLock.lock();
            // Parallelize decoding using a fixed thread pool equal to the number of cores
            List<Future<AudioOutputSpeech.Result>> futureResults =
                    mDecodeExecutorService.invokeAll(mAudioOutputs.values());
            for(Future<AudioOutputSpeech.Result> future : futureResults) {
                AudioOutputSpeech.Result result = future.get();
                if (result.isAlive()) {
                    sources.add(result);
                } else {
                    AudioOutputSpeech speech = result.getSpeechOutput();
                    Log.v(TAG, "Deleted audio user " + speech.getUser().getName());
                    mAudioOutputs.remove(speech.getSession());
                    speech.destroy();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        } finally {
            mPacketLock.unlock();
        }

        if (sources.size() == 0)
            return false;

        mMixer.mix(sources, buffer, bufferOffset, bufferSize);
        
        // Apply output gain
        for (int i = bufferOffset; i < bufferOffset + bufferSize; i++) {
            float sample = buffer[i] * mOutputGain;
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            buffer[i] = (short) sample;
        }
        
        return true;
    }

    public void queueVoiceData(byte[] data, HumlaUDPMessageType messageType) {
        if(!mRunning)
            return;

        byte msgFlags = (byte) (data[0] & 0x1f);
        PacketBuffer pds = new PacketBuffer(data, data.length);
        pds.skip(1);
        int session = (int) pds.readLong();
        User user = mListener.getUser(session);
        if(user != null && !user.isLocalMuted()) {
            // TODO check for whispers here
            int seq = (int) pds.readLong();

            // Synchronize so we don't destroy an output while we add a buffer to it.
            mPacketLock.lock();
            AudioOutputSpeech aop = mAudioOutputs.get(session);
            if(aop != null && aop.getCodec() != messageType) {
                aop.destroy();
                aop = null;
            }
            if(aop == null) {
                try {
                    aop = new AudioOutputSpeech(user, messageType, mBufferSize, this);
                } catch (NativeAudioException e) {
                    Log.v(TAG, "Failed to create audio user " + user.getName());
                    e.printStackTrace();
                    return;
                }
                Log.v(TAG, "Created audio user " + user.getName());
                mAudioOutputs.put(session, aop);
            }
            mPacketLock.unlock();

            PacketBuffer dataBuffer = new PacketBuffer(pds.bufferBlock(pds.left()));
            aop.addFrameToBuffer(dataBuffer, msgFlags, seq);

            synchronized (mInactiveLock) {
                mInactiveLock.notify();
            }
        }

    }

    @Override
    public void onTalkStateUpdated(final int session, final TalkState state) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                final User user = mListener.getUser(session);
                if(user != null && user.getTalkState() != state) {
                    user.setTalkState(state);
                    mListener.onUserTalkStateUpdated(user);
                }
            }
        });
    }
    
    /**
     * Set the output gain (volume multiplier).
     * @param gain The gain multiplier (0.5 to 3.0 recommended).
     */
    public void setOutputGain(float gain) {
        mOutputGain = Math.max(0.1f, Math.min(gain, 5.0f)); // Clamp between 0.1 and 5.0
        Log.v(TAG, "Output gain set to: " + mOutputGain);
    }
    
    /**
     * Set the preferred audio device for output (requires Android M+).
     * This allows explicit routing to a specific audio device (e.g., built-in speaker)
     * to prevent automatic routing that could cause TX/RX crosstalk with USB audio.
     * 
     * @param deviceInfo The AudioDeviceInfo to use for output, or null to use system default
     * @return true if the device was set successfully, false otherwise
     */
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mAudioTrack != null) {
                boolean success = mAudioTrack.setPreferredDevice(deviceInfo);
                if (success && deviceInfo != null) {
                    Log.i(TAG, "Set preferred output device: " + deviceInfo.getProductName() + 
                          " (Type: " + deviceInfo.getType() + ", ID: " + deviceInfo.getId() + ")");
                } else if (success) {
                    Log.i(TAG, "Reset output device to system default");
                } else {
                    Log.w(TAG, "Failed to set preferred output device");
                }
                return success;
            } else {
                Log.w(TAG, "Cannot set preferred device: AudioTrack not initialized");
                return false;
            }
        } else {
            Log.w(TAG, "setPreferredDevice requires Android M (API 23) or higher");
            return false;
        }
    }
    
    /**
     * Get the currently routed audio device (requires Android M+).
     * 
     * @return The currently active AudioDeviceInfo, or null if unknown
     */
    public AudioDeviceInfo getRoutedDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mAudioTrack != null) {
                return mAudioTrack.getRoutedDevice();
            }
        }
        return null;
    }

    public static interface AudioOutputListener {
        /**
         * Called when a user's talking state is changed.
         * @param user The user whose talking state has been modified.
         */
        public void onUserTalkStateUpdated(User user);

        /**
         * Used to set audio-related user data.
         * @return The user for the associated session.
         */
        public User getUser(int session);
    }
}
