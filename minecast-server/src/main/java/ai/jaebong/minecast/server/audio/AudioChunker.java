package ai.jaebong.minecast.server.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioChunker {
    public static final int CHUNK_SIZE = 32 * 1024; // 32KB

    public List<byte[]> split(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            chunks.add(Arrays.copyOfRange(data, offset, offset + length));
            offset += length;
        }
        return chunks;
    }
}
