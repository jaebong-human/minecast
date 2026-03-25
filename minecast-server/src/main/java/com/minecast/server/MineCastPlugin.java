package com.minecast.server;

import com.minecast.server.audio.AudioBroadcaster;
import com.minecast.server.command.CastCommand;
import com.minecast.server.config.PluginConfig;
import com.minecast.server.tts.TypecastClient;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MineCastPlugin extends JavaPlugin {
    private ExecutorService executor;
    private PluginConfig pluginConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(getConfig());
        executor = Executors.newFixedThreadPool(10);

        TypecastClient ttsClient = new TypecastClient(pluginConfig);
        AudioBroadcaster broadcaster = new AudioBroadcaster();
        CastCommand castCommand = new CastCommand(pluginConfig, ttsClient, broadcaster, executor);

        getCommand("cast").setExecutor(castCommand);
        getServer().getMessenger().registerOutgoingPluginChannel(this, AudioBroadcaster.CHANNEL);
        getLogger().info("MineCast enabled.");
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        getLogger().info("MineCast disabled.");
    }
}
