package ai.jaebong.minecast.server.audio;

import ai.jaebong.minecast.server.network.MinecastPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public class AudioBroadcaster {
    private static final byte TYPE_START = 0x00;
    private static final byte TYPE_CHUNK = 0x01;
    private static final byte TYPE_END   = 0x02;

    public void sendToPlayer(ServerPlayer player, byte[] mp3Bytes, List<byte[]> chunks) {
        try {
            if (!ServerPlayNetworking.canSend(player, MinecastPayload.TYPE)) return;

            // START
            ByteArrayOutputStream startBuf = new ByteArrayOutputStream();
            DataOutputStream startOut = new DataOutputStream(startBuf);
            startOut.writeByte(TYPE_START);
            startOut.writeInt(mp3Bytes.length);
            startOut.writeInt(chunks.size());
            ServerPlayNetworking.send(player, new MinecastPayload(startBuf.toByteArray()));

            // CHUNKs
            for (int i = 0; i < chunks.size(); i++) {
                if (!ServerPlayNetworking.canSend(player, MinecastPayload.TYPE)) return;
                byte[] chunk = chunks.get(i);
                ByteArrayOutputStream chunkBuf = new ByteArrayOutputStream();
                DataOutputStream chunkOut = new DataOutputStream(chunkBuf);
                chunkOut.writeByte(TYPE_CHUNK);
                chunkOut.writeInt(i);
                chunkOut.write(chunk);
                ServerPlayNetworking.send(player, new MinecastPayload(chunkBuf.toByteArray()));
            }

            // END
            if (!ServerPlayNetworking.canSend(player, MinecastPayload.TYPE)) return;
            ServerPlayNetworking.send(player, new MinecastPayload(new byte[]{TYPE_END}));

        } catch (IOException e) {
            throw new RuntimeException("패킷 빌드 실패", e);
        }
    }
}
