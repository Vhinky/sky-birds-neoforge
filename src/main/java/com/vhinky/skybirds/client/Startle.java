package com.vhinky.skybirds.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Пугаем птиц громкими звуками: взрывы, гром, фейерверки, выстрелы из лука.
 * Звук слышит клиент — значит, работает и на любом сервере.
 */
public final class Startle {
    private Startle() {}

    public static void onSound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.isRelative()) return;
        String path = sound.getLocation().getPath();

        double strong = strongRadius(path);
        if (strong > 0) {
            FlockManager.startle(sound.getX(), sound.getY(), sound.getZ(), strong, true);
            return;
        }
        double mild = mildRadius(path);
        if (mild > 0) FlockManager.startle(sound.getX(), sound.getY(), sound.getZ(), mild, false);
    }

    /** Резкие громкие звуки — птицы шарахаются в разные стороны. */
    private static double strongRadius(String path) {
        if (path.contains("wither.spawn") || path.contains("ender_dragon.growl")) return 128;
        if (path.contains("lightning_bolt")) return 96;
        if (path.contains("explode")) return 72;
        if (path.contains("firework_rocket.blast") || path.contains("firework_rocket.large_blast")) return 48;
        return 0;
    }

    /** Звуки, которые просто настораживают, — стая плавно отворачивает. */
    private static double mildRadius(String path) {
        if (path.contains("goat_horn")) return 64;
        if (path.contains("block.bell.use")) return 32;
        if (path.contains("arrow.shoot") || path.contains("crossbow.shoot") || path.contains("trident.throw")) return 28;
        return 0;
    }
}
