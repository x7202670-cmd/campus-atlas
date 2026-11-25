package top.niunaijun.blackbox.app;

import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import top.niunaijun.blackbox.core.env.BEnvironment;

public final class VerifiedGuestStartup {
    private static final String TAG = "VerifiedGuestStartup";
    private static final String APK_SHA = "f0a1b52f94bdd679acd4ff3c54b0a8d507202df0995ce88db8220bca31069e7f";
    private static final String PAYLOAD_SHA = "0e33543319fbdaf0e5855101b40747b50b924254c95a5e01b3a4d3782f6c0589";
    private static final String ZIP_SHA = "7f4e8de000784103ef46cda4f44c542215422b394bb5ece8b81ad38aba6b6de0";
    private static final String[] NAMES = {"classes.dex", "classes2.dex", "classes3.dex"};
    private static final int[] SIZES = {9692488,10567504,7074456};
    private static final String[] SHAS = {
        "a2b4652d12cfe42c64da1d4523c62aea09f4de732a5c8e8a9dbb4aac02ce223c",
        "32a40c9a32d077e949c45cf3cdaaab05a2e8d37ef104e265390ec6323be78a1c",
        "5dac73afa11c5f483436da8c306ed2ce73b8d5a29e571e4a385d6c0fd72cb465"};
    private static final String PATCHED_DEX3_SHA = "9875ab9af323c481c43324f38ed0b0bd4ed643997ffb0afcd2ee964d61a6b96d";
    private static final Set<ClassLoader> DONE = Collections.newSetFromMap(new WeakHashMap<ClassLoader,Boolean>());
    private static final Set<ClassLoader> BOOTSTRAPPED = Collections.newSetFromMap(new WeakHashMap<ClassLoader,Boolean>());

    private static synchronized void installBusinessDex(String pkg, String apkPath, ClassLoader guest) {
        if (!"net.crigh.mysport".equals(pkg)) return;
        if (DONE.contains(guest)) return;
        try {
            if (guest == null) throw new IOException("Guest classloader is null");
            File apk = new File(apkPath);
            if (!APK_SHA.equals(hash(apk))) throw new IOException("Unrecognized APK digest");
            byte[] payload;
            try (ZipFile zip = new ZipFile(apk)) {
                ZipEntry entry = zip.getEntry("assets/csnd5cc84fd.data");
                if (entry == null || entry.getSize()!=27334775) throw new IOException("Unexpected payload carrier");
                try (InputStream in=zip.getInputStream(entry)) { payload=readBounded(in,27334775); }
            }
            if (!PAYLOAD_SHA.equals(hash(payload))) throw new IOException("Payload digest mismatch");
            byte[] key = {(byte)0x63,(byte)0xf3,0x6f,0x09,(byte)0xc1,0x0b,0x1b,0x6b};
            for (int start : new int[]{0,payload.length-4096})
                for(int i=0;i<4096;i++) payload[start+i]^=key[i%key.length];
            if (!ZIP_SHA.equals(hash(payload))) throw new IOException("Decoded carrier digest mismatch");
            File dir=new File(BEnvironment.getCacheDir(),"classroom-dex-v115-notification-immutable");
            if (!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("Cannot create private DEX directory");
            boolean[] found=new boolean[3];
            byte[][] verified=new byte[NAMES.length][];
            try (ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(payload))) {
                ZipEntry entry;
                while ((entry=in.getNextEntry())!=null) {
                    int index=Arrays.asList(NAMES).indexOf(entry.getName());
                    if(index<0) throw new IOException("Unexpected decoded member");
                    if(found[index]) throw new IOException("Duplicate decoded member");
                    byte[] dex=readBounded(in,SIZES[index]);
                    if(dex.length!=SIZES[index]||!SHAS[index].equals(hash(dex))) throw new IOException("DEX identity mismatch: "+NAMES[index]);
                    found[index]=true;
                    verified[index]=dex;
                }
            }
            for(boolean item:found) if(!item) throw new IOException("Incomplete decoded archive");
            for(int index=0;index<NAMES.length;index++) {
                byte[] dex=verified[index];
                String storedSha=SHAS[index];
                if(index==2) {
                    patchImmutableNotification(dex);
                    storedSha=PATCHED_DEX3_SHA;
                    if(!storedSha.equals(hash(dex))) throw new IOException("Patched DEX identity mismatch");
                }
                File file=new File(dir,NAMES[index]);
                if(file.exists()&&!storedSha.equals(hash(file))&&!file.delete()) throw new IOException("Cannot replace invalid private DEX");
                if(!file.exists()) {
                    try(FileOutputStream out=new FileOutputStream(file)) {
                        if(!file.setReadOnly()) throw new IOException("Cannot make DEX read-only");
                        out.write(dex); out.getFD().sync();
                    }
                }
                if(!storedSha.equals(hash(file))) throw new IOException("Stored DEX identity mismatch");
                if(!file.setReadOnly()) throw new IOException("Cannot ensure cached DEX is read-only");
                Log.i(TAG,"validated "+NAMES[index]+" bytes="+dex.length);
            }
            StringBuilder paths=new StringBuilder();
            for(String name:NAMES){ if(paths.length()>0) paths.append(File.pathSeparator); paths.append(new File(dir,name).getPath()); }
            Field pathList=field(guest.getClass(),"pathList");
            Object targetList=pathList.get(guest);
            Field elements=field(targetList.getClass(),"dexElements");
            Object[] before=(Object[])elements.get(targetList);
            DexClassLoader donor=new DexClassLoader(paths.toString(),dir.getPath(),null,guest.getParent());
            Object donorList=pathList.get(donor);
            Field suppressed=field(donorList.getClass(),"dexElementsSuppressedExceptions");
            Object errors=suppressed.get(donorList);
            if(errors instanceof IOException[] && ((IOException[])errors).length>0) {
                for(IOException error:(IOException[])errors) Log.e(TAG,"DEX load suppressed error",error);
                throw new IOException("DEX loader reported suppressed errors");
            }
            Object[] added=(Object[])elements.get(donorList);
            if(added==null||added.length!=3) throw new IOException("Unexpected DEX element count");
            Object[] merged=(Object[])Array.newInstance(before.getClass().getComponentType(),before.length+added.length);
            System.arraycopy(before,0,merged,0,before.length); System.arraycopy(added,0,merged,before.length,added.length);
            elements.set(targetList,merged);
            Log.i(TAG,"elements before="+before.length+" after="+merged.length+" loader="+guest.getClass().getName());
            Class<?> business=Class.forName("net.crigh.cgsport.application.MyApplication",false,guest);
            Log.i(TAG,"business-class-resolved definingLoader="+business.getClassLoader().getClass().getName());
            DONE.add(guest);
        } catch(Throwable error) {
            Log.e(TAG,"Class visibility experiment failed",error);
            throw new IllegalStateException("Classroom business DEX preparation failed",error);
        }
    }
    private static Field field(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f; } catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static void patchImmutableNotification(byte[] dex) throws Exception {
        final int offset=0x3b07b8;
        byte[] expected={0x15,0x05,0x00,0x08};
        if(dex.length<=offset+3) throw new IOException("DEX is too short for verified notification patch");
        for(int i=0;i<expected.length;i++)
            if(dex[offset+i]!=expected[i]) throw new IOException("Notification instruction mismatch");
        dex[offset+3]=0x0c;
        MessageDigest signature=MessageDigest.getInstance("SHA-1");
        signature.update(dex,32,dex.length-32);
        byte[] signed=signature.digest();
        System.arraycopy(signed,0,dex,12,signed.length);
        Adler32 checksum=new Adler32();
        checksum.update(dex,12,dex.length-12);
        long value=checksum.getValue();
        for(int i=0;i<4;i++) dex[8+i]=(byte)(value >>> (i*8));
    }
    public static boolean supports(String pkg,String digest) {
        return "net.crigh.mysport".equals(pkg)&&APK_SHA.equals(digest);
    }
    public static synchronized void prepare(String pkg,String apkPath,android.content.Context context,ClassLoader loader) {
        if(!"dev.campus.atlas.oss".equals(top.niunaijun.blackbox.BlackBoxCore.getHostPkg())||!"net.crigh.mysport".equals(pkg))return;
        try {
            if(!supports(pkg,hash(new File(apkPath)))) {
                Log.i(TAG,"No verified startup preset for this APK; retaining its original startup");return;
            }
            if(!BOOTSTRAPPED.contains(loader)) {
                Class<?> bootstrap=Class.forName("com.security.shell.Da2",true,loader);
                bootstrap.getDeclaredMethod("initNative",android.content.Context.class).invoke(null,context);
                BOOTSTRAPPED.add(loader);
                Log.i(TAG,"original native bootstrap completed");
            }
            installBusinessDex(pkg,apkPath,loader);
        } catch(Throwable error) {throw new IllegalStateException("Verified sample startup failed",error);}
    }
    private static byte[] readBounded(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(max,65536)); byte[] buffer=new byte[65536]; int n;
        while((n=in.read(buffer))!=-1){ if(out.size()+n>max) throw new IOException("Member exceeds expected length");out.write(buffer,0,n); }
        return out.toByteArray();
    }
    private static String hash(File file) throws Exception { try(InputStream in=new FileInputStream(file)){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);return hex(md.digest());} }
    private static String hash(byte[] data) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(data)); }
    private static String hex(byte[] data) { StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString(); }
}
