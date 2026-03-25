package com.minecast.server.config;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {
    private final String apiKey;
    private final String actorId;
    private final String apiUrl;
    private final int cooldownSeconds;
    private final int maxTextLength;

    public PluginConfig(FileConfiguration config) {
        this.apiKey = config.getString("typecast.api-key", "");
        this.actorId = config.getString("typecast.actor-id", "");
        this.apiUrl = config.getString("typecast.api-url", "https://typecast.ai/api/speak");
        this.cooldownSeconds = config.getInt("cast.cooldown-seconds", 10);
        this.maxTextLength = config.getInt("cast.max-text-length", 200);
    }

    public String getApiKey() { return apiKey; }
    public String getActorId() { return actorId; }
    public String getApiUrl() { return apiUrl; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getMaxTextLength() { return maxTextLength; }
}
