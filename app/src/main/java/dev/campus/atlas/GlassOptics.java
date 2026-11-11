package dev.campus.atlas;

/** Samples an extended backdrop through a rounded glass surface. */
final class GlassOptics {
    private GlassOptics() {}

    static void refract(int[] source, int sourceWidth, int sourceHeight, int[] out,
                        int width, int height, int padding, float radius, float depth) {
        float cx = width * .5f, cy = height * .5f;
        radius = Math.min(radius, Math.min(cx, cy));
        float band = Math.max(1f, Math.min(depth, Math.min(cx, cy)));
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            float dx = x + .5f - cx, dy = y + .5f - cy;
            float qx = Math.abs(dx) - (cx - radius), qy = Math.abs(dy) - (cy - radius);
            float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
            float length = (float) Math.hypot(ox, oy);
            float distance = Math.min(Math.max(qx, qy), 0) + length - radius;
            float nx, ny;
            if (length > .0001f) { nx = Math.signum(dx) * ox / length; ny = Math.signum(dy) * oy / length; }
            else if (qx > qy) { nx = Math.signum(dx); ny = 0; }
            else { nx = 0; ny = Math.signum(dy); }
            float edge = Math.max(0, Math.min(1, 1 + distance / band));
            // A smoothly curved lens refracts most strongly near its perimeter.
            float shift = depth * .35f * edge * edge * (3 - 2 * edge);
            float sx = x + padding + nx * shift, sy = y + padding + ny * shift;
            int center = sample(source, sourceWidth, sourceHeight, sx, sy);
            float chroma = shift * .10f;
            int red = sample(source, sourceWidth, sourceHeight, sx + nx * chroma, sy + ny * chroma);
            int blue = sample(source, sourceWidth, sourceHeight, sx - nx * chroma, sy - ny * chroma);
            out[y * width + x] = (center & 0xff00ff00) | (red & 0x00ff0000) | (blue & 0x000000ff);
        }
    }

    private static int sample(int[] pixels, int width, int height, float x, float y) {
        x = Math.max(0, Math.min(width - 1, x)); y = Math.max(0, Math.min(height - 1, y));
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(x0 + 1, width - 1), y1 = Math.min(y0 + 1, height - 1);
        float fx = x - x0, fy = y - y0;
        int a = pixels[y0 * width + x0], b = pixels[y0 * width + x1];
        int c = pixels[y1 * width + x0], d = pixels[y1 * width + x1];
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            float top = ((a >>> shift) & 255) * (1 - fx) + ((b >>> shift) & 255) * fx;
            float bottom = ((c >>> shift) & 255) * (1 - fx) + ((d >>> shift) & 255) * fx;
            result |= Math.round(top * (1 - fy) + bottom * fy) << shift;
        }
        return result;
    }
}
