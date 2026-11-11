package dev.campus.atlas;

import org.junit.Test;
import static org.junit.Assert.*;

public class GlassOpticsTest {
    @Test public void edgesRefractBackdropButCenterStaysStable() {
        int width = 80, height = 30, padding = 10;
        int sw = width + padding * 2, sh = height + padding * 2;
        int[] source = new int[sw * sh], output = new int[width * height];
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++)
            source[y * sw + x] = 0xff000000 | (x << 16) | (y << 8) | x;
        GlassOptics.refract(source, sw, sh, output, width, height, padding, 10, 8);
        assertEquals(source[(padding + 15) * sw + padding + 40], output[15 * width + 40]);
        assertNotEquals(source[padding * sw + padding + 40], output[40]);
        for (int color : output) assertEquals(255, color >>> 24);
    }

    @Test public void tinyBackdropsStayWithinBounds() {
        int[] output = new int[1];
        GlassOptics.refract(new int[]{0xff345678}, 1, 1, output, 1, 1, 0, 20, 20);
        assertEquals(0xff345678, output[0]);
    }
}
