package com.vhinky.skybirds.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Стая: общий «центр», за которым летят птицы-частицы.
 */
public final class Flock {
    private static final int FADE_TICKS = 40;
    private static final float TWO_PI = (float) (Math.PI * 2);
    /** Самый низкий допустимый полёт над землёй/кронами (перед дождём). */
    private static final double CLEARANCE = 7.0;
    /** Сколько тиков длится сильный испуг (взрыв, молния). */
    private static final int SCATTER_TICKS = 90;
    /** Через сколько тиков после резкого звука стая как целое начинает разворачиваться. */
    private static final int TURN_DELAY = 12;
    /** Сколько длится мягкое беспокойство (рог, колокол, игрок рядом). */
    private static final int WARY_TICKS = 60;
    /** Сколько тиков пути стаи помним (по нему летят задние птицы). */
    private static final int HIST = 160;

    public final BirdSpecies species;

    double x, y, z;
    /** Направление полёта в радианах: вперёд = (sin(yaw), cos(yaw)). */
    float yaw;

    private float turnRate;
    private float avoidTurn;
    private int avoidTicks;
    /** Обычная высота этой стаи над землёй; погода её умножает. */
    private double heightAbove = 20.0;
    /** Медленное «гуляние» высоты, чтобы стаи летели не по линейке. */
    private double drift;
    /** Под стаей биом без осадков? Проверяется на лету. */
    private boolean dry;
    /** Свой множитель высоты от погоды: плавно тянется к нужному для биома под стаей. */
    private float altFactor = -1f;
    private double desiredY;
    private double groundY = Double.NEGATIVE_INFINITY;
    private final float speed;

    private int age;
    private final int maxAge;
    private int fadeTicks;
    private boolean fading;
    private boolean dead;

    // испуг
    private int scatterTicks;
    private int waryTicks;
    private float fleeYaw;
    /** Номер последнего сильного испуга — по нему птицы узнают, что надо разлетаться. */
    int startleCount;
    /** Откуда был резкий звук. */
    double startleX, startleY, startleZ;

    // путь ведущего: задние птицы пролетают там же, где он, — поворот идёт волной по стае
    private final double[] hx = new double[HIST], hy = new double[HIST], hz = new double[HIST];
    private final float[] hyaw = new float[HIST];
    private int histHead, histCount;

    // кружение (ястребы, мурмурация скворцов); при испуге стая перестаёт кружить и улетает
    private boolean circling;
    private double centerX, centerZ;
    private float angle, radius;
    private int dir;
    private float driftX, driftZ;

    private Flock(BirdSpecies species, double x, double y, double z, float yaw, RandomSource r) {
        this.species = species;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.desiredY = y;
        this.speed = species.speed * (0.85f + r.nextFloat() * 0.3f);
        this.circling = species.formation == BirdSpecies.Formation.CIRCLE
                || species.formation == BirdSpecies.Formation.MURMUR;
        this.maxAge = circling ? 700 + r.nextInt(800) : 900 + r.nextInt(700);
    }

    /** Стая, пролетающая мимо игрока. */
    static Flock passing(BirdSpecies species, double x, double y, double z, double targetX, double targetZ, RandomSource r) {
        float yaw = (float) Math.atan2(targetX - x, targetZ - z);
        return new Flock(species, x, y, z, yaw, r);
    }

    /** Птица (или стая), кружащая над точкой. */
    static Flock circling(BirdSpecies species, double cx, double y, double cz, float radius, RandomSource r) {
        float angle = r.nextFloat() * TWO_PI;
        int dir = r.nextBoolean() ? 1 : -1;
        double x = cx + Mth.sin(angle) * radius;
        double z = cz + Mth.cos(angle) * radius;
        Flock f = new Flock(species, x, y, z, 0f, r);
        f.centerX = cx;
        f.centerZ = cz;
        f.radius = radius;
        f.angle = angle;
        f.dir = dir;
        float windAngle = r.nextFloat() * TWO_PI;
        f.driftX = Mth.sin(windAngle) * 0.02f;
        f.driftZ = Mth.cos(windAngle) * 0.02f;
        f.updateCircleYaw();
        return f;
    }

    void tick(ClientLevel level, double playerX, double playerZ) {
        if (dead) return;
        age++;
        RandomSource r = level.random;
        float scatter = scatter();

        if (circling) {
            angle += dir * speed / radius;
            if (angle > TWO_PI) angle -= TWO_PI;
            if (angle < 0) angle += TWO_PI;
            centerX += driftX;
            centerZ += driftZ;
            x = centerX + Mth.sin(angle) * radius;
            z = centerZ + Mth.cos(angle) * radius;
            updateCircleYaw();
        } else {
            float maxTurn = species.formation == BirdSpecies.Formation.V ? 0.006f : 0.02f * species.wander;
            turnRate += (r.nextFloat() - 0.5f) * 0.004f * species.wander;
            turnRate *= 0.98f;
            turnRate = Mth.clamp(turnRate, -maxTurn, maxTurn);
            yaw += turnRate;
            if (scatterTicks > 0 && scatterTicks < SCATTER_TICKS - TURN_DELAY) {
                // сильный испуг: стая как целое разворачивается не сразу и не резко
                yaw += Mth.clamp(wrap(fleeYaw - yaw), -0.045f, 0.045f);
            } else if (waryTicks > 0) {
                // мягкое беспокойство: плавно отворачиваем
                yaw += Mth.clamp(wrap(fleeYaw - yaw), -0.02f, 0.02f);
            } else if (avoidTicks > 0) {
                // впереди стена или дождь — отворачиваем
                yaw += avoidTurn;
                avoidTicks--;
            }
            yaw = wrap(yaw);
            float v = speed * (1f + scatter * 0.8f + wary() * 0.3f);
            x += Mth.sin(yaw) * v;
            z += Mth.cos(yaw) * v;
        }
        if (scatterTicks > 0) scatterTicks--;
        if (waryTicks > 0) waryTicks--;
        if (circling && waryTicks > 0) {
            // кружащая птица просто смещает круг в сторону от беспокойства
            centerX += Mth.sin(fleeYaw) * 0.06;
            centerZ += Mth.cos(fleeYaw) * 0.06;
        }

        // Высота, погода, препятствия — раз в 5 тиков
        if (age % 5 == 0) {
            drift = Mth.clamp(drift + (r.nextDouble() - 0.5) * 0.8, -4.0, 6.0);
            BlockPos here = BlockPos.containing(x, y, z);
            if (level.hasChunkAt(here)) dry = FlockManager.isDry(level, here);
            groundY = circling ? sampleCircle(level) : sampleAhead(level);
            if (!circling) {
                checkWall(level);
                if (!badAt(level, x, z)) checkBadAhead(level);
            } else if (dry && FlockManager.isRainingNow()) {
                keepCircleDry(level);
            }
            if (groundY > Double.NEGATIVE_INFINITY) {
                // высота над рельефом и кронами, с поправкой на погоду
                double want = Math.max(CLEARANCE, heightAbove * altFactor + drift);
                desiredY = groundY + want;
            }
            // потолок — от высоты мира, так что моды на высокие миры не мешают
            desiredY = Math.min(desiredY, level.getMaxBuildHeight() + 48.0);
        }
        // погода меняет высоту плавно, примерно за полминуты
        float target = FlockManager.targetAltitude(dry);
        if (altFactor < 0f) altFactor = target;
        altFactor += (target - altFactor) * 0.003f;

        double bob = Math.sin(age * 0.015) * 1.5;
        double dy = desiredY + bob + scatter * 8.0 + wary() * 3.0 - y;
        if (y < groundY + CLEARANCE - 2.0) {
            // слишком низко — набираем высоту быстро
            y += Math.min(Math.max(dy, 0.0), 0.5);
        } else {
            y += Mth.clamp(dy, -0.12, 0.25 + scatter * 0.3);
        }

        recordPath();

        // Жизненный цикл
        if (!fading && age > maxAge) fading = true;
        if (fading && ++fadeTicks >= FADE_TICKS) dead = true;

        double ddx = x - playerX, ddz = z - playerZ;
        if (age > 20 && ddx * ddx + ddz * ddz > 220.0 * 220.0) dead = true;
    }

    private double sampleAhead(ClientLevel level) {
        double sx = Mth.sin(yaw), cz = Mth.cos(yaw);
        double max = Double.NEGATIVE_INFINITY;
        for (int d = 0; d <= 40; d += 8) {
            // по курсу и чуть в стороны — стая широкая
            max = Math.max(max, groundAt(level, x + sx * d, z + cz * d));
            max = Math.max(max, groundAt(level, x + sx * d + cz * 6, z + cz * d - sx * 6));
            max = Math.max(max, groundAt(level, x + sx * d - cz * 6, z + cz * d + sx * 6));
        }
        return max;
    }

    /**
     * Крутой склон: если в ближайших блоках по курсу земля выше, чем стая успеет
     * подняться, стая разворачивается туда, где ниже.
     */
    private void checkWall(ClientLevel level) {
        double fx = Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = Mth.cos(yaw), rz = -Mth.sin(yaw); // сторона, куда поворачивает рост yaw
        for (int d = 6; d <= 30; d += 6) {
            double g = groundAt(level, x + fx * d, z + fz * d);
            // за d блоков стая поднимется примерно на d / speed * 0.5
            double canClimb = d / Math.max(speed, 0.1) * 0.5;
            if (g + 6.0 > y + canClimb) {
                double plus = groundAt(level, x + fx * 12 + rx * 20, z + fz * 12 + rz * 20);
                double minus = groundAt(level, x + fx * 12 - rx * 20, z + fz * 12 - rz * 20);
                float dirSign = plus <= minus ? 1f : -1f;
                if (avoidTicks <= 0) avoidTurn = dirSign * 0.07f;
                avoidTicks = 12;
                return;
            }
        }
    }

    /**
     * Куда лететь не надо: туда, где идёт дождь (если стая сейчас над сухим биомом),
     * и в зимние биомы для теплолюбивых птиц (ласточки, чайки, скворцы).
     * Если впереди такое место, стая сворачивает в хорошую сторону, а если плохо
     * с обеих сторон — разворачивается обратно.
     */
    private void checkBadAhead(ClientLevel level) {
        double fx = Mth.sin(yaw), fz = Mth.cos(yaw);
        if (!badAt(level, x + fx * 28, z + fz * 28)) return;

        double rx = Mth.cos(yaw), rz = -Mth.sin(yaw);
        boolean plusOk = !badAt(level, x + fx * 16 + rx * 24, z + fz * 16 + rz * 24);
        boolean minusOk = !badAt(level, x + fx * 16 - rx * 24, z + fz * 16 - rz * 24);
        float dirSign;
        if (plusOk != minusOk) dirSign = plusOk ? 1f : -1f;
        else dirSign = avoidTurn != 0f ? Math.signum(avoidTurn) : 1f; // плохо с обеих сторон — разворот
        if (avoidTicks <= 0) avoidTurn = dirSign * 0.06f;
        avoidTicks = 10;
    }

    private boolean badAt(ClientLevel level, double px, double pz) {
        BlockPos p = BlockPos.containing(px, y, pz);
        if (!level.hasChunkAt(p)) return false; // не знаем — не дёргаемся
        if (species.avoidsCold() && FlockManager.isCold(level, p)) return true;
        return FlockManager.isRainingNow() && !FlockManager.isDry(level, p);
    }

    private void recordPath() {
        histHead = (histHead + 1) % HIST;
        hx[histHead] = x;
        hy[histHead] = y;
        hz[histHead] = z;
        hyaw[histHead] = yaw;
        if (histCount < HIST) histCount++;
    }

    /** Птицы этой стаи летят «след в след» за ведущим (не кружат). */
    boolean followsPath() {
        return !circling && species.formation != BirdSpecies.Formation.MURMUR;
    }

    float baseSpeed() {
        return speed;
    }

    /**
     * Где был ведущий ticksAgo тиков назад: out = {x, y, z, yaw}.
     * Если столько истории нет — продлеваем путь назад по прямой.
     */
    void pathSample(double ticksAgo, double[] out) {
        if (histCount == 0) {
            out[0] = x - Mth.sin(yaw) * speed * ticksAgo;
            out[1] = y;
            out[2] = z - Mth.cos(yaw) * speed * ticksAgo;
            out[3] = yaw;
            return;
        }
        double maxBack = histCount - 1;
        double back = Math.max(0.0, Math.min(ticksAgo, maxBack));
        int i0 = (int) Math.floor(back);
        int i1 = Math.min(i0 + 1, (int) maxBack);
        double f = back - i0;
        int a = Math.floorMod(histHead - i0, HIST), b = Math.floorMod(histHead - i1, HIST);
        out[0] = hx[a] + (hx[b] - hx[a]) * f;
        out[1] = hy[a] + (hy[b] - hy[a]) * f;
        out[2] = hz[a] + (hz[b] - hz[a]) * f;
        out[3] = hyaw[a] + wrap(hyaw[b] - hyaw[a]) * (float) f;
        double extra = ticksAgo - back;
        if (extra > 0) {
            out[0] -= Math.sin(out[3]) * speed * extra;
            out[2] -= Math.cos(out[3]) * speed * extra;
        }
    }

    /** Кружащая птица над сухим биомом: не даём ветру снести её туда, где дождь. */
    private void keepCircleDry(ClientLevel level) {
        double ax = centerX + driftX * 400 + Math.signum(driftX) * radius;
        double az = centerZ + driftZ * 400 + Math.signum(driftZ) * radius;
        if (!dryAt(level, ax, az)) {
            driftX = -driftX;
            driftZ = -driftZ;
        }
    }

    private boolean dryAt(ClientLevel level, double px, double pz) {
        BlockPos p = BlockPos.containing(px, y, pz);
        if (!level.hasChunkAt(p)) return true; // не знаем — не дёргаемся
        return FlockManager.isDry(level, p);
    }

    private double sampleCircle(ClientLevel level) {
        double max = groundAt(level, centerX, centerZ);
        for (int i = 0; i < 8; i++) {
            float a = i * TWO_PI / 8f;
            max = Math.max(max, groundAt(level, centerX + Mth.sin(a) * radius, centerZ + Mth.cos(a) * radius));
        }
        return max;
    }

    /** Высота верхнего блока, включая листву. Для незагруженных чанков — минус бесконечность. */
    static double groundAt(ClientLevel level, double px, double pz) {
        int bx = Mth.floor(px), bz = Mth.floor(pz);
        if (!level.hasChunkAt(new BlockPos(bx, 0, bz))) return Double.NEGATIVE_INFINITY;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    private void updateCircleYaw() {
        // касательная к окружности
        yaw = (float) Math.atan2(dir * Mth.cos(angle), -dir * Mth.sin(angle));
    }

    /**
     * Мурмурация: облако скворцов всё время меняет форму — растягивается,
     * сжимается, перекатывается волнами и медленно поворачивается.
     * p — смещение птицы в стае, меняется на месте.
     */
    void warp(double[] p) {
        double t = age * 0.02;
        double sx = 1.0 + 0.55 * Math.sin(t * 1.3);
        double sy = 0.7 + 0.45 * Math.sin(t * 0.9 + 1.0);
        double sz = 1.0 + 0.55 * Math.cos(t * 1.1 + 2.0);
        double x0 = p[0] * sx, y0 = p[1] * sy, z0 = p[2] * sz;
        x0 += 3.5 * Math.sin(t * 2.1 + z0 * 0.35);
        y0 += 2.5 * Math.sin(t * 1.7 + x0 * 0.30);
        z0 += 3.0 * Math.cos(t * 1.5 + y0 * 0.40);
        double a = t * 0.6, c = Math.cos(a), s = Math.sin(a);
        p[0] = x0 * c - z0 * s;
        p[1] = y0;
        p[2] = x0 * s + z0 * c;
    }

    /**
     * Резкий громкий звук (взрыв, молния): птицы разлетаются каждая сама по себе,
     * стая потом медленно разворачивается прочь и собирается снова.
     */
    void startle(double sx, double sy, double sz) {
        if (dead || fading) return;
        if (scatterTicks > SCATTER_TICKS / 2) return; // уже напуганы
        double dx = x - sx, dz = z - sz;
        fleeYaw = (dx * dx + dz * dz) < 1.0 ? yaw : (float) Math.atan2(dx, dz);
        scatterTicks = SCATTER_TICKS;
        startleCount++;
        startleX = sx;
        startleY = sy;
        startleZ = sz;
        if (circling) {
            // перестаём кружить и улетаем
            circling = false;
        }
    }

    /** Что-то беспокоит (рог, колокол, стрела, игрок рядом): стая плавно отворачивает. */
    void disturb(double sx, double sz) {
        if (dead || fading || scatterTicks > 0) return;
        double dx = x - sx, dz = z - sz;
        if (dx * dx + dz * dz < 1.0) return;
        fleeYaw = (float) Math.atan2(dx, dz);
        waryTicks = WARY_TICKS;
    }

    private float wary() {
        return waryTicks / (float) WARY_TICKS;
    }

    /** 0..1 — насколько стая сейчас напугана (разлетается, ускоряется, набирает высоту). */
    float scatter() {
        float s = scatterTicks / (float) SCATTER_TICKS;
        return s * s * (3f - 2f * s);
    }

    /** 0..1 — плавное появление и исчезновение стаи. */
    float fade(float partialTick) {
        float in = Mth.clamp((age + partialTick) / FADE_TICKS, 0f, 1f);
        float out = fading ? Mth.clamp(1f - (fadeTicks + partialTick) / FADE_TICKS, 0f, 1f) : 1f;
        return in * out;
    }

    /** Обычная высота над землёй, выбранная при появлении. */
    void setHeightAbove(double h) {
        this.heightAbove = h;
    }

    /** Улететь/спрятаться: плавно исчезнуть (например, начался дождь). */
    void leave() {
        if (!fading && !dead) fading = true;
    }

    void setDry(boolean dry) {
        this.dry = dry;
    }

    boolean isDry() {
        return dry;
    }

    void kill() {
        dead = true;
    }

    boolean isDead() {
        return dead;
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= TWO_PI;
        while (a < -Math.PI) a += TWO_PI;
        return a;
    }
}
