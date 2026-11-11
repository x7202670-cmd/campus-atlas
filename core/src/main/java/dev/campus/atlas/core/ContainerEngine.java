package dev.campus.atlas.core;
import java.io.File;
/** Import storage is separate from runtime installation. Never patch or re-sign guest APKs. */
public interface ContainerEngine {
    boolean isAvailable();
    String status();
    void installOriginal(File apk);
    void launch(String packageName);
    LocationOutput locationOutput(String packageName);
    final class Unavailable implements ContainerEngine {
        public boolean isAvailable(){return false;}
        public String status(){return "容器引擎待接入";}
        public void installOriginal(File apk){throw unsupported();}
        public void launch(String packageName){throw unsupported();}
        public LocationOutput locationOutput(String packageName){throw unsupported();}
        private UnsupportedOperationException unsupported(){return new UnsupportedOperationException(status());}
    }
}
