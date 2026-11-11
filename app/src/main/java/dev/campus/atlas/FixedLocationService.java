package dev.campus.atlas;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.ServiceInfo;
import dev.campus.atlas.core.*;

/** User-started fixed point writer. All location work runs off the UI thread. */
public final class FixedLocationService extends Service {
    private static final String CHANNEL="system_mock_location", STOP="dev.campus.atlas.system.STOP";
    private static final int NOTIFICATION=4202;
    private static final long REFRESH_MS=SystemLocationOutput.REFRESH_MS, NOTIFICATION_MS=2000;
    private static FixedLocationService active;
    private HandlerThread thread;
    private Handler worker;
    private PlaybackRuntime runtime;
    private Point point;
    private boolean restoreOnDestroy=true,refreshing,foregroundReady;
    private PowerManager.WakeLock wakeLock;
    private long renewed,lastNotification;
    private final Runnable refresh=new Runnable(){public void run(){
        synchronized(runtime.player){
            if(!refreshing||point==null||runtime.owner!=FixedLocationService.this)return;
            try{
                runtime.output.publish(point);renewWakeLock();
                long now=SystemClock.elapsedRealtime();
                if(now-lastNotification>=NOTIFICATION_MS){postNotification(runtime.systemLocation.status());lastNotification=now;}
                worker.postDelayed(this,REFRESH_MS);
            }catch(RuntimeException error){fail(error);}
        }
    }};
    static void start(Context context,Point point){
        PlaybackRuntime runtime=PlaybackRuntime.get(context);
        context.startForegroundService(new Intent(context,FixedLocationService.class).putExtra("lat",point.lat).putExtra("lon",point.lon).putExtra("request",runtime.newRequest()));
    }
    static void stopForHandoff(Context context){
        PlaybackRuntime runtime=PlaybackRuntime.get(context);
        synchronized(runtime.player){
            if(active!=null){active.refreshing=false;active.restoreOnDestroy=false;active.worker.removeCallbacksAndMessages(null);}
            context.stopService(new Intent(context,FixedLocationService.class));
        }
    }
    @Override public void onCreate(){
        super.onCreate();runtime=PlaybackRuntime.get(this);active=this;
        thread=new HandlerThread("atlas-fixed-location",android.os.Process.THREAD_PRIORITY_FOREGROUND);thread.start();worker=new Handler(thread.getLooper());
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"系统模拟定位",NotificationManager.IMPORTANCE_LOW));
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":fixed-system-location");wakeLock.setReferenceCounted(false);
        try{
            Notification initial=notification("正在准备固定位置");
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,initial,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NOTIFICATION,initial);
            foregroundReady=true;
        }catch(RuntimeException error){runtime.serviceError="固定位置服务启动失败："+error.getMessage();android.util.Log.e("AtlasLocation",runtime.serviceError,error);stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!foregroundReady){stopSelf(startId);return START_NOT_STICKY;}
        if(intent==null||STOP.equals(intent.getAction())){
            synchronized(runtime.player){
                refreshing=false;worker.removeCallbacksAndMessages(null);
                if(runtime.owner==this){runtime.newRequest();try{runtime.player.stop(true);}catch(RuntimeException error){runtime.player.requireRecovery(error.getMessage());}}
            }
            stopSelf(startId);return START_NOT_STICKY;
        }
        final long request=intent.hasExtra("request")?intent.getLongExtra("request",-1):runtime.newRequest();
        worker.post(()->{
            synchronized(runtime.player){
                if(!runtime.currentRequest(request)){if(runtime.owner!=this)stopSelf(startId);return;}
                if(!SystemLocationOutput.DESCRIPTION.equals(runtime.output.description())||!intent.hasExtra("lat")||!intent.hasExtra("lon")){if(runtime.owner!=this)stopSelf(startId);return;}
                try{
                    Point next=new Point(intent.getDoubleExtra("lat",0),intent.getDoubleExtra("lon",0));
                    String reason=runtime.systemLocation.unavailableReason();if(!reason.isEmpty())throw new IllegalStateException(reason);
                    if(runtime.player.snapshot().state==RoutePlayer.State.ERROR&&!runtime.player.snapshot().restored)throw new IllegalStateException("请先重试还原当前任务");
                    // Validate the foreground service before stopping the previous route.
                    runtime.player.stop(false);runtime.owner=this;
                    runtime.systemLocation.beginSegment();runtime.output.publish(next);
                    point=next;restoreOnDestroy=true;refreshing=true;runtime.serviceError="";
                    worker.removeCallbacks(refresh);renewWakeLock();
                    postNotification(runtime.systemLocation.status());lastNotification=SystemClock.elapsedRealtime();
                    worker.postDelayed(refresh,REFRESH_MS);
                    if(RoutePlaybackService.alive)try{stopService(new Intent(this,RoutePlaybackService.class));}catch(RuntimeException cleanup){android.util.Log.w("AtlasLocation","Unable to stop previous route service",cleanup);}
                }catch(RuntimeException error){
                    if(runtime.owner==this)fail(error);
                    else{runtime.serviceError="固定位置请求失败："+error.getMessage();postNotification(runtime.serviceError);stopSelf(startId);}
                }
            }
        });
        return START_NOT_STICKY;
    }
    private void fail(RuntimeException error){
        refreshing=false;worker.removeCallbacksAndMessages(null);
        android.util.Log.e("AtlasLocation","Fixed output failed at "+SystemClock.elapsedRealtime(),error);
        runtime.player.systemFailure("系统定位刷新失败："+error.getMessage());runtime.serviceError=runtime.player.snapshot().error;postNotification(runtime.serviceError);stopSelf();
    }
    @android.annotation.SuppressLint("WakelockTimeout") private void renewWakeLock(){if(!wakeLock.isHeld()||SystemClock.elapsedRealtime()-renewed>60000){wakeLock.acquire(120000);renewed=SystemClock.elapsedRealtime();}}
    private Notification notification(String status){
        PendingIntent open=PendingIntent.getActivity(this,3,new Intent(this,MainActivity.class).putExtra("open_tab",0).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getForegroundService(this,4,new Intent(this,FixedLocationService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_atlas).setContentTitle("行迹空间 · 系统模拟定位").setContentText(status).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止并还原",stop).build()).build();
    }
    private void postNotification(String status){getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(status));}
    @Override public void onDestroy(){
        synchronized(runtime.player){
            refreshing=false;worker.removeCallbacksAndMessages(null);
            if(runtime.owner==this){
                if(restoreOnDestroy)try{
                    if(runtime.player.snapshot().state!=RoutePlayer.State.ERROR)runtime.player.stop(true);
                    else if(!runtime.player.snapshot().restored)runtime.player.systemFailure(runtime.player.snapshot().error);
                }catch(RuntimeException error){runtime.player.requireRecovery(error.getMessage());}
                runtime.owner=null;
            }
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
            if(active==this)active=null;
        }
        thread.quitSafely();
        boolean pending=runtime.player.snapshot().state==RoutePlayer.State.ERROR&&!runtime.player.snapshot().restored;
        stopForeground(pending?STOP_FOREGROUND_DETACH:STOP_FOREGROUND_REMOVE);
        if(pending)postNotification(runtime.player.snapshot().error);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

