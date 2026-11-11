// Modified for Campus Atlas; see vendor/blackbox/NOTICE-ATLAS.md.
package top.niunaijun.blackbox.fake.service;

import android.content.pm.PackageManager;
import java.lang.reflect.Method;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import black.android.os.BRServiceManager;
import black.android.permission.BRIPermissionManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.service.base.PkgMethodProxy;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        return BRIPermissionManagerStub.get().asInterface(BRServiceManager.get().getService(P));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");
        BRActivityThread.getWithException()._set_sPermissionManager(proxyInvocation);
        
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static boolean isMappedPermission(Object permission) {
        if(android.Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)
                ||android.Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission))return true;
        return false;
    }

    // Modern Android routes Context permission checks through permissionmgr.
    // Translate only this guest's identity; keep the real system grant/denial.
    @ProxyMethod("checkUidPermission")
    public static class CheckLocationUidPermission extends MethodHook {
        @Override protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args!=null&&args.length>=2&&isMappedPermission(args[0]) && args[1] instanceof Integer
                    && ((BlackBoxCore.getBUid() >= 0 && ((Integer) args[1]) == BlackBoxCore.getBUid())
                    || (GuestMediaPermissionBridge.applies(args[0])
                    && GuestMediaPermissionBridge.isOwnUid((Integer) args[1])))) {
                if (GuestMediaPermissionBridge.hasLegacyVirtualStorage(args[0])) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                args[1] = BlackBoxCore.getHostUid();
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("checkPermission")
    public static class CheckLocationPackagePermission extends MethodHook {
        @Override protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // IPermissionManager is (packageName, permissionName, device, user),
            // unlike IPackageManager's (permissionName, packageName, user).
            if (args != null && args.length >= 2
                    && isMappedPermission(args[1])
                    && (java.util.Objects.equals(args[0],
                    top.niunaijun.blackbox.app.BActivityThread.getAppPackageName())
                    || (GuestMediaPermissionBridge.applies(args[1])
                    && GuestMediaPermissionBridge.isOwnPackage(args[0])))
                    && args[0] != null) {
                if (GuestMediaPermissionBridge.hasLegacyVirtualStorage(args[1])) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                args[0] = BlackBoxCore.getHostPkg();
            }
            return method.invoke(who, args);
        }
    }

}
