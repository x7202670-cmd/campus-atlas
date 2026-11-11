package dev.campus.atlas;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Loads display resources from the saved original APK, never on the UI thread. */
final class ApkPresentationLoader {
    static final class Presentation {
        final String label;
        final Drawable.ConstantState icon;
        Presentation(String label,Drawable.ConstantState icon){this.label=label;this.icon=icon;}
    }
    private final Context context;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Presentation> cache=new LruCache<>(48);
    private final Map<String,List<Consumer<Presentation>>> pending=new HashMap<>();
    private final Map<String,String> names=new HashMap<>();
    private boolean closed;

    ApkPresentationLoader(Context context){this.context=context.getApplicationContext();}
    String cachedName(String pkg){String name=names.get(pkg);return name==null?pkg:name;}

    void bind(JSONObject item,TextView name,ImageView icon,View placeholder,View described,String descriptionSuffix){
        String pkg=item.optString("package","");String filename=item.optString("file","");
        WeakReference<TextView> nameRef=new WeakReference<>(name);
        WeakReference<ImageView> iconRef=new WeakReference<>(icon);
        WeakReference<View> placeholderRef=new WeakReference<>(placeholder),describedRef=new WeakReference<>(described);
        load(pkg,filename,result->{
            TextView label=nameRef.get();if(label!=null&&label.isAttachedToWindow()&&!result.label.isEmpty())label.setText(result.label);
            ImageView image=iconRef.get();if(image!=null&&image.isAttachedToWindow()&&result.icon!=null){try{image.setImageDrawable(result.icon.newDrawable().mutate());image.setVisibility(View.VISIBLE);View fallback=placeholderRef.get();if(fallback!=null)fallback.setVisibility(View.GONE);}catch(RuntimeException ignored){}}
            View option=describedRef.get();if(option!=null&&option.isAttachedToWindow()&&!result.label.isEmpty())option.setContentDescription(result.label+descriptionSuffix);
        });
    }

    private void load(String pkg,String filename,Consumer<Presentation> callback){
        if(closed||pkg.isEmpty()||filename.isEmpty())return;
        final File file;
        try{File dir=new File(context.getFilesDir(),"original-apks").getCanonicalFile();file=new File(dir,filename).getCanonicalFile();if(!dir.equals(file.getParentFile())||!file.isFile())return;}catch(IOException e){return;}
        String key=pkg+"|"+file.getPath()+"|"+file.length()+"|"+file.lastModified()+"|"+context.getResources().getConfiguration().toString();
        Presentation present=cache.get(key);if(present!=null){main.post(()->{if(!closed)callback.accept(present);});return;}
        List<Consumer<Presentation>> callbacks=pending.get(key);if(callbacks!=null){callbacks.add(callback);return;}
        callbacks=new ArrayList<>();callbacks.add(callback);pending.put(key,callbacks);
        executor.execute(()->{Presentation result=readArchive(pkg,file);main.post(()->{if(closed)return;cache.put(key,result);if(!result.label.isEmpty())names.put(pkg,result.label);List<Consumer<Presentation>> listeners=pending.remove(key);if(listeners!=null)for(Consumer<Presentation> listener:listeners)listener.accept(result);});});
    }

    Presentation readArchive(String pkg,File file){
        try{
            PackageManager pm=context.getPackageManager();PackageInfo info=pm.getPackageArchiveInfo(file.getAbsolutePath(),0);
            if(info==null||info.applicationInfo==null||!pkg.equals(info.packageName))return new Presentation("",null);
            ApplicationInfo app=info.applicationInfo;app.sourceDir=file.getAbsolutePath();app.publicSourceDir=file.getAbsolutePath();
            CharSequence loaded=pm.getApplicationLabel(app);String label=loaded==null?"":loaded.toString().trim();if(label.equals(pkg))label="";
            Drawable.ConstantState icon=null;try{Drawable loadedIcon=pm.getApplicationIcon(app);if(loadedIcon!=null)icon=loadedIcon.getConstantState();}catch(RuntimeException ignored){}
            return new Presentation(label,icon);
        }catch(RuntimeException e){return new Presentation("",null);}
    }

    void close(){closed=true;pending.clear();executor.shutdownNow();}
}
