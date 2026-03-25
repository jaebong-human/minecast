package com.minecast.server.tts;

import com.minecast.server.config.PluginConfig;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TypecastClientTest {
    private MockWebServer server;
    private TypecastClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        YamlConfiguration config = new YamlConfiguration();
        config.set("typecast.api-key", "test-key");
        config.set("typecast.actor-id", "test-actor");
        config.set("typecast.api-url", server.url("/api/speak").toString());
        config.set("cast.cooldown-seconds", 10);
        config.set("cast.max-text-length", 200);

        client = new TypecastClient(new PluginConfig(config));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchMp3_returnsAudioBytes() throws Exception {
        // 1단계: speak_v2_url 반환
        server.enqueue(new MockResponse()
            .setBody("{\"result\":{\"speak_v2_url\":\"" + server.url("/audio/test.mp3") + "\"}}")
            .addHeader("Content-Type", "application/json"));
        // 2단계: MP3 bytes
        byte[] fakeAudio = new byte[]{(byte) 0xFF, (byte) 0xFB, 0x10, 0x00};
        server.enqueue(new MockResponse()
            .setBody(new okio.Buffer().write(fakeAudio))
            .addHeader("Content-Type", "audio/mpeg"));

        byte[] result = client.fetchMp3("안녕하세요");

        assertNotNull(result);
        assertArrayEquals(fakeAudio, result);

        // 1단계 요청 검증
        RecordedRequest step1 = server.takeRequest();
        assertEquals("POST", step1.getMethod());
        assertTrue(step1.getBody().readUtf8().contains("안녕하세요"));
        assertTrue(step1.getHeader("Authorization").startsWith("Bearer "));
    }

    @Test
    void fetchMp3_throwsOnApiError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThrows(IOException.class, () -> client.fetchMp3("test"));
    }
}
