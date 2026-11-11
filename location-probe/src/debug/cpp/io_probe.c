#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <string.h>

JNIEXPORT jint JNICALL Java_dev_campus_atlas_probe_IoProbeApplication_readMarker(
        JNIEnv *env, jclass clazz, jstring path, jboolean direct) {
    (void)clazz;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name) return ENOMEM;
    int fd = direct ? (int)syscall(__NR_openat, AT_FDCWD, name, O_RDONLY | O_CLOEXEC, 0)
                    : open(name, O_RDONLY | O_CLOEXEC);
    int result;
    if (fd < 0) {
        result = errno;
    } else {
        unsigned char value = 0;
        ssize_t count = read(fd, &value, 1);
        result = count < 0 ? errno : (count == 1 && value == 0x5a ? 0 : -1);
        close(fd);
    }
    (*env)->ReleaseStringUTFChars(env, path, name);
    return result;
}

JNIEXPORT jint JNICALL Java_dev_campus_atlas_probe_IoProbeApplication_createMarker(
        JNIEnv *env, jclass clazz, jstring path, jstring directory, jint kind) {
    (void)clazz;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name) return ENOMEM;
    const char *dir = (*env)->GetStringUTFChars(env, directory, NULL);
    if (!dir) { (*env)->ReleaseStringUTFChars(env, path, name); return ENOMEM; }
    const int flags = O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC;
    int fd = -1;
    if (kind == 0) fd = open(name, flags, 0600);
    if (kind == 1) fd = openat(AT_FDCWD, name, flags, 0600);
    if (kind == 2) {
        int dirfd = open(dir, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
        if (dirfd >= 0) {
            const char *leaf = strrchr(name, '/');
            fd = openat(dirfd, leaf ? leaf + 1 : name, flags, 0600);
            int saved = errno;
            close(dirfd);
            errno = saved;
        }
    }
    if (kind == 3) fd = open64(name, flags, 0600);
    int result = fd < 0 ? errno : 0;
    if (fd >= 0) {
        const unsigned char value = 0x5a;
        ssize_t count = write(fd, &value, 1);
        result = count < 0 ? errno : (count == 1 ? 0 : -1);
        close(fd);
    }
    (*env)->ReleaseStringUTFChars(env, directory, dir);
    (*env)->ReleaseStringUTFChars(env, path, name);
    return result;
}
