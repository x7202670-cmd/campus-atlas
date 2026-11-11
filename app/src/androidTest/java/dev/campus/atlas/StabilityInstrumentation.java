package dev.campus.atlas;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import dev.campus.atlas.core.*;
import java.util.function.BooleanSupplier;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Field;

/** Device-only service regression driver; no input injection or security changes. */
public final class StabilityInstrumentation extends Instrumentation {
    private PlaybackRuntime runtime;
    private LocationManager locations;
    private boolean screen,backgroundOnly,foregroundOnly;
    private final Point a=new Point(30.263,120.122),b=new Point(30.2632,120.1222);
    private boolean originallySystem;
    private int passed;
    private volatile boolean foreground;
    private Application app;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity activity){if(activity instanceof MainActivity)foreground=true;}
        public void onActivityPaused(Activity activity){if(activity instanceof MainActivity)foreground=false;}
        public void onActivityCreated(Activity activity,Bundle state){}
        public void onActivityStarted(Activity activity){}
        public void onActivityStopped(Activity activity){}
        public void onActivitySaveInstanceState(Activity activity,Bundle state){}
        public void onActivityDestroyed(Activity activity){}
    };
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);screen=arguments!=null&&"true".equals(arguments.getString("screen"));backgroundOnly=arguments!=null&&"true".equals(arguments.getString("background"));foregroundOnly=arguments!=null&&"true".equals(arguments.getString("foreground"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable failure=null;long began=SystemClock.elapsedRealtime();
        try{
            stage("starting","Driving only the first-party location services");
            app=(Application)getTargetContext().getApplicationContext();app.registerActivityLifecycleCallbacks(lifecycle);
            stage("await_app_foreground","Host should launch MainActivity using am start");
            waitFor(()->foreground,45000,"main app foreground");
            runtime=PlaybackRuntime.get(getTargetContext());locations=getTargetContext().getSystemService(LocationManager.class);
            originallySystem=SystemLocationOutput.DESCRIPTION.equals(runtime.output.description());
            require(runtime.systemLocation.unavailableReason().isEmpty(),runtime.systemLocation.unavailableReason());
            onMain(()->{stopOutput();runtime.output.select(runtime.systemLocation);});
            fixed(a);monitor("fixed",a,6000);
            if(Build.VERSION.SDK_INT>=31){
                android.location.provider.ProviderProperties gps=locations.getProviderProperties("gps"),network=locations.getProviderProperties("network");
                require(gps.getAccuracy()==1&&gps.getPowerUsage()==3&&gps.hasSatelliteRequirement(),"Unexpected GPS provider properties");
                require(network.getAccuracy()==2&&network.getPowerUsage()==1&&network.hasMonetaryCost()&&network.hasNetworkRequirement()&&network.hasCellRequirement(),"Unexpected network provider properties");
                stage("provider_properties","GPS fine/high-power; network coarse/low-power/network+cell+cost verified from system");passed++;
            }
            if(screen){
                stage("await_screen_off","Press the phone power button; waiting up to 90 seconds");
                PowerManager power=getTargetContext().getSystemService(PowerManager.class);
                waitFor(()->!power.isInteractive(),90000,"screen off");
                monitorWriter("screen_off_fixed",20000);
                require(!power.isInteractive(),"screen was switched on before the observation ended");
            }else if(backgroundOnly){
                stage("independent_probe_window","Host may open the standalone probe; output stays active for 30 seconds");monitorBackground(30000);
            }else{
                fixed(b);monitor("changed_fixed",b,5000);
                onMain(()->getTargetContext().startForegroundService(new Intent(getTargetContext(),RoutePlaybackService.class)
                    .setAction(RoutePlaybackService.START).putExtra("request",runtime.newRouteRequest(new RoutePlan(java.util.Arrays.asList(b,new Point(30.2642,120.1232)),6d,1d,60000L,false)))));
                waitFor(()->runtime.player.snapshot().state==RoutePlayer.State.RUNNING,8000,"route start");
                monitor("route_long_interval",null,6000);
                require(runtime.player.snapshot().distanceMeters>5,"route did not advance");
                command(RoutePlaybackService.PAUSE);waitFor(()->runtime.player.snapshot().state==RoutePlayer.State.PAUSED,5000,"pause");
                RoutePlayer.Snapshot paused=runtime.player.snapshot();Point hold=runtime.output.last();
                monitor("paused_heartbeat",hold,12000);
                require(runtime.player.snapshot().elapsedMillis==paused.elapsedMillis,"pause advanced route time");
                require(Math.abs(runtime.player.snapshot().distanceMeters-paused.distanceMeters)<0.001,"pause advanced route distance");
                require(Math.abs(locations.getLastKnownLocation("gps").getSpeed())<0.01,"paused speed was not zero");
                command(RoutePlaybackService.RESUME);waitFor(()->runtime.player.snapshot().state==RoutePlayer.State.RUNNING,5000,"resume");
                monitor("resumed_route",null,5000);
                require(runtime.player.snapshot().distanceMeters>paused.distanceMeters+5,"resume did not advance route");
                fixed(a);monitor("route_to_fixed",a,5000);
                if(!foregroundOnly){stage("independent_probe_window","Host may open the standalone probe; output stays active for 30 seconds");monitorBackground(30000);}
            }
            stage("stopping","Removing both test providers");
        }catch(Throwable error){failure=error;result.putString("failure",android.util.Log.getStackTraceString(error));stage("failure",error.toString());}
        finally{
            if(runtime!=null)try{
                onMain(()->stopOutput());SystemClock.sleep(1000);
                require(runtime.output.last()==null,"output was not cleared");
                require(!getTargetContext().getSharedPreferences("playback_recovery",0).contains("pending_target"),"recovery journal still pending");
                require(!runtime.systemLocation.verified(),"mock readback remains confirmed after stop");
                onMain(()->{if(originallySystem)runtime.output.select(runtime.systemLocation);else runtime.output.preview();});
                stage("restored","Output stopped; form settings untouched; original output mode restored");
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;result.putString("cleanup_failure",android.util.Log.getStackTraceString(cleanup));}
            if(app!=null)app.unregisterActivityLifecycleCallbacks(lifecycle);
        }
        result.putString("stream",failure==null?"PASS: device service stability checks and cleanup completed":"FAIL: "+failure);
        result.putInt("passed_stages",passed);result.putLong("duration_ms",SystemClock.elapsedRealtime()-began);
        finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void stopOutput(){
        runtime.newRequest();FixedLocationService.stopForHandoff(getTargetContext());
        runtime.player.stop(true);getTargetContext().stopService(new Intent(getTargetContext(),RoutePlaybackService.class));
    }
    private void fixed(Point point){
        onMain(()->FixedLocationService.start(getTargetContext(),point));
        waitFor(()->{Point last=runtime.output.last();return last!=null&&last.distanceTo(point)<0.1&&runtime.owner instanceof FixedLocationService;},8000,"fixed point");
        waitFor(()->matches("gps",point)&&matches("network",point),8000,"fresh fixed provider caches");
    }
    @SuppressWarnings("deprecation") private boolean matches(String provider,Point point){Location location=locations.getLastKnownLocation(provider);return location!=null&&location.isFromMockProvider()&&point.distanceTo(new Point(location.getLatitude(),location.getLongitude()))<1&&(SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos())<2500000000L;}
    private void command(String action){onMain(()->getTargetContext().startForegroundService(new Intent(getTargetContext(),RoutePlaybackService.class).setAction(action)));}
    private void monitorWriter(String stage,long duration)throws Exception{
        Field timestamp=SystemLocationOutput.class.getDeclaredField("lastNanos");timestamp.setAccessible(true);
        long end=SystemClock.elapsedRealtime()+duration,last=-1,maxGap=0;double maxAge=0;int observations=0;
        while(SystemClock.elapsedRealtime()<end){
            long nanos;synchronized(runtime.systemLocation){nanos=timestamp.getLong(runtime.systemLocation);}
            double age=(SystemClock.elapsedRealtimeNanos()-nanos)/1e6;
            require(nanos>0&&age<1500,"writer stopped refreshing");maxAge=Math.max(maxAge,age);
            require(runtime.output.last()!=null&&runtime.output.last().distanceTo(a)<0.1,"writer target changed");
            if(nanos!=last){if(last>=0)maxGap=Math.max(maxGap,(nanos-last)/1000000);last=nanos;observations++;}
            SystemClock.sleep(250);
        }
        Bundle evidence=new Bundle();evidence.putString("stage",stage);evidence.putInt("successful_write_observations",observations);evidence.putLong("max_observed_write_interval_ms",maxGap);evidence.putDouble("max_write_age_ms",maxAge);sendStatus(0,evidence);passed++;
    }
    private void monitorBackground(long duration)throws Exception{
        Field timestamp=SystemLocationOutput.class.getDeclaredField("lastNanos");timestamp.setAccessible(true);
        UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        long end=SystemClock.elapsedRealtime()+duration,last=-1,maxGap=0;int writes=0,probeMatches=0;String probeStatus="";
        java.util.Set<String> distinctTimes=new java.util.HashSet<>();
        while(SystemClock.elapsedRealtime()<end){
            long nanos;synchronized(runtime.systemLocation){nanos=timestamp.getLong(runtime.systemLocation);}
            require(nanos>0&&(SystemClock.elapsedRealtimeNanos()-nanos)<1500000000L,"background writer stopped refreshing");
            require(runtime.output.last()!=null&&runtime.output.last().distanceTo(a)<0.1,"background target changed");
            if(nanos!=last){if(last>=0)maxGap=Math.max(maxGap,(nanos-last)/1000000);last=nanos;writes++;}
            AccessibilityNodeInfo root=automation.getRootInActiveWindow();
            if(root!=null&&"dev.campus.atlas.probe".contentEquals(root.getPackageName()==null?"":root.getPackageName())){
                String status=probeText(root);
                if(status.contains("实时回调")&&status.contains("latitude: 30.2630000")&&status.contains("longitude: 120.1220000")&&status.contains("mock: true")){probeMatches++;probeStatus=status;java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("位置时间: (\\d+)").matcher(status);if(matcher.find())distinctTimes.add(matcher.group(1));}
                root.recycle();
            }
            SystemClock.sleep(500);
        }
        require(probeMatches>=3,"independent probe did not provide three matching live observations");
        require(distinctTimes.size()>=3,"independent probe sample timestamps did not advance");
        Bundle evidence=new Bundle();evidence.putString("stage","independent_background_probe");evidence.putInt("successful_write_observations",writes);evidence.putLong("max_observed_write_interval_ms",maxGap);evidence.putInt("matching_probe_observations",probeMatches);evidence.putInt("distinct_probe_sample_timestamps",distinctTimes.size());evidence.putString("probe_summary",probeStatus);sendStatus(0,evidence);passed++;
    }
    private String probeText(AccessibilityNodeInfo node){
        StringBuilder text=new StringBuilder();CharSequence value=node.getText();
        if(value!=null&&(value.toString().startsWith("实时回调")||value.toString().startsWith("独立接收结果")))text.append(value).append('\n');
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){text.append(probeText(child));child.recycle();}}
        return text.toString();
    }
    @SuppressWarnings("deprecation") private void monitor(String name,Point expected,long duration){
        long end=SystemClock.elapsedRealtime()+duration,lastGps=-1,maxGap=0;double maxAge=0,maxDistance=0;int unique=0;
        while(SystemClock.elapsedRealtime()<end){
            for(String provider:new String[]{"gps","network"}){
                Location location=locations.getLastKnownLocation(provider);require(location!=null,name+": missing "+provider+" foreground="+foreground+" state="+runtime.player.snapshot().state+" serviceError="+runtime.serviceError);
                require(location.isFromMockProvider(),name+": non-mock "+provider);
                double age=(SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos())/1e6;
                require(age>=0&&age<2500,name+": stale "+provider+" age="+age);maxAge=Math.max(maxAge,age);
                if(expected!=null){double distance=expected.distanceTo(new Point(location.getLatitude(),location.getLongitude()));require(distance<1,name+": target mismatch "+provider);maxDistance=Math.max(maxDistance,distance);}
                if("gps".equals(provider)&&location.getElapsedRealtimeNanos()!=lastGps){
                    if(lastGps>=0)maxGap=Math.max(maxGap,(location.getElapsedRealtimeNanos()-lastGps)/1000000);
                    lastGps=location.getElapsedRealtimeNanos();unique++;
                }
            }
            SystemClock.sleep(100);
        }
        Bundle evidence=new Bundle();evidence.putString("stage",name);evidence.putInt("unique_gps_samples",unique);
        evidence.putLong("max_sample_interval_ms",maxGap);evidence.putDouble("max_age_ms",maxAge);evidence.putDouble("max_target_distance_m",maxDistance);
        evidence.putString("state",runtime.player.snapshot().state.name());sendStatus(0,evidence);passed++;
    }
    private void waitFor(BooleanSupplier condition,long timeout,String label){
        long end=SystemClock.elapsedRealtime()+timeout;
        while(!condition.getAsBoolean()){if(SystemClock.elapsedRealtime()>=end)throw new AssertionError("Timed out: "+label+" / "+(runtime==null?"":runtime.serviceError));SystemClock.sleep(100);}
    }
    private void stage(String name,String detail){Bundle update=new Bundle();update.putString("stage",name);update.putString("detail",detail);update.putLong("mono_ms",SystemClock.elapsedRealtime());sendStatus(0,update);}
    private void onMain(Runnable runnable){final Throwable[] error={null};runOnMainSync(()->{try{runnable.run();}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

