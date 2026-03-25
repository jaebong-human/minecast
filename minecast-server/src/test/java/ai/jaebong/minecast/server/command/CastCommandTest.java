package ai.jaebong.minecast.server.command;

import ai.jaebong.minecast.server.config.PluginConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CastCommandTest {

    private PluginConfig makeConfig(int maxLen) {
        return new PluginConfig("key", "voice", maxLen);
    }

    @Test
    void textLength_withinLimit_passes() {
        PluginConfig config = makeConfig(50);
        String text = "a".repeat(50);
        assertFalse(text.length() > config.getMaxTextLength());
    }

    @Test
    void textLength_exceedsLimit_fails() {
        PluginConfig config = makeConfig(50);
        String text = "a".repeat(51);
        assertTrue(text.length() > config.getMaxTextLength());
    }
}
