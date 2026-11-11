package dev.campus.atlas;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import dev.campus.atlas.core.*;
import org.json.*;
import java.util.*;

/** User-started, foreground, single-process route playback. No automatic restart. */
public final class RoutePlaybackService extends Service {
    static final String START="dev.campus.atlas.route.START",RESUME="dev.campus.atlas.route.RESUME",
        PAUSE="dev.campus.atlas.route.PAUSE",STOP="dev.campus.atlas.route.STOP",SYNC="dev.campus.atlas.route.SYNC";
    private static final String CHANNEL="route_playback";
    private static final int NOTIFICATION=4201;
    static volatile boolean alive;
    private PlaybackRuntime runtime;
    private HandlerThread thread;
    private Handler worker;
    private PowerManager.WakeLock wakeLock;
    private boolean foregroundReady;
    private long lastNotification;
    private long renewed;
    private int processedStartId;
    private final Runnable tick=new Runnable(){public void run(){
        synchronized(runtime.player){
            if(runtime.owner!=RoutePlaybackService.this){releaseWakeLock();stopSelf();return;}
            if(runtime.player.snapshot().state==RoutePlayer.State.PAUSED)runtime.player.hold();else runtime.player.tick();
            updateService(false);
        }
    }};

    @Override public void onCreate(){
        super.onCreate();alive=true;runtime=PlaybackRuntime.get(this);
        thread=new HandlerThread("atlas-route-player",android.os.Process.THREAD_PRIORITY_FOREGROUND);thread.start();worker=new Handler(thread.getLooper());
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"路线后台播放",NotificationManager.IMPORTANCE_LOW));
        try {
            Notification initial=notification("正在准备路线服务",true,false);
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,initial,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION,initial);
            wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":route");
            wakeLock.setReferenceCounted(false);
            foregroundReady=true;
        } catch(Exception error){runtime.serviceError="启动路线服务失败："+error.getMessage();android.util.Log.e("AtlasLocation",runtime.serviceError,error);stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!foregroundReady){stopSelf(startId);return START_NOT_STICKY;}
        String action=intent==null?STOP:intent.getAction();
        final long request=START.equals(action)?(intent.hasExtra("request")?intent.getLongExtra("request",-1):runtime.newRequest()):-1;
        worker.post(()->{
          synchronized(runtime.player){
            processedStartId=startId;
            if(START.equals(action)&&!runtime.currentRequest(request)){if(runtime.owner!=this)stopSelfResult(startId);return;}
            if(!START.equals(action)&&runtime.owner!=this){stopSelfResult(startId);return;}
            worker.removeCallbacks(tick);
            try {
                if(START.equals(action)){
                    RoutePlan plan=runtime.takeRouteRequest(request);
                    if(plan==null)throw new IllegalStateException("路线请求已失效，请重新开始");
                    RoutePlayer.State state=runtime.player.snapshot().state;
                    if(state==RoutePlayer.State.RUNNING||state==RoutePlayer.State.PAUSED){updateService(true);return;}
                    // Only hand off after foreground startup and route validation succeed.
                    FixedLocationService.stopForHandoff(this);runtime.owner=this;runtime.serviceError="";
                    if(isSystemMode())runtime.systemLocation.beginSegment();
                    runtime.player.start(plan);
                }else if(RESUME.equals(action)){if(runtime.player.snapshot().state==RoutePlayer.State.PAUSED){if(isSystemMode())runtime.systemLocation.beginSegment();runtime.player.resume();}}
                else if(PAUSE.equals(action)){runtime.player.pause();if(isSystemMode())runtime.player.hold();}
                else if(STOP.equals(action)){runtime.player.stop(true);}
                else if(!SYNC.equals(action)){throw new IllegalArgumentException("未知播放命令");}
            } catch(Exception error){
                if(runtime.owner==this){if(runtime.player.snapshot().state!=RoutePlayer.State.ERROR)runtime.player.systemFailure("路线命令失败："+error.getMessage());}
                else{runtime.serviceError="路线请求失败："+error.getMessage();stopSelfResult(startId);return;}
            }
            updateService(true);
          }
        });
        return START_NOT_STICKY;
    }
    @android.annotation.SuppressLint("WakelockTimeout")
    private void updateService(boolean force){
        RoutePlayer.Snapshot snapshot=runtime.player.snapshot();
        if(snapshot.state==RoutePlayer.State.ERROR)runtime.serviceError=snapshot.error;
        boolean running=snapshot.state==RoutePlayer.State.RUNNING;
        boolean holding=snapshot.state==RoutePlayer.State.PAUSED&&isSystemMode();
        if(running||holding){
            if(!wakeLock.isHeld()||SystemClock.elapsedRealtime()-renewed>=60000){
                try{wakeLock.acquire(120000);renewed=SystemClock.elapsedRealtime();}
                catch(Exception error){runtime.player.systemFailure("无法保持后台运行："+error.getMessage());updateService(true);return;}
            }
            if(force||SystemClock.elapsedRealtime()-lastNotification>=2000){postNotification(snapshot,true);lastNotification=SystemClock.elapsedRealtime();}
            long interval=holding?1000:Math.min(snapshot.intervalMillis,snapshot.durationMillis-snapshot.elapsedMillis);
            if(isSystemMode())interval=Math.min(interval,SystemLocationOutput.REFRESH_MS);
            worker.postDelayed(tick,Math.max(1,interval));
        }else{
            releaseWakeLock();
            boolean keep=snapshot.state==RoutePlayer.State.PAUSED||(snapshot.state==RoutePlayer.State.ERROR&&!snapshot.restored);
            postNotification(snapshot,keep);
            if(!keep){if(stopSelfResult(processedStartId))stopForeground(STOP_FOREGROUND_DETACH);}
        }
    }
    private boolean isSystemMode(){return SystemLocationOutput.DESCRIPTION.equals(runtime.output.description());}
    private void postNotification(RoutePlayer.Snapshot snapshot,boolean ongoing){
        String status;
        switch(snapshot.state){
            case RUNNING:status="路线正在后台播放";break;
            case PAUSED:status="已暂停 · 位置保持在最后一点";break;
            case COMPLETED:status="路线已完成 · 已关闭位置覆盖";break;
            case ERROR:status=snapshot.error+(snapshot.restored?" · 已还原":" · 需要重试还原");break;
            default:status="已结束 · 已关闭路线输出";
        }
        if(snapshot.hasPlan)status+=String.format(Locale.CHINA,"\n%.0f 米 · %d / %d 秒",snapshot.distanceMeters,snapshot.elapsedMillis/1000,snapshot.durationMillis/1000);
        getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(status,ongoing,snapshot.state==RoutePlayer.State.PAUSED));
    }
    private Notification notification(String status,boolean ongoing,boolean paused){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_atlas)
            .setContentTitle("行迹空间 · 路线播放").setContentText(status).setStyle(new Notification.BigTextStyle().bigText(status))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(ongoing).setCategory(Notification.CATEGORY_SERVICE);
        if(ongoing){
            RoutePlayer.State state=runtime.player.snapshot().state;
            if(state==RoutePlayer.State.RUNNING||state==RoutePlayer.State.PAUSED)
                builder.addAction(new Notification.Action.Builder(null,paused?"继续":"暂停",action(paused?RESUME:PAUSE,1)).build());
            builder.addAction(new Notification.Action.Builder(null,"结束并还原",action(STOP,2)).build());
        }
        return builder.build();
    }
    private PendingIntent action(String command,int code){return PendingIntent.getForegroundService(this,code,new Intent(this,RoutePlaybackService.class).setAction(command),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private void releaseWakeLock(){if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}
    @Override public void onDestroy(){
        alive=false;
        synchronized(runtime.player){
            if(worker!=null)worker.removeCallbacksAndMessages(null);
            if(runtime.owner==this){
                RoutePlayer.Snapshot snapshot=runtime.player.snapshot();
                if(snapshot.state==RoutePlayer.State.RUNNING||snapshot.state==RoutePlayer.State.PAUSED){
                    try{runtime.player.stop(true);}catch(Exception ignored){/* Recovery journal persists until verified reset. */}
                }
                runtime.owner=null;
            }
            releaseWakeLock();
        }
        if(thread!=null)thread.quitSafely();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
