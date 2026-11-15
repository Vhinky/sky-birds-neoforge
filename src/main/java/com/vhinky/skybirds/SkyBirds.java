package com.vhinky.skybirds;

import com.vhinky.skybirds.network.ForecastNetwork;
import com.vhinky.skybirds.network.ServerWeather;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Sky Birds — атмосферные птицы в небе (частицы, а не мобы).
 *
 * Общая часть — работает и на клиенте, и на сервере.
 * На сервере мод необязателен: если он там стоит, игроки с модом получают
 * прогноз погоды (птицы летают низко перед дождём). Игроки без мода
 * спокойно заходят на такой сервер.
 * Всё, что касается отрисовки птиц, — в {@link SkyBirdsClient}.
 */
@Mod(SkyBirds.MODID)
public final class SkyBirds {
    public static final String MODID = "sky_birds";

    public SkyBirds(IEventBus modBus) {
        modBus.addListener(ForecastNetwork::register);
        NeoForge.EVENT_BUS.addListener(ServerWeather::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerWeather::onPlayerLogin);
    }
}
