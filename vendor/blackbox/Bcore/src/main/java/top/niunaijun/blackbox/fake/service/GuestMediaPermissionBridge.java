package top.niunaijun.blackbox.fake.service;

/** Application-specific permission adaptation is omitted from this candidate. */
final class GuestMediaPermissionBridge {
    private GuestMediaPermissionBridge() {}
    static boolean applies(Object permission) { return false; }
    static boolean isOwnPackage(Object packageName) { return false; }
    static boolean isOwnUid(int uid) { return false; }
    static boolean hasLegacyVirtualStorage(Object permission) { return false; }
}
