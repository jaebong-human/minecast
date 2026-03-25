package com.minecast.client.audio;

import java.io.ByteArrayOutputStream;

public class AudioBuffer {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private int expectedChunkCount;
    private int nextExpectedIndex;
    private int receivedChunkCount;

    public AudioBuffer(int totalBytes, int chunkCount) {
        this.expectedChunkCount = chunkCount;
        this.nextExpectedIndex = 0;
        this.receivedChunkCount = 0;
    }

    public boolean addChunk(int index, byte[] data) {
        if (index != nextExpectedIndex) {
            clear();
            return true;
        }
        stream.write(data, 0, data.length);
        nextExpectedIndex++;
        receivedChunkCount++;
        return false;
    }

    public boolean isComplete(int expectedCount) {
        return receivedChunkCount == expectedCount;
    }

    public byte[] toByteArray() {
        return stream.toByteArray();
    }

    public int getReceivedChunkCount() {
        return receivedChunkCount;
    }

    public int getExpectedChunkCount() {
        return expectedChunkCount;
    }

    public void reset(int totalBytes, int chunkCount) {
        clear();
        this.expectedChunkCount = chunkCount;
    }

    private void clear() {
        stream.reset();
        nextExpectedIndex = 0;
        receivedChunkCount = 0;
    }
}
