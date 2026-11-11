package dev.campus.atlas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Directional outer and inner highlights for the floating glass capsule. */
final class GlassRimView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lastW = -1f, lastH = -1f;
    private LinearGradient horizShader, vertShader;

    GlassRimView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight(), radius = 28 * d;
        if (w <= 0 || h <= 0) return;
        if (lastW != w || lastH != h || horizShader == null) {
            lastW = w; lastH = h;
            horizShader = new LinearGradient(0, 0, w, 0,
                    new int[]{0x28879b8e, 0x38a0b0a6, 0x4891a497, 0x38a0b0a6, 0x28879b8e},
                    new float[]{0f, .18f, .5f, .82f, 1f}, Shader.TileMode.CLAMP);
            vertShader = new LinearGradient(0, 0, 0, h,
                    new int[]{0x72ffffff, 0x00ffffff, 0x22728b7c},
                    new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, d));
        paint.setShader(horizShader);
        float half = paint.getStrokeWidth() / 2;
        canvas.drawRoundRect(half, half, w - half, h - half, radius, radius, paint);
        paint.setStrokeWidth(Math.max(1, .5f * d));
        paint.setShader(vertShader);
        float inner = 2 * d;
        canvas.drawRoundRect(inner, inner, w - inner, h - inner,
                Math.max(0, radius - inner), Math.max(0, radius - inner), paint);
        paint.setShader(null);
    }
}
