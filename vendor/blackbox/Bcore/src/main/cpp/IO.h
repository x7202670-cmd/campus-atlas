// Modified for Campus Atlas; see vendor/blackbox/NOTICE-ATLAS.md.




#ifndef VIRTUALM_IO_H
#define VIRTUALM_IO_H

#include <jni.h>

#include <list>
#include <iostream>
#include "BoxCore.h"

using namespace std;

class IO {
public:
    static void init(JNIEnv *env);

    struct RelocateInfo {
        const char *targetPath;
        const char *relocatePath;
    };

    static void addRule(const char *targetPath, const char *relocatePath);

    static jstring redirectPath(JNIEnv *env, jstring path);

    static jobject redirectPath(JNIEnv *env, jobject path);

    static const char *redirectPath(const char *__path);

    // Absolute guest private-data paths only; no procfs or environment filtering.
    static bool redirectNativeDataPath(const char *path, std::string &redirected);

};


#endif 
