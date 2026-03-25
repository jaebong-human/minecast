package ai.jaebong.minecast.server.audio;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AudioChunkerTest {
    private final AudioChunker chunker = new AudioChunker();

    @Test
    void smallData_returnsSingleChunk() {
        byte[] data = new byte[100];
        List<byte[]> chunks = chunker.split(data);
        assertEquals(1, chunks.size());
        assertArrayEquals(data, chunks.get(0));
    }

    @Test
    void exactChunkSize_returnsOneChunk() {
        byte[] data = new byte[AudioChunker.CHUNK_SIZE];
        List<byte[]> chunks = chunker.split(data);
        assertEquals(1, chunks.size());
    }

    @Test
    void largeData_splitsIntoChunks() {
        byte[] data = new byte[AudioChunker.CHUNK_SIZE + 1];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 127);
        List<byte[]> chunks = chunker.split(data);
        assertEquals(2, chunks.size());
        assertEquals(AudioChunker.CHUNK_SIZE, chunks.get(0).length);
        assertEquals(1, chunks.get(1).length);
        assertEquals(data[0], chunks.get(0)[0]);
        assertEquals(data[AudioChunker.CHUNK_SIZE], chunks.get(1)[0]);
    }

    @Test
    void emptyData_returnsEmptyList() {
        List<byte[]> chunks = chunker.split(new byte[0]);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void reconstructed_matchesOriginal() {
        byte[] data = new byte[80_000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 200);
        List<byte[]> chunks = chunker.split(data);
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] reconstructed = new byte[total];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, reconstructed, pos, chunk.length);
            pos += chunk.length;
        }
        assertArrayEquals(data, reconstructed);
    }
}
