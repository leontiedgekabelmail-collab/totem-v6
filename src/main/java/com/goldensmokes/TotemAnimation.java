package com.goldensmokes;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * 3D-Animation (im HUD gezeichnet): Die Schachtel dreht sich ein, der Deckel klappt auf,
 * die Zigaretten (echte 3D-Meshes) ziehen sich aus der Schachtel und fallen herunter.
 * Danach goldene Funken und Ausblenden. Masse: Schachtel = 34.5 x 44 x 11 Einheiten.
 */
public final class TotemAnimation {
    private static final float DURATION_MS = 3200f;
    private static final float G = 220f;                 // Fallbeschleunigung (Einheiten/s^2)
    private static final int STARS = 90;
    private static final int MINI_TOTEMS = 8;
    private static final Identifier STAR = Identifier.of(GoldenSmokesClient.MOD_ID, "textures/gui/star.png");
    private static final Identifier MINI = Identifier.of(GoldenSmokesClient.MOD_ID, "textures/gui/mini_totem.png");

    // 18 Zigaretten stehen in der Schachtel (2 Reihen a 9), alle fallen nach vorne heraus (vordere Reihe zuerst)
    private static final int STAND = 18;
    private static final float[] SX = new float[STAND];
    private static final float[] SZ = new float[STAND];
    private static final int[] ORDER = new int[STAND];   // Reihenfolge beim Herausfallen

    static {
        for (int i = 0; i < STAND; i++) {
            SX[i] = -14f + 3.5f * (i % 9);
            SZ[i] = -1.8f + 3.6f * (i / 9);
            ORDER[i] = (i < 9 ? 9 : 0) + i % 9;      // zweite Reihe (z > 0, vorne) faellt zuerst
        }
    }

    private static long startMs = -1L;

    /** Zufallsgenerator mit gut durchmischtem Seed (aufeinanderfolgende Seeds liefern sonst fast gleiche erste Werte). */
    private static Random rng(long seed) {
        return new Random(seed * 0x9E3779B97F4A7C15L + 0x7F4A7C15L);
    }

    private TotemAnimation() {}

    public static void start() {
        startMs = Util.getMeasuringTimeMs();
        spawnWorldParticles();
    }

    /** Goldene Funken und Mini-Totem-Partikel in der Spielwelt rund um den Spieler. */
    private static void spawnWorldParticles() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (int i = 0; i < 60; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double rr = 0.3 + world.random.nextDouble() * 1.1;
            double x = px + Math.cos(a) * rr;
            double y = py + world.random.nextDouble() * 2.2;
            double z = pz + Math.sin(a) * rr;
            double vx = (world.random.nextDouble() - 0.5) * 0.12;
            double vy = 0.04 + world.random.nextDouble() * 0.16;
            double vz = (world.random.nextDouble() - 0.5) * 0.12;
            world.addParticle(ParticleTypes.WAX_ON, x, y, z, vx, vy, vz);       // goldene Sterne
            if (i % 2 == 0) {
                world.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, vx * 2, vy * 2, vz * 2);   // Mini-Totem-Konfetti
            }
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (startMs < 0) return;
        float t = (Util.getMeasuringTimeMs() - startMs) / DURATION_MS;
        if (t >= 1f) {
            startMs = -1L;
            return;
        }

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float pix = h * 0.5f / 48f;                       // Bildschirm-Pixel pro Einheit
        float alpha = t > 0.8f ? 1f - (t - 0.8f) / 0.2f : 1f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (t < 0.2f) {                                   // kurzer goldener Blitz
            int a = (int) (70f * (1f - t / 0.2f));
            ctx.fill(0, 0, w, h, (a << 24) | 0xFFD24A);
        }

        ctx.draw();                                       // wartende 2D-Draws ausgeben
        ctx.setShaderColor(1f, 1f, 1f, alpha);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        drawPack(ctx, t, cx, cy, pix);
        drawCigarettes(ctx, t, cx, cy, pix);

        RenderSystem.enableCull();
        RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);   // Tiefenpuffer fuer die restliche GUI zuruecksetzen
        ctx.setShaderColor(1f, 1f, 1f, 1f);

        drawEffects(ctx, t, cx, cy, h, pix, alpha);
        RenderSystem.disableBlend();
    }

    // ---------- Schachtel-Pose als Funktion der Zeit ----------

    private static float boxScale(float t) {
        float appear = easeOutBack(Math.min(1f, t / 0.15f));
        float shrink = t > 0.8f ? 1f - 0.3f * (t - 0.8f) / 0.2f : 1f;
        return appear * shrink;
    }

    private static Quaternionf boxRot(float t) {
        float spin = (1f - ease(Math.min(1f, t / 0.2f))) * 200f;      // dreht sich beim Auftauchen ein
        float yaw = -28f + spin + (float) Math.sin(t * 5f) * 8f;
        float pitch = -16f;                                           // leicht von oben, damit man hineinsieht
        float roll = -6f + (float) Math.sin(t * 6f) * 3f;
        return new Quaternionf().rotationXYZ(
            (float) Math.toRadians(pitch), (float) Math.toRadians(yaw), (float) Math.toRadians(roll));
    }

    private static float openAmount(float t) {
        return easeOutBack(clamp01((t - 0.18f) / 0.17f));
    }

    private static void drawPack(DrawContext ctx, float t, float cx, float cy, float pix) {
        float s = boxScale(t);
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 200f);
        m.scale(pix * s, pix * s, pix * s);
        PackModel.draw(m.peek().getPositionMatrix(), boxRot(t), openAmount(t));
        m.pop();
    }

    private static void drawCigarettes(DrawContext ctx, float t, float cx, float cy, float pix) {
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        MatrixStack m = ctx.getMatrices();
        Quaternionf flip = new Quaternionf().rotationZ((float) Math.PI);   // Filter nach oben
        int quads = 0;

        for (int i = 0; i < STAND; i++) {
            Random r = rng(4242L + i * 97L);
            float delay = ORDER[i] * 0.04f + r.nextFloat() * 0.04f;
            float vF = 22f + r.nextFloat() * 14f;               // Tempo nach vorne (Richtung Schachtel-Vorderseite)
            float vUp = 8f + r.nextFloat() * 20f;               // kleiner Hupfer nach oben
            float vSide = (r.nextFloat() - 0.5f) * 16f;
            float wx = 150f + r.nextFloat() * 250f;             // kippt nach vorne ueber
            float wy = (r.nextFloat() - 0.5f) * 300f;
            float wz = (r.nextFloat() - 0.5f) * 200f;

            float tr = 0.30f + delay * 1000f / DURATION_MS;      // Zeitpunkt des Herausfallens
            float tp = Math.min(t, tr);                          // bis dahin folgt sie der Schachtel
            float sBox = boxScale(tp);
            float openP = clamp01(openAmount(tp));

            Quaternionf bRot = boxRot(tp);
            Vector3f pos = new Vector3f(SX[i], -12f - 3f * openP, SZ[i]);  // zieht sich beim Oeffnen nach oben
            bRot.transform(pos);
            pos.mul(sBox);
            Quaternionf rot = new Quaternionf(bRot).mul(flip);

            float grow = 1f;                                     // faellt Richtung Betrachter -> wirkt groesser
            if (t > tr) {
                float u = (t - tr) * DURATION_MS / 1000f;
                grow = 1f + 0.6f * ease(clamp01(u / 0.7f));
                Vector3f fwd = new Vector3f(0f, 0f, 1f);          // "vorne" der Schachtel zum Zeitpunkt des Herausfallens
                bRot.transform(fwd);
                pos.add(fwd.x * vF * u + vSide * u,
                        fwd.y * vF * u - vUp * u + 0.5f * G * u * u,
                        fwd.z * vF * u);
                Quaternionf spin = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(wx * u), (float) Math.toRadians(wy * u), (float) Math.toRadians(wz * u));
                rot = new Quaternionf(spin).mul(rot);
            }

            float k = pix * sBox * grow;
            m.push();
            m.translate(cx + pos.x * pix, cy + pos.y * pix, 200f + pos.z * pix);
            m.scale(k, k, k);
            quads += Cigarette3D.build(buf, m.peek().getPositionMatrix(), rot);
            m.pop();
        }

        if (quads > 0) {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
    }

    /** Goldene Sterne (4-zackig, funkelnd) und kleine schwebende Mini-Totems. */
    private static void drawEffects(DrawContext ctx, float t, float cx, float cy, int h, float pix, float alpha) {
        MatrixStack m = ctx.getMatrices();

        for (int k = 0; k < MINI_TOTEMS; k++) {
            Random r = rng(7000L + k * 31L);
            float start = 0.2f + r.nextFloat() * 0.35f;
            float life = (t - start) / 0.5f;
            if (life < 0f || life > 1f) continue;
            float ang = (k / (float) MINI_TOTEMS) * (float) (Math.PI * 2) + r.nextFloat() * 0.6f;
            float d = (0.12f + 0.22f * life) * h * (0.8f + r.nextFloat() * 0.4f);
            float x = cx + (float) Math.cos(ang) * d;
            float y = cy + (float) Math.sin(ang) * d * 0.7f - life * h * 0.18f;
            float a = alpha * (float) Math.sin(Math.PI * life);
            float size = pix * 8f * (0.8f + r.nextFloat() * 0.4f);

            ctx.setShaderColor(1f, 1f, 1f, a);
            m.push();
            m.translate(x, y, 0f);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 9f + k) * 10f));
            m.scale(size / 32f, size / 32f, 1f);
            ctx.drawTexture(MINI, -16, -16, 32, 32, 0f, 0f, 32, 32, 32, 32);
            m.pop();
        }

        for (int k = 0; k < STARS; k++) {
            Random r = rng(900L + k * 13L);
            float ang = k * 2.3999632f + r.nextFloat() * 0.5f;              // goldener Winkel: gleichmaessig rundherum
            float dist0 = (0.07f + 0.40f * (float) Math.sqrt(r.nextFloat())) * h;
            float phase = r.nextFloat() * 6.28f;
            float start = 0.12f + r.nextFloat() * 0.45f;
            float sizeVar = 0.6f + r.nextFloat() * 0.8f;
            if (t < start) continue;

            float life = t - start;
            float d = dist0 * (0.7f + life * 0.6f);
            float x = cx + (float) Math.cos(ang) * d;
            float y = cy + (float) Math.sin(ang) * d - life * h * 0.03f;

            float tw = 0.5f + 0.5f * (float) Math.sin(phase + t * 14f);
            float a = alpha * tw * clamp01(life * 6f);
            if (a < 0.03f) continue;
            float size = pix * (3f + 5f * tw) * sizeVar;

            ctx.setShaderColor(1f, 1f, 1f, a);
            m.push();
            m.translate(x, y, 0f);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(life * 90f + phase * 20f));
            m.scale(size / 32f, size / 32f, 1f);
            ctx.drawTexture(STAR, -16, -16, 32, 32, 0f, 0f, 32, 32, 32, 32);
            m.pop();
        }
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float ease(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }
}
