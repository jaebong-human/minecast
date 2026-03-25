package ai.jaebong.minecast.server.command;

import ai.jaebong.minecast.server.MineCastServerMod;
import ai.jaebong.minecast.server.audio.AudioBroadcaster;
import ai.jaebong.minecast.server.audio.AudioChunker;
import ai.jaebong.minecast.server.config.PluginConfig;
import ai.jaebong.minecast.server.tts.TypecastClient;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CastCommand {
    private static PluginConfig config;
    private static TypecastClient ttsClient;
    private static AudioBroadcaster broadcaster;
    private static final AudioChunker chunker = new AudioChunker();

    private record CastRequest(CommandSourceStack source, String text, String voiceId) {}

    private static final LinkedBlockingQueue<CastRequest> queue = new LinkedBlockingQueue<>();

    public static void register(PluginConfig cfg, TypecastClient tts, AudioBroadcaster bc) {
        config = cfg;
        ttsClient = tts;
        broadcaster = bc;

        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    CastRequest req = queue.take();
                    process(req);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "minecast-queue");
        worker.setDaemon(true);
        worker.start();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("cast")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(CastCommand::execute))
            )
        );
    }

    private static final Pattern VOICE_PATTERN =
        Pattern.compile("^(.*?)\\s*--voice\\s+(\\S+)\\s*$", Pattern.DOTALL);

    private record ParsedInput(String text, String voiceId) {}

    private static ParsedInput parseInput(String raw) {
        Matcher m = VOICE_PATTERN.matcher(raw);
        if (m.matches()) {
            return new ParsedInput(m.group(1).trim(), m.group(2).trim());
        }
        return new ParsedInput(raw.trim(), null);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ParsedInput input = parseInput(StringArgumentType.getString(ctx, "text"));
        String text = input.text();
        String voiceId = input.voiceId() != null ? input.voiceId() : config.getVoiceId();

        if (voiceId.isEmpty()) {
            source.sendFailure(Component.literal(
                "voice_id가 없습니다. config/minecast.json에 설정하거나 --voice <id>를 사용하세요."));
            return 0;
        }

        if (text.length() > config.getMaxTextLength()) {
            source.sendFailure(Component.literal(
                "텍스트가 너무 깁니다. 최대 " + config.getMaxTextLength() + "자."));
            return 0;
        }

        queue.offer(new CastRequest(source, text, voiceId));
        return 1;
    }

    private static void process(CastRequest req) {
        try {
            byte[] mp3 = ttsClient.fetchMp3(req.text(), req.voiceId());
            List<byte[]> chunks = chunker.split(mp3);
            List<ServerPlayer> players = req.source().getServer().getPlayerList().getPlayers();
            players.parallelStream().forEach(player -> {
                try {
                    broadcaster.sendToPlayer(player, mp3, chunks);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            req.source().sendFailure(Component.literal("TTS 오류: " + e.getMessage()));
        }
    }
}
