package dev.campus.atlas;

import android.graphics.Bitmap;
import java.util.Arrays;

/** Keeps published frames immutable while reusing them when their pixels do not change. */
final class GlassFrameCache {
    private int[] lastPixels;
    private Bitmap frame;

    Bitmap frame(Bitmap work, int[] pixels, int width, int height) {
        if(frame!=null&&frame.getWidth()==width&&frame.getHeight()==height&&Arrays.equals(lastPixels,pixels))return frame;
        work.setPixels(pixels,0,width,0,0,width,height);
        Bitmap next=work.copy(Bitmap.Config.ARGB_8888,false);
        if(next==null)throw new IllegalStateException("Unable to copy glass frame");
        if(lastPixels==null||lastPixels.length!=pixels.length)lastPixels=new int[pixels.length];
        System.arraycopy(pixels,0,lastPixels,0,pixels.length);
        frame=next;
        return frame;
    }

    void clear(){lastPixels=null;frame=null;}
}
