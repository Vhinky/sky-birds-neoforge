package com.vhinky.skybirds.client;

import com.vhinky.skybirds.network.ReceivedForecast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * «Народная примета»: птицы чувствуют погоду.
 * - Дождь идёт — птицы прячутся и не летают.
 * - Скоро дождь — летают низко.
 * - Долго будет ясно — летают высоко.
 *
 * Когда дождь начнётся, знает только сервер:
 * - одиночная игра / свой мир по LAN — читаем прямо из встроенного сервера;
 * - сервер с модом — он присылает прогноз раз в 5 секунд;
 * - сервер без мода — прогноза нет, птицы летают на обычной высоте
 *   (но в дождь всё равно прячутся — сам дождь клиенту виден).
 */
public final class Weather {
    public enum Forecast {
        /** Ясно надолго: летают высоко. */
        CLEAR(1.5f),
        /** Обычная погода. */
        NORMAL(1.0f),
        /** Скоро дождь: летают низко. */
        RAIN_SOON(0.4f),
        /** Дождь или гроза: не летают. */
        RAINING(0.4f);

        /** Множитель высоты полёта над землёй. */
        public final float altitude;

        Forecast(float altitude) {
            this.altitude = altitude;
        }
    }

    /** За сколько тиков до дождя птицы начинают летать низко (6000 = 5 минут). */
    private static final int RAIN_SOON_TICKS = 6000;
    /** Если до дождя больше этого (24000 = 1 игровой день) — «ясно надолго». */
    private static final int CLEAR_LONG_TICKS = 24000;

    private Weather() {}

    public static Forecast forecast(Minecraft mc, ClientLevel level) {
        if (level.isRaining() || level.isThundering() || level.getRainLevel(1f) > 0.2f) {
            return Forecast.RAINING;
        }
        int untilRain, clearTime;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            ServerLevelData data = server.getWorldData().overworldData();
            // пока дождя нет, rainTime — сколько тиков осталось до его начала
            untilRain = data.getRainTime();
            clearTime = data.getClearWeatherTime();
        } else if (ReceivedForecast.isFresh()) {
            untilRain = ReceivedForecast.untilRain();
            clearTime = ReceivedForecast.clearTime();
        } else {
            return Forecast.NORMAL;
        }

        // /weather clear — ясно гарантированно
        if (clearTime > 0) return Forecast.CLEAR;
        if (untilRain < RAIN_SOON_TICKS) return Forecast.RAIN_SOON;
        if (untilRain > CLEAR_LONG_TICKS) return Forecast.CLEAR;
        return Forecast.NORMAL;
    }
}
