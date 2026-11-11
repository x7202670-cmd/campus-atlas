package dev.campus.atlas.probe;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.location.*;
import android.view.View;
import android.widget.*;
import java.util.Locale;

/** Standalone first-party test guest. No accounts, campus APIs, or data uploads. */
public final class ProbeActivity extends Activity implements LocationListener {
    private LocationManager manager;
    private TextView result;
    private TextView diagnostics;
    private int samples;
    private boolean automaticPermissionRequestMade;
    private TextView historyView;
    private final java.util.ArrayDeque<String> history=new java.util.ArrayDeque<>();
    private long previousCallback=-1,maxGap;
    private int screenOffSamples;
    private boolean subscribed;
    private final java.util.Map<String,Location> providerSamples=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,Integer> providerCounts=new java.util.HashMap<>();
    private double expectedLat=Double.NaN,expectedLon=Double.NaN;
    private TextView providerStatus;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        expectedLat=getIntent().getDoubleExtra("target_lat",Double.NaN);expectedLon=getIntent().getDoubleExtra("target_lon",Double.NaN);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,48,24,24);
        TextView title=new TextView(this);title.setText("行迹定位探针 "+BuildConfig.VERSION_NAME);title.setTextSize(26);page.addView(title);
        TextView note=new TextView(this);note.setText("系统模式请从手机桌面独立启动此探针。若从容器内启动，结果不能证明外部应用可收到系统位置。这里分别检查 GPS、network 和 fused 的实时回调；日志不记录坐标。");page.addView(note);
        Button refresh=new Button(this);refresh.setText("授权并重新订阅位置");refresh.setOnClickListener(v->subscribe());page.addView(refresh);
        Button stop=new Button(this);stop.setText("停止订阅");stop.setOnClickListener(v->{unsubscribe();result.setText("已停止订阅；保留本次诊断记录。");});page.addView(stop);
        diagnostics=new TextView(this);diagnostics.setTextSize(13);diagnostics.setTextIsSelectable(true);page.addView(diagnostics);
        providerStatus=new TextView(this);providerStatus.setTextSize(15);providerStatus.setText("等待各提供器实时回调");page.addView(providerStatus);
        result=new TextView(this);result.setTextSize(17);result.setTextIsSelectable(true);result.setText("等待定位权限和位置回调");page.addView(result);
        historyView=new TextView(this);historyView.setTextSize(12);historyView.setTextIsSelectable(true);page.addView(historyView);
        ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(24,i.getSystemWindowInsetTop()+24,24,i.getSystemWindowInsetBottom()+24);return i;});
        manager=(LocationManager)getSystemService(LOCATION_SERVICE);
    }
    private void subscribe(){
        showDiagnostics();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);return;}
        unsubscribe();StringBuilder status=new StringBuilder();
        for(String provider:manager.getAllProviders()){
            if(!provider.equals("gps")&&!provider.equals("network")&&!provider.equals("fused"))continue;
            try{manager.requestLocationUpdates(provider,1000,0,this);subscribed=true;status.append(provider).append(" 已订阅\n");Location last=manager.getLastKnownLocation(provider);if(last!=null)displayLocation(last,"缓存位置（不计入实时样本）");}
            catch(Exception e){status.append(provider).append(": ").append(e.getClass().getSimpleName()).append('\n');}
        }
        if(samples==0)result.setText(status+"\n等待回调；无回调不能算定位桥已验证。");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==7){showDiagnostics();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)subscribe();else result.setText("位置权限未授予。上方为本次权限诊断结果。\n授权回调："+java.util.Arrays.toString(grants));}}
    private void showDiagnostics(){
        StringBuilder report=new StringBuilder("权限诊断：0=允许，-1=拒绝\n");
        int processUid=android.os.Process.myUid();
        report.append("自身 UID: ").append(processUid).append("\n");
        int hostUid=-1;
        try{hostUid=getPackageManager().getApplicationInfo("dev.campus.atlas",0).uid;report.append("宿主 UID: ").append(hostUid).append("\n");}
        catch(Exception e){report.append("宿主信息: ").append(e.getClass().getSimpleName()).append("\n");}
        for(String permission:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}){
            report.append(permission.endsWith("FINE_LOCATION")?"精确":"大致").append(": ");
            try{
                report.append("自身=").append(checkSelfPermission(permission));
                report.append("，包名=").append(getPackageManager().checkPermission(permission,getPackageName()));
                report.append("，宿主包名=").append(getPackageManager().checkPermission(permission,"dev.campus.atlas"));
                if(hostUid>=0)report.append("，宿主UID=").append(checkPermission(permission,android.os.Process.myPid(),hostUid));
            }catch(Exception e){report.append(" 查询异常 ").append(e.getClass().getSimpleName());}
            report.append("\n");
        }
        diagnostics.setText(report.toString());
        android.util.Log.i("AtlasProbeDiag",report.toString());
    }
    @Override public void onLocationChanged(Location p){
        if(!subscribed)return;
        samples++;long now=android.os.SystemClock.elapsedRealtime();long gap=previousCallback<0?0:now-previousCallback;
        providerSamples.put(p.getProvider(),new Location(p));providerCounts.put(p.getProvider(),providerCounts.getOrDefault(p.getProvider(),0)+1);renderProviderStatus();
        previousCallback=now;maxGap=Math.max(maxGap,gap);
        boolean interactive=((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        if(!interactive)screenOffSamples++;
        double age=(android.os.SystemClock.elapsedRealtimeNanos()-p.getElapsedRealtimeNanos())/1e9;
        String line=String.format(Locale.US,"n=%d mono=%d gap=%d screen=%s provider=%s mock=%s age=%.3fs targetDistance=%s",samples,now,gap,interactive?"on":"off",p.getProvider(),p.isFromMockProvider(),age,Double.isFinite(expectedLat)&&Double.isFinite(expectedLon)?String.format(Locale.US,"%.1fm",distanceToTarget(p)):"unset");
        android.util.Log.i("AtlasProbeSample",line);
        history.addLast(line);while(history.size()>80)history.removeFirst();
        historyView.setText("本次实例最近回调（最多80条）：\n"+android.text.TextUtils.join("\n",history));
        displayLocation(p,"实时回调");
    }
    private float distanceToTarget(Location sample){Location target=new Location("expected");target.setLatitude(expectedLat);target.setLongitude(expectedLon);return sample.distanceTo(target);}
    @SuppressWarnings("deprecation") private void renderProviderStatus(){
        StringBuilder summary=new StringBuilder("独立接收结果（只统计实时回调）：\n");
        for(String provider:new String[]{"gps","network","fused"}){summary.append(provider).append(": ");Location sample=providerSamples.get(provider);if(sample==null){summary.append("尚无实时回调\n");continue;}double age=(android.os.SystemClock.elapsedRealtimeNanos()-sample.getElapsedRealtimeNanos())/1e9;summary.append(providerCounts.get(provider)).append(" 次 · mock=").append(sample.isFromMockProvider()).append(String.format(Locale.US," · 样本年龄 %.1fs",age));if(Double.isFinite(expectedLat)&&Double.isFinite(expectedLon)){Location target=new Location("expected");target.setLatitude(expectedLat);target.setLongitude(expectedLon);summary.append(String.format(Locale.US," · 距目标 %.1fm",sample.distanceTo(target)));}summary.append('\n');}
        providerStatus.setText(summary.toString());
    }
    private void displayLocation(Location p,String source){result.setText(String.format(Locale.US,"%s\n实时样本：%d · 熄屏样本：%d\n最大回调间隔：%d ms\nprovider: %s\nlatitude: %.7f\nlongitude: %.7f\naccuracy: %.1f m\nspeed: %.2f m/s\nmock: %s\n位置时间: %d\n显示时间: %d",source,samples,screenOffSamples,maxGap,p.getProvider(),p.getLatitude(),p.getLongitude(),p.getAccuracy(),p.getSpeed(),p.isFromMockProvider(),p.getTime(),System.currentTimeMillis()));}
    private void unsubscribe(){subscribed=false;if(manager!=null)try{manager.removeUpdates(this);}catch(Exception ignored){}}
    @Override protected void onResume(){super.onResume();if(manager!=null){showDiagnostics();if(!automaticPermissionRequestMade){automaticPermissionRequestMade=true;subscribe();}else if(subscribed==false&&(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED))result.setText("已停止订阅，点击上方按钮重新订阅。");}}
    @Override protected void onDestroy(){unsubscribe();super.onDestroy();}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
}
