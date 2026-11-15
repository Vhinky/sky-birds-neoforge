package com.vhinky.skybirds.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Серверная часть: раз в 5 секунд рассказывает игрокам с модом,
 * когда начнётся дождь. Игрокам без мода ничего не отправляется.
 */
public final class ServerWeather {
    private static final int SEND_INTERVAL = 100;

    private ServerWeather() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SEND_INTERVAL != 0) return;
        ForecastPayload payload = current(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, payload);
        }
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, current(player.server));
        }
    }

    private static ForecastPayload current(MinecraftServer server) {
        ServerLevelData data = server.getWorldData().overworldData();
        return new ForecastPayload(data.getRainTime(), data.getClearWeatherTime());
    }

    private static void send(ServerPlayer player, ForecastPayload payload) {
        if (player.connection.hasChannel(ForecastPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
