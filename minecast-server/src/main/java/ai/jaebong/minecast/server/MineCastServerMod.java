package ai.jaebong.minecast.server;

import ai.jaebong.minecast.server.audio.AudioBroadcaster;
import ai.jaebong.minecast.server.audio.AudioChunker;
import ai.jaebong.minecast.server.command.CastCommand;
import ai.jaebong.minecast.server.config.PluginConfig;
import ai.jaebong.minecast.server.network.MinecastPayload;
import ai.jaebong.minecast.server.tts.TypecastClient;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MineCastServerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("minecast-server");
    public static ExecutorService EXECUTOR;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(MinecastPayload.TYPE, MinecastPayload.CODEC);

        PluginConfig config = PluginConfig.load(FabricLoader.getInstance().getConfigDir());
        EXECUTOR = Executors.newFixedThreadPool(10);

        if (config.getApiKey().isEmpty() || config.getVoiceId().isEmpty()) {
            LOGGER.warn("MineCast: config/minecast.json에 apiKey와 voiceId를 설정하세요.");
        }

        TypecastClient ttsClient = new TypecastClient(config);
        AudioBroadcaster broadcaster = new AudioBroadcaster();
        AudioChunker chunker = new AudioChunker();
        CastCommand.register(config, ttsClient, broadcaster);

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            if (config.getVoiceId().isEmpty()) return;
            String name = player.getName().getString();
            String text = name + "이(가) 사망했습니다.";
            EXECUTOR.submit(() -> {
                try {
                    byte[] mp3 = ttsClient.fetchMp3(text, config.getVoiceId());
                    List<byte[]> chunks = chunker.split(mp3);
                    for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
                        broadcaster.sendToPlayer(p, mp3, chunks);
                    }
                } catch (Exception e) {
                    LOGGER.warn("MineCast 사망 이벤트 오류: {}", e.getMessage());
                }
            });
        });

        LOGGER.info("MineCast enabled.");
    }
}
