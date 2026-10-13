#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pty.h>
#include <signal.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

extern char **environ;

static char *java_string_utf8(JNIEnv *env, jstring value) {
    const jchar *source = (*env)->GetStringChars(env, value, NULL);
    if (source == NULL) return NULL;
    jsize length = (*env)->GetStringLength(env, value);
    char *output = malloc((size_t)length * 3 + 1);
    if (output == NULL) {
        (*env)->ReleaseStringChars(env, value, source);
        return NULL;
    }
    size_t at = 0;
    for (jsize index = 0; index < length; ++index) {
        uint32_t codepoint = source[index];
        if (codepoint >= 0xD800 && codepoint <= 0xDBFF &&
            index + 1 < length && source[index + 1] >= 0xDC00 &&
            source[index + 1] <= 0xDFFF) {
            codepoint = 0x10000 + ((codepoint - 0xD800) << 10) +
                (source[++index] - 0xDC00);
        } else if (codepoint >= 0xD800 && codepoint <= 0xDFFF) {
            codepoint = 0xFFFD;
        }
        if (codepoint < 0x80) {
            output[at++] = (char)codepoint;
        } else if (codepoint < 0x800) {
            output[at++] = (char)(0xC0 | (codepoint >> 6));
            output[at++] = (char)(0x80 | (codepoint & 0x3F));
        } else if (codepoint < 0x10000) {
            output[at++] = (char)(0xE0 | (codepoint >> 12));
            output[at++] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
            output[at++] = (char)(0x80 | (codepoint & 0x3F));
        } else {
            output[at++] = (char)(0xF0 | (codepoint >> 18));
            output[at++] = (char)(0x80 | ((codepoint >> 12) & 0x3F));
            output[at++] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
            output[at++] = (char)(0x80 | (codepoint & 0x3F));
        }
    }
    output[at] = '\0';
    (*env)->ReleaseStringChars(env, value, source);
    return output;
}

static int has_env_key(const char *entry, const char *key) {
    size_t length = strlen(key);
    return strncmp(entry, key, length) == 0 && entry[length] == '=';
}

static int handle_fd(jlong handle) {
    return (int)(uint32_t)handle;
}

static pid_t handle_pid(jlong handle) {
    return (pid_t)(uint32_t)((uint64_t)handle >> 32);
}

static jlong make_handle(int fd, pid_t pid) {
    return (jlong)(((uint64_t)(uint32_t)pid << 32) | (uint32_t)fd);
}

static void throw_io(JNIEnv *env, const char *operation) {
    char message[160];
    snprintf(message, sizeof(message), "%s failed: %s", operation, strerror(errno));
    jclass exception = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception != NULL) (*env)->ThrowNew(env, exception, message);
}

JNIEXPORT jlong JNICALL
Java_com_closecode_ui_ide_NativePty_open(
        JNIEnv *env, jobject thiz, jstring shell_value, jstring directory_value,
        jint columns, jint rows) {
    (void)thiz;
    char *shell = java_string_utf8(env, shell_value);
    if (shell == NULL) return 0;
    char *directory = java_string_utf8(env, directory_value);
    if (directory == NULL) {
        free(shell);
        return 0;
    }

    struct termios terminal;
    memset(&terminal, 0, sizeof(terminal));
    terminal.c_iflag = ICRNL | IXON;
    terminal.c_oflag = OPOST | ONLCR;
    terminal.c_cflag = CREAD | CS8;
    terminal.c_lflag = ISIG | ICANON | IEXTEN;
    terminal.c_cc[VINTR] = 3;
    terminal.c_cc[VEOF] = 4;
    terminal.c_cc[VMIN] = 1;
    terminal.c_cc[VTIME] = 0;

    struct winsize size = {
        .ws_row = (unsigned short)(rows > 0 ? rows : 24),
        .ws_col = (unsigned short)(columns > 0 ? columns : 80),
    };
    size_t environment_count = 0;
    for (char **entry = environ; *entry != NULL; ++entry) {
        if (!has_env_key(*entry, "TERM") && !has_env_key(*entry, "PS1") &&
            !has_env_key(*entry, "COLUMNS") && !has_env_key(*entry, "LINES")) {
            ++environment_count;
        }
    }
    char **child_environment = calloc(environment_count + 5, sizeof(char *));
    if (child_environment == NULL) {
        free(directory);
        free(shell);
        errno = ENOMEM;
        throw_io(env, "PTY environment allocation");
        return 0;
    }
    size_t environment_index = 0;
    for (char **entry = environ; *entry != NULL; ++entry) {
        if (!has_env_key(*entry, "TERM") && !has_env_key(*entry, "PS1") &&
            !has_env_key(*entry, "COLUMNS") && !has_env_key(*entry, "LINES")) {
            child_environment[environment_index++] = *entry;
        }
    }
    char columns_value[32];
    char rows_value[32];
    snprintf(columns_value, sizeof(columns_value), "COLUMNS=%d", size.ws_col);
    snprintf(rows_value, sizeof(rows_value), "LINES=%d", size.ws_row);
    child_environment[environment_index++] = "TERM=xterm-256color";
    child_environment[environment_index++] = "PS1=";
    child_environment[environment_index++] = columns_value;
    child_environment[environment_index++] = rows_value;

    char *arguments[] = {(char *)shell, (char *)shell, "-i", NULL};
    int master = -1;
    pid_t pid = forkpty(&master, NULL, &terminal, &size);
    if (pid == 0) {
        if (directory[0] != '\0' && chdir(directory) != 0) _exit(126);
        execve(shell, arguments, child_environment);
        _exit(errno == ENOENT ? 127 : 126);
    }
    int saved_errno = errno;
    free(child_environment);
    free(directory);
    free(shell);
    errno = saved_errno;
    if (pid < 0 || master < 0) {
        if (master >= 0) close(master);
        throw_io(env, "forkpty");
        return 0;
    }
    return make_handle(master, pid);
}

JNIEXPORT jint JNICALL
Java_com_closecode_ui_ide_NativePty_read(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray buffer_value) {
    (void)thiz;
    int fd = handle_fd(handle);
    jsize capacity = (*env)->GetArrayLength(env, buffer_value);
    if (capacity <= 0) return 0;
    jbyte *buffer = (*env)->GetByteArrayElements(env, buffer_value, NULL);
    if (buffer == NULL) return -1;

    fd_set readers;
    FD_ZERO(&readers);
    FD_SET(fd, &readers);
    struct timeval timeout = {.tv_sec = 0, .tv_usec = 100000};
    int ready;
    do {
        ready = select(fd + 1, &readers, NULL, NULL, &timeout);
    } while (ready < 0 && errno == EINTR);
    ssize_t count = 0;
    if (ready > 0) {
        do {
            count = read(fd, buffer, (size_t)capacity);
        } while (count < 0 && errno == EINTR);
        if (count < 0 && (errno == EIO || errno == EAGAIN || errno == EWOULDBLOCK)) count = 0;
        else if (count < 0) throw_io(env, "PTY read");
    } else if (ready < 0) {
        throw_io(env, "PTY select");
        count = -1;
    }
    (*env)->ReleaseByteArrayElements(env, buffer_value, buffer, count > 0 ? 0 : JNI_ABORT);
    return (jint)count;
}

JNIEXPORT jint JNICALL
Java_com_closecode_ui_ide_NativePty_write(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray bytes_value) {
    (void)thiz;
    jsize length = (*env)->GetArrayLength(env, bytes_value);
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytes_value, NULL);
    if (bytes == NULL) return -1;
    size_t written = 0;
    while (written < (size_t)length) {
        ssize_t count = write(handle_fd(handle), bytes + written, (size_t)length - written);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) {
            throw_io(env, "PTY write");
            break;
        }
        written += (size_t)count;
    }
    (*env)->ReleaseByteArrayElements(env, bytes_value, bytes, JNI_ABORT);
    return (jint)written;
}

JNIEXPORT jboolean JNICALL
Java_com_closecode_ui_ide_NativePty_resize(
        JNIEnv *env, jobject thiz, jlong handle, jint columns, jint rows) {
    (void)thiz;
    struct winsize size = {
        .ws_row = (unsigned short)(rows > 0 ? rows : 24),
        .ws_col = (unsigned short)(columns > 0 ? columns : 80),
    };
    if (ioctl(handle_fd(handle), TIOCSWINSZ, &size) == 0) return JNI_TRUE;
    throw_io(env, "PTY resize");
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_closecode_ui_ide_NativePty_interrupt(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    const unsigned char interrupt = 3;
    if (write(handle_fd(handle), &interrupt, sizeof(interrupt)) == (ssize_t)sizeof(interrupt)) {
        return JNI_TRUE;
    }
    throw_io(env, "PTY interrupt");
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_closecode_ui_ide_NativePty_close(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    pid_t pid = handle_pid(handle);
    kill(-pid, SIGHUP);
    close(handle_fd(handle));

    int status = 0;
    for (int attempt = 0; attempt < 20; ++attempt) {
        pid_t result = waitpid(pid, &status, WNOHANG);
        if (result == pid || (result < 0 && errno == ECHILD)) return JNI_TRUE;
        if (result < 0 && errno != EINTR) break;
        usleep(10000);
    }
    kill(-pid, SIGKILL);
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    return JNI_TRUE;
}
