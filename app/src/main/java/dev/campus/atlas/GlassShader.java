package dev.campus.atlas;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import androidx.annotation.RequiresApi;

/** API 33+ lens rendered per pixel on the GPU; older devices use GlassOptics. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class GlassShader {
    private static final String SOURCE =
        "uniform shader backdrop;\n" +
        "uniform float2 size; uniform float radius; uniform float depth;\n" +
        "uniform float padding; uniform float downsample;\n" +
        "half4 main(float2 p) {\n" +
        "  float2 halfSize = size * 0.5;\n" +
        "  float2 d = p - halfSize;\n" +
        "  float2 q = abs(d) - (halfSize - radius);\n" +
        "  float2 outside = max(q, 0.0);\n" +
        "  float len = length(outside);\n" +
        "  float sdf = min(max(q.x,q.y),0.0) + len - radius;\n" +
        "  float2 normal;\n" +
        "  if (len > 0.001) normal = sign(d) * outside / len;\n" +
        "  else if (q.x > q.y) normal = float2(sign(d.x),0.0);\n" +
        "  else normal = float2(0.0,sign(d.y));\n" +
        "  float edge = clamp(1.0 + sdf / max(depth,1.0),0.0,1.0);\n" +
        "  float bend = depth * 0.35 * edge * edge * (3.0 - 2.0 * edge);\n" +
        "  float2 uv = (p + normal * bend + padding) / downsample;\n" +
        "  float2 dispersion = normal * bend * 0.10 / downsample;\n" +
        "  half4 base = backdrop.eval(uv);\n" +
        "  half red = backdrop.eval(uv + dispersion).r;\n" +
        "  half blue = backdrop.eval(uv - dispersion).b;\n" +
        "  float rim = pow(edge, 8.0) * (0.10 + 0.16 * max(-normal.y,0.0));\n" +
        "  half3 rgb = mix(half3(red,base.g,blue),half3(1.0),half(rim));\n" +
        "  return half4(rgb,base.a);\n" +
        "}\n";
    private final RuntimeShader shader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap boundBitmap;

    GlassShader() {
        if (Build.VERSION.SDK_INT < 33) throw new IllegalStateException("RuntimeShader requires API 33");
        shader = new RuntimeShader(SOURCE);
        paint.setShader(shader);
    }

    void draw(Canvas canvas, Bitmap backdrop, int width, int height, float radius, float depth, int padding, int scale) {
        if (boundBitmap != backdrop) {
            shader.setInputShader("backdrop", new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            boundBitmap = backdrop;
        }
        shader.setFloatUniform("size", width, height);
        shader.setFloatUniform("radius", radius);
        shader.setFloatUniform("depth", depth);
        shader.setFloatUniform("padding", padding * scale);
        shader.setFloatUniform("downsample", scale);
        canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);
    }
}
