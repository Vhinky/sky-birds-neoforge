package com.vhinky.skybirds.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Одна птица. Это обычная частица: без хитбокса, без ИИ, без сохранения в мир.
 * Рисуется маленькой 3D-моделью: объёмное тело-«веретено» с головой и клювом,
 * текстурированные крылья из двух частей (машут) и хвост.
 */
public final class BirdParticle extends Particle {
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float PX_U = 1f / BirdRenderType.ATLAS_W;
    private static final float PX_V = 1f / BirdRenderType.ATLAS_H;

    private final Flock flock;
    private final BirdSpecies species;
    private final double ox, oy, oz;
    private final float driftPhase, driftSpeed, driftAmp;
    private final float size;
    private final float shade;
    /** Своя небольшая «реакция»: насколько позже других птица повторяет манёвр ведущего. */
    private final float followLag;
    private final double[] pathTmp = new double[4];

    private float yaw, prevYaw;
    private float pitch, prevPitch;
    private float roll, prevRoll;
    private float phase, prevPhase;
    private float amp = 1f, prevAmp = 1f;
    private boolean gliding;
    private int stateTimer;

    // личный испуг: каждая птица реагирует сама, со своей задержкой и в свою сторону
    private int seenStartle;
    private int reactDelay = -1;
    private float panic;
    private double pvx, pvy, pvz;

    // временные значения для отрисовки
    private float rpx, rpy, rpz;
    private float cy, sy, cp, sp, cr, sr;
    private float light;
    private int cA;

    BirdParticle(ClientLevel level, Flock flock, double ox, double oy, double oz,
                 float driftAmp, float driftSpeed, float size) {
        super(level, flock.x, flock.y, flock.z);
        this.flock = flock;
        this.species = flock.species;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.driftAmp = driftAmp;
        this.driftSpeed = driftSpeed;
        this.driftPhase = random.nextFloat() * TWO_PI;
        this.size = size;
        this.shade = 0.92f + random.nextFloat() * 0.16f;
        this.followLag = random.nextFloat() * 6f;

        this.hasPhysics = false;
        this.gravity = 0f;
        this.lifetime = Integer.MAX_VALUE;
        this.setSize(size * 2f, size);

        double[] t = target();
        this.setPos(t[0], t[1], t[2]);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        this.yaw = this.prevYaw = flock.yaw;
        this.seenStartle = flock.startleCount;
        this.phase = this.prevPhase = random.nextFloat() * TWO_PI;
        this.stateTimer = random.nextInt(40);
    }

    private double[] target() {
        float t = this.age;
        double lx = ox + Mth.sin(t * driftSpeed + driftPhase) * driftAmp;
        double ly = oy + Mth.sin(t * driftSpeed * 1.3f + driftPhase * 2f) * driftAmp * 0.5f;
        double lz = oz + Mth.cos(t * driftSpeed * 0.8f + driftPhase) * driftAmp;
        if (species.formation == BirdSpecies.Formation.MURMUR) {
            double[] p = {lx, ly, lz};
            flock.warp(p);
            lx = p[0];
            ly = p[1];
            lz = p[2];
        }
        // после испуга стая какое-то время летит рыхлее
        float sc = flock.scatter();
        if (sc > 0f) {
            double k = 1.0 + 0.6 * sc;
            lx *= k;
            ly *= k;
            lz *= k;
        }
        double bx, by, bz, yawB;
        if (flock.followsPath()) {
            // Птица позади ведущего летит там, где он был недавно: поворот проходит
            // по стае волной, а не всей стаей сразу, как твёрдое тело.
            double behind = Math.max(0.0, -lz);
            double delay = behind / Math.max(flock.baseSpeed(), 0.05f) + followLag;
            flock.pathSample(delay, pathTmp);
            bx = pathTmp[0];
            by = pathTmp[1];
            bz = pathTmp[2];
            yawB = pathTmp[3];
            lz = Math.max(0.0, lz); // впереди ведущего — просто чуть дальше по курсу
        } else {
            bx = flock.x;
            by = flock.y;
            bz = flock.z;
            yawB = flock.yaw;
        }
        double s = Math.sin(yawB), c = Math.cos(yawB);
        return new double[] {
                bx + lx * c + lz * s,
                by + ly,
                bz - lx * s + lz * c
        };
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        prevYaw = yaw;
        prevPitch = pitch;
        prevRoll = roll;
        prevPhase = phase;
        prevAmp = amp;

        if (flock.isDead()) {
            remove();
            return;
        }
        age++;

        updatePanic();

        double[] t = target();
        double nx = x + (t[0] - x) * 0.3;
        double ny = y + (t[1] - y) * 0.3;
        double nz = z + (t[2] - z) * 0.3;
        if (panic > 0f) {
            // своя траектория испуга, постепенно возвращаемся к стае
            double px = x + pvx, py = y + pvy, pz = z + pvz;
            float w = panic * panic * (3f - 2f * panic);
            nx = px * w + nx * (1f - w);
            ny = py * w + ny * (1f - w);
            nz = pz * w + nz * (1f - w);
        }
        // страховка: птица никогда не оказывается в кронах или в скале
        double ground = Flock.groundAt(level, nx, nz);
        if (ny < ground + 3.0) ny = ground + 3.0;
        else if (ny < ground + 5.0) ny = Math.min(ground + 5.0, ny + 0.6);
        setPos(nx, ny, nz);

        double vx = x - xo, vy = y - yo, vz = z - zo;
        double h = Math.sqrt(vx * vx + vz * vz);

        if (h > 1.0E-3) {
            float targetYaw = (float) Math.atan2(vx, vz);
            yaw += wrap(targetYaw - yaw) * 0.35f;
            yaw = wrap(yaw);
        }
        float yawRate = wrap(yaw - prevYaw);
        roll += (Mth.clamp(-yawRate * 10f, -0.8f, 0.8f) - roll) * 0.2f;

        float targetPitch = (float) Mth.clamp(Math.atan2(vy, Math.max(h, 0.05)), -0.6, 0.6);
        pitch += (targetPitch - pitch) * 0.2f;

        // взмахи / парение
        if (--stateTimer <= 0) {
            gliding = random.nextFloat() < species.glideChance;
            stateTimer = gliding ? 30 + random.nextInt(60) : 15 + random.nextInt(40);
        }
        boolean glideNow = gliding;
        if (panic > 0.15f || flock.scatter() > 0.2f) glideNow = false; // напуганные птицы часто машут
        if (vy > 0.06) glideNow = false;
        else if (vy < -0.08) glideNow = true;

        amp += ((glideNow ? 0f : 1f) - amp) * 0.12f;
        phase += species.flapSpeed;
        if (phase > TWO_PI) {
            phase -= TWO_PI;
            prevPhase -= TWO_PI;
        }
    }

    /**
     * Сильный испуг: птица замечает его не сразу (дальние от звука — позже, как волна),
     * шарахается прочь со своим разбросом и вверх, потом плавно возвращается в стаю.
     */
    private void updatePanic() {
        if (flock.startleCount != seenStartle) {
            seenStartle = flock.startleCount;
            double dx = x - flock.startleX, dz = z - flock.startleZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            reactDelay = 1 + random.nextInt(8) + (int) Math.min(12.0, dist / 6.0);
        }
        if (reactDelay > 0) {
            reactDelay--;
        } else if (reactDelay == 0) {
            reactDelay = -1;
            double dx = x - flock.startleX, dz = z - flock.startleZ;
            float away = (dx * dx + dz * dz) < 1.0 ? random.nextFloat() * TWO_PI : (float) Math.atan2(dx, dz);
            float dirYaw = away + (random.nextFloat() - 0.5f) * 2.8f; // разброс ±80°
            float sp = species.speed * (1.6f + random.nextFloat() * 1.2f);
            pvx = Mth.sin(dirYaw) * sp;
            pvz = Mth.cos(dirYaw) * sp;
            pvy = 0.15 + random.nextDouble() * 0.25;
            panic = 1f;
        }
        if (panic > 0f) {
            // скорость затухает, траектория слегка виляет
            pvx *= 0.97;
            pvz *= 0.97;
            pvy *= 0.94;
            float wob = (random.nextFloat() - 0.5f) * 0.15f;
            double c = Math.cos(wob), s = Math.sin(wob);
            double vx = pvx * c + pvz * s;
            pvz = -pvx * s + pvz * c;
            pvx = vx;
            panic = Math.max(0f, panic - 0.009f);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return BirdRenderType.INSTANCE;
    }

    // ---- форма тела: кольца вдоль оси «хвост → клюв», всё в долях длины тела ----
    //                               z       ширина  высота  центр по Y
    private static final float[][] PROFILE = {
            {-0.42f, 0.030f, 0.022f, 0.010f},  // основание хвоста
            {-0.32f, 0.070f, 0.050f, 0.010f},
            {-0.18f, 0.112f, 0.095f, 0.000f},
            {-0.03f, 0.132f, 0.125f, -0.012f}, // грудь — самое толстое место, брюхо ниже
            {0.09f, 0.122f, 0.118f, -0.005f},
            {0.17f, 0.088f, 0.085f, 0.012f},   // шея
            {0.23f, 0.070f, 0.070f, 0.025f},   // голова
            {0.28f, 0.085f, 0.082f, 0.032f},
            {0.33f, 0.082f, 0.078f, 0.034f},
            {0.37f, 0.060f, 0.056f, 0.030f},
            {0.385f, 0.034f, 0.028f, 0.026f},  // основание клюва
            {0.385f, 0.0f, 0.0f, 0.018f}       // кончик клюва (z сдвигается на длину клюва)
    };
    /** 0 — туловище, 1 — голова, 2 — клюв. */
    private static final int[] RING_PART = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2};
    /** Где каждое кольцо лежит на текстуре тела (0 — хвост, 1 — кончик клюва). */
    private static final float[] RING_V = {0.0f, 0.08f, 0.20f, 0.34f, 0.47f, 0.58f, 0.66f, 0.74f, 0.82f, 0.88f, 0.93f, 1.0f};
    private static final int SIDES = 8;
    /** Вершин по кругу на одну больше: у текстуры есть шов. */
    private static final int COLS = SIDES + 1;
    private static final int RINGS = PROFILE.length;

    private final float[] ringX = new float[RINGS * COLS];
    private final float[] ringY = new float[RINGS * COLS];
    private final float[] ringZ = new float[RINGS * COLS];
    private final float[] ringU = new float[RINGS * COLS];
    private final float[] ringV = new float[RINGS * COLS];
    private final int[] ringC = new int[RINGS * COLS];

    private float outX, outY, outZ;

    @Override
    public void render(VertexConsumer buf, Camera camera, float pt) {
        Vec3 cam = camera.getPosition();
        rpx = (float) (Mth.lerp(pt, xo, x) - cam.x);
        rpy = (float) (Mth.lerp(pt, yo, y) - cam.y);
        rpz = (float) (Mth.lerp(pt, zo, z) - cam.z);
        float dist = Mth.sqrt(rpx * rpx + rpy * rpy + rpz * rpz);

        float alpha = flock.fade(pt) * FlockManager.distanceFade(dist);
        if (alpha < 0.02f) return;
        cA = (int) (alpha * 255f);
        light = Mth.clamp(level.getSkyDarken(pt), 0.25f, 1f) * shade;

        float yawR = prevYaw + wrap(yaw - prevYaw) * pt;
        float pitchR = Mth.lerp(pt, prevPitch, pitch);
        float rollR = Mth.lerp(pt, prevRoll, roll);
        cy = Mth.cos(yawR);
        sy = Mth.sin(yawR);
        cp = Mth.cos(pitchR);
        sp = Mth.sin(pitchR);
        cr = Mth.cos(rollR);
        sr = Mth.sin(rollR);

        // Видим ли мы птицу сверху? (ось «вверх» птицы смотрит на камеру)
        rotate(0f, 1f, 0f);
        boolean fromAbove = outX * rpx + outY * rpy + outZ * rpz < 0f;

        float ph = Mth.lerp(pt, prevPhase, phase);
        float am = Mth.lerp(pt, prevAmp, amp);
        float flap = Mth.sin(ph) * am + (1f - am) * 0.12f;
        float flapOuter = Mth.sin(ph - 0.6f) * am + (1f - am) * 0.05f;

        float S = size;
        float L = S * species.body * 1.3f;
        float rowV = species.ordinal() * 16f;

        renderBody(buf, L, rowV);

        // ---- хвост: текстура 64..80 ----
        float tw = S * 0.55f;
        float tz0 = -0.30f * L;
        float tailLight = fromAbove ? 1f : 0.82f;
        quad(buf, tailLight,
                -tw * 0.5f, 0, tz0, 64f, rowV,
                tw * 0.5f, 0, tz0, 80f, rowV,
                tw * 0.5f, 0, tz0 - tw, 80f, rowV + 16f,
                -tw * 0.5f, 0, tz0 - tw, 64f, rowV + 16f);

        // ---- крылья: верх 0..32, низ 32..64; внутренняя половина / внешняя ----
        float wu = fromAbove ? 0f : 32f;
        float wingLight = fromAbove ? 1f : 0.85f;
        float ia = flap * 0.6f;
        float oa = ia + flapOuter * 0.5f;
        float hi = S * 0.5f, ho = S * 0.5f;
        float chord = S * 0.5f;
        float zf = chord * 0.38f, zb = -chord * 0.62f;
        float sw = S * species.sweep * 0.4f;

        for (int side = -1; side <= 1; side += 2) {
            // крыло растёт прямо из середины тела — щели нет
            float ex = side * hi * Mth.cos(ia);
            float ey = hi * Mth.sin(ia);
            float tx = ex + side * ho * Mth.cos(oa);
            float ty = ey + ho * Mth.sin(oa);

            quad(buf, wingLight,
                    0, 0, zf, wu, rowV,
                    ex, ey, zf, wu + 16f, rowV,
                    ex, ey, zb, wu + 16f, rowV + 16f,
                    0, 0, zb, wu, rowV + 16f);
            quad(buf, wingLight * 0.96f,
                    ex, ey, zf, wu + 16f, rowV,
                    tx, ty, zf - sw, wu + 32f, rowV,
                    tx, ty, zb - sw, wu + 32f, rowV + 16f,
                    ex, ey, zb, wu + 16f, rowV + 16f);
        }
    }

    /**
     * Объёмное тело: кольца разного размера, на которые натянута текстура
     * (перья на спине, пестрины на брюхе, глаза, клюв). Свет — сверху.
     * Текстура тела в атласе: x 80..112, своя полоса у каждого вида.
     */
    private void renderBody(VertexConsumer buf, float L, float rowV) {
        for (int i = 0; i < RINGS; i++) {
            float[] pr = PROFILE[i];
            int part = RING_PART[i];
            float zf = pr[0];
            if (zf > 0.17f) zf = 0.17f + (zf - 0.17f) * species.neck;
            if (i == RINGS - 1) zf += species.beakLen;
            float rx = pr[1], ry = pr[2], yc = pr[3];
            if (part == 0) {
                rx *= species.thick;
                ry *= species.thick;
            } else if (part == 1) {
                rx *= species.headSize;
                ry *= species.headSize;
            }
            if (i == RINGS - 1 && species == BirdSpecies.HAWK) yc -= 0.02f; // крючковатый клюв

            float v = (rowV + 0.02f + RING_V[i] * 15.96f) * PX_V;
            for (int j = 0; j < COLS; j++) {
                float a = j * (float) (Math.PI * 2) / SIDES;
                float ca = Mth.cos(a), sa = Mth.sin(a);
                int k = i * COLS + j;

                rotate(ca * rx * L, (yc + sa * ry) * L, zf * L);
                ringX[k] = rpx + outX;
                ringY[k] = rpy + outY;
                ringZ[k] = rpz + outZ;
                ringU[k] = (80.02f + (j / (float) SIDES) * 31.96f) * PX_U;
                ringV[k] = v;

                // освещение: нормаль, повёрнутая вместе с птицей
                rotate(ca, sa, 0f);
                float bright = (0.62f + 0.38f * (outY * 0.5f + 0.5f)) * light;
                ringC[k] = (int) (Mth.clamp(bright, 0f, 1f) * 255f);
            }
        }

        for (int i = 0; i < RINGS - 1; i++) {
            for (int j = 0; j < SIDES; j++) {
                int a = i * COLS + j, b = i * COLS + j + 1, c = (i + 1) * COLS + j + 1, d = (i + 1) * COLS + j;
                bodyV(buf, a);
                bodyV(buf, b);
                bodyV(buf, c);
                bodyV(buf, d);
                bodyV(buf, d);
                bodyV(buf, c);
                bodyV(buf, b);
                bodyV(buf, a);
            }
        }
    }

    private void bodyV(VertexConsumer buf, int k) {
        int c = ringC[k];
        buf.addVertex(ringX[k], ringY[k], ringZ[k])
                .setUv(ringU[k], ringV[k])
                .setColor(c, c, c, cA);
    }

    /** Поворот точки из системы птицы в мир (без сдвига): крен → тангаж → курс. */
    private void rotate(float lx, float ly, float lz) {
        float x1 = lx * cr - ly * sr;
        float y1 = lx * sr + ly * cr;
        float y2 = y1 * cp + lz * sp;
        float z2 = -y1 * sp + lz * cp;
        outX = x1 * cy + z2 * sy;
        outY = y2;
        outZ = -x1 * sy + z2 * cy;
    }

    /**
     * Текстурированный полигон с обеих сторон (одинаковая текстура, мерцания нет).
     * UV в пикселях атласа.
     */
    private void quad(VertexConsumer buf, float bright,
                      float ax, float ay, float az, float au, float av,
                      float bx, float by, float bz, float bu, float bv,
                      float cx, float cy2, float cz, float cu, float cv,
                      float dx, float dy, float dz, float du, float dv) {
        float minU = Math.min(Math.min(au, bu), Math.min(cu, du));
        float minV = Math.min(Math.min(av, bv), Math.min(cv, dv));
        int c = (int) (Mth.clamp(light * bright, 0f, 1f) * 255f);

        v(buf, ax, ay, az, insetU(au, minU), inset(av, minV), c);
        v(buf, bx, by, bz, insetU(bu, minU), inset(bv, minV), c);
        v(buf, cx, cy2, cz, insetU(cu, minU), inset(cv, minV), c);
        v(buf, dx, dy, dz, insetU(du, minU), inset(dv, minV), c);

        v(buf, dx, dy, dz, insetU(du, minU), inset(dv, minV), c);
        v(buf, cx, cy2, cz, insetU(cu, minU), inset(cv, minV), c);
        v(buf, bx, by, bz, insetU(bu, minU), inset(bv, minV), c);
        v(buf, ax, ay, az, insetU(au, minU), inset(av, minV), c);
    }

    /** Шов между внутренней и внешней половиной крыла не сдвигаем. */
    private static float insetU(float p, float min) {
        return (p == 16f || p == 48f) ? p : inset(p, min);
    }

    /** Чуть сдвигаем край региона внутрь, чтобы не захватить соседний. */
    private static float inset(float p, float min) {
        return p == min ? p + 0.02f : p - 0.02f;
    }

    private void v(VertexConsumer buf, float lx, float ly, float lz, float pu, float pv, int c) {
        rotate(lx, ly, lz);
        buf.addVertex(rpx + outX, rpy + outY, rpz + outZ)
                .setUv(pu * PX_U, pv * PX_V)
                .setColor(c, c, c, cA);
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= TWO_PI;
        while (a < -Math.PI) a += TWO_PI;
        return a;
    }
}
