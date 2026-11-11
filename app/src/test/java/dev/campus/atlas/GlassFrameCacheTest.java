package dev.campus.atlas;

import android.app.Application;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class GlassFrameCacheTest {
    @Test public void unchangedPixelsReuseImmutableFrameAndChangesPublishNewFrame(){
        GlassFrameCache cache=new GlassFrameCache();Bitmap work=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888);
        int[] pixels={0xff123456,0xffabcdef};Bitmap first=cache.frame(work,pixels,2,1);
        assertFalse(first.isMutable());assertSame(first,cache.frame(work,pixels.clone(),2,1));
        pixels[0]=0xff112233;Bitmap next=cache.frame(work,pixels,2,1);
        assertNotSame(first,next);assertEquals(0xff123456,first.getPixel(0,0));assertEquals(0xff112233,next.getPixel(0,0));
        cache.clear();assertNotSame(next,cache.frame(work,pixels,2,1));
    }
}
