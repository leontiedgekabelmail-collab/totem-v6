package com.goldensmokes;

import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 3D-Zigarette als abgerundeter "Pixel-Zylinder": ein Achteck-Prisma (Kanten des Quaders abgeschraegt),
 * also nicht glatt rund, aber auch nicht mehr kantig viereckig. Flache Schattierung pro Seite.
 * Laengsachse = Y (Filterende bei +Y), Laenge 11, Breite ca. 3 Einheiten.
 * Mehr/weniger Rundung: SIDES aendern (4 = Quader, 8 = Achteck, 12 = fast rund).
 */
final class Cigarette3D {
    private static final int SIDES = 8;
    private static final float APOTHEM = 1.5f;                                        // halbe Breite
    private static final float RADIUS = (float) (APOTHEM / Math.cos(Math.PI / SIDES)); // Eckenabstand

    // {yOben, yUnten, r, g, b}
    private static final float[][] PARTS = {
        {-5.5f, 0.5f, 246, 238, 224},   // Papier
        { 0.5f, 1.3f, 238, 200, 146},   // heller Ring
        { 1.3f, 5.5f, 212, 146,  70},   // Filter
    };

    private static final Vector3f LIGHT = new Vector3f(-0.4f, -0.6f, 0.7f).normalize();

    private Cigarette3D() {}

    /** Fuegt die Zigarette (nur Quads) in den Puffer ein. Rueckgabe: Anzahl Quads. */
    static int build(BufferBuilder buf, Matrix4f m, Quaternionf rot) {
        int quads = 0;
        for (float[] part : PARTS) {
            float y0 = part[0], y1 = part[1];
            float[] c = {part[2], part[3], part[4]};
            for (int k = 0; k < SIDES; k++) {
                double aMid = k * 2 * Math.PI / SIDES;
                float nx = (float) Math.cos(aMid), nz = (float) Math.sin(aMid);
                float x0 = corner(k, true), z0 = corner(k, false);
                float x1 = corner(k + 1, true), z1 = corner(k + 1, false);
                face(buf, m, rot, nx, 0f, nz, c, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0);
                quads++;
            }
        }
        quads += cap(buf, m, rot, -5.5f, -1f, new float[]{232, 226, 212});   // Papierende
        quads += cap(buf, m, rot, 5.5f, 1f, new float[]{190, 126, 56});      // Filterende
        return quads;
    }

    /** Ecke j des Achtecks (liegt zwischen zwei Seitenflaechen). */
    private static float corner(int j, boolean x) {
        double a = (j - 0.5) * 2 * Math.PI / SIDES;
        return RADIUS * (float) (x ? Math.cos(a) : Math.sin(a));
    }

    private static int cap(BufferBuilder buf, Matrix4f m, Quaternionf rot, float y, float ny, float[] col) {
        int quads = 0;
        for (int k = 0; k < SIDES; k += 2) {          // jedes Quad deckt zwei Dreiecke des Achtecks ab
            face(buf, m, rot, 0f, ny, 0f, col,
                0f, y, 0f,
                corner(k, true), y, corner(k, false),
                corner(k + 1, true), y, corner(k + 1, false),
                corner(k + 2, true), y, corner(k + 2, false));
            quads++;
        }
        return quads;
    }

    private static void face(BufferBuilder buf, Matrix4f m, Quaternionf rot, float nx, float ny, float nz,
                             float[] col, float... p) {
        Vector3f n = new Vector3f(nx, ny, nz);
        rot.transform(n);
        float light = 0.45f + 0.70f * Math.max(0f, n.dot(LIGHT));
        int r = clamp(col[0] * light), g = clamp(col[1] * light), b = clamp(col[2] * light);
        for (int i = 0; i < 4; i++) {
            Vector3f pos = new Vector3f(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
            rot.transform(pos);
            buf.vertex(m, pos.x, pos.y, pos.z).color(r, g, b, 255);
        }
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }
}
