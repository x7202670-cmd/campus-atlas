package dev.campus.atlas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.View;

/** Refracts a downsampled snapshot of the content behind the navigation bar. */
final class GlassBackdrop extends View {
    private final View content;
    private final View overlay;
    private final View overlayAnchor;
    private final int scale;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final Rect destination = new Rect();
    private final GlassFrameCache gpuFrames = new GlassFrameCache(), cpuFrames = new GlassFrameCache();
    private Bitmap snapshot, refracted;
    private int[] pixels, scratch, output;
    private GlassShader gpuShader;
    private boolean gpuUnavailable;
    private boolean hasGoodSnapshot;
    private final int[] contentPosition = new int[2], glassPosition = new int[2], overlayPosition = new int[2];

    GlassBackdrop(Context context, View content) { this(context, content, null, null); }

    GlassBackdrop(Context context, View content, View overlay, View overlayAnchor) {
        super(context);
        this.content = content;
        this.overlay = overlay;
        this.overlayAnchor = overlayAnchor;
        this.scale = overlay == null ? 4 : 2;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        float radius = AtlasUi.dp(getContext(), 28);
        clip.reset();
        clip.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        int ow = Math.max(1, (width + scale - 1) / scale);
        int oh = Math.max(1, (height + scale - 1) / scale);
        int padding = Math.max(2, (AtlasUi.dp(getContext(), 20) + scale - 1) / scale);
        int sw = ow + padding * 2, sh = oh + padding * 2;
        if (snapshot == null || snapshot.getWidth() != sw || snapshot.getHeight() != sh) {
            if (snapshot != null) snapshot.recycle();
            if (refracted != null) refracted.recycle();
            snapshot = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            refracted = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
            pixels = new int[sw * sh]; scratch = new int[sw * sh];
            output = new int[ow * oh];
            gpuFrames.clear(); cpuFrames.clear();
            hasGoodSnapshot = false;
        }
        snapshot.eraseColor(AtlasUi.BG);
        content.getLocationInWindow(contentPosition);
        getLocationInWindow(glassPosition);
        Canvas sample = new Canvas(snapshot);
        sample.scale(1f / scale, 1f / scale);
        sample.translate(contentPosition[0] - glassPosition[0] + padding * scale - content.getScrollX(),
                         contentPosition[1] - glassPosition[1] + padding * scale - content.getScrollY());
        boolean captured = true;
        try { content.draw(sample); } catch (RuntimeException ignored) {
            // Some map surfaces cannot be drawn into a software canvas. Keep
            // the last complete frame instead of briefly flashing a blank one.
            captured = false;
        }
        if (captured || !hasGoodSnapshot) {
            if (!captured) snapshot.eraseColor(AtlasUi.BG);
            snapshot.getPixels(pixels, 0, sw, 0, 0, sw, sh);
            blur(pixels, scratch, sw, sh, true);
            blur(scratch, pixels, sw, sh, false);
        }
        if (captured && overlay != null && overlayAnchor != null && overlayAnchor.isShown()) {
            // The page is frosted, while tab artwork remains legible inside the lens.
            snapshot.setPixels(pixels, 0, sw, 0, 0, sw, sh);
            overlayAnchor.getLocationInWindow(overlayPosition);
            Canvas overlayCanvas = new Canvas(snapshot);
            overlayCanvas.scale(1f / scale, 1f / scale);
            overlayCanvas.translate(overlayPosition[0] - glassPosition[0] + padding * scale - overlay.getScrollX(),
                    overlayPosition[1] - glassPosition[1] + padding * scale - overlay.getScrollY());
            try { overlay.draw(overlayCanvas); } catch (RuntimeException ignored) { }
            snapshot.getPixels(pixels, 0, sw, 0, 0, sw, sh);
        }
        if (captured) hasGoodSnapshot = true;
        boolean rendered = false;
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated() && !gpuUnavailable) {
            try {
                if (gpuShader == null) { gpuShader = new GlassShader(); Log.i("AtlasGlass", "GPU glass shader ready"); }
                // The render thread may still be sampling the previous frame
                // when the UI thread starts drawing the next snapshot. Reuse
                // the immutable frame only when all sampled pixels match.
                Bitmap frame = gpuFrames.frame(snapshot,pixels,sw,sh);
                gpuShader.draw(canvas, frame, width, height, radius, AtlasUi.dp(getContext(), overlay == null ? 20 : 14), padding, scale);
                rendered = true;
            } catch (RuntimeException ex) {
                gpuUnavailable = true;
                Log.w("AtlasGlass", "GPU glass unavailable; using compatible renderer", ex);
            }
        }
        if (!rendered) {
            GlassOptics.refract(pixels, sw, sh, output, ow, oh, padding,
                    radius / scale, AtlasUi.dp(getContext(), overlay == null ? 20 : 14) / (float) scale);
            Bitmap frame;
            if(canvas.isHardwareAccelerated())frame=cpuFrames.frame(refracted,output,ow,oh);
            else{refracted.setPixels(output,0,ow,0,0,ow,oh);frame=refracted;}
            destination.set(0,0,width,height);canvas.drawBitmap(frame, null, destination, paint);
        }
        canvas.drawColor(0x39faf8f5);
        canvas.restoreToCount(save);
        // Refresh dynamic content as well as scroll events, without running every display frame.
        if (isShown()) postInvalidateDelayed(100);
    }

    private static void blur(int[] input, int[] output, int width, int height, boolean horizontal) {
        final int radius = 1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int px = horizontal ? Math.max(0, Math.min(width - 1, x + k)) : x;
                int py = horizontal ? y : Math.max(0, Math.min(height - 1, y + k));
                int c = input[py * width + px];
                a += Color.alpha(c); r += Color.red(c); g += Color.green(c); b += Color.blue(c);
            }
            output[y * width + x] = Color.argb(a / 3, r / 3, g / 3, b / 3);
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (snapshot != null) { snapshot.recycle(); snapshot = null; }
        if (refracted != null) { refracted.recycle(); refracted = null; }
        gpuFrames.clear(); cpuFrames.clear();
    }
}
