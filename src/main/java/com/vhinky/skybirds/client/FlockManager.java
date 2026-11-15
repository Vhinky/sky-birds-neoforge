package com.vhinky.skybirds.client;

import com.vhinky.skybirds.Config;
import com.vhinky.skybirds.network.ReceivedForecast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Решает, когда и где появляются стаи, и двигает их.
 */
public final class FlockManager {
    private static final List<Flock> FLOCKS = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static int cooldown = 60;

    /** Дистанция, на которой птицы полностью исчезают (зависит от дальности прорисовки). */
    private static float fadeEnd = 136f;

    /** Текущий прогноз погоды. */
    private static Weather.Forecast forecast = Weather.Forecast.NORMAL;

    /** Вечером вороны летят «на ночёвку» в одну сторону — к лесу. */
    private static float roostYaw;
    private static long roostDay = -1;
    private static double roostX, roostZ;

    private FlockManager() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;

        if (level != lastLevel) {
            clear();
            lastLevel = level;
            cooldown = 60;
            if (level == null) ReceivedForecast.reset(); // вышли с сервера — старый прогноз не нужен
        }
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;

        fadeEnd = Math.min(136f, mc.options.getEffectiveRenderDistance() * 16f * 0.95f);

        // Погода: высота меняется плавно (~полминуты), в дождь птицы прячутся
        forecast = Weather.forecast(mc, level);
        if (forecast == Weather.Forecast.RAINING) {
            // прячутся только там, где реально идёт дождь или снег
            for (Flock f : FLOCKS) if (!f.isDry()) f.leave();
        }

        Iterator<Flock> it = FLOCKS.iterator();
        while (it.hasNext()) {
            Flock f = it.next();
            f.tick(level, player.getX(), player.getZ());
            if (f.isDead()) it.remove();
        }

        // Стрела или трезубец, пролетающие рядом со стаей, — резкий испуг
        if (!FLOCKS.isEmpty()) {
            for (Entity e : level.entitiesForRendering()) {
                if (e instanceof AbstractArrow arrow && arrow.getDeltaMovement().lengthSqr() > 0.25) {
                    startle(arrow.getX(), arrow.getY(), arrow.getZ(), 12.0, true);
                }
            }
        }

        // Игрок на элитрах (или летящий в творческом) рядом со стаей — стая отворачивает
        if (level.getGameTime() % 5 == 0 && !FLOCKS.isEmpty()) {
            for (Player p : level.players()) {
                boolean flying = p.isFallFlying() || (p == player && player.getAbilities().flying);
                if (flying) startle(p.getX(), p.getY(), p.getZ(), 24.0, false);
            }
        }

        if (!Config.ENABLED.get()) {
            clear();
            return;
        }

        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) return;

        if (--cooldown > 0) return;
        RandomSource r = level.random;
        cooldown = (int) ((80 + r.nextInt(160)) / Config.FREQUENCY.get());

        int max = Config.MAX_FLOCKS.get();
        if (status == ParticleStatus.DECREASED) max = Math.max(1, max / 2);
        if (FLOCKS.size() >= max) return;

        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) return;

        // игрок в пещере или под землёй (небесного света нет) — новых стай не создаём
        if (level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition())) == 0) return;

        long time = level.getDayTime() % 24000L;
        float chance = timeFactor(time);
        if (chance <= 0f) return;
        if (isDusk(time)) updateRoost(level, player);

        trySpawn(mc, level, player, chance, time, status);
    }

    private static void trySpawn(Minecraft mc, ClientLevel level, LocalPlayer player, float chance,
                                 long time, ParticleStatus status) {
        RandomSource r = level.random;

        // Вечер: часть стай — вороны, летящие «на ночёвку»: появляются с одной стороны
        // и летят через игрока в сторону леса
        boolean roosting = isDusk(time) && forecast != Weather.Forecast.RAINING && r.nextFloat() < 0.45f;

        // Сначала выбираем место, потом по биому — вид птиц
        double ang = roosting
                ? roostYaw + Math.PI + (r.nextDouble() - 0.5) * 1.2
                : r.nextDouble() * Math.PI * 2;
        double dist = fadeEnd * (0.65 + r.nextDouble() * 0.25);
        double sx = player.getX() + Math.sin(ang) * dist;
        double sz = player.getZ() + Math.cos(ang) * dist;
        BlockPos probe = BlockPos.containing(sx, player.getY(), sz);
        if (!level.hasChunkAt(probe)) return;

        boolean dry = isDry(level, BlockPos.containing(sx, player.getY() + 30, sz));
        // в дождь стаи появляются только над биомами, где осадков не бывает (пустыня, саванна, бесплодные земли)
        if (forecast == Weather.Forecast.RAINING && !dry) return;
        Habitat habitat = Habitat.of(level.getBiome(probe));
        if (r.nextFloat() > chance * habitat.density) return;
        BirdSpecies species = habitat.pick(r);

        if (roosting && habitat != Habitat.OCEAN && habitat != Habitat.ARID) {
            species = BirdSpecies.CROW;
        } else {
            roosting = false;
            // закат над равниной или рекой — мурмурация скворцов (одна на всё небо)
            if (time >= 11500 && time < 12800 && forecast != Weather.Forecast.RAINING
                    && (habitat == Habitat.PLAINS || habitat == Habitat.RIVER)
                    && !hasMurmuration() && r.nextFloat() < 0.25f) {
                species = BirdSpecies.STARLING;
            }
        }
        // примета: перед дождём низко над землёй носятся ласточки
        if (forecast == Weather.Forecast.RAIN_SOON && !dry && habitat != Habitat.OCEAN && r.nextFloat() < 0.4f) {
            species = BirdSpecies.SWALLOW;
        }
        if (crowded(sx, sz, species)) return;
        double heightAbove = species.altMin + r.nextDouble() * species.altRange;

        int minDist = 20; // стаи пролетают немного в стороне, а не строго над головой
        Flock flock;
        if (species.formation == BirdSpecies.Formation.CIRCLE || species.formation == BirdSpecies.Formation.MURMUR) {
            // кружит в стороне от игрока, но в пределах видимости
            float radius = species.formation == BirdSpecies.Formation.MURMUR
                    ? 18f + r.nextFloat() * 10f
                    : 12f + r.nextFloat() * 14f;
            double minCd = minDist + radius + 10.0;
            double maxCd = fadeEnd - radius - 10.0;
            if (maxCd < minCd) return;
            double cd = minCd + r.nextDouble() * (maxCd - minCd);
            sx = player.getX() + Math.sin(ang) * cd;
            sz = player.getZ() + Math.cos(ang) * cd;
            if (!level.hasChunkAt(BlockPos.containing(sx, player.getY(), sz))) return;
            double y = altitude(level, sx, sz, radius, heightAbove * targetAltitude(dry));
            flock = Flock.circling(species, sx, y, sz, radius, r);
        } else {
            double y = altitude(level, sx, sz, 16.0, heightAbove * targetAltitude(dry));
            // пролетают сбоку от игрока, а не прямо над головой
            double side = r.nextBoolean() ? 1.0 : -1.0;
            double miss = (minDist + 10.0 + r.nextDouble() * 40.0) * side;
            double px = Math.cos(ang), pz = -Math.sin(ang); // перпендикуляр к направлению на точку появления
            double tx = player.getX() + px * miss;
            double tz = player.getZ() + pz * miss;
            if (roosting) {
                // цель — дальше, в сторону ночёвки
                tx += Math.sin(roostYaw) * 80.0;
                tz += Math.cos(roostYaw) * 80.0;
            }
            flock = Flock.passing(species, sx, y, sz, tx, tz, r);
        }
        flock.setHeightAbove(heightAbove);
        flock.setDry(dry);
        FLOCKS.add(flock);
        spawnBirds(mc, level, flock, r, status);
    }

    /** Высота появления: над самой высокой точкой рельефа/крон вокруг. */
    private static double altitude(ClientLevel level, double cx, double cz, double radius, double heightAbove) {
        double ground = Flock.groundAt(level, cx, cz);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            ground = Math.max(ground, Flock.groundAt(level, cx + Math.sin(a) * radius, cz + Math.cos(a) * radius));
        }
        double base = Math.max(ground, level.getSeaLevel());
        double h = Math.max(7.0, heightAbove);
        return Math.min(base + h, level.getMaxBuildHeight() + 32.0);
    }

    private static void spawnBirds(Minecraft mc, ClientLevel level, Flock flock, RandomSource r, ParticleStatus status) {
        BirdSpecies sp = flock.species;
        int count = sp.minCount + (sp.maxCount > sp.minCount ? r.nextInt(sp.maxCount - sp.minCount + 1) : 0);
        if (status == ParticleStatus.DECREASED && sp.formation == BirdSpecies.Formation.MURMUR) count /= 2;
        float sizeMul = (float) (double) Config.SIZE_MULTIPLIER.get();

        for (int i = 0; i < count; i++) {
            double ox, oy, oz;
            float driftAmp, driftSpeed;
            switch (sp.formation) {
                case V -> {
                    if (i == 0) {
                        ox = 0;
                        oz = 0;
                    } else {
                        int k = (i + 1) / 2;
                        int side = (i % 2 == 1) ? 1 : -1;
                        ox = side * k * 1.7;
                        oz = -k * 1.5;
                    }
                    oy = (r.nextDouble() - 0.5) * 0.3;
                    driftAmp = 0.15f;
                    driftSpeed = 0.02f;
                }
                case CIRCLE -> {
                    ox = 0;
                    oy = 0;
                    oz = 0;
                    driftAmp = 0.3f;
                    driftSpeed = 0.02f;
                }
                case MURMUR -> {
                    // точка внутри «облака» радиусом ~9 блоков, чуть сплюснутого по высоте
                    double u = r.nextDouble() * 2 - 1;
                    double a = r.nextDouble() * Math.PI * 2;
                    double rr = 9.0 * Math.cbrt(r.nextDouble());
                    double h = Math.sqrt(1 - u * u);
                    ox = Math.cos(a) * h * rr;
                    oy = u * rr * 0.6;
                    oz = Math.sin(a) * h * rr;
                    driftAmp = 0.8f;
                    driftSpeed = 0.05f + r.nextFloat() * 0.03f;
                }
                default -> {
                    double spread = 1.5 + count * 0.4;
                    ox = (r.nextDouble() - 0.5) * 2.0 * spread;
                    oy = (r.nextDouble() - 0.5) * 2.4;
                    oz = (r.nextDouble() - 0.5) * 2.0 * spread;
                    driftAmp = 0.6f + r.nextFloat() * 0.9f;
                    driftSpeed = 0.03f + r.nextFloat() * 0.04f;
                }
            }
            float size = sp.halfSpan * sizeMul * (0.9f + r.nextFloat() * 0.2f);
            mc.particleEngine.add(new BirdParticle(level, flock, ox, oy, oz, driftAmp, driftSpeed, size));
        }
    }

    /**
     * Как у настоящих птиц: больше всего их на рассвете и на закате,
     * в полдень поменьше, ночью нет.
     */
    private static float timeFactor(long t) {
        if (t >= 22500 || t < 1500) return 1.4f; // рассвет
        if (t < 4000) return 1.0f;
        if (t < 9000) return 0.6f;               // полдень
        if (t < 11000) return 1.0f;
        if (t < 12800) return 1.4f;              // закат
        if (t < 13300) return 0.5f;              // сумерки
        return 0f;
    }

    private static boolean isDusk(long t) {
        return t >= 11000 && t < 13000;
    }

    /** Раз в день (или если игрок ушёл далеко) выбираем сторону, где больше леса. */
    private static void updateRoost(ClientLevel level, LocalPlayer player) {
        long day = level.getDayTime() / 24000L;
        double mdx = player.getX() - roostX, mdz = player.getZ() - roostZ;
        if (day == roostDay && mdx * mdx + mdz * mdz < 160.0 * 160.0) return;
        roostDay = day;
        roostX = player.getX();
        roostZ = player.getZ();

        int best = -1, bestScore = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int score = 0;
            for (double d = 40; d <= fadeEnd; d += 24) {
                BlockPos p = BlockPos.containing(player.getX() + Math.sin(a) * d, player.getY(), player.getZ() + Math.cos(a) * d);
                if (!level.hasChunkAt(p)) continue;
                Holder<Biome> b = level.getBiome(p);
                if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_JUNGLE)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        // леса рядом нет — направление «на ночёвку» всё равно одно на весь вечер
        roostYaw = best >= 0 ? (float) (best * Math.PI / 4.0) : (float) ((day * 2654435761L & 1023) / 1024.0 * Math.PI * 2);
    }

    private static boolean hasMurmuration() {
        for (Flock f : FLOCKS) if (f.species == BirdSpecies.STARLING && !f.isDead()) return true;
        return false;
    }

    /**
     * Пугаем стаи в радиусе.
     * strong — резкий звук: птицы разлетаются; иначе стая просто плавно отворачивает.
     */
    static void startle(double x, double y, double z, double radius, boolean strong) {
        for (Flock f : FLOCKS) {
            double dx = f.x - x, dy = f.y - y, dz = f.z - z;
            if (dx * dx + dy * dy + dz * dz >= radius * radius) continue;
            if (strong) f.startle(x, y, z);
            else f.disturb(x, z);
        }
    }

    /**
     * Рядом с точкой уже летит стая — новую там не создаём,
     * чтобы две стаи не шли бок о бок. Клин на небе всегда один.
     */
    private static boolean crowded(double x, double z, BirdSpecies species) {
        for (Flock f : FLOCKS) {
            if (f.isDead()) continue;
            if (species.formation == BirdSpecies.Formation.V && f.species.formation == BirdSpecies.Formation.V) return true;
            double dx = f.x - x, dz = f.z - z;
            if (dx * dx + dz * dz < 60.0 * 60.0) return true;
        }
        return false;
    }

    static boolean isRainingNow() {
        return forecast == Weather.Forecast.RAINING;
    }

    /**
     * Множитель высоты полёта от погоды (0.4 — перед дождём, 1.5 — ясно надолго).
     * Там, где осадков не бывает, дождь птиц не волнует.
     */
    static float targetAltitude(boolean dry) {
        if (dry) return forecast == Weather.Forecast.CLEAR ? Weather.Forecast.CLEAR.altitude : 1f;
        return forecast.altitude;
    }

    /** Зимний биом (такой же порог, как при выборе птиц). */
    static boolean isCold(ClientLevel level, BlockPos pos) {
        return level.getBiome(pos).value().getBaseTemperature() < 0.15f;
    }

    /** Биом без осадков в этой точке (ни дождя, ни снега). */
    static boolean isDry(ClientLevel level, BlockPos pos) {
        return level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.NONE;
    }

    /** Плавное исчезновение только у края дальности прорисовки. */
    static float distanceFade(float dist) {
        float fadeStart = Math.max(fadeEnd - 40f, fadeEnd * 0.6f);
        return Mth.clamp((fadeEnd - dist) / (fadeEnd - fadeStart), 0f, 1f);
    }

    private static void clear() {
        for (Flock f : FLOCKS) f.kill();
        FLOCKS.clear();
    }

    /**
     * Какие птицы где водятся. Веса по порядку: SWALLOW, CROW, GULL, GOOSE, HAWK, STARLING.
     * Скворцы сюда не входят — они появляются только мурмурацией на закате.
     */
    private enum Habitat {
        OCEAN(0.9f, 5, 0, 70, 10, 0, 0),
        RIVER(1.0f, 30, 10, 15, 30, 5, 0),
        MOUNTAIN(0.8f, 15, 30, 0, 5, 40, 0),
        ARID(0.45f, 5, 25, 0, 0, 55, 0),
        // зимние биомы: птиц заметно меньше, ласточки и чайки улетели, остались вороны и ястребы
        COLD(0.35f, 0, 50, 0, 10, 15, 0),
        FOREST(1.0f, 40, 35, 0, 10, 10, 0),
        PLAINS(1.0f, 40, 20, 0, 20, 20, 0);

        final float density;
        final int[] weights;
        final int total;

        Habitat(float density, int... weights) {
            this.density = density;
            this.weights = weights;
            int t = 0;
            for (int w : weights) t += w;
            this.total = t;
        }

        BirdSpecies pick(RandomSource r) {
            int roll = r.nextInt(total);
            BirdSpecies[] all = BirdSpecies.values();
            for (int i = 0; i < weights.length; i++) {
                roll -= weights[i];
                if (roll < 0) return all[i];
            }
            return BirdSpecies.SWALLOW;
        }

        static Habitat of(Holder<Biome> biome) {
            float temp = biome.value().getBaseTemperature();
            // холод проверяем первым: замёрзший океан или река — тоже зима
            if (temp < 0.15f) return COLD;
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) return OCEAN;
            if (biome.is(BiomeTags.IS_RIVER)) return RIVER;
            if (biome.is(BiomeTags.IS_BADLANDS) || (temp >= 1.5f && !biome.value().hasPrecipitation())) return ARID;
            if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return MOUNTAIN;
            if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) return FOREST;
            return PLAINS;
        }
    }
}
