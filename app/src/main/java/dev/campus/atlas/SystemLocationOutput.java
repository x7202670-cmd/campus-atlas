package dev.campus.atlas;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.location.provider.ProviderProperties;
import android.os.*;
import dev.campus.atlas.core.*;
import java.util.*;

/** Android's public mock-provider API. Mock flags are deliberately left intact. */
final class SystemLocationOutput implements LocationOutput {
    static final String TARGET="@android-system", DESCRIPTION="系统模拟定位";
    // Update network provider before GPS provider to maintain stable ordering across cycles.
    static final long REFRESH_MS=100;
    static final double MOCK_ALTITUDE_METERS=55;
    private static final String[] PROVIDERS={LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER};
    private static final int MAX_FAILED_CYCLES=3;
    private static final long READBACK_MAX_AGE_NANOS=5_000_000_000L;
    static final class Sample {
        final Point point;final long nanos;final boolean mock;
        Sample(Point point,long nanos,boolean mock){this.point=point;this.nanos=nanos;this.mock=mock;}
    }
    interface Backend {
        String unavailableReason();
        void prepare(String provider);
        void publish(String provider,Location location);
        Sample read(String provider);
        void remove(String provider);
    }
    private final Backend backend;
    private final SharedPreferences recovery;
    private final Set<String> prepared=new HashSet<>(),verified=new HashSet<>();
    private Point last;private long lastNanos,startedNanos=-1;
    private Point motionLast;
    private float motionBearing;
    private int failedCycles;
    private String writeWarning="";
    synchronized void beginSegment(){motionLast=null;motionBearing=0;}
    SystemLocationOutput(Context context){this(context,new AndroidBackend(context));}
    SystemLocationOutput(Context context,Backend backend){this.backend=backend;recovery=context.getSharedPreferences("playback_recovery",0);}
    public String description(){return DESCRIPTION;}
    String unavailableReason(){return backend.unavailableReason();}
    synchronized String status(){
        String reason=unavailableReason();if(!reason.isEmpty())return reason;
        if(!writeWarning.isEmpty())return writeWarning;
        if(last==null)return "已授权，尚未输出系统模拟位置";
        inspectReadback();return verified.containsAll(Arrays.asList(PROVIDERS))?"GPS / 网络缓存已匹配，其他应用接收待验证":"坐标已提交，等待当前目标的系统缓存回读";
    }
    synchronized boolean verified(){inspectReadback();return verified.containsAll(Arrays.asList(PROVIDERS));}
    private void inspectReadback(){
        verified.clear();
        if(last==null||!writeWarning.isEmpty())return;
        long now=SystemClock.elapsedRealtimeNanos();
        for(String provider:PROVIDERS)try{Sample sample=backend.read(provider);if(sample!=null&&sample.mock&&sample.nanos>=startedNanos&&sample.nanos<=now&&now-sample.nanos<=READBACK_MAX_AGE_NANOS&&sample.point.distanceTo(last)<1)verified.add(provider);}catch(RuntimeException ignored){/* No current evidence means no confirmed status. */}
    }
    synchronized String diagnostics(){
        if(last==null||!unavailableReason().isEmpty())return "";
        long now=SystemClock.elapsedRealtimeNanos();StringBuilder details=new StringBuilder();
        for(String provider:new String[]{"network","gps","fused"}){if(details.length()>0)details.append('\n');details.append(provider).append(": ");try{Sample sample=backend.read(provider);if(sample==null){details.append("无缓存结果");continue;}details.append(sample.mock?"模拟数据":"非模拟数据").append(String.format(Locale.CHINA," · %.1f 秒前 · 距当前目标 %.1f 米",(now-sample.nanos)/1e9,sample.point.distanceTo(last)));}catch(RuntimeException e){details.append("无法回读 · ").append(e.getClass().getSimpleName());}}
        return details.toString();
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    public synchronized void publish(Point point){
        String reason=unavailableReason();if(!reason.isEmpty())throw new IllegalStateException(reason);
        if(!TARGET.equals(recovery.getString("pending_target",""))&&!recovery.edit().putString("pending_target",TARGET).commit())throw new IllegalStateException("无法保存系统定位还原记录，已取消输出");
        long nanos=SystemClock.elapsedRealtimeNanos();double distance=motionLast==null?0:motionLast.distanceTo(point);
        float speed=0f;
        if(motionLast!=null&&nanos>lastNanos){double deltaSec=(nanos-lastNanos)/1e9;if(deltaSec>=0.05){double rawSpeed=distance/deltaSec;if(Double.isFinite(rawSpeed)&&rawSpeed>=0&&rawSpeed<=100.0)speed=(float)rawSpeed;}}
        if(startedNanos<0)startedNanos=nanos;
        try{
            IllegalStateException cycleFailure=null;
            for(String provider:PROVIDERS){
              try{
                if(!prepared.contains(provider)){backend.prepare(provider);prepared.add(provider);}
                Location location=new Location(provider);location.setLatitude(point.lat);location.setLongitude(point.lon);location.setAccuracy("gps".equals(provider)?1:2);location.setSpeed(speed);
                location.setAltitude(MOCK_ALTITUDE_METERS);
                if(motionLast!=null&&distance>0.01)motionBearing=bearing(motionLast,point);
                location.setBearing(motionBearing);
                location.setTime(System.currentTimeMillis());location.setElapsedRealtimeNanos(nanos);
                if("gps".equals(provider)){Bundle extras=new Bundle();extras.putInt("satellites",7);location.setExtras(extras);}
                try{backend.publish(provider,location);}catch(IllegalStateException transientFailure){
                    // One bounded retry; permission/argument failures are never swallowed.
                    android.util.Log.w("AtlasLocation","Retrying "+provider+" write at "+SystemClock.elapsedRealtime(),transientFailure);
                    backend.publish(provider,location);
                }
              }catch(IllegalStateException transientFailure){
                // A temporary error on one provider must not starve the other.
                if(cycleFailure==null)cycleFailure=transientFailure;else cycleFailure.addSuppressed(transientFailure);
              }
            }
            if(cycleFailure!=null){
                failedCycles++;verified.clear();
                // Initial publication must succeed. Once established, keep test providers
                // across two missed cycles; sustained failure still stops and restores.
                if(last==null||failedCycles>=MAX_FAILED_CYCLES)throw cycleFailure;
                writeWarning="部分定位写入失败，正在重试（"+failedCycles+"/"+MAX_FAILED_CYCLES+"）";
                android.util.Log.w("AtlasLocation",writeWarning,cycleFailure);return;
            }
            failedCycles=0;writeWarning="";
            last=point;motionLast=point;lastNanos=nanos;verified.clear();
        }catch(RuntimeException failure){android.util.Log.e("AtlasLocation","Location write failed at "+SystemClock.elapsedRealtime()+", last success age ms="+(lastNanos==0?-1:(SystemClock.elapsedRealtimeNanos()-lastNanos)/1000000),failure);try{reset();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw new IllegalStateException("系统模拟定位写入失败："+failure.getMessage(),failure);}
    }
    private static float bearing(Point from,Point to){
        double a=Math.toRadians(from.lat),b=Math.toRadians(to.lat),d=Math.toRadians(to.lon-from.lon);
        float brg=(float)((Math.toDegrees(Math.atan2(Math.sin(d)*Math.cos(b),Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(d)))+360.0)%360.0);
        return (brg>=360f||brg<0f||!Float.isFinite(brg))?0f:brg;
    }
    @androidx.annotation.RequiresApi(31) static ProviderProperties providerProperties(String provider){
        boolean gps=LocationManager.GPS_PROVIDER.equals(provider);
        return new ProviderProperties.Builder().setHasNetworkRequirement(!gps).setHasSatelliteRequirement(gps).setHasCellRequirement(!gps).setHasMonetaryCost(!gps).setHasAltitudeSupport(true).setHasSpeedSupport(true).setHasBearingSupport(true).setPowerUsage(gps?ProviderProperties.POWER_USAGE_HIGH:ProviderProperties.POWER_USAGE_LOW).setAccuracy(gps?ProviderProperties.ACCURACY_FINE:ProviderProperties.ACCURACY_COARSE).build();
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    public synchronized void reset(){
        if(prepared.isEmpty()&&!TARGET.equals(recovery.getString("pending_target","")))return;
        RuntimeException failure=null;
        for(String provider:PROVIDERS)try{backend.remove(provider);prepared.remove(provider);}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(failure!=null)throw new IllegalStateException("系统模拟定位尚未确认关闭，请重新选择行迹空间为模拟位置应用后重试",failure);
        if(TARGET.equals(recovery.getString("pending_target",""))&&!recovery.edit().remove("pending_target").commit())throw new IllegalStateException("模拟提供器已移除，还原记录清理失败，请重试");
        last=null;motionLast=null;motionBearing=0;lastNanos=0;startedNanos=-1;failedCycles=0;writeWarning="";verified.clear();
    }
    private static final class AndroidBackend implements Backend {
        private final Context context;private final LocationManager manager;
        AndroidBackend(Context context){this.context=context.getApplicationContext();manager=this.context.getSystemService(LocationManager.class);}
        public String unavailableReason(){
            AppOpsManager ops=context.getSystemService(AppOpsManager.class);
            if(ops==null||ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),context.getPackageName())!=AppOpsManager.MODE_ALLOWED)return "请在开发者选项中将行迹空间选为模拟位置信息应用";
            if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return "请先授予行迹空间精确位置权限，用于回读验证";
            if(manager==null)return "系统定位服务不可用";
            if(Build.VERSION.SDK_INT>=28?!manager.isLocationEnabled():!manager.isProviderEnabled(LocationManager.GPS_PROVIDER))return "请先开启手机的系统定位开关";
            return "";
        }
        public void prepare(String provider){
            // Remove only a previous test provider. Real providers are not removed by this API.
            try{manager.removeTestProvider(provider);}catch(IllegalArgumentException ignored){}
            boolean gps=LocationManager.GPS_PROVIDER.equals(provider);
            if(Build.VERSION.SDK_INT>=31)manager.addTestProvider(provider,providerProperties(provider));
            else prepareLegacy(provider,gps);
            manager.setTestProviderEnabled(provider,true);
        }
        // API 26–30 uses Criteria constants; their values equal the API 31 provider constants.
        @android.annotation.SuppressLint("WrongConstant") @SuppressWarnings("deprecation") private void prepareLegacy(String provider,boolean gps){manager.addTestProvider(provider,!gps,gps,!gps,!gps,true,true,true,gps?Criteria.POWER_HIGH:Criteria.POWER_LOW,gps?Criteria.ACCURACY_FINE:Criteria.ACCURACY_COARSE);}
        public void publish(String provider,Location location){manager.setTestProviderLocation(provider,location);}
        @android.annotation.SuppressLint("MissingPermission") @SuppressWarnings("deprecation") public Sample read(String provider){Location l=manager.getLastKnownLocation(provider);return l==null?null:new Sample(new Point(l.getLatitude(),l.getLongitude()),l.getElapsedRealtimeNanos(),Build.VERSION.SDK_INT>=31?l.isMock():l.isFromMockProvider());}
        public void remove(String provider){try{manager.removeTestProvider(provider);}catch(IllegalArgumentException ignored){/* No test provider remains. */}}
    }
}
