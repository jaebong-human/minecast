# Minecast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 `/cast <텍스트>`를 치면 Typecast TTS API로 MP3를 생성하고 Plugin Messaging Channel을 통해 모든 플레이어에게 전송, Fabric 클라이언트 모드가 OpenAL로 재생한다.

**Architecture:** 두 독립 Gradle 프로젝트 (minecast-server, minecast-client)가 단일 리포지토리에 공존한다. 서버는 10-스레드 풀로 비동기 처리, 타입 바이트(0x00/0x01/0x02)로 구분되는 START/CHUNK/END 패킷을 플레이어별 병렬 전송한다. 클라이언트는 엄격한 순서로 청크를 수신하고, JLayer로 MP3 디코딩 후 LWJGL OpenAL로 재생한다.

**Tech Stack:** Java 21, Paper API 1.21.4, Fabric API 1.21.x + fabric-loom, OkHttp 4.12, JLayer 1.0.1, MockBukkit 3.x (서버 테스트), MockWebServer 4.12 (HTTP 테스트), JUnit 5

---

## 파일 구조

```
minecast/
├── settings.gradle
├── minecast-server/
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/minecast/server/
│       │   ├── MineCastPlugin.java              ← 플러그인 진입점, 스레드 풀 생명주기
│       │   ├── config/PluginConfig.java          ← config.yml 래퍼
│       │   ├── command/CastCommand.java          ← /cast 명령어, 쿨다운 관리
│       │   ├── tts/TypecastClient.java           ← Typecast HTTP 2단계 호출
│       │   └── audio/
│       │       ├── AudioChunker.java             ← byte[] → List<byte[]> (32KB 청크)
│       │       └── AudioBroadcaster.java         ← START/CHUNK/END 패킷 전송
│       ├── main/resources/
│       │   ├── plugin.yml
│       │   └── config.yml
│       └── test/java/com/minecast/server/
│           ├── audio/AudioChunkerTest.java
│           ├── tts/TypecastClientTest.java
│           └── command/CastCommandTest.java
└── minecast-client/
    ├── build.gradle
    └── src/
        ├── main/java/com/minecast/client/
        │   ├── MineCastClient.java               ← Fabric 모드 진입점
        │   ├── network/AudioPacketHandler.java   ← 채널 리스너, 타입 바이트 라우팅
        │   └── audio/
        │       ├── AudioBuffer.java              ← 청크 누적, 순서/개수 검증
        │       └── AudioPlayer.java             ← JLayer 디코딩 + OpenAL 재생
        ├── main/resources/
        │   └── fabric.mod.json
        └── test/java/com/minecast/client/
            └── audio/AudioBufferTest.java
```

---

## Task 1: Gradle 프로젝트 스캐폴딩

**Files:**
- Create: `settings.gradle`
- Create: `minecast-server/build.gradle`
- Create: `minecast-client/build.gradle`

- [ ] **Step 1: 루트 settings.gradle 작성**

```groovy
// settings.gradle
rootProject.name = 'minecast'
include('minecast-server', 'minecast-client')
```

- [ ] **Step 2: 서버 플러그인 build.gradle 작성**

```groovy
// minecast-server/build.gradle
plugins {
    id 'java'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

group = 'com.minecast'
version = '1.0.0'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'com.github.seeseemelk:MockBukkit-v1.21:3.133.0'
    testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
}

test { useJUnitPlatform() }

shadowJar {
    relocate 'okhttp3', 'com.minecast.shadow.okhttp3'
    relocate 'okio', 'com.minecast.shadow.okio'
}
```

- [ ] **Step 3: 클라이언트 모드 build.gradle 작성**

```groovy
// minecast-client/build.gradle
plugins {
    id 'fabric-loom' version '1.7-SNAPSHOT'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

group = 'com.minecast'
version = '1.0.0'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

loom {
    splitEnvironmentSourceSets()
    mods { minecast { sourceSet sourceSets.main } }
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft 'com.mojang:minecraft:1.21.4'
    mappings loom.officialMojangMappings()
    modImplementation 'net.fabricmc:fabric-loader:0.16.9'
    modImplementation fabricApi.module('fabric-networking-api-v1', '0.106.1+1.21.4')

    // JLayer: MP3 디코딩
    implementation 'javazoom:jlayer:1.0.1'
    include 'javazoom:jlayer:1.0.1'   // fat jar에 포함

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

test { useJUnitPlatform() }
```

- [ ] **Step 4: 빌드 확인**

```bash
cd /Users/ironyee/Documents/minecast
gradle :minecast-server:compileJava :minecast-client:compileJava
```

Expected: BUILD SUCCESSFUL (소스가 없어도 컴파일 단계 통과)

- [ ] **Step 5: 커밋**

```bash
git add settings.gradle minecast-server/build.gradle minecast-client/build.gradle
git commit -m "chore: scaffold gradle multi-project build"
```

---

## Task 2: 서버 — 리소스 파일 + 플러그인 부트스트랩

**Files:**
- Create: `minecast-server/src/main/resources/plugin.yml`
- Create: `minecast-server/src/main/resources/config.yml`
- Create: `minecast-server/src/main/java/com/minecast/server/MineCastPlugin.java`
- Create: `minecast-server/src/main/java/com/minecast/server/config/PluginConfig.java`

- [ ] **Step 1: plugin.yml 작성**

```yaml
# minecast-server/src/main/resources/plugin.yml
name: MineCast
version: '1.0.0'
main: com.minecast.server.MineCastPlugin
api-version: '1.21'
commands:
  cast:
    description: "TTS를 통해 월드 전체에 음성을 재생합니다"
    permission: minecast.use
    usage: /cast <텍스트>
permissions:
  minecast.use:
    description: "/cast 명령어 사용 가능"
    default: op
```

- [ ] **Step 2: config.yml (기본값) 작성**

```yaml
# minecast-server/src/main/resources/config.yml
typecast:
  api-key: "YOUR_API_KEY"
  actor-id: "YOUR_ACTOR_ID"
  api-url: "https://typecast.ai/api/speak"

cast:
  cooldown-seconds: 10
  max-text-length: 200
```

- [ ] **Step 3: PluginConfig.java 작성**

```java
// minecast-server/src/main/java/com/minecast/server/config/PluginConfig.java
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
```

- [ ] **Step 4: MineCastPlugin.java 작성**

```java
// minecast-server/src/main/java/com/minecast/server/MineCastPlugin.java
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
```

- [ ] **Step 5: 컴파일 확인**

```bash
gradle :minecast-server:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add minecast-server/src/main/resources/ minecast-server/src/main/java/com/minecast/server/MineCastPlugin.java minecast-server/src/main/java/com/minecast/server/config/
git commit -m "feat(server): plugin bootstrap and config"
```

---

## Task 3: 서버 — TypecastClient

**Files:**
- Create: `minecast-server/src/main/java/com/minecast/server/tts/TypecastClient.java`
- Create: `minecast-server/src/test/java/com/minecast/server/tts/TypecastClientTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// minecast-server/src/test/java/com/minecast/server/tts/TypecastClientTest.java
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
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
gradle :minecast-server:test --tests "com.minecast.server.tts.TypecastClientTest"
```

Expected: FAIL (TypecastClient 클래스 없음)

- [ ] **Step 3: TypecastClient.java 구현**

```java
// minecast-server/src/main/java/com/minecast/server/tts/TypecastClient.java
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

    // 테스트용 생성자 (MockWebServer 등 주입)
    TypecastClient(PluginConfig config, OkHttpClient http) {
        this.config = config;
        this.http = http;
    }

    /**
     * Typecast 2단계 호출: POST → speak_v2_url → GET MP3 bytes
     */
    public byte[] fetchMp3(String text) throws IOException {
        // 1단계: TTS 생성 요청
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

        // 2단계: MP3 다운로드
        Request step2 = new Request.Builder().url(audioUrl).get().build();
        try (Response resp = http.newCall(step2).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Audio download error: " + resp.code());
            }
            return resp.body().bytes();
        }
    }
}
```

> **주의:** `org.json:json` 의존성을 build.gradle에 추가하거나, OkHttp와 함께 오는 `okhttp3`의 내장 JSON 파서를 쓸 수도 있다. 가장 간단한 방법은 `implementation 'org.json:json:20240303'`을 추가하는 것.

- [ ] **Step 4: build.gradle에 org.json 추가**

```groovy
// minecast-server/build.gradle dependencies 블록에 추가:
implementation 'org.json:json:20240303'
```

shadowJar relocate에도 추가:
```groovy
relocate 'org.json', 'com.minecast.shadow.json'
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
gradle :minecast-server:test --tests "com.minecast.server.tts.TypecastClientTest"
```

Expected: PASS (2 tests)

- [ ] **Step 6: 커밋**

```bash
git add minecast-server/src/main/java/com/minecast/server/tts/ minecast-server/src/test/java/com/minecast/server/tts/ minecast-server/build.gradle
git commit -m "feat(server): typecast API client with 2-step MP3 fetch"
```

---

## Task 4: 서버 — AudioChunker

**Files:**
- Create: `minecast-server/src/main/java/com/minecast/server/audio/AudioChunker.java`
- Create: `minecast-server/src/test/java/com/minecast/server/audio/AudioChunkerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// minecast-server/src/test/java/com/minecast/server/audio/AudioChunkerTest.java
package com.minecast.server.audio;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AudioChunkerTest {
    private final AudioChunker chunker = new AudioChunker();

    @Test
    void smallData_returnsSingleChunk() {
        byte[] data = new byte[100];
        List<byte[]> chunks = chunker.split(data);
        assertEquals(1, chunks.size());
        assertArrayEquals(data, chunks.get(0));
    }

    @Test
    void exactChunkSize_returnsOneChunk() {
        byte[] data = new byte[AudioChunker.CHUNK_SIZE];
        List<byte[]> chunks = chunker.split(data);
        assertEquals(1, chunks.size());
    }

    @Test
    void largeData_splitsIntoChunks() {
        // 32KB + 1 byte → 2 chunks
        byte[] data = new byte[AudioChunker.CHUNK_SIZE + 1];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 127);

        List<byte[]> chunks = chunker.split(data);

        assertEquals(2, chunks.size());
        assertEquals(AudioChunker.CHUNK_SIZE, chunks.get(0).length);
        assertEquals(1, chunks.get(1).length);
        // 데이터 무결성 확인
        assertEquals(data[0], chunks.get(0)[0]);
        assertEquals(data[AudioChunker.CHUNK_SIZE], chunks.get(1)[0]);
    }

    @Test
    void emptyData_returnsEmptyList() {
        List<byte[]> chunks = chunker.split(new byte[0]);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void reconstructed_matchesOriginal() {
        byte[] data = new byte[80_000]; // ~80KB (5초 MP3)
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 200);

        List<byte[]> chunks = chunker.split(data);

        // 재조합
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] reconstructed = new byte[total];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, reconstructed, pos, chunk.length);
            pos += chunk.length;
        }
        assertArrayEquals(data, reconstructed);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
gradle :minecast-server:test --tests "com.minecast.server.audio.AudioChunkerTest"
```

Expected: FAIL

- [ ] **Step 3: AudioChunker.java 구현**

```java
// minecast-server/src/main/java/com/minecast/server/audio/AudioChunker.java
package com.minecast.server.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioChunker {
    public static final int CHUNK_SIZE = 32 * 1024; // 32KB

    public List<byte[]> split(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            chunks.add(Arrays.copyOfRange(data, offset, offset + length));
            offset += length;
        }
        return chunks;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
gradle :minecast-server:test --tests "com.minecast.server.audio.AudioChunkerTest"
```

Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add minecast-server/src/main/java/com/minecast/server/audio/AudioChunker.java minecast-server/src/test/java/com/minecast/server/audio/AudioChunkerTest.java
git commit -m "feat(server): audio chunker (32KB split)"
```

---

## Task 5: 서버 — AudioBroadcaster

**Files:**
- Create: `minecast-server/src/main/java/com/minecast/server/audio/AudioBroadcaster.java`

> AudioBroadcaster는 Bukkit Player 객체에 직접 의존하므로 단위 테스트보다 통합 테스트가 적합하다. MockBukkit으로 Player mock을 만들 수 있으나, Plugin Messaging Channel은 MockBukkit에서 에뮬레이션이 제한적이다. 이 Task는 구현 + 수동 검증으로 진행한다.

- [ ] **Step 1: AudioBroadcaster.java 구현**

패킷 포맷 (채널: `minecast:audio`):
- START: `[0x00][totalBytes: 4bytes int BE][chunkCount: 4bytes int BE]`
- CHUNK: `[0x01][index: 4bytes int BE][data: N bytes]`
- END: `[0x02]`

```java
// minecast-server/src/main/java/com/minecast/server/audio/AudioBroadcaster.java
package com.minecast.server.audio;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;

import java.util.List;

public class AudioBroadcaster {
    public static final String CHANNEL = "minecast:audio";

    private static final byte TYPE_START = 0x00;
    private static final byte TYPE_CHUNK = 0x01;
    private static final byte TYPE_END = 0x02;

    /**
     * 단일 플레이어에게 START→CHUNK×N→END 패킷을 전송한다.
     * 플레이어가 접속 해제된 경우 조용히 중단한다.
     */
    public void sendToPlayer(Player player, byte[] mp3Bytes, List<byte[]> chunks) {
        if (!player.isOnline()) return;

        // START
        ByteArrayDataOutput startOut = ByteStreams.newDataOutput();
        startOut.writeByte(TYPE_START);
        startOut.writeInt(mp3Bytes.length);
        startOut.writeInt(chunks.size());
        player.sendPluginMessage(getPlugin(), CHANNEL, startOut.toByteArray());

        // CHUNKs
        for (int i = 0; i < chunks.size(); i++) {
            if (!player.isOnline()) return;
            byte[] chunk = chunks.get(i);
            ByteArrayDataOutput chunkOut = ByteStreams.newDataOutput();
            chunkOut.writeByte(TYPE_CHUNK);
            chunkOut.writeInt(i);
            chunkOut.write(chunk);
            player.sendPluginMessage(getPlugin(), CHANNEL, chunkOut.toByteArray());
        }

        // END
        if (!player.isOnline()) return;
        ByteArrayDataOutput endOut = ByteStreams.newDataOutput();
        endOut.writeByte(TYPE_END);
        player.sendPluginMessage(getPlugin(), CHANNEL, endOut.toByteArray());
    }

    // 플러그인 인스턴스 접근 (MineCastPlugin.getInstance()로 대체 가능)
    private org.bukkit.plugin.Plugin getPlugin() {
        return org.bukkit.Bukkit.getPluginManager().getPlugin("MineCast");
    }
}
```

> **주의:** `sendPluginMessage`는 플러그인이 해당 채널에 등록되어 있어야 한다. MineCastPlugin.onEnable()에 아래를 추가할 것:
> ```java
> getServer().getMessenger().registerOutgoingPluginChannel(this, AudioBroadcaster.CHANNEL);
> ```

- [ ] **Step 2: MineCastPlugin.onEnable()에 채널 등록 추가**

```java
// MineCastPlugin.onEnable() 마지막에 추가:
getServer().getMessenger().registerOutgoingPluginChannel(this, AudioBroadcaster.CHANNEL);
```

- [ ] **Step 3: 컴파일 확인**

```bash
gradle :minecast-server:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add minecast-server/src/main/java/com/minecast/server/audio/AudioBroadcaster.java minecast-server/src/main/java/com/minecast/server/MineCastPlugin.java
git commit -m "feat(server): audio broadcaster with typed packets"
```

---

## Task 6: 서버 — CastCommand (쿨다운 + 오케스트레이션)

**Files:**
- Create: `minecast-server/src/main/java/com/minecast/server/command/CastCommand.java`
- Create: `minecast-server/src/test/java/com/minecast/server/command/CastCommandTest.java`

- [ ] **Step 1: 실패하는 테스트 작성 (쿨다운 + 권한)**

```java
// minecast-server/src/test/java/com/minecast/server/command/CastCommandTest.java
package com.minecast.server.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.minecast.server.MineCastPlugin;
import com.minecast.server.audio.AudioBroadcaster;
import com.minecast.server.audio.AudioChunker;
import com.minecast.server.config.PluginConfig;
import com.minecast.server.tts.TypecastClient;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class CastCommandTest {
    private ServerMock server;
    private PlayerMock player;
    private TypecastClient mockTts;
    private CastCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "minecast.use", true);

        YamlConfiguration config = new YamlConfiguration();
        config.set("typecast.api-key", "key");
        config.set("typecast.actor-id", "actor");
        config.set("typecast.api-url", "http://localhost");
        config.set("cast.cooldown-seconds", 5);
        config.set("cast.max-text-length", 50);

        PluginConfig pluginConfig = new PluginConfig(config);
        mockTts = Mockito.mock(TypecastClient.class);
        AudioBroadcaster broadcaster = Mockito.mock(AudioBroadcaster.class);

        command = new CastCommand(pluginConfig, mockTts, broadcaster,
            Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void textTooLong_sendsErrorMessage() {
        String longText = "a".repeat(51);
        command.onCommand(player, null, "cast", new String[]{longText});
        assertTrue(player.nextMessage().contains("200자"));
    }

    @Test
    void noArgs_sendsUsageMessage() {
        command.onCommand(player, null, "cast", new String[]{});
        assertNotNull(player.nextMessage());
    }

    @Test
    void globalCooldown_blocksSecondCall() throws Exception {
        Mockito.when(mockTts.fetchMp3(Mockito.anyString()))
            .thenReturn(new byte[100]);

        command.onCommand(player, null, "cast", new String[]{"hi"});
        // 쿨다운 활성 중 두 번째 호출
        command.onCommand(player, null, "cast", new String[]{"hi again"});

        // 두 번째 호출은 쿨다운 메시지를 받아야 함
        // (비동기라 약간의 대기 필요)
        Thread.sleep(100);
        String msg = player.nextMessage();
        // 쿨다운 메시지 또는 두 번째 전송 차단 확인
        assertNotNull(msg);
    }
}
```

> MockBukkit에 Mockito 추가 필요: `testImplementation 'org.mockito:mockito-core:5.11.0'`을 build.gradle에 추가.

- [ ] **Step 2: build.gradle에 Mockito 추가**

```groovy
testImplementation 'org.mockito:mockito-core:5.11.0'
```

- [ ] **Step 3: CastCommand.java 구현**

```java
// minecast-server/src/main/java/com/minecast/server/command/CastCommand.java
package com.minecast.server.command;

import com.minecast.server.audio.AudioBroadcaster;
import com.minecast.server.audio.AudioChunker;
import com.minecast.server.config.PluginConfig;
import com.minecast.server.tts.TypecastClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CastCommand implements CommandExecutor {
    private final PluginConfig config;
    private final TypecastClient ttsClient;
    private final AudioBroadcaster broadcaster;
    private final AudioChunker chunker = new AudioChunker();
    private final ExecutorService executor;

    // 글로벌 쿨다운: 마지막 전송 완료 시각 (ms)
    private final AtomicLong lastCastCompletedAt = new AtomicLong(0);
    private final AtomicBoolean casting = new AtomicBoolean(false);

    public CastCommand(PluginConfig config, TypecastClient ttsClient,
                       AudioBroadcaster broadcaster, ExecutorService executor) {
        this.config = config;
        this.ttsClient = ttsClient;
        this.broadcaster = broadcaster;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("사용법: /cast <텍스트>", NamedTextColor.YELLOW));
            return true;
        }

        String text = String.join(" ", args);

        if (text.length() > config.getMaxTextLength()) {
            sender.sendMessage(Component.text(
                "텍스트가 너무 깁니다. 최대 " + config.getMaxTextLength() + "자.", NamedTextColor.RED));
            return true;
        }

        // 글로벌 쿨다운 확인
        long elapsed = (System.currentTimeMillis() - lastCastCompletedAt.get()) / 1000;
        long remaining = config.getCooldownSeconds() - elapsed;
        if (remaining > 0 || casting.get()) {
            long wait = casting.get() ? config.getCooldownSeconds() : remaining;
            sender.sendMessage(Component.text(
                "쿨다운 중입니다. " + wait + "초 후에 다시 시도하세요.", NamedTextColor.GOLD));
            return true;
        }

        casting.set(true);
        executor.submit(() -> {
            try {
                byte[] mp3 = ttsClient.fetchMp3(text);
                List<byte[]> chunks = chunker.split(mp3);

                // 각 플레이어에게 병렬 전송
                Bukkit.getOnlinePlayers().parallelStream().forEach(player -> {
                    try {
                        broadcaster.sendToPlayer(player, mp3, chunks);
                    } catch (Exception e) {
                        // 개별 플레이어 실패는 무시
                    }
                });

            } catch (IOException e) {
                sender.sendMessage(Component.text(
                    "TTS 오류: " + e.getMessage(), NamedTextColor.RED));
            } finally {
                lastCastCompletedAt.set(System.currentTimeMillis());
                casting.set(false);
            }
        });

        return true;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
gradle :minecast-server:test --tests "com.minecast.server.command.CastCommandTest"
```

Expected: PASS (3 tests)

- [ ] **Step 5: 전체 서버 테스트 통과 확인**

```bash
gradle :minecast-server:test
```

Expected: PASS (전체)

- [ ] **Step 6: 서버 플러그인 JAR 빌드**

```bash
gradle :minecast-server:shadowJar
```

Expected: `minecast-server/build/libs/minecast-server-1.0.0-all.jar` 생성

- [ ] **Step 7: 커밋**

```bash
git add minecast-server/
git commit -m "feat(server): cast command with global cooldown and async dispatch"
```

---

## Task 7: 클라이언트 — AudioBuffer

**Files:**
- Create: `minecast-client/src/main/java/com/minecast/client/audio/AudioBuffer.java`
- Create: `minecast-client/src/test/java/com/minecast/client/audio/AudioBufferTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// minecast-client/src/test/java/com/minecast/client/audio/AudioBufferTest.java
package com.minecast.client.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioBufferTest {

    @Test
    void normalFlow_returnsCompleteAudio() {
        AudioBuffer buffer = new AudioBuffer(100, 2);
        buffer.addChunk(0, new byte[]{1, 2, 3});
        buffer.addChunk(1, new byte[]{4, 5});

        assertTrue(buffer.isComplete(2));
        byte[] result = buffer.toByteArray();
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, result);
    }

    @Test
    void outOfOrderChunk_resetsBuffer() {
        AudioBuffer buffer = new AudioBuffer(100, 3);
        buffer.addChunk(0, new byte[]{1});
        boolean reset = buffer.addChunk(2, new byte[]{3}); // index 1 건너뜀

        assertTrue(reset, "순서 오류 시 reset=true 반환해야 함");
    }

    @Test
    void wrongChunkCount_notComplete() {
        AudioBuffer buffer = new AudioBuffer(100, 3);
        buffer.addChunk(0, new byte[]{1});
        buffer.addChunk(1, new byte[]{2});

        assertFalse(buffer.isComplete(3)); // 아직 2개만 받음
    }

    @Test
    void resetAfterStart_clearsState() {
        AudioBuffer buffer = new AudioBuffer(100, 2);
        buffer.addChunk(0, new byte[]{1, 2});
        buffer.reset(200, 3);  // 새 START

        assertEquals(0, buffer.getReceivedChunkCount());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
gradle :minecast-client:test --tests "com.minecast.client.audio.AudioBufferTest"
```

Expected: FAIL

- [ ] **Step 3: AudioBuffer.java 구현**

```java
// minecast-client/src/main/java/com/minecast/client/audio/AudioBuffer.java
package com.minecast.client.audio;

import java.io.ByteArrayOutputStream;

public class AudioBuffer {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private int expectedChunkCount;
    private int nextExpectedIndex;
    private int receivedChunkCount;

    public AudioBuffer(int totalBytes, int chunkCount) {
        this.expectedChunkCount = chunkCount;
        this.nextExpectedIndex = 0;
        this.receivedChunkCount = 0;
    }

    /**
     * 청크 추가. 순서가 맞지 않으면 버퍼를 리셋하고 true 반환.
     */
    public boolean addChunk(int index, byte[] data) {
        if (index != nextExpectedIndex) {
            clear();
            return true; // 리셋 신호
        }
        stream.write(data, 0, data.length);
        nextExpectedIndex++;
        receivedChunkCount++;
        return false;
    }

    public boolean isComplete(int expectedCount) {
        return receivedChunkCount == expectedCount;
    }

    public byte[] toByteArray() {
        return stream.toByteArray();
    }

    public int getReceivedChunkCount() {
        return receivedChunkCount;
    }

    public void reset(int totalBytes, int chunkCount) {
        clear();
        this.expectedChunkCount = chunkCount;
    }

    public int getExpectedChunkCount() {
        return expectedChunkCount;
    }

    private void clear() {
        stream.reset();
        nextExpectedIndex = 0;
        receivedChunkCount = 0;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
gradle :minecast-client:test --tests "com.minecast.client.audio.AudioBufferTest"
```

Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add minecast-client/src/main/java/com/minecast/client/audio/AudioBuffer.java minecast-client/src/test/java/com/minecast/client/audio/AudioBufferTest.java
git commit -m "feat(client): audio buffer with strict sequential chunk validation"
```

---

## Task 8: 클라이언트 — AudioPlayer (JLayer + OpenAL)

**Files:**
- Create: `minecast-client/src/main/java/com/minecast/client/audio/AudioPlayer.java`

> AudioPlayer는 LWJGL OpenAL에 의존하므로 Minecraft 런타임 없이 단위 테스트가 불가능하다. 구현 후 수동 E2E 테스트로 검증한다.

- [ ] **Step 1: AudioPlayer.java 구현**

```java
// minecast-client/src/main/java/com/minecast/client/audio/AudioPlayer.java
package com.minecast.client.audio;

import javazoom.jl.decoder.*;
import org.lwjgl.openal.AL10;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class AudioPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioPlayer.class);

    private int alSource = -1;
    private int alBuffer = -1;

    /**
     * MP3 bytes를 디코딩하여 OpenAL로 재생한다.
     * 이전 재생 중이면 중단하고 새로 시작한다.
     */
    public void play(byte[] mp3Bytes) {
        stopAndCleanup();

        Thread playThread = new Thread(() -> {
            try {
                byte[] pcm = decodeMp3ToPcm(mp3Bytes);
                int sampleRate = getSampleRate(mp3Bytes);
                playPcm(pcm, sampleRate);
            } catch (Exception e) {
                LOGGER.warn("[MineCast] MP3 재생 실패: {}", e.getMessage());
            }
        }, "minecast-audio");
        playThread.setDaemon(true);
        playThread.start();
    }

    /**
     * JLayer로 MP3 → PCM(16bit, mono/stereo) 디코딩
     */
    private byte[] decodeMp3ToPcm(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();

        Header frame;
        while ((frame = bitstream.readFrame()) != null) {
            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
            short[] samples = output.getBuffer();
            for (short s : samples) {
                pcmOut.write(s & 0xFF);
                pcmOut.write((s >> 8) & 0xFF);
            }
            bitstream.closeFrame();
        }
        return pcmOut.toByteArray();
    }

    private int getSampleRate(byte[] mp3) throws Exception {
        Bitstream bs = new Bitstream(new ByteArrayInputStream(mp3));
        Header header = bs.readFrame();
        return header != null ? (int) header.frequency() : 44100;
    }

    /**
     * OpenAL로 PCM 재생 (LWJGL, Minecraft에 번들됨)
     */
    private void playPcm(byte[] pcm, int sampleRate) {
        alBuffer = AL10.alGenBuffers();
        ByteBuffer buf = ByteBuffer.allocateDirect(pcm.length);
        buf.put(pcm).flip();
        AL10.alBufferData(alBuffer, AL10.AL_FORMAT_STEREO16, buf, sampleRate);

        alSource = AL10.alGenSources();
        AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
        AL10.alSourcePlay(alSource);

        // 재생 완료 대기
        int state;
        do {
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        } while (state == AL10.AL_PLAYING);

        stopAndCleanup();
    }

    /**
     * 현재 재생 중단 및 OpenAL 리소스 해제
     */
    public void stopAndCleanup() {
        if (alSource != -1) {
            AL10.alSourceStop(alSource);
            AL10.alDeleteSources(alSource);
            alSource = -1;
        }
        if (alBuffer != -1) {
            AL10.alDeleteBuffers(alBuffer);
            alBuffer = -1;
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
gradle :minecast-client:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add minecast-client/src/main/java/com/minecast/client/audio/AudioPlayer.java
git commit -m "feat(client): MP3 to OpenAL playback via JLayer"
```

---

## Task 9: 클라이언트 — AudioPacketHandler + 모드 진입점

**Files:**
- Create: `minecast-client/src/main/java/com/minecast/client/network/MinecastPayload.java`
- Create: `minecast-client/src/main/java/com/minecast/client/network/AudioPacketHandler.java`
- Create: `minecast-client/src/main/java/com/minecast/client/MineCastClient.java`
- Create: `minecast-client/src/main/resources/fabric.mod.json`

- [ ] **Step 1: fabric.mod.json 작성**

```json
{
  "schemaVersion": 1,
  "id": "minecast",
  "version": "1.0.0",
  "name": "MineCast",
  "description": "서버 TTS 오디오를 인게임에서 재생합니다",
  "authors": [],
  "environment": "client",
  "entrypoints": {
    "client": ["com.minecast.client.MineCastClient"]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.4",
    "fabric-networking-api-v1": "*"
  }
}
```

- [ ] **Step 2: MinecastPayload.java 작성 (Fabric 1.21.4 CustomPacketPayload 래퍼)**

```java
// minecast-client/src/main/java/com/minecast/client/network/MinecastPayload.java
package com.minecast.client.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MinecastPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<MinecastPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("minecast", "audio"));

    public static final StreamCodec<FriendlyByteBuf, MinecastPayload> CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeByteArray(payload.data()),
            buf -> new MinecastPayload(buf.readByteArray())
        );

    @Override
    public Type<MinecastPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 3: AudioPacketHandler.java 구현**

```java
// minecast-client/src/main/java/com/minecast/client/network/AudioPacketHandler.java
package com.minecast.client.network;

import com.minecast.client.audio.AudioBuffer;
import com.minecast.client.audio.AudioPlayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class AudioPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioPacketHandler.class);

    private static final byte TYPE_START = 0x00;
    private static final byte TYPE_CHUNK = 0x01;
    private static final byte TYPE_END   = 0x02;

    private final AudioPlayer player = new AudioPlayer();
    private AudioBuffer buffer;

    public void register() {
        // Fabric 1.21.4: CustomPacketPayload 기반 등록
        ClientPlayNetworking.registerGlobalReceiver(MinecastPayload.TYPE,
            (payload, context) -> {
                try {
                    handlePacket(payload.data());
                } catch (IOException e) {
                    LOGGER.warn("[MineCast] 패킷 파싱 오류: {}", e.getMessage());
                }
            });
    }

    private void handlePacket(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte type = in.readByte();

        switch (type) {
            case TYPE_START -> {
                int totalBytes = in.readInt();
                int chunkCount = in.readInt();
                if (buffer == null) {
                    buffer = new AudioBuffer(totalBytes, chunkCount);
                } else {
                    buffer.reset(totalBytes, chunkCount);
                }
                player.stopAndCleanup();
                LOGGER.debug("[MineCast] START: {}bytes, {}chunks", totalBytes, chunkCount);
            }
            case TYPE_CHUNK -> {
                if (buffer == null) return;
                int index = in.readInt();
                byte[] chunkData = in.readAllBytes();
                boolean reset = buffer.addChunk(index, chunkData);
                if (reset) {
                    LOGGER.warn("[MineCast] 청크 순서 오류 (index={}), 버퍼 리셋", index);
                }
            }
            case TYPE_END -> {
                if (buffer == null) return;
                // START 패킷에서 받은 expectedChunkCount와 실제 수신량 비교
                if (!buffer.isComplete(buffer.getExpectedChunkCount())) {
                    LOGGER.warn("[MineCast] 청크 수 불일치 (수신={}, 기대={}), 재생 스킵",
                        buffer.getReceivedChunkCount(), buffer.getExpectedChunkCount());
                    buffer = null;
                    return;
                }
                byte[] mp3 = buffer.toByteArray();
                buffer = null;
                player.play(mp3);
                LOGGER.debug("[MineCast] 재생 시작 ({}bytes)", mp3.length);
            }
            default -> LOGGER.warn("[MineCast] 알 수 없는 패킷 타입: 0x{}", String.format("%02X", type));
        }
    }
}
```

> **주의:** Fabric Networking API 1.21.4에서 `ClientPlayNetworking.registerGlobalReceiver`의 페이로드 타입이 변경됐다. `CustomPacketPayload`를 구현하는 래퍼 클래스가 필요할 수 있다. Fabric 공식 Wiki의 네트워킹 가이드를 참고할 것.

- [ ] **Step 4: MineCastClient.java 구현**

```java
// minecast-client/src/main/java/com/minecast/client/MineCastClient.java
package com.minecast.client;

import com.minecast.client.network.AudioPacketHandler;
import net.fabricmc.api.ClientModInitializer;

public class MineCastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new AudioPacketHandler().register();
    }
}
```

- [ ] **Step 5: 클라이언트 모드 빌드**

```bash
gradle :minecast-client:remapJar
```

Expected: `minecast-client/build/libs/minecast-client-1.0.0.jar` 생성

- [ ] **Step 6: 커밋**

```bash
git add minecast-client/src/
git commit -m "feat(client): fabric mod bootstrap and packet handler"
```

---

## Task 10: 수동 E2E 테스트 및 마무리

**준비물:**
- Paper 1.21.4 서버 JAR
- Fabric Loader + Fabric API가 설치된 Minecraft 1.21.4 클라이언트
- Typecast API 키와 actor_id

- [ ] **Step 1: 서버 설정**

```bash
# Paper 서버 디렉토리에 플러그인 설치
cp minecast-server/build/libs/minecast-server-1.0.0-all.jar /path/to/server/plugins/

# config.yml 수정
# plugins/MineCast/config.yml:
# typecast.api-key: "실제_API_키"
# typecast.actor-id: "실제_ACTOR_ID"
```

- [ ] **Step 2: 클라이언트 설정**

```bash
# Fabric 모드 디렉토리에 설치
cp minecast-client/build/libs/minecast-client-1.0.0.jar ~/.minecraft/mods/
```

- [ ] **Step 3: 기능 테스트**

서버 실행 후 클라이언트 접속, OP 계정으로:

```
/cast 안녕하세요 테스트입니다
```

기대 결과:
- 콘솔에 TTS 요청 로그 출력
- 클라이언트에서 한국어 음성 재생
- 10초 내 재실행 시 쿨다운 메시지 출력

- [ ] **Step 4: 에러 케이스 테스트**

```
# 텍스트 길이 초과
/cast (201자 이상 텍스트)
→ "텍스트가 너무 깁니다" 메시지 확인

# 쿨다운
/cast 첫번째
/cast 두번째  (즉시)
→ 쿨다운 메시지 확인
```

- [ ] **Step 5: 최종 커밋**

```bash
git add .
git commit -m "chore: finalize minecast implementation"
```

---

## 참고: 서버 측 채널 등록 (MinecastPayload 타입 매칭)

서버(Paper)에서 보내는 채널 ID는 `minecast:audio`이고, 클라이언트의 `MinecastPayload.TYPE`도 동일한 `ResourceLocation`을 사용한다. Paper의 `sendPluginMessage`는 채널 이름으로 매칭되므로 별도 처리 없이 호환된다.
