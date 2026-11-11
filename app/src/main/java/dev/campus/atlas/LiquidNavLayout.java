package dev.campus.atlas;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/** Floating tab indicator with the press and drag interaction of a glass dock. */
final class LiquidNavLayout extends FrameLayout {
    interface Selection { void select(int index); }
    private final int selected, slop;
    private final Selection selection;
    private final View mirrorTabs;
    private final FrameLayout lens;
    private final GlassBackdrop lensGlass;
    private final View lensTint, lensRim;
    private final FrameLayout restIndicator;
    private float downX, downY;
    private int dragIndex;
    private boolean dragging;

    LiquidNavLayout(Context context, int selected, View content, View tabs, View tabsAnchor, Selection selection) {
        super(context);
        this.selected = selected;
        this.dragIndex = selected;
        this.selection = selection;
        this.mirrorTabs = tabs;
        this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        restIndicator = new FrameLayout(context);
        restIndicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        restIndicator.setBackground(AtlasUi.shape(context, 0x25ffffff, 0, 22));
        restIndicator.addView(new GlassRimView(context), new LayoutParams(-1,-1));
        lens = new FrameLayout(context);
        lens.setClipChildren(false);
        lens.setBackground(AtlasUi.shape(context, 0x01000000, 0, 26));
        lens.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        lensGlass = new GlassBackdrop(context, content, tabs, tabsAnchor);
        lensGlass.setAlpha(0f);
        lens.addView(lensGlass, new LayoutParams(-1, -1));
        lensTint = new View(context);
        lensTint.setBackground(AtlasUi.shape(context, 0x25ffffff, 0x60ffffff, 22));
        lensTint.setAlpha(0f);
        lens.addView(lensTint, new LayoutParams(-1, -1));
        lensRim = new DropletHighlight(context);
        lensRim.setAlpha(0f);
        lens.addView(lensRim, new LayoutParams(-1, -1));
    }

    void addLens() { addView(lens, new LayoutParams(1, 1)); }
    void addRestIndicator() { addView(restIndicator, new LayoutParams(1, 1)); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int margin = AtlasUi.dp(getContext(), 3);
        int inset = AtlasUi.dp(getContext(), 7);
        int width = Math.max(1, (getMeasuredWidth() - 2 * inset - 8 * margin) / 4);
        int height = Math.max(1, getMeasuredHeight() - 2 * AtlasUi.dp(getContext(), 6));
        lens.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        restIndicator.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        mirrorTabs.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mirrorTabs.layout(0, 0, getWidth(), getHeight());
        int margin = AtlasUi.dp(getContext(), 3);
        int inset = AtlasUi.dp(getContext(), 7);
        int itemWidth = (getWidth() - 2 * inset - 8 * margin) / 4;
        int x = inset + margin + dragIndex * (itemWidth + 2 * margin);
        int y = AtlasUi.dp(getContext(), 6);
        int restX = inset + margin + selected * (itemWidth + 2 * margin);
        restIndicator.layout(restX, y, restX + itemWidth, getHeight() - y);
        lens.setTranslationX(0);
        lens.layout(x, y, x + itemWidth, getHeight() - y);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); dragging = false; dragIndex = selected;
            animate().cancel(); animate().scaleX(1.02f).scaleY(1.05f).setDuration(120).start();
            lens.setX(Math.max(0, Math.min(getWidth() - lens.getWidth(), downX - lens.getWidth() / 2f)));
            lensGlass.invalidate();
            restIndicator.animate().alpha(0f).setDuration(120).start();
            lensGlass.animate().alpha(1f).setDuration(120).start();
            lensTint.animate().alpha(.60f).setDuration(120).start();
            lensRim.animate().alpha(1f).setDuration(120).start();
            lens.animate().cancel(); lens.animate().scaleX(1.20f).scaleY(78f/56f).translationZ(AtlasUi.dp(getContext(),4)).setDuration(120).start();
        } else if (action == MotionEvent.ACTION_MOVE && dragging) {
            moveLens(event.getX());
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragging) {
                releaseDragVisuals();
                if (action == MotionEvent.ACTION_UP) moveLens(event.getX());
                int target = dragIndex;
                boolean cancelled = action == MotionEvent.ACTION_CANCEL;
                dragging = false;
                if (cancelled) { dragIndex = selected; requestLayout(); }
                else if (target != selected) selection.select(target);
                else { dragIndex = selected; requestLayout(); }
                return true;
            }
            // A tap can rebuild the page as soon as the child receives ACTION_UP.
            // Clear the transient lens before dispatching that click, so an old
            // press animation cannot remain visible over the new selection.
            clearTapVisuals();
            dragIndex = selected;
            requestLayout();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && Math.abs(event.getX() - downX) > slop && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY)) {
            dragging = true; moveLens(event.getX()); return true;
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent event) { return dragging || super.onTouchEvent(event); }

    private void clearTapVisuals() {
        animate().cancel(); setScaleX(1f); setScaleY(1f);
        lens.animate().cancel(); lens.setScaleX(1f); lens.setScaleY(1f); lens.setTranslationZ(0f); lens.setTranslationX(0f);
        lensGlass.animate().cancel(); lensGlass.setAlpha(0f);
        lensTint.animate().cancel(); lensTint.setAlpha(0f);
        lensRim.animate().cancel(); lensRim.setAlpha(0f);
        restIndicator.animate().cancel(); restIndicator.setAlpha(1f);
    }

    private void releaseDragVisuals() {
        animate().cancel(); animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.2f)).start();
        lensGlass.animate().alpha(0f).setDuration(180).start();
        lensTint.animate().alpha(0f).setDuration(180).start();
        lensRim.animate().alpha(0f).setDuration(180).start();
        restIndicator.animate().alpha(1f).setDuration(180).start();
        lens.animate().cancel(); lens.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(220).setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private void moveLens(float x) {
        int next = Math.max(0, Math.min(3, (int) (x * 4 / Math.max(1, getWidth()))));
        if (next != dragIndex) { dragIndex = next; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
        float half = lens.getWidth() / 2f;
        lens.setX(Math.max(0, Math.min(getWidth() - lens.getWidth(), x - half)));
        lensGlass.invalidate();
    }
}
