package dev.campus.atlas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Pressed-lens highlight and inner edge, following the backdrop catalog treatment. */
final class DropletHighlight extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lastH = -1f;
    private LinearGradient outerShader, innerShader;
    DropletHighlight(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(), h=getHeight(), d=getResources().getDisplayMetrics().density;
        if(w<=0||h<=0)return;
        float radius=Math.min(w,h)/2f;
        if(lastH!=h||outerShader==null){
            lastH=h;
            outerShader=new LinearGradient(0,0,0,h,
                    new int[]{0xeaffffff,0x72ffffff,0x44ffffff,0xc8ffffff},
                    new float[]{0f,.3f,.7f,1f},Shader.TileMode.CLAMP);
            innerShader=new LinearGradient(0,0,0,h,
                    new int[]{0x30707070,0x00ffffff,0x24707070},
                    new float[]{0f,.48f,1f},Shader.TileMode.CLAMP);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f,d));
        paint.setShader(outerShader);
        float inset=paint.getStrokeWidth()/2f;
        canvas.drawRoundRect(inset,inset,w-inset,h-inset,radius,radius,paint);
        paint.setStrokeWidth(Math.max(1f,1.2f*d));
        paint.setShader(innerShader);
        inset=2*d;
        canvas.drawRoundRect(inset,inset,w-inset,h-inset,Math.max(0,radius-inset),Math.max(0,radius-inset),paint);
        paint.setShader(null);
    }
}
