package dev.campus.atlas;

import android.app.Application;
import android.view.View;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class)
public class LiquidNavLayoutTest {
    @Test public void dropletContentsFillMeasuredLens() {
        Application context=RuntimeEnvironment.getApplication();
        LiquidNavLayout bar=new LiquidNavLayout(context,0,new View(context),new View(context),new View(context),index->{});
        bar.addLens();
        bar.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(62,View.MeasureSpec.EXACTLY));
        bar.layout(0,0,400,62);
        FrameLayout lens=(FrameLayout)bar.getChildAt(0);
        assertTrue(lens.getWidth()>1);
        assertEquals(lens.getWidth(),lens.getChildAt(0).getWidth());
        assertEquals(lens.getHeight(),lens.getChildAt(0).getHeight());
    }

    @Test public void releasePositionDeterminesDraggedDestination() {
        Application context=RuntimeEnvironment.getApplication();int[] selected={-1};
        View tabs=new View(context);tabs.setClickable(true);
        LiquidNavLayout bar=new LiquidNavLayout(context,0,new View(context),new View(context),tabs,index->selected[0]=index);
        bar.addRestIndicator();bar.addView(tabs,new FrameLayout.LayoutParams(-1,-1));bar.addLens();
        bar.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(62,View.MeasureSpec.EXACTLY));
        bar.layout(0,0,400,62);
        MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,30,30,0);
        MotionEvent move=MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,210,30,0);
        MotionEvent up=MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,380,30,0);
        bar.dispatchTouchEvent(down);bar.dispatchTouchEvent(move);bar.dispatchTouchEvent(up);
        down.recycle();move.recycle();up.recycle();assertEquals(3,selected[0]);
    }

    @Test public void directTapClearsPressedLensBeforeClickDispatch() {
        Application context=RuntimeEnvironment.getApplication();int[] clicks={0};
        View tabs=new View(context);tabs.setClickable(true);
        LiquidNavLayout bar=new LiquidNavLayout(context,0,new View(context),new View(context),tabs,index->{});
        bar.addRestIndicator();bar.addView(tabs,new FrameLayout.LayoutParams(-1,-1));bar.addLens();
        bar.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(62,View.MeasureSpec.EXACTLY));
        bar.layout(0,0,400,62);
        FrameLayout indicator=(FrameLayout)bar.getChildAt(0),lens=(FrameLayout)bar.getChildAt(2);
        tabs.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_UP){
                assertEquals(1f,indicator.getAlpha(),0f);
                for(int i=0;i<3;i++)assertEquals(0f,lens.getChildAt(i).getAlpha(),0f);
                clicks[0]++;
            }
            return true;
        });
        MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,30,30,0);
        bar.dispatchTouchEvent(down);down.recycle();
        lens.getChildAt(0).setAlpha(1f);lens.getChildAt(1).setAlpha(.6f);lens.getChildAt(2).setAlpha(1f);
        indicator.setAlpha(0f);lens.setScaleX(1.2f);lens.setTranslationX(25f);
        MotionEvent up=MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,30,30,0);
        bar.dispatchTouchEvent(up);up.recycle();
        assertEquals(1,clicks[0]);assertEquals(1f,indicator.getAlpha(),0f);
        for(int i=0;i<3;i++)assertEquals(0f,lens.getChildAt(i).getAlpha(),0f);
        assertEquals(1f,lens.getScaleX(),0f);assertEquals(0f,lens.getTranslationX(),0f);
    }
}
