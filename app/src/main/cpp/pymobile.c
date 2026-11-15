// Runs the Python command line (python3 <args>) inside the calling process.
// The process is started fresh for every run by PythonService, because an interpreter cannot be
// initialised twice, and it is killed to stop a run.
#include <Python.h>
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define TAG "pymobile"

static char *copy_utf8(JNIEnv *env, jstring s) {
    const char *chars = (*env)->GetStringUTFChars(env, s, NULL);
    char *out = strdup(chars);
    (*env)->ReleaseStringUTFChars(env, s, chars);
    return out;
}

JNIEXPORT jint JNICALL
Java_dev_xenolab_pymobile_python_PythonNative_run(
        JNIEnv *env, jclass clazz, jobjectArray jargs, jobjectArray jenv, jstring jcwd,
        jint fd_in, jint fd_out, jint fd_err) {
    // Standard streams: the IDE process holds the other ends of these pipes.
    dup2(fd_in, 0);
    dup2(fd_out, 1);
    dup2(fd_err, 2);
    if (fd_in > 2) close(fd_in);
    if (fd_out > 2) close(fd_out);
    if (fd_err > 2) close(fd_err);

    const char *cwd = (*env)->GetStringUTFChars(env, jcwd, NULL);
    if (chdir(cwd) != 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "chdir(%s) failed", cwd);
    }
    (*env)->ReleaseStringUTFChars(env, jcwd, cwd);

    jsize env_count = (*env)->GetArrayLength(env, jenv);
    for (jsize i = 0; i < env_count; i++) {
        char *pair = copy_utf8(env, (jstring) (*env)->GetObjectArrayElement(env, jenv, i));
        char *eq = strchr(pair, '=');
        if (eq != NULL) {
            *eq = '\0';
            setenv(pair, eq + 1, 1);
        }
        free(pair);
    }

    jsize count = (*env)->GetArrayLength(env, jargs);
    char **argv = calloc((size_t) count + 2, sizeof(char *));
    argv[0] = strdup("python3");
    for (jsize i = 0; i < count; i++) {
        argv[i + 1] = copy_utf8(env, (jstring) (*env)->GetObjectArrayElement(env, jargs, i));
    }
    int rc = Py_BytesMain((int) count + 1, argv);
    fflush(NULL);
    return rc;
}
