package com.vhinky.skybirds.network;

/**
 * Последний прогноз, полученный клиентом от сервера.
 * Без клиентских классов, чтобы сервер тоже мог загрузить обработчик пакета.
 */
public final class ReceivedForecast {
    /** Прогноз считается свежим это время после получения (сервер шлёт каждые 5 секунд). */
    private static final long FRESH_MILLIS = 30_000L;

    private static volatile int untilRain;
    private static volatile int clearTime;
    private static volatile long receivedAt;

    private ReceivedForecast() {}

    static void update(ForecastPayload payload) {
        untilRain = payload.untilRain();
        clearTime = payload.clearTime();
        receivedAt = System.currentTimeMillis();
    }

    /** Есть ли свежий прогноз от сервера (то есть мод стоит и на сервере). */
    public static boolean isFresh() {
        return receivedAt != 0L && System.currentTimeMillis() - receivedAt < FRESH_MILLIS;
    }

    /** Тиков до дождя с поправкой на время, прошедшее с получения. */
    public static int untilRain() {
        long passedTicks = (System.currentTimeMillis() - receivedAt) / 50L;
        return (int) Math.max(0L, untilRain - passedTicks);
    }

    public static int clearTime() {
        long passedTicks = (System.currentTimeMillis() - receivedAt) / 50L;
        return (int) Math.max(0L, clearTime - passedTicks);
    }

    /** Забыть прогноз (выход с сервера). */
    public static void reset() {
        receivedAt = 0L;
    }
}
