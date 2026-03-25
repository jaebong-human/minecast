package com.minecast.server.audio;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;

import java.util.List;

public class AudioBroadcaster {
    public static final String CHANNEL = "minecast:audio";

    private static final byte TYPE_START = 0x00;
    private static final byte TYPE_CHUNK = 0x01;
    private static final byte TYPE_END = 0x02;

    /**
     * 단일 플레이어에게 START→CHUNK×N→END 패킷을 전송한다.
     * 플레이어가 접속 해제된 경우 조용히 중단한다.
     */
    public void sendToPlayer(Player player, byte[] mp3Bytes, List<byte[]> chunks) {
        if (!player.isOnline()) return;

        // START
        ByteArrayDataOutput startOut = ByteStreams.newDataOutput();
        startOut.writeByte(TYPE_START);
        startOut.writeInt(mp3Bytes.length);
        startOut.writeInt(chunks.size());
        player.sendPluginMessage(getPlugin(), CHANNEL, startOut.toByteArray());

        // CHUNKs
        for (int i = 0; i < chunks.size(); i++) {
            if (!player.isOnline()) return;
            byte[] chunk = chunks.get(i);
            ByteArrayDataOutput chunkOut = ByteStreams.newDataOutput();
            chunkOut.writeByte(TYPE_CHUNK);
            chunkOut.writeInt(i);
            chunkOut.write(chunk);
            player.sendPluginMessage(getPlugin(), CHANNEL, chunkOut.toByteArray());
        }

        // END
        if (!player.isOnline()) return;
        ByteArrayDataOutput endOut = ByteStreams.newDataOutput();
        endOut.writeByte(TYPE_END);
        player.sendPluginMessage(getPlugin(), CHANNEL, endOut.toByteArray());
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return org.bukkit.Bukkit.getPluginManager().getPlugin("MineCast");
    }
}
