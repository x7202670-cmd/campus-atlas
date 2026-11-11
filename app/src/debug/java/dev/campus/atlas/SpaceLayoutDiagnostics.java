package dev.campus.atlas;

import android.app.*;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Opt-in screenshots of this app only; changes no system display settings or permissions. */
public final class SpaceLayoutDiagnostics extends Instrumentation {
    private boolean large;
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);large=args!=null&&"large".equals(args.getString("variant"));start();}
    @Override public Activity newActivity(ClassLoader loader,String className,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{
        Activity created=super.newActivity(loader,className,intent);
        if(large&&created instanceof MainActivity){Configuration override=new Configuration();override.fontScale=1.5f;override.screenWidthDp=320;created.applyOverrideConfiguration(override);}
        return created;
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class).putExtra("open_tab",3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity=(MainActivity)startActivitySync(intent);
            if(large)runOnMainSync(()->activity.getWindow().setLayout(AtlasUi.dp(activity,320),ViewGroup.LayoutParams.MATCH_PARENT));
            settle();
            runOnMainSync(()->{
                require(find(root(),"空间应用")!=null,"app section missing");
                require(find(root(),"查看权限与设置")!=null,"settings toggle missing");
                require(!find(root(),"在系统设置中管理权限").isShown(),"settings must start collapsed");
                require(find(root(),"导入")!=null,"import missing");
            });
            screenshot(large?"narrow-large":"normal");
            runOnMainSync(()->{find(root(),"查看权限与设置").performClick();require(find(root(),"在系统设置中管理权限").isShown(),"settings did not expand");});
            settle();runOnMainSync(()->{ScrollView scroll=scroll(root());if(scroll!=null)scroll.fullScroll(View.FOCUS_DOWN);});
            settle();screenshot(large?"narrow-large-settings":"settings");
            runOnMainSync(()->{
                Button toggle=(Button)find(root(),"收起权限与设置");toggle.performClick();
                require(!find(root(),"在系统设置中管理权限").isShown(),"settings did not collapse");
            });
            result.putString("result","PASS "+(large?"320dp fontScale=1.5":"normal")+" space layout; screenshots saved");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("result","FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}
    }
    private View root(){return activity.getWindow().getDecorView();}
    private void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private TextView find(View view,String prefix){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(prefix))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),prefix);if(found!=null)return found;}}
        return null;
    }
    private ScrollView scroll(View view){if(view instanceof ScrollView)return (ScrollView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ScrollView found=scroll(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private void settle()throws InterruptedException{waitForIdleSync();Thread.sleep(450);waitForIdleSync();}
    private void screenshot(String name)throws IOException{
        Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("screenshot unavailable");
        File directory=getTargetContext().getExternalFilesDir("space-layout-checks");if(directory==null)throw new IOException("output directory unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
}
