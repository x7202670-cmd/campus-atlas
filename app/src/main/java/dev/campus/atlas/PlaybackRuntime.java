package dev.campus.atlas;
import android.content.Context;
import android.os.SystemClock;
import dev.campus.atlas.core.*;

/** Main-process session shared by the Activity and the unexported playback service. */
final class PlaybackRuntime {
    private static PlaybackRuntime instance;
    final RoutedOutput output=new RoutedOutput();
    final SystemLocationOutput systemLocation;
    final RoutePlayer player=new RoutePlayer(SystemClock::elapsedRealtime,output);
    // Ownership and request ordering are guarded by player, shared with both writers.
    Object owner;
    private long requestVersion;
    private RoutePlan pendingRoute;
    private boolean routeImporting;
    private long routeLibraryGeneration;
    volatile String serviceError="";
    long newRequest(){synchronized(player){serviceError="";pendingRoute=null;return ++requestVersion;}}
    long newRouteRequest(RoutePlan plan){synchronized(player){if(plan==null)throw new IllegalArgumentException("路线不能为空");if(routeImporting)throw new IllegalStateException("路线正在导入，请稍候");long request=newRequest();pendingRoute=plan;return request;}}
    boolean beginRouteImport(){synchronized(player){RoutePlayer.Snapshot s=player.snapshot();if(routeImporting||pendingRoute!=null||s.state==RoutePlayer.State.RUNNING||s.state==RoutePlayer.State.PAUSED||(s.state==RoutePlayer.State.ERROR&&!s.restored))return false;routeImporting=true;return true;}}
    void finishRouteImport(){synchronized(player){routeImporting=false;}}
    void finishRouteImportWithChanges(){synchronized(player){routeLibraryGeneration++;routeImporting=false;}}
    boolean isRouteImporting(){synchronized(player){return routeImporting;}}
    long routeLibraryGeneration(){synchronized(player){return routeLibraryGeneration;}}
    RoutePlan takeRouteRequest(long request){synchronized(player){if(!currentRequest(request))return null;RoutePlan plan=pendingRoute;pendingRoute=null;return plan;}}
    void cancelRouteRequest(long request){synchronized(player){if(currentRequest(request))pendingRoute=null;}}
    boolean currentRequest(long version){synchronized(player){return version==requestVersion;}}
    static synchronized PlaybackRuntime get(Context context){
        if(instance==null)instance=new PlaybackRuntime(context.getApplicationContext());
        return instance;
    }
    private PlaybackRuntime(Context context){
        this(context,new SystemLocationOutput(context));
    }
    PlaybackRuntime(Context context,SystemLocationOutput systemLocation){
        this.systemLocation=systemLocation;
        String pending=context.getSharedPreferences("playback_recovery",0).getString("pending_target","");
        if(!pending.isEmpty()){
            output.select(SystemLocationOutput.TARGET.equals(pending)?systemLocation:new BlackBoxEngine(context).recoveryOutput(pending));
            player.requireRecovery("上次位置覆盖未确认关闭，请先点“结束并还原”："+pending);
        }else if("system".equals(context.getSharedPreferences("atlas",0).getString("output_mode","")))output.select(systemLocation);
    }
}
