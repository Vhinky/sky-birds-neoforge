package com.vhinky.skybirds.network;

import com.vhinky.skybirds.SkyBirds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Прогноз погоды от сервера.
 *
 * @param untilRain тиков до начала дождя (пока его нет)
 * @param clearTime тиков гарантированно ясной погоды (после /weather clear)
 */
public record ForecastPayload(int untilRain, int clearTime) implements CustomPacketPayload {
    public static final Type<ForecastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBirds.MODID, "forecast"));

    public static final StreamCodec<ByteBuf, ForecastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ForecastPayload::untilRain,
            ByteBufCodecs.VAR_INT, ForecastPayload::clearTime,
            ForecastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
