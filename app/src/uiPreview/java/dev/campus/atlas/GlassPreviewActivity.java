package dev.campus.atlas;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Isolated high-contrast fixture for checking backdrop sampling on a real device. */
public final class GlassPreviewActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        float density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int[] colors = {0xff254e68, 0xffe4a27f, 0xff83ad91, 0xffb85a64, 0xff6c71a0, 0xffd5b35e};
        for (int i = 0; i < 30; i++) {
            TextView stripe = new TextView(this);
            stripe.setText("GLASS  /  " + (i + 1));
            stripe.setTextColor(Color.WHITE);
            stripe.setTextSize(18);
            stripe.setGravity(Gravity.CENTER);
            stripe.setBackgroundColor(colors[i % colors.length]);
            column.addView(stripe, new LinearLayout.LayoutParams(-1, Math.round(52 * density)));
        }
        scroll.addView(column); root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout dock = new FrameLayout(this);
        GlassBackdrop glass = new GlassBackdrop(this, scroll);
        dock.addView(glass, new FrameLayout.LayoutParams(-1, -1));
        TextView labels = new TextView(this);
        labels.setText("定位             路线             课表             空间");
        labels.setTextColor(Color.WHITE); labels.setTextSize(13); labels.setGravity(Gravity.CENTER);
        GradientDrawable rim = new GradientDrawable(); rim.setColor(0x20ffffff);
        rim.setCornerRadius(28 * density); rim.setStroke(Math.round(density), 0x73ffffff);
        labels.setBackground(rim); dock.addView(labels, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, Math.round(62 * density), Gravity.BOTTOM);
        position.setMargins(Math.round(14*density), 0, Math.round(14*density), Math.round(10*density));
        root.addView(dock, position);
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->glass.invalidate());
        setContentView(root);
        glass.post(() -> { int[] a=new int[2], b=new int[2]; scroll.getLocationInWindow(a); glass.getLocationInWindow(b); android.util.Log.i("AtlasGlassTest", "scrollY="+scroll.getScrollY()+" contentY="+a[1]+" glassY="+b[1]+" density="+density); });
    }
}
