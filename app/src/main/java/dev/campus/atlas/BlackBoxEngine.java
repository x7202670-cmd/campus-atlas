package dev.campus.atlas;
import java.io.File;
import dev.campus.atlas.core.*;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService;

final class BlackBoxEngine implements ContainerEngine {
    private static final int USER=0;
    private final android.content.Context context;
    BlackBoxEngine(android.content.Context context){this.context=context.getApplicationContext();}
    public boolean isAvailable(){
        if(AtlasApplication.initializationError!=null)return false;
        try{return BlackBoxCore.get().isServicesAvailable();}catch(Throwable e){return false;}
    }
    public String status(){return AtlasApplication.initializationError!=null?"初始化失败："+AtlasApplication.initializationError:isAvailable()?"容器服务已连接":"容器服务正在初始化 / 尚未连接";}
    private void requireAvailable(){if(!isAvailable())throw new IllegalStateException(status());}
    private IBLocationManagerService locationService(){
        requireAvailable();
        IBLocationManagerService service=BLocationManager.get().getService();
        if(service==null||!service.asBinder().isBinderAlive())throw new IllegalStateException("容器定位服务未连接，无法确认写入或还原");
        return service;
    }
    public boolean installed(String pkg){return isAvailable()&&BlackBoxCore.get().isInstalled(pkg,USER);}
    public void installOriginal(File apk){
        requireAvailable();
        if(!apk.isFile()||apk.length()==0)throw new IllegalArgumentException("原版 APK 文件已丢失，请重新导入");
        InstallResult result=BlackBoxCore.get().installPackageAsUser(apk,USER);
        if(result==null||!result.success)throw new IllegalStateException(result==null?"容器未返回安装结果":result.msg);
        if(result.packageName==null||!installed(result.packageName))throw new IllegalStateException("安装结果未能通过容器包列表回读确认");
    }
    public void launch(String pkg){requireAvailable();if(!installed(pkg))throw new IllegalStateException("请先安装到容器");if(!BlackBoxCore.get().launchApk(pkg,USER))throw new IllegalStateException("容器启动请求失败");}
    public LocationOutput locationOutput(String pkg){
        requireAvailable();if(!installed(pkg))throw new IllegalStateException("此应用尚未安装到容器");
        return recoveryOutput(pkg);
    }
    LocationOutput recoveryOutput(String pkg){
        return new JournaledOutput(context,pkg,new LocationOutput(){
            private final MotionEstimate motion = new MotionEstimate();
            public String description(){return "容器位置输出 → "+pkg+"（接收效果待实测）";}
            public synchronized void publish(Point p){
                try{
                    IBLocationManagerService service=locationService();
                    MotionEstimate.Result current = motion.update(p, android.os.SystemClock.elapsedRealtimeNanos());
                    service.setLocation(USER,pkg,new BLocation(p.lat,p.lon,current.speedMetersPerSecond,current.bearingDegrees));service.setPattern(USER,pkg,BLocationManager.OWN_MODE);
                    BLocation actual=service.getLocation(USER,pkg);
                    if(service.getPattern(USER,pkg)!=BLocationManager.OWN_MODE||actual==null||Math.abs(actual.getLatitude()-p.lat)>1e-7||Math.abs(actual.getLongitude()-p.lon)>1e-7)
                        throw new IllegalStateException("容器位置回读失败");
                }catch(android.os.RemoteException error){motion.reset();throw new IllegalStateException("容器位置通信失败",error);}
                catch(RuntimeException error){motion.reset();throw error;}
            }
            public synchronized void reset(){
                motion.reset();
                try{
                    IBLocationManagerService service=locationService();service.setPattern(USER,pkg,BLocationManager.CLOSE_MODE);
                    if(service.getPattern(USER,pkg)!=BLocationManager.CLOSE_MODE)throw new IllegalStateException("关闭容器位置覆盖失败，请重试");
                }catch(android.os.RemoteException error){throw new IllegalStateException("还原通信失败，尚未确认覆盖已关闭",error);}
            }
        });
    }
}
