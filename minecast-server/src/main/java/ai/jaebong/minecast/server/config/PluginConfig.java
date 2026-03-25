package ai.jaebong.minecast.server.config;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PluginConfig {
    private final String apiKey;
    private final String voiceId;
    private final int maxTextLength;

    public PluginConfig(String apiKey, String voiceId, int maxTextLength) {
        this.apiKey = apiKey;
        this.voiceId = voiceId;
        this.maxTextLength = maxTextLength;
    }

    public static PluginConfig load(Path configDir) {
        Path file = configDir.resolve("minecast.json");
        if (Files.exists(file)) {
            try {
                JSONObject json = new JSONObject(Files.readString(file));
                return new PluginConfig(
                    json.optString("apiKey", ""),
                    json.optString("voiceId", "tc_60e5426de8b95f1d3000d7b5"),
                    json.optInt("maxTextLength", 200)
                );
            } catch (IOException e) {
                throw new RuntimeException("config/minecast.json 읽기 실패", e);
            }
        }

        PluginConfig defaults = new PluginConfig("", "tc_60e5426de8b95f1d3000d7b5", 200);
        defaults.save(file);
        return defaults;
    }

    private void save(Path file) {
        JSONObject json = new JSONObject()
            .put("apiKey", apiKey)
            .put("voiceId", voiceId)
            .put("maxTextLength", maxTextLength);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString(2));
        } catch (IOException e) {
            System.err.println("[MineCast] config/minecast.json 저장 실패: " + e.getMessage());
        }
    }

    public String getApiKey() { return apiKey; }
    public String getVoiceId() { return voiceId; }
    public int getMaxTextLength() { return maxTextLength; }
}
