package dev.campus.atlas;
import android.content.Context;
import android.content.SharedPreferences;
import dev.campus.atlas.core.*;

/** Persist cleanup intent BEFORE touching the container; clear it only after verified reset. */
final class JournaledOutput implements LocationOutput {
    private final SharedPreferences recovery;
    private final String pkg;
    private final LocationOutput delegate;
    JournaledOutput(Context context,String pkg,LocationOutput delegate){
        this.recovery=context.getSharedPreferences("playback_recovery",0);this.pkg=pkg;this.delegate=delegate;
    }
    public String description(){return delegate.description();}
    @android.annotation.SuppressLint("ApplySharedPref")
    public void publish(Point point){
        if(!pkg.equals(recovery.getString("pending_target","")) && !recovery.edit().putString("pending_target",pkg).commit())
            throw new IllegalStateException("无法保存位置还原记录，已取消输出");
        delegate.publish(point);
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    public void reset(){
        delegate.reset();
        if(pkg.equals(recovery.getString("pending_target","")) && !recovery.edit().remove("pending_target").commit())
            throw new IllegalStateException("位置覆盖已关闭，但清理记录保存失败，请重试");
    }
}
