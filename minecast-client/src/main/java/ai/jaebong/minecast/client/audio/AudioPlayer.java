package ai.jaebong.minecast.client.audio;

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

    private record AudioData(byte[] pcm, int sampleRate, int channels) {}

    public void play(byte[] mp3Bytes) {
        stopAndCleanup();

        Thread thread = new Thread(() -> {
            try {
                AudioData data = decodeMp3(mp3Bytes);
                playPcm(data);
            } catch (Exception e) {
                LOGGER.warn("[MineCast] MP3 재생 실패: {}", e.getMessage());
            }
        }, "minecast-audio");
        thread.setDaemon(true);
        playThread = thread;
        thread.start();
    }

    private AudioData decodeMp3(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        int sampleRate = 44100;
        int channels = 2;
        try {
            Header frame;
            boolean first = true;
            while ((frame = bitstream.readFrame()) != null) {
                if (first) {
                    sampleRate = (int) frame.frequency();
                    channels = (frame.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                    first = false;
                }
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                short[] samples = output.getBuffer();
                int count = (channels == 1) ? output.getBufferLength() : samples.length;
                for (int i = 0; i < count; i++) {
                    short s = samples[i];
                    pcmOut.write(s & 0xFF);
                    pcmOut.write((s >> 8) & 0xFF);
                }
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }
        return new AudioData(pcmOut.toByteArray(), sampleRate, channels);
    }

    private void playPcm(AudioData data) {
        int buf = AL10.alGenBuffers();
        alBuffer = buf;
        ByteBuffer byteBuf = ByteBuffer.allocateDirect(data.pcm().length);
        byteBuf.put(data.pcm()).flip();
        int format = (data.channels() == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        AL10.alBufferData(buf, format, byteBuf, data.sampleRate());
        LOGGER.debug("[MineCast] 재생: {}Hz {}ch {}bytes", data.sampleRate(), data.channels(), data.pcm().length);

        int src = AL10.alGenSources();
        alSource = src;
        AL10.alSourcei(src, AL10.AL_BUFFER, buf);
        AL10.alSourcef(src, AL10.AL_GAIN, 4.0f);
        AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(src, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0f);
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
        cleanupSource(alSource, alBuffer);
    }
}
