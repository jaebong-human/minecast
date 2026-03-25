package com.minecast.client.audio;

import javazoom.jl.decoder.*;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class AudioPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioPlayer.class);

    private volatile int alSource = -1;
    private volatile int alBuffer = -1;
    private volatile Thread playThread = null;

    public void play(byte[] mp3Bytes) {
        stopAndCleanup();

        Thread thread = new Thread(() -> {
            try {
                byte[] pcm = decodeMp3ToPcm(mp3Bytes);
                int sampleRate = getSampleRate(mp3Bytes);
                playPcm(pcm, sampleRate);
            } catch (Exception e) {
                LOGGER.warn("[MineCast] MP3 재생 실패: {}", e.getMessage());
            }
        }, "minecast-audio");
        thread.setDaemon(true);
        playThread = thread;
        thread.start();
    }

    private byte[] decodeMp3ToPcm(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        try {
            Header frame;
            while ((frame = bitstream.readFrame()) != null) {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                short[] samples = output.getBuffer();
                for (short s : samples) {
                    pcmOut.write(s & 0xFF);
                    pcmOut.write((s >> 8) & 0xFF);
                }
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }
        return pcmOut.toByteArray();
    }

    private int getSampleRate(byte[] mp3) throws Exception {
        Bitstream bs = new Bitstream(new ByteArrayInputStream(mp3));
        try {
            Header header = bs.readFrame();
            return header != null ? (int) header.frequency() : 44100;
        } finally {
            bs.close();
        }
    }

    private void playPcm(byte[] pcm, int sampleRate) {
        int buf = AL10.alGenBuffers();
        alBuffer = buf;
        ByteBuffer byteBuf = ByteBuffer.allocateDirect(pcm.length);
        byteBuf.put(pcm).flip();
        AL10.alBufferData(buf, AL10.AL_FORMAT_STEREO16, byteBuf, sampleRate);

        int src = AL10.alGenSources();
        alSource = src;
        AL10.alSourcei(src, AL10.AL_BUFFER, buf);
        AL10.alSourcePlay(src);

        int state;
        do {
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            state = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
        } while (state == AL10.AL_PLAYING);

        cleanupSource(src, buf);
    }

    private synchronized void cleanupSource(int src, int buf) {
        if (src != -1 && alSource == src) {
            AL10.alSourceStop(src);
            AL10.alDeleteSources(src);
            alSource = -1;
        }
        if (buf != -1 && alBuffer == buf) {
            AL10.alDeleteBuffers(buf);
            alBuffer = -1;
        }
    }

    public synchronized void stopAndCleanup() {
        Thread t = playThread;
        if (t != null) {
            t.interrupt();
            playThread = null;
        }
        int src = alSource;
        int buf = alBuffer;
        if (src != -1) {
            AL10.alSourceStop(src);
            AL10.alDeleteSources(src);
            alSource = -1;
        }
        if (buf != -1) {
            AL10.alDeleteBuffers(buf);
            alBuffer = -1;
        }
    }
}
