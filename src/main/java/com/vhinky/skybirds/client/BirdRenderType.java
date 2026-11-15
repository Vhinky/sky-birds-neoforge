package com.vhinky.skybirds.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.vhinky.skybirds.SkyBirds;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Свой тип отрисовки частиц: текстурированная геометрия из одной текстуры-атласа.
 * Регистрировать типы частиц не нужно — мод остаётся полностью клиентским.
 */
public final class BirdRenderType implements ParticleRenderType {
    public static final BirdRenderType INSTANCE = new BirdRenderType();

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SkyBirds.MODID, "textures/particle/birds.png");

    /** Размер атласа в «логических» пикселях (картинка может быть в 2, 4… раза больше). */
    public static final float ATLAS_W = 112f;
    public static final float ATLAS_H = 96f;

    private BirdRenderType() {}

    @Override
    public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
        // чёткие пиксели, без размытия и ореолов по краям
        textureManager.getTexture(TEXTURE).setFilter(false, false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    @Override
    public String toString() {
        return "SKY_BIRDS";
    }
}
