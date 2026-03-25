package ai.jaebong.minecast.server;

import ai.jaebong.minecast.server.audio.AudioBroadcaster;
import ai.jaebong.minecast.server.audio.AudioChunker;
import ai.jaebong.minecast.server.command.CastCommand;
import ai.jaebong.minecast.server.config.PluginConfig;
import ai.jaebong.minecast.server.network.MinecastPayload;
import ai.jaebong.minecast.server.tts.TypecastClient;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineCastServerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("minecast-server");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(MinecastPayload.TYPE, MinecastPayload.CODEC);

        PluginConfig config = PluginConfig.load(FabricLoader.getInstance().getConfigDir());

        if (config.getApiKey().isEmpty() || config.getVoiceId().isEmpty()) {
            LOGGER.warn("MineCast: config/minecast.json에 apiKey와 voiceId를 설정하세요.");
        }

        TypecastClient ttsClient = new TypecastClient(config);
        AudioBroadcaster broadcaster = new AudioBroadcaster();
        CastCommand.register(config, ttsClient, broadcaster);

        LOGGER.info("MineCast enabled.");
    }
}
