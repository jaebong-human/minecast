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
            (buf, payload) -> buf.writeBytes(payload.data()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new MinecastPayload(bytes);
            }
        );

    @Override
    public Type<MinecastPayload> type() {
        return TYPE;
    }
}
