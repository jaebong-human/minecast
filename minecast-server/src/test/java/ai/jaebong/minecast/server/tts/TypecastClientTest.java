package ai.jaebong.minecast.server.tts;

import ai.jaebong.minecast.server.config.PluginConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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

        PluginConfig config = new PluginConfig("test-key", "test-voice", 200);
        client = new TypecastClient(config, java.net.http.HttpClient.newHttpClient(),
            server.url("/v1/text-to-speech").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchMp3_returnsAudioBytes() throws Exception {
        byte[] fakeAudio = new byte[]{(byte) 0xFF, (byte) 0xFB, 0x10, 0x00};
        okhttp3.mockwebserver.MockResponse response = new MockResponse();
        response.setBody(new okio.Buffer().write(fakeAudio));
        response.addHeader("Content-Type", "audio/mpeg");
        server.enqueue(response);

        byte[] result = client.fetchMp3("안녕하세요", "test-voice");

        assertNotNull(result);

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("안녕하세요"));
        assertTrue(body.contains("voice_id"));
        assertNotNull(req.getHeader("X-API-KEY"));
    }

    @Test
    void fetchMp3_throwsOnApiError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThrows(IOException.class, () -> client.fetchMp3("test", "test-voice"));
    }
}
