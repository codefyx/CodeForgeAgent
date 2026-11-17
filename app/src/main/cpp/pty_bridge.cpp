#include <jni.h>
#include <android/log.h>
#include <pty.h>
#include <unistd.h>
#include <sys/wait.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <errno.h>

#define LOG_TAG "PtyBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

// Open a PTY pair and return master fd via out param; returns slave name.
JNIEXPORT jstring JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeOpenPty(JNIEnv* env, jclass clazz, jintArray masterFd) {
    int master, slave;
    char slaveName[256];
    if (openpty(&master, &slave, slaveName, nullptr, nullptr) != 0) {
        LOGE("openpty failed: %s", strerror(errno));
        return nullptr;
    }
    // Set master non-blocking
    int flags = fcntl(master, F_GETFL, 0);
    fcntl(master, F_SETFL, flags | O_NONBLOCK);

    jint* arr = env->GetIntArrayElements(masterFd, nullptr);
    arr[0] = master;
    env->ReleaseIntArrayElements(masterFd, arr, 0);

    // Keep slave fd open in a global? We return slave path; caller opens it.
    close(slave);
    return env->NewStringUTF(slaveName);
}

JNIEXPORT jint JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeSpawn(JNIEnv* env, jclass clazz,
                                                           jstring shellPath, jstring cwd, jint rows, jint cols,
                                                           jint masterFd) {
    const char* shell = env->GetStringUTFChars(shellPath, nullptr);
    const char* workdir = cwd ? env->GetStringUTFChars(cwd, nullptr) : nullptr;

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(shellPath, shell);
        if (workdir) env->ReleaseStringUTFChars(cwd, workdir);
        return -1;
    }
    if (pid == 0) {
        // Child
        setsid();
        // Open slave
        int slave = open(ttyname(masterFd), O_RDWR);
        if (slave < 0) _exit(1);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > 2) close(slave);
        close(masterFd);

        // Set window size
        struct winsize ws;
        memset(&ws, 0, sizeof(ws));
        ws.ws_row = rows > 0 ? rows : 24;
        ws.ws_col = cols > 0 ? cols : 80;
        ioctl(STDOUT_FILENO, TIOCSWINSZ, &ws);

        if (workdir) chdir(workdir);

        // Set env
        setenv("TERM", "xterm-256color", 1);
        setenv("HOME", "/data/data/com.codeforge.agent/files", 1);

        execl(shell, shell, "-i", (char*)nullptr);
        // Fallback to sh
        execl("/system/bin/sh", "sh", "-i", (char*)nullptr);
        _exit(127);
    }

    env->ReleaseStringUTFChars(shellPath, shell);
    if (workdir) env->ReleaseStringUTFChars(cwd, workdir);
    return (jint)pid;
}

JNIEXPORT jint JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeRead(JNIEnv* env, jclass clazz, jint fd, jbyteArray buf, jint len) {
    jbyte* b = env->GetByteArrayElements(buf, nullptr);
    ssize_t n = read(fd, b, len);
    env->ReleaseByteArrayElements(buf, b, 0);
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeWrite(JNIEnv* env, jclass clazz, jint fd, jbyteArray buf, jint len) {
    jbyte* b = env->GetByteArrayElements(buf, nullptr);
    ssize_t n = write(fd, b, len);
    env->ReleaseByteArrayElements(buf, b, JNI_ABORT);
    return (jint)n;
}

JNIEXPORT void JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeResize(JNIEnv* env, jclass clazz, jint fd, jint rows, jint cols) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = rows;
    ws.ws_col = cols;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeClose(JNIEnv* env, jclass clazz, jint fd) {
    if (fd >= 0) close(fd);
}

JNIEXPORT jint JNICALL
Java_com_codeforge_agent_nativebridge_PtyBridge_nativeWait(JNIEnv* env, jclass clazz, jint pid) {
    int status = 0;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

} // extern C