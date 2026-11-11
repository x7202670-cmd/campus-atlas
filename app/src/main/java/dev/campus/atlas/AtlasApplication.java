package dev.campus.atlas;
import android.app.Application;
import android.content.Context;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

public final class AtlasApplication extends Application {
    static volatile String initializationError;
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            BlackBoxCore.get().doAttachBaseContext(base,new ClientConfiguration(){
                @Override public String getHostPackageName(){return base.getPackageName();}
                @Override public boolean isEnableDaemonService(){return false;}
                @Override public String getLogSenderChatId(){return "";}
            });
        } catch(Throwable error) { initializationError=error.getClass().getSimpleName()+": "+error.getMessage(); }
    }
    @Override public void onCreate(){
        super.onCreate();
        if(initializationError==null)try {BlackBoxCore.get().doCreate();}
        catch(Throwable error){initializationError=error.getClass().getSimpleName()+": "+error.getMessage();}
    }
}
