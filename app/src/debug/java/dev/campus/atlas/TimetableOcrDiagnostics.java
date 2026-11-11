package dev.campus.atlas;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import dev.campus.atlas.core.TimetableParser;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in OCR checks with generated images and isolated preferences; no position output. */
@android.annotation.SuppressLint("ApplySharedPref") // Isolated preferences are verified synchronously by this test thread.
public final class TimetableOcrDiagnostics extends Instrumentation {
    private LocalStore store;
    private TimetableRecognition.LocalBackend backend;
    private int passed;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();File first=null,copy=null;
        try{
            Context context=getTargetContext();
            Context isolated=new ContextWrapper(context){
                @Override public SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences("timetable_qa_"+name,mode);}
            };
            store=new LocalStore(isolated);store.prefs.edit().clear().commit();
            backend=new TimetableRecognition.LocalBackend(context,store);
            first=new File(context.getCacheDir(),"timetable-qa.png");copy=new File(context.getCacheDir(),"timetable-qa-copy.png");
            draw(first,"4-512",false);
            Recognition recognized=recognize(first);
            List<String> venues=TimetableParser.candidates(recognized.text);
            require(venues.size()==3&&keys(venues).equals(keys(Arrays.asList("朝阳教 4-512","海淀教 6-304","朝阳新桥门足球场"))),"multi_column_ocr",recognized.text+"\nvenues="+venues);
            Files.copy(first.toPath(),copy.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            store.prefs.edit().putString("ocr_image_sha256",recognized.hash).putString("ocr_image_text","cached-marker").putString("ocr","cached-marker").commit();
            require("cached-marker".equals(recognize(copy).text),"same_image_new_uri_uses_cache","");
            store.prefs.edit().putString("ocr","manual edit").commit();
            require(!"cached-marker".equals(recognize(first).text),"manual_edit_invalidates_cache","");
            store.prefs.edit().putString("ocr","cached-marker").commit();draw(first,"4-510",false);
            Recognition changed=recognize(first);
            require(!recognized.hash.equals(changed.hash)&&keys(TimetableParser.candidates(changed.text)).contains(TimetableParser.placeKey("朝阳教 4-510")),"changed_file_same_uri_reprocessed",changed.text);
            draw(first,"",true);require(recognize(first).text.trim().isEmpty(),"blank_image","");
            result.putString("result","PASS "+passed+" device OCR checks");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("result","FAIL after "+passed+": "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(store!=null)store.prefs.edit().clear().commit();if(first!=null)first.delete();if(copy!=null)copy.delete();}
    }
    private static Set<String> keys(List<String> names){Set<String> out=new HashSet<>();for(String name:names)out.add(TimetableParser.placeKey(name));return out;}
    private void require(boolean ok,String name,String detail){if(!ok)throw new AssertionError(name+": "+detail);passed++;Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
    private Recognition recognize(File file)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);Recognition out=new Recognition();
        backend.recognize(Uri.fromFile(file),new TimetableRecognition.Result(){
            public void success(String text,String hash){out.text=text;out.hash=hash;latch.countDown();}
            public void failure(Exception error){out.error=error;latch.countDown();}
        });
        if(!latch.await(45,TimeUnit.SECONDS))throw new IOException("OCR timeout");if(out.error!=null)throw out.error;return out;
    }
    private static final class Recognition{String text,hash;Exception error;}
    private void draw(File file,String room,boolean blank)throws IOException{
        Bitmap bitmap=Bitmap.createBitmap(1500,1000,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        if(!blank){
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(54);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            label(canvas,paint,"示例课程甲",90,140);label(canvas,paint,"朝阳教",180,215);label(canvas,paint,room,180,280);
            label(canvas,paint,"示例课程丙",840,140);label(canvas,paint,"海淀教",900,215);label(canvas,paint,"6-304",930,280);
            label(canvas,paint,"示例课程甲",90,570);label(canvas,paint,"朝阳教",180,645);label(canvas,paint,room,180,710);
            label(canvas,paint,"示例体育课",950,570);label(canvas,paint,"朝阳新桥",900,645);label(canvas,paint,"门足球场",900,710);
        }
        try(FileOutputStream stream=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}finally{bitmap.recycle();}
    }
    private void label(Canvas canvas,Paint paint,String text,int x,int y){canvas.drawText(text,x,y,paint);}
}
