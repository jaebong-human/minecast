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

    private int alSource = -1;
    private int alBuffer = -1;

    /**
     * MP3 bytes를 디코딩하여 OpenAL로 재생한다.
     * 이전 재생 중이면 중단하고 새로 시작한다.
     */
    public void play(byte[] mp3Bytes) {
        stopAndCleanup();

        Thread playThread = new Thread(() -> {
            try {
                byte[] pcm = decodeMp3ToPcm(mp3Bytes);
                int sampleRate = getSampleRate(mp3Bytes);
                playPcm(pcm, sampleRate);
            } catch (Exception e) {
                LOGGER.warn("[MineCast] MP3 재생 실패: {}", e.getMessage());
            }
        }, "minecast-audio");
        playThread.setDaemon(true);
        playThread.start();
    }

    private byte[] decodeMp3ToPcm(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();

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
        return pcmOut.toByteArray();
    }

    private int getSampleRate(byte[] mp3) throws Exception {
        Bitstream bs = new Bitstream(new ByteArrayInputStream(mp3));
        Header header = bs.readFrame();
        return header != null ? (int) header.frequency() : 44100;
    }

    private void playPcm(byte[] pcm, int sampleRate) {
        alBuffer = AL10.alGenBuffers();
        ByteBuffer buf = ByteBuffer.allocateDirect(pcm.length);
        buf.put(pcm).flip();
        AL10.alBufferData(alBuffer, AL10.AL_FORMAT_STEREO16, buf, sampleRate);

        alSource = AL10.alGenSources();
        AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
        AL10.alSourcePlay(alSource);

        int state;
        do {
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        } while (state == AL10.AL_PLAYING);

        stopAndCleanup();
    }

    public void stopAndCleanup() {
        if (alSource != -1) {
            AL10.alSourceStop(alSource);
            AL10.alDeleteSources(alSource);
            alSource = -1;
        }
        if (alBuffer != -1) {
            AL10.alDeleteBuffers(alBuffer);
            alBuffer = -1;
        }
    }
}
