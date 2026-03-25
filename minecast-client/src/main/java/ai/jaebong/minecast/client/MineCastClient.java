package ai.jaebong.minecast.client;

import ai.jaebong.minecast.client.network.AudioPacketHandler;
import ai.jaebong.minecast.client.network.MinecastPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class MineCastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(MinecastPayload.TYPE, MinecastPayload.CODEC);
        new AudioPacketHandler().register();
    }
}
