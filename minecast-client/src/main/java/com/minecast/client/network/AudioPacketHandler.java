package com.minecast.client.network;

import com.minecast.client.audio.AudioBuffer;
import com.minecast.client.audio.AudioPlayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class AudioPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioPacketHandler.class);

    private static final byte TYPE_START = 0x00;
    private static final byte TYPE_CHUNK = 0x01;
    private static final byte TYPE_END   = 0x02;

    private final AudioPlayer player = new AudioPlayer();
    private AudioBuffer buffer;

    public void register() {
        ClientPlayNetworking.registerGlobalReceiver(MinecastPayload.TYPE,
            (payload, context) -> {
                try {
                    handlePacket(payload.data());
                } catch (IOException e) {
                    LOGGER.warn("[MineCast] 패킷 파싱 오류: {}", e.getMessage());
                }
            });
    }

    private void handlePacket(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte type = in.readByte();

        switch (type) {
            case TYPE_START -> {
                int totalBytes = in.readInt();
                int chunkCount = in.readInt();
                if (buffer == null) {
                    buffer = new AudioBuffer(totalBytes, chunkCount);
                } else {
                    buffer.reset(totalBytes, chunkCount);
                }
                player.stopAndCleanup();
                LOGGER.debug("[MineCast] START: {}bytes, {}chunks", totalBytes, chunkCount);
            }
            case TYPE_CHUNK -> {
                if (buffer == null) return;
                int index = in.readInt();
                byte[] chunkData = in.readAllBytes();
                boolean reset = buffer.addChunk(index, chunkData);
                if (reset) {
                    LOGGER.warn("[MineCast] 청크 순서 오류 (index={}), 버퍼 리셋", index);
                }
            }
            case TYPE_END -> {
                if (buffer == null) return;
                if (!buffer.isComplete(buffer.getExpectedChunkCount())) {
                    LOGGER.warn("[MineCast] 청크 수 불일치 (수신={}, 기대={}), 재생 스킵",
                        buffer.getReceivedChunkCount(), buffer.getExpectedChunkCount());
                    buffer = null;
                    return;
                }
                byte[] mp3 = buffer.toByteArray();
                buffer = null;
                player.play(mp3);
                LOGGER.debug("[MineCast] 재생 시작 ({}bytes)", mp3.length);
            }
            default -> LOGGER.warn("[MineCast] 알 수 없는 패킷 타입: 0x{}", String.format("%02X", type));
        }
    }
}
