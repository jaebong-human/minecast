package com.minecast.server.tts;

import com.minecast.server.config.PluginConfig;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TypecastClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final PluginConfig config;

    public TypecastClient(PluginConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    // 테스트용 생성자
    TypecastClient(PluginConfig config, OkHttpClient http) {
        this.config = config;
        this.http = http;
    }

    public byte[] fetchMp3(String text) throws IOException {
        String body = new JSONObject()
            .put("text", text)
            .put("actor_id", config.getActorId())
            .put("lang", "auto")
            .put("xapi_hd", true)
            .put("model_version", "latest")
            .toString();

        Request step1 = new Request.Builder()
            .url(config.getApiUrl())
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .post(RequestBody.create(body, JSON))
            .build();

        String audioUrl;
        try (Response resp = http.newCall(step1).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Typecast API error: " + resp.code());
            }
            JSONObject json = new JSONObject(resp.body().string());
            audioUrl = json.getJSONObject("result").getString("speak_v2_url");
        }

        Request step2 = new Request.Builder().url(audioUrl).get().build();
        try (Response resp = http.newCall(step2).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Audio download error: " + resp.code());
            }
            return resp.body().bytes();
        }
    }
}
