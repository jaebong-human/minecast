package com.minecast.server.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.minecast.server.audio.AudioBroadcaster;
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
        assertTrue(player.nextMessage().contains("자"));
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
        command.onCommand(player, null, "cast", new String[]{"hi again"});

        Thread.sleep(100);
        String msg = player.nextMessage();
        assertNotNull(msg);
    }
}
