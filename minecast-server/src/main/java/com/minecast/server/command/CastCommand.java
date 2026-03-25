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
