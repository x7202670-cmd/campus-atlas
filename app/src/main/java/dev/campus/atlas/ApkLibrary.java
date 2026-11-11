package dev.campus.atlas;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Byte-for-byte copy into private storage, never a system or guest install. */
final class ApkLibrary {
    interface Uninstaller { void uninstall(String pkg) throws Exception; }
    static void removePackage(Context context,String pkg,Uninstaller uninstaller)throws Exception{
        android.content.SharedPreferences prefs=context.getSharedPreferences("atlas",Context.MODE_PRIVATE);
        JSONArray old=new JSONArray(prefs.getString("apks","[]")),keep=new JSONArray();
        ArrayList<String> remove=new ArrayList<>();HashSet<String> retained=new HashSet<>();
        for(int i=0;i<old.length();i++){
            JSONObject item=old.getJSONObject(i);String file=item.optString("file"),sha=item.optString("sha256");
            if(pkg.equals(item.optString("package"))){
                if(!sha.matches("[0-9a-f]{64}")||!file.equals(sha+".apk"))throw new IOException("APK 记录格式异常，无法安全删除");
                remove.add(file);
            }else{keep.put(item);retained.add(file);}
        }
        if(remove.isEmpty())throw new IOException("未找到该应用的导入记录");
        File dir=new File(context.getFilesDir(),"original-apks").getCanonicalFile();
        for(String name:remove)if(!dir.equals(new File(dir,name).getCanonicalFile().getParentFile()))throw new IOException("APK 文件路径异常");
        uninstaller.uninstall(pkg);
        for(String name:remove){if(retained.contains(name))continue;File file=new File(dir,name);if(file.exists()&&!file.delete())throw new IOException("无法删除 APK 文件，请重试");}
        if(!prefs.edit().putString("apks",keep.toString()).commit())throw new IOException("无法保存应用列表，请重试");
    }
    static JSONObject importOriginal(Context c,Uri uri)throws Exception{
        File dir=new File(c.getFilesDir(),"original-apks");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法建立 APK 库");
        File temp=File.createTempFile("import-",".apk",dir);
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("无法读取文件");
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){size+=n;if(size>512L*1024*1024)throw new IOException("APK 大于 512 MB");out.write(buffer,0,n);digest.update(buffer,0,n);}
            }
            PackageInfo info=c.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),0);
            if(info==null)throw new IOException("无法解析 APK，请选择单个完整 APK");
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));
            File destination=new File(dir,hash+".apk");
            if(!destination.exists()&&!temp.renameTo(destination))throw new IOException("保存 APK 失败");
            return new JSONObject().put("package",info.packageName).put("version",info.versionName).put("sha256",hash.toString()).put("bytes",size).put("file",destination.getName());
        }finally{if(temp.exists())temp.delete();}
    }
}
