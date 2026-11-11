// Modified for Campus Atlas; see vendor/blackbox/NOTICE-ATLAS.md.
#include "FileSystemHook.h"
#include "IO.h"
#include "Log.h"
#include "xdl.h"
#include "Dobby/dobby.h"
#include <atomic>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdarg.h>
#include <string>

using Open = int (*)(const char *, int, ...);
using OpenAt = int (*)(int, const char *, int, ...);
static Open originalOpen = nullptr;
static Open originalOpen64 = nullptr;
static OpenAt originalOpenAt = nullptr;
static OpenAt originalOpenAt64 = nullptr;
static std::atomic<unsigned> redirectedCount{0};

static bool needsMode(int flags) {
    return (flags & O_CREAT) != 0 || (flags & O_TMPFILE) == O_TMPFILE;
}
static const char *mappedPath(const char *path, std::string &mapped) {
    if (!IO::redirectNativeDataPath(path, mapped)) return path;
    if (redirectedCount.fetch_add(1, std::memory_order_relaxed) < 8)
        ALOGD("Native private-data open redirected");
    return mapped.c_str();
}
// Variadic mode is present only for creation flags. Relative openat paths retain dirfd.
#define OPEN_WRAPPER(name, original) \
static int name(const char *path, int flags, ...) { \
    mode_t mode = 0; \
    if (needsMode(flags)) { va_list args; va_start(args, flags); mode = va_arg(args, mode_t); va_end(args); } \
    std::string mapped; \
    return original(mappedPath(path, mapped), flags, mode); \
}
#define OPENAT_WRAPPER(name, original) \
static int name(int dirfd, const char *path, int flags, ...) { \
    mode_t mode = 0; \
    if (needsMode(flags)) { va_list args; va_start(args, flags); mode = va_arg(args, mode_t); va_end(args); } \
    std::string mapped; \
    return original(dirfd, mappedPath(path, mapped), flags, mode); \
}
OPEN_WRAPPER(redirectOpen, originalOpen)
OPEN_WRAPPER(redirectOpen64, originalOpen64)
OPENAT_WRAPPER(redirectOpenAt, originalOpenAt)
OPENAT_WRAPPER(redirectOpenAt64, originalOpenAt64)

void FileSystemHook::init() {
    static bool initialized = false;
    if (initialized) return;
    initialized = true;
    void *libc = xdl_open("libc.so", XDL_DEFAULT);
    if (libc == nullptr) { ALOGE("Native open redirect: libc unavailable"); return; }
    struct Entry { const char *name; void *replacement; void **original; };
    Entry entries[] = {
        {"open", (void *) redirectOpen, (void **) &originalOpen},
        {"open64", (void *) redirectOpen64, (void **) &originalOpen64},
        {"openat", (void *) redirectOpenAt, (void **) &originalOpenAt},
        {"openat64", (void *) redirectOpenAt64, (void **) &originalOpenAt64},
    };
    void *installed[sizeof(entries) / sizeof(entries[0])] = {};
    unsigned count = 0;
    for (auto &entry : entries) {
        void *target = xdl_sym(libc, entry.name, nullptr);
        if (target == nullptr) { ALOGE("Native open redirect: %s unavailable", entry.name); continue; }
        bool alias = false;
        for (unsigned i = 0; i < count; ++i) if (installed[i] == target) alias = true;
        if (alias) { ALOGD("Native open redirect: %s shares an installed entry", entry.name); continue; }
        const int result = DobbyHook(target, entry.replacement, entry.original);
        ALOGD("Native open redirect: %s installation result=%d", entry.name, result);
        if (result == 0) installed[count++] = target;
    }
    xdl_close(libc);
}

