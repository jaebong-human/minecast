package ai.jaebong.minecast.server.tts;

import ai.jaebong.minecast.server.config.PluginConfig;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TypecastClient {
    private static final String API_URL = "https://api.typecast.ai/v1/text-to-speech";

    private final HttpClient http;
    private final PluginConfig config;
    private final String effectiveUrl;

    public TypecastClient(PluginConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.effectiveUrl = API_URL;
    }

    // 테스트용 생성자
    TypecastClient(PluginConfig config, HttpClient http, String apiUrlOverride) {
        this.config = config;
        this.http = http;
        this.effectiveUrl = apiUrlOverride;
    }

    public byte[] fetchMp3(String text, String voiceId) throws IOException {
        String body = new JSONObject()
            .put("text", text)
            .put("voice_id", voiceId)
            .put("model", "ssfm-v30")
            .put("output", new JSONObject().put("audio_format", "mp3"))
            .toString();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(effectiveUrl))
            .header("Content-Type", "application/json")
            .header("X-API-KEY", config.getApiKey())
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Typecast API 오류: " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("요청 중단됨", e);
        }
    }
}
