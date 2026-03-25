package com.minecast.server.audio;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class AudioBroadcaster {
    public static final String CHANNEL = "minecast:audio";

    private static final byte TYPE_START = 0x00;
    private static final byte TYPE_CHUNK = 0x01;
    private static final byte TYPE_END = 0x02;

    private final Plugin plugin;

    public AudioBroadcaster(Plugin plugin) {
        this.plugin = plugin;
    }

    public void sendToPlayer(Player player, byte[] mp3Bytes, List<byte[]> chunks) {
        if (!player.isOnline()) return;

        // START
        ByteArrayDataOutput startOut = ByteStreams.newDataOutput();
        startOut.writeByte(TYPE_START);
        startOut.writeInt(mp3Bytes.length);
        startOut.writeInt(chunks.size());
        player.sendPluginMessage(plugin, CHANNEL, startOut.toByteArray());

        // CHUNKs
        for (int i = 0; i < chunks.size(); i++) {
            if (!player.isOnline()) return;
            byte[] chunk = chunks.get(i);
            ByteArrayDataOutput chunkOut = ByteStreams.newDataOutput();
            chunkOut.writeByte(TYPE_CHUNK);
            chunkOut.writeInt(i);
            chunkOut.write(chunk);
            player.sendPluginMessage(plugin, CHANNEL, chunkOut.toByteArray());
        }

        // END
        if (!player.isOnline()) return;
        ByteArrayDataOutput endOut = ByteStreams.newDataOutput();
        endOut.writeByte(TYPE_END);
        player.sendPluginMessage(plugin, CHANNEL, endOut.toByteArray());
    }
}
