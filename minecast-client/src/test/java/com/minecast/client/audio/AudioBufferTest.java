package com.minecast.client.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioBufferTest {

    @Test
    void normalFlow_returnsCompleteAudio() {
        AudioBuffer buffer = new AudioBuffer(100, 2);
        buffer.addChunk(0, new byte[]{1, 2, 3});
        buffer.addChunk(1, new byte[]{4, 5});

        assertTrue(buffer.isComplete(2));
        byte[] result = buffer.toByteArray();
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, result);
    }

    @Test
    void outOfOrderChunk_resetsBuffer() {
        AudioBuffer buffer = new AudioBuffer(100, 3);
        buffer.addChunk(0, new byte[]{1});
        boolean reset = buffer.addChunk(2, new byte[]{3}); // index 1 건너뜀

        assertTrue(reset, "순서 오류 시 reset=true 반환해야 함");
    }

    @Test
    void wrongChunkCount_notComplete() {
        AudioBuffer buffer = new AudioBuffer(100, 3);
        buffer.addChunk(0, new byte[]{1});
        buffer.addChunk(1, new byte[]{2});

        assertFalse(buffer.isComplete(3)); // 아직 2개만 받음
    }

    @Test
    void resetAfterStart_clearsState() {
        AudioBuffer buffer = new AudioBuffer(100, 2);
        buffer.addChunk(0, new byte[]{1, 2});
        buffer.reset(200, 3);  // 새 START

        assertEquals(0, buffer.getReceivedChunkCount());
    }
}
