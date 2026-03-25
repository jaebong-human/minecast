package com.minecast.client;

import com.minecast.client.network.AudioPacketHandler;
import net.fabricmc.api.ClientModInitializer;

public class MineCastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new AudioPacketHandler().register();
    }
}
