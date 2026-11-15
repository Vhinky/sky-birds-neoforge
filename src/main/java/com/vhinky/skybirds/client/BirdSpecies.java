package com.vhinky.skybirds.client;

/**
 * Виды птиц. Порядок важен: номер вида = полоса в текстуре birds.png.
 * Размеры в блоках, скорость в блоках за тик, взмахи в радианах за тик.
 * altMin / altRange — высота над землёй (с учётом деревьев) при появлении.
 * Цвета тела (0xRRGGBB): спина, брюхо, верх головы, низ головы, клюв.
 * Форма: размер головы, длина клюва (в долях тела), толщина тела.
 */
public enum BirdSpecies {
    //        halfSpan body  neck  sweep  flap   glide  speed  min max  formation          altMin altRange wander  back      belly     head      face      beak      head  beakLen thick
    SWALLOW(  0.34f,  0.95f, 1.0f, 0.45f, 1.10f, 0.25f, 0.55f, 4, 10, Formation.LOOSE,  20,  20,  1.6f,  0x1C2034, 0x2E3040, 0x1C2034, 0x3A2A26, 0x121212, 1.0f, 0.05f, 0.90f),
    CROW(     0.50f,  0.95f, 1.0f, 0.15f, 0.55f, 0.30f, 0.36f, 1,  4, Formation.LOOSE,  22,  20,  1.0f,  0x1A1A1E, 0x222226, 0x1A1A1E, 0x1A1A1E, 0x202020, 1.05f, 0.12f, 1.0f),
    GULL(     0.72f,  0.80f, 1.0f, 0.30f, 0.42f, 0.65f, 0.40f, 1,  4, Formation.LOOSE,  16,  18,  1.3f,  0x1C1D22, 0x24252A, 0x1C1D22, 0x1C1D22, 0x2A2A2A, 0.95f, 0.10f, 0.95f),
    GOOSE(    0.80f,  1.05f, 1.6f, 0.15f, 0.38f, 0.05f, 0.50f, 5, 11, Formation.V,      36,  25,  0.3f,  0x1E1C1C, 0x262424, 0x1A1A1A, 0x1A1A1A, 0x161616, 0.85f, 0.14f, 0.90f),
    HAWK(     0.85f,  0.85f, 1.0f, 0.10f, 0.35f, 0.90f, 0.30f, 1,  1, Formation.CIRCLE, 32,  25,  0.5f,  0x1F1B1A, 0x272322, 0x1C1918, 0x1C1918, 0x262626, 1.05f, 0.07f, 1.05f),
    STARLING( 0.26f,  0.90f, 1.0f, 0.40f, 1.25f, 0.10f, 0.42f, 40, 70, Formation.MURMUR, 18,  14,  1.0f,  0x16181C, 0x1E2024, 0x16181C, 0x16181C, 0x202020, 1.0f, 0.08f, 0.95f);

    /** LOOSE — рыхлая стайка, V — клин, CIRCLE — кружит, MURMUR — мурмурация скворцов. */
    public enum Formation { LOOSE, V, CIRCLE, MURMUR }

    /** Перелётные теплолюбивые птицы не залетают в зимние биомы — разворачиваются. */
    public boolean avoidsCold() {
        return this == SWALLOW || this == GULL || this == STARLING;
    }

    public final float halfSpan, body, neck, sweep;
    public final float flapSpeed, glideChance, speed;
    public final int minCount, maxCount;
    public final Formation formation;
    public final int altMin, altRange;
    public final float wander;
    public final int back, belly, head, face, beak;
    public final float headSize, beakLen, thick;

    BirdSpecies(float halfSpan, float body, float neck, float sweep,
                float flapSpeed, float glideChance, float speed,
                int minCount, int maxCount, Formation formation,
                int altMin, int altRange, float wander,
                int back, int belly, int head, int face, int beak,
                float headSize, float beakLen, float thick) {
        this.halfSpan = halfSpan;
        this.body = body;
        this.neck = neck;
        this.sweep = sweep;
        this.flapSpeed = flapSpeed;
        this.glideChance = glideChance;
        this.speed = speed;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.formation = formation;
        this.altMin = altMin;
        this.altRange = altRange;
        this.wander = wander;
        this.back = back;
        this.belly = belly;
        this.head = head;
        this.face = face;
        this.beak = beak;
        this.headSize = headSize;
        this.beakLen = beakLen;
        this.thick = thick;
    }
}
