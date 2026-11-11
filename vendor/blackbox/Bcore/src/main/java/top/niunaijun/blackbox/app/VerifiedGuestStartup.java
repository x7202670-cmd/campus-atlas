package top.niunaijun.blackbox.app;
/** Third-party application-specific startup adaptation omitted from this candidate. */
public final class VerifiedGuestStartup {
 private VerifiedGuestStartup() {}
 public static boolean supports(String pkg, String digest) { return false; }
 public static void prepare(String pkg, String apkPath, android.content.Context context, ClassLoader loader) {}
}
