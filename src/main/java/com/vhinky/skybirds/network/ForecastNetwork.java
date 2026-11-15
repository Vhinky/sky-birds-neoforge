package com.vhinky.skybirds.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация канала прогноза. Канал НЕОБЯЗАТЕЛЬНЫЙ: можно заходить
 * с модом на сервер без мода и без мода на сервер с модом.
 */
public final class ForecastNetwork {
    private ForecastNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(ForecastPayload.TYPE, ForecastPayload.STREAM_CODEC,
                (payload, context) -> ReceivedForecast.update(payload));
    }
}
