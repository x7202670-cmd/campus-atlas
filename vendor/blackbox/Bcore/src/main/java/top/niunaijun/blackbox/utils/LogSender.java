// Modified for Campus Atlas; see vendor/blackbox/NOTICE-ATLAS.md.
package top.niunaijun.blackbox.utils;
import java.io.File;
/** Atlas integration: external log transport removed. */
public class LogSender {
    public static String send(String chatId, File file, String caption) {
        return "External log upload disabled";
    }
}