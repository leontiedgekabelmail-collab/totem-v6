package com.goldensmokes;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D-Modell der Schachtel: Koerper + Deckel (Scharnier an der Rueckkante), mit Innenwaenden.
 * Texturen: front/back/side/top aus assets/goldensmokes/textures/gui/.
 * Einheiten: Breite 34.5, Hoehe 44 (Deckel ca. 10.5 + Koerper ca. 33.5), Tiefe 11. Ursprung = Mitte der Schachtel.
 */
final class PackModel {
    private static final Identifier[] TEX = {
        Identifier.of(GoldenSmokesClient.MOD_ID, "textures/gui/front.png"),
        Identifier.of(GoldenSmokesClient.MOD_ID, "textures/gui/back.png"),
        Identifier.of(GoldenSmokesClient.MOD_ID, "textures/gui/side.png"),
        Identifier.of(GoldenSmokesClient.MOD_ID, "textures/gui/top.png"),
    };
    private static final int FRONT = 0, BACK = 1, SIDE = 2, TOP = 3;

    private static final float HW = 17.25f, HD = 5.5f;
    private static final float TOP_Y = -22f, BOT_Y = 22f;
    private static final float LID_V = 0.24f;                 // Deckel = obere 24 % der Textur (Naht im Screenshot)
    private static final float RIM_Y = TOP_Y + 44f * LID_V;   // Deckelkante (ca. -11.4)
    private static final float IN = 0.5f;
    private static final float IN_U = 0.06f, IN_V = 0.5f;      // einfarbige Stelle der top-Textur fuer die Innenwaende                     // Abstand der Innenwand

    private static final Vector3f LIGHT = new Vector3f(-0.4f, -0.6f, 0.7f).normalize();

    private PackModel() {}

    private static final class Quad {
        final int pass;
        final float[] v = new float[20];   // 4 x (x, y, z, u, v)
        int shade;
        Quad(int pass) { this.pass = pass; }
    }

    /** open: 0 = zu, 1 = ganz offen (Deckel klappt um ca. 112 Grad nach hinten). */
    static void draw(Matrix4f screen, Quaternionf rot, float open) {
        List<Quad> quads = new ArrayList<>();
        Matrix4f id = new Matrix4f();
        Matrix4f hinge = new Matrix4f()
            .translate(0f, RIM_Y, -HD)
            .rotateX((float) Math.toRadians(open * 112f))
            .translate(0f, -RIM_Y, HD);

        // ---------- Koerper ----------
        quad(quads, FRONT, id, rot, 0, 0, 1,
            -HW, RIM_Y, HD, 0, LID_V,   HW, RIM_Y, HD, 1, LID_V,   HW, BOT_Y, HD, 1, 1,   -HW, BOT_Y, HD, 0, 1);
        quad(quads, BACK, id, rot, 0, 0, -1,
             HW, RIM_Y, -HD, 0, LID_V,  -HW, RIM_Y, -HD, 1, LID_V,  -HW, BOT_Y, -HD, 1, 1,   HW, BOT_Y, -HD, 0, 1);
        quad(quads, SIDE, id, rot, 1, 0, 0,
             HW, RIM_Y, HD, 0, LID_V,   HW, RIM_Y, -HD, 1, LID_V,   HW, BOT_Y, -HD, 1, 1,   HW, BOT_Y, HD, 0, 1);
        quad(quads, SIDE, id, rot, -1, 0, 0,
            -HW, RIM_Y, -HD, 0, LID_V, -HW, RIM_Y, HD, 1, LID_V,   -HW, BOT_Y, HD, 1, 1,   -HW, BOT_Y, -HD, 0, 1);
        quad(quads, TOP, id, rot, 0, 1, 0,
            -HW, BOT_Y, HD, 0, 0,       HW, BOT_Y, HD, 1, 0,        HW, BOT_Y, -HD, 1, 1,   -HW, BOT_Y, -HD, 0, 1);
        // Innenwaende des Koerpers (einfarbig gold)
        inner(quads, id, rot, 0, 0, -1, -HW + IN, RIM_Y, HD - IN, HW - IN, BOT_Y - IN, HD - IN, true);
        inner(quads, id, rot, 0, 0, 1, -HW + IN, RIM_Y, -HD + IN, HW - IN, BOT_Y - IN, -HD + IN, true);
        inner(quads, id, rot, 1, 0, 0, -HW + IN, RIM_Y, -HD + IN, -HW + IN, BOT_Y - IN, HD - IN, false);
        inner(quads, id, rot, -1, 0, 0, HW - IN, RIM_Y, -HD + IN, HW - IN, BOT_Y - IN, HD - IN, false);

        // ---------- Deckel (am Scharnier gedreht) ----------
        quad(quads, FRONT, hinge, rot, 0, 0, 1,
            -HW, TOP_Y, HD, 0, 0,       HW, TOP_Y, HD, 1, 0,        HW, RIM_Y, HD, 1, LID_V,   -HW, RIM_Y, HD, 0, LID_V);
        quad(quads, BACK, hinge, rot, 0, 0, -1,
             HW, TOP_Y, -HD, 0, 0,     -HW, TOP_Y, -HD, 1, 0,      -HW, RIM_Y, -HD, 1, LID_V,   HW, RIM_Y, -HD, 0, LID_V);
        quad(quads, SIDE, hinge, rot, 1, 0, 0,
             HW, TOP_Y, HD, 0, 0,       HW, TOP_Y, -HD, 1, 0,       HW, RIM_Y, -HD, 1, LID_V,   HW, RIM_Y, HD, 0, LID_V);
        quad(quads, SIDE, hinge, rot, -1, 0, 0,
            -HW, TOP_Y, -HD, 0, 0,     -HW, TOP_Y, HD, 1, 0,       -HW, RIM_Y, HD, 1, LID_V,   -HW, RIM_Y, -HD, 0, LID_V);
        quad(quads, TOP, hinge, rot, 0, -1, 0,
            -HW, TOP_Y, HD, 0, 1,       HW, TOP_Y, HD, 1, 1,        HW, TOP_Y, -HD, 1, 0,      -HW, TOP_Y, -HD, 0, 0);
        // Innenseite des Deckels
        inner(quads, hinge, rot, 0, 0, -1, -HW + IN, TOP_Y + IN, HD - IN, HW - IN, RIM_Y, HD - IN, true);
        inner(quads, hinge, rot, 0, 0, 1, -HW + IN, TOP_Y + IN, -HD + IN, HW - IN, RIM_Y, -HD + IN, true);
        inner(quads, hinge, rot, 1, 0, 0, -HW + IN, TOP_Y + IN, -HD + IN, -HW + IN, RIM_Y, HD - IN, false);
        inner(quads, hinge, rot, -1, 0, 0, HW - IN, TOP_Y + IN, -HD + IN, HW - IN, RIM_Y, HD - IN, false);
        quad(quads, TOP, hinge, rot, 0, 1, 0,
            -HW + IN, TOP_Y + IN, HD - IN, .06f, .5f,  HW - IN, TOP_Y + IN, HD - IN, .07f, .5f,
             HW - IN, TOP_Y + IN, -HD + IN, .07f, .51f, -HW + IN, TOP_Y + IN, -HD + IN, .06f, .51f);

        for (int pass = 0; pass < TEX.length; pass++) {
            drawPass(quads, pass, screen);
        }
    }

    /** Einfarbige Innenflaeche (nutzt einen goldenen Punkt der top-Textur). nx,ny,nz = Normale nach innen. */
    private static void inner(List<Quad> out, Matrix4f local, Quaternionf rot, float nx, float ny, float nz,
                              float x0, float y0, float z0, float x1, float y1, float z1, boolean zConst) {
        float u = IN_U, v = IN_V;
        if (zConst) {
            quad(out, TOP, local, rot, nx, ny, nz, x0, y0, z0, u, v, x1, y0, z0, u, v, x1, y1, z0, u, v, x0, y1, z0, u, v);
        } else {
            quad(out, TOP, local, rot, nx, ny, nz, x0, y0, z0, u, v, x0, y0, z1, u, v, x0, y1, z1, u, v, x0, y1, z0, u, v);
        }
    }

    private static void quad(List<Quad> out, int pass, Matrix4f local, Quaternionf rot, float nx, float ny, float nz,
                             float... p) {
        Quad q = new Quad(pass);
        for (int i = 0; i < 4; i++) {
            Vector3f pos = new Vector3f(p[i * 5], p[i * 5 + 1], p[i * 5 + 2]);
            local.transformPosition(pos);
            rot.transform(pos);
            q.v[i * 5] = pos.x;
            q.v[i * 5 + 1] = pos.y;
            q.v[i * 5 + 2] = pos.z;
            q.v[i * 5 + 3] = p[i * 5 + 3];
            q.v[i * 5 + 4] = p[i * 5 + 4];
        }
        Vector3f n = new Vector3f(nx, ny, nz);
        local.transformDirection(n);
        rot.transform(n);
        float light = 0.62f + 0.45f * Math.max(0f, n.dot(LIGHT));
        q.shade = Math.min(255, (int) (255f * light));
        out.add(q);
    }

    private static void drawPass(List<Quad> quads, int pass, Matrix4f screen) {
        BufferBuilder buf = null;
        for (Quad q : quads) {
            if (q.pass != pass) continue;
            if (buf == null) {
                buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            }
            for (int i = 0; i < 4; i++) {
                buf.vertex(screen, q.v[i * 5], q.v[i * 5 + 1], q.v[i * 5 + 2])
                    .texture(q.v[i * 5 + 3], q.v[i * 5 + 4])
                    .color(q.shade, q.shade, q.shade, 255);
            }
        }
        if (buf != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, TEX[pass]);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
    }
}
