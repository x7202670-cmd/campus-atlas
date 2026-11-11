package dev.campus.atlas;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import dev.campus.atlas.core.TimetableLayout;
import dev.campus.atlas.core.TimetableParser;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One application-scoped job survives page changes and Activity recreation. */
final class TimetableRecognition {
    interface Result { void success(String text,String hash);void failure(Exception error); }
    interface Backend { void recognize(Uri uri,Result result); }
    private static TimetableRecognition instance;
    private final Context app;
    private final LocalStore store;
    private final Backend backend;
    private boolean busy;
    private Runnable listener;
    private String message="";

    static synchronized TimetableRecognition get(Context context){
        Context app=context.getApplicationContext();
        if(instance==null||instance.app!=app)instance=new TimetableRecognition(app,null);
        return instance;
    }
    TimetableRecognition(Context context,Backend backend){
        app=context.getApplicationContext();store=new LocalStore(app);
        this.backend=backend==null?new LocalBackend(app,store):backend;
    }
    boolean busy(){return busy;}
    void attach(Runnable listener){this.listener=listener;}
    void detach(Runnable listener){if(this.listener==listener)this.listener=null;}
    String takeMessage(){String value=message;message="";return value;}
    boolean start(Uri uri){
        if(busy)return false;
        busy=true;message="";String original=store.prefs.getString("ocr","");
        if(listener!=null)listener.run();
        try{backend.recognize(uri,new Result(){
            public void success(String text,String hash){
                String recognized=TimetableParser.normalizeOcrText(text);
                if(recognized.trim().isEmpty())finish("未识别到文字，已保留原结果。请使用清晰的原始课表截图");
                else if(!original.equals(store.prefs.getString("ocr","")))finish("识别期间文字已修改，已保留你的修改。可重新选择图片识别");
                else{
                    store.prefs.edit().putString("ocr",recognized).putString("ocr_image_sha256",hash)
                            .putString("ocr_image_text",recognized).apply();
                    int count=TimetableParser.candidates(recognized).size();
                    finish(recognized.equals(original)?"该图片的识别结果未变化，已保留现有地点":count==0?
                            "已识别文字，未找到可靠地点。请展开文字核对或手动添加":
                            "识别完成，已合并重复地点，共 "+count+" 个候选。请核对地点与教室号");
                }
            }
            public void failure(Exception error){finish("识别失败，已保留原结果。请重新选择清晰截图");}
        });}catch(Exception error){finish("无法读取图片，已保留原结果。请重新选择图片");}
        return true;
    }
    private void finish(String text){busy=false;message=text;if(listener!=null)listener.run();}

    static final class LocalBackend implements Backend {
        private final Context context;private final LocalStore store;
        private final Handler main=new Handler(Looper.getMainLooper());
        private final ExecutorService worker=Executors.newSingleThreadExecutor();
        LocalBackend(Context context,LocalStore store){this.context=context;this.store=store;}
        public void recognize(Uri uri,Result result){
            worker.execute(()->{
                TextRecognizer client=null;
                try{
                    String hash=digest(uri);
                    String cached=store.prefs.getString("ocr_image_text","");
                    if(hash.equals(store.prefs.getString("ocr_image_sha256",""))&&!cached.isEmpty()
                            &&cached.equals(store.prefs.getString("ocr",""))){main.post(()->result.success(cached,hash));return;}
                    InputImage image=InputImage.fromFilePath(context,uri);
                    client=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                    TextRecognizer active=client;
                    client.process(image).addOnSuccessListener(worker,text->{
                        try{
                            List<TimetableLayout.Line> lines=new ArrayList<>();
                            for(com.google.mlkit.vision.text.Text.TextBlock block:text.getTextBlocks()){
                                for(com.google.mlkit.vision.text.Text.Line line:block.getLines()){
                                    android.graphics.Rect box=line.getBoundingBox();
                                    if(box!=null)lines.add(new TimetableLayout.Line(line.getText(),box.left,box.top,box.right,box.bottom));
                                }
                            }
                            String recognized=lines.isEmpty()?text.getText():TimetableLayout.text(lines);
                            main.post(()->result.success(recognized,hash));
                        }catch(Exception error){main.post(()->result.failure(error));}
                        finally{active.close();}
                    }).addOnFailureListener(worker,error->{active.close();main.post(()->result.failure(error));});
                }catch(Exception error){if(client!=null)client.close();main.post(()->result.failure(error));}
            });
        }
        private String digest(Uri uri)throws Exception{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream stream=context.getContentResolver().openInputStream(uri)){
                if(stream==null)throw new java.io.IOException("无法读取图片");
                byte[] buffer=new byte[16384];int count;
                while((count=stream.read(buffer))!=-1)digest.update(buffer,0,count);
            }
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return hex.toString();
        }
    }
}
