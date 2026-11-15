package com.vhinky.skybirds;

import com.vhinky.skybirds.client.FlockManager;
import com.vhinky.skybirds.client.Startle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Клиентская часть Sky Birds: настройки, меню настроек и сами птицы.
 * На выделенном сервере этот класс не загружается.
 */
@Mod(value = SkyBirds.MODID, dist = Dist.CLIENT)
public final class SkyBirdsClient {
    public SkyBirdsClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(FlockManager::onClientTick);
        NeoForge.EVENT_BUS.addListener(Startle::onSound);
    }
}
