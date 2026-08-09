#include <jni.h>
#include <string>
#include <cstdint>
#include <unistd.h>
#include <sstream>
#include <vector>
#include <iterator>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <android/input.h>
#include "anbox/graphics/emugl/Renderer.h"
#include "anbox/graphics/emugl/RenderApi.h"
#include "anbox/graphics/emugl/RenderControl.h"
#include "anbox/network/published_socket_connector.h"
#include "anbox/qemu/pipe_connection_creator.h"
#include "anbox/runtime.h"
#include "anbox/common/dispatcher.h"
#include "anbox/input/manager.h"
#include "anbox/input/device.h"
#include "anbox/graphics/layer_composer.h"
#include "anbox/graphics/emugl/DisplayManager.h"
#include "external/android-emugl/shared/emugl/common/logging.h"
#include <android/log.h>
#include <android/native_window_jni.h>
#include "Parcel.h"
#define TAG "libAnbox"

#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define __android_second(dummy, second, ...)     second
#define __android_rest(first, ...)               , ## __VA_ARGS__
#define android_printAssert(cond, tag, fmt...) \
    __android_log_assert(cond, tag, \
        __android_second(0, ## fmt, NULL) __android_rest(fmt))

#define CONDITION(cond)     (__builtin_expect((cond)!=0, 0))
#ifndef LOG_ALWAYS_FATAL_IF
#define LOG_ALWAYS_FATAL_IF(cond, ...) \
    ( (CONDITION(cond)) \
    ? ((void)android_printAssert(#cond, TAG, ## __VA_ARGS__)) \
    : (void)0 )
#endif

static const int MAX_FINGERS = 10;
static const int MAX_TRACKING_ID = 10;
static int touch_slots[MAX_FINGERS];
static int last_slot = -1;
static std::shared_ptr<anbox::Runtime> rt;
static std::shared_ptr<anbox::graphics::Rect> frame = std::make_shared<anbox::graphics::Rect>();
static std::shared_ptr<::Renderer> renderer_;
static std::shared_ptr<anbox::network::PublishedSocketConnector> qemu_pipe_connector_;
static std::shared_ptr<anbox::input::Device> touch_;
static ANativeWindow* native_window;
static char path[255];


void logger_write(const emugl::LogLevel &level, const char *format, ...) {
    (void)level;

    char message[2048];
    va_list args;

    va_start(args, format);
    vsnprintf(message, sizeof(message) - 1, format, args);
    va_end(args);

    switch (level) {
        case emugl::LogLevel::WARNING:
            __android_log_print(ANDROID_LOG_WARN, TAG, "%s", message);
            break;
        case emugl::LogLevel::ERROR:
            __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", message);
            break;
        case emugl::LogLevel::FATAL:
            __android_log_print(ANDROID_LOG_FATAL, TAG, "%s", message);
            break;
        case emugl::LogLevel::DEBUG:
            __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", message);
            break;
        case emugl::LogLevel::TRACE:
//            __android_log_print(ANDROID_LOG_VERBOSE, TAG, "%s", message);
            break;
        default:
            break;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cyanmint_anbox_Anbox_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_startRuntime(
        JNIEnv *env,
        jobject thiz) {
    rt->start();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_stopRuntime(JNIEnv *env, jobject thiz) {
    if (rt != nullptr) {
        rt->stop();
        rt = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cyanmint_anbox_Anbox_initRuntime(
        JNIEnv* env,
        jobject thiz,
        jint width,
        jint height,
        jint dpi) {
//    auto gl_libs = anbox::graphics::emugl::default_gl_libraries();
//    if (!anbox::graphics::emugl::initialize(gl_libs, nullptr, nullptr)) {
//        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to initialize OpenGL renderer");
//        return false;
//    }
    if (rt != NULL)
        return false;
    set_emugl_logger(logger_write);
    set_emugl_cxt_logger(logger_write);

    std::uint32_t flags = 0;

    rt = anbox::Runtime::create();

    renderer_ = std::make_shared<::Renderer>();
//    native_window = ANativeWindow_fromSurface(env, surface);
//    int32_t width_ = ANativeWindow_getWidth(native_window);
//    int32_t height_ = ANativeWindow_getHeight(native_window);
    frame->resize(width, height);
    auto display_info_ = anbox::graphics::emugl::DisplayInfo::get();
    display_info_->set_resolution(width, height);
    display_info_->set_dpi(dpi);

    renderer_->initialize(EGL_DEFAULT_DISPLAY);
    registerRenderer(renderer_);

    auto sensors_state = std::make_shared<anbox::application::SensorsState>();
    auto gps_info_broker = std::make_shared<anbox::application::GpsInfoBroker>();

    auto input_manager = std::make_shared<anbox::input::Manager>(rt, anbox::utils::string_format("%s/rootfs/dev/input", path));
//    auto pointer_ = input_manager->create_device();
//    pointer_->set_name("anbox-pointer");
//    pointer_->set_driver_version(1);
//    pointer_->set_input_id({BUS_VIRTUAL, 2, 2, 2});
//    pointer_->set_physical_location("none");
//    pointer_->set_key_bit(BTN_MOUSE);
//    // NOTE: We don't use REL_X/REL_Y in reality but have to specify them here
//    // to allow InputFlinger to detect we're a cursor device.
//    pointer_->set_rel_bit(REL_X);
//    pointer_->set_rel_bit(REL_Y);
//    pointer_->set_rel_bit(REL_HWHEEL);
//    pointer_->set_rel_bit(REL_WHEEL);
//    pointer_->set_prop_bit(INPUT_PROP_POINTER);

//    auto keyboard_ = input_manager->create_device();
//    keyboard_->set_name("anbox-keyboard");
//    keyboard_->set_driver_version(1);
//    keyboard_->set_input_id({BUS_VIRTUAL, 3, 3, 3});
//    keyboard_->set_physical_location("none");
//    keyboard_->set_key_bit(BTN_MISC);
//    keyboard_->set_key_bit(KEY_OK);

    touch_ = input_manager->create_device();
    touch_->set_name("anbox-touch");
    touch_->set_driver_version(1);
    touch_->set_input_id({BUS_VIRTUAL, 4, 4, 4});
    touch_->set_physical_location("none");
    touch_->set_abs_bit(ABS_MT_SLOT);
    touch_->set_abs_max(ABS_MT_SLOT, 10);
    touch_->set_abs_bit(ABS_MT_TOUCH_MAJOR);
    touch_->set_abs_max(ABS_MT_TOUCH_MAJOR, 127);
    touch_->set_abs_bit(ABS_MT_TOUCH_MINOR);
    touch_->set_abs_max(ABS_MT_TOUCH_MINOR, 127);
    touch_->set_abs_bit(ABS_MT_POSITION_X);
    touch_->set_abs_max(ABS_MT_POSITION_X, width);
    touch_->set_abs_bit(ABS_MT_POSITION_Y);
    touch_->set_abs_max(ABS_MT_POSITION_Y, height);
    touch_->set_abs_bit(ABS_MT_TRACKING_ID);
    touch_->set_abs_max(ABS_MT_TRACKING_ID, MAX_TRACKING_ID);
    touch_->set_prop_bit(INPUT_PROP_DIRECT);

    // delete qemu_pipe if exists
    std::string socket_file = anbox::utils::string_format("%s/qemu_pipe", path);
    unlink(socket_file.c_str());
    qemu_pipe_connector_ =
            std::make_shared<anbox::network::PublishedSocketConnector>(
                    anbox::utils::string_format("%s/qemu_pipe", path), rt,
                    std::make_shared<anbox::qemu::PipeConnectionCreator>(renderer_, rt, sensors_state, gps_info_broker));

    return true;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_cyanmint_anbox_Anbox_startContainer(JNIEnv *env, jobject thiz, jstring cmd_) {
    const char *cmd_chars = env->GetStringUTFChars(cmd_, 0);
    std::string cmd(cmd_chars);
    env->ReleaseStringUTFChars(cmd_, cmd_chars);

    std::istringstream cmd_stream(cmd);
    std::vector<std::string> args_storage{
            std::istream_iterator<std::string>{cmd_stream},
            std::istream_iterator<std::string>{}};
    if (args_storage.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "proot command is empty");
        return -1;
    }

    // Pipe used to forward the child's stdout/stderr back to the Java side
    // so the executed command's output can be shown in the app's log box.
    int out_pipe[2];
    if (pipe(out_pipe) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "pipe() failed: %s", strerror(errno));
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "fork() failed: %s", strerror(errno));
        close(out_pipe[0]);
        close(out_pipe[1]);
        return -1;
    }
    if (pid != 0) {
        // Parent: keep the read end open for Java to consume, we don't need
        // the write end.
        close(out_pipe[1]);
        return out_pipe[0];
    }

    // Child.
    close(out_pipe[0]);
    dup2(out_pipe[1], STDOUT_FILENO);
    dup2(out_pipe[1], STDERR_FILENO);
    close(out_pipe[1]);

    sigset_t signals_to_unblock;
    sigfillset(&signals_to_unblock);
    sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

    // Run the command with the profile's rootfs as the working directory, so
    // relative paths in a custom launch command (e.g. "sh run.sh . ./proot")
    // resolve against the rootfs instead of whatever directory this process
    // happened to inherit.
    std::string rootfs_dir = anbox::utils::string_format("%s/rootfs", path);
    if (chdir(rootfs_dir.c_str()) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "chdir to %s failed: %s",
                             rootfs_dir.c_str(), strerror(errno));
    }

    // Some proot builds accelerate ptrace-based syscall tracing using
    // seccomp, but that fast path can fail to correctly emulate certain
    // syscalls (e.g. capset(), which the guest's Zygote calls when forking
    // system_server), causing the guest to abort in a boot loop with
    // "capset failed" and never finish booting far enough to render
    // anything. Force the slower but more compatible pure-ptrace mode
    // unless the launch command already overrides it.
    setenv("PROOT_NO_SECCOMP", "1", 0);

    std::vector<char *> args;
    args.reserve(args_storage.size() + 1);
    for (auto &arg : args_storage) {
        args.push_back(const_cast<char *>(arg.c_str()));
    }
    args.push_back(nullptr);
    execvp(args_storage[0].c_str(), args.data());
    __android_log_print(ANDROID_LOG_ERROR, TAG, "proot command excuted failed: %s", strerror(errno));
    _exit(1);
 }
extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_resetWindow(JNIEnv *env, jobject thiz, jint height, jint width) {
    // TODO: check why change frame size cause nothing to be displayed
//    frame->resize(width, height);
    anbox::graphics::emugl::DisplayInfo::get()->set_resolution(height, width);
}

int find_touch_slot(int id){
    for (int i = 0; i < MAX_FINGERS; i++) {
        if (touch_slots[i] == id)
            return i;
    }
    return -1;
}

void push_slot(std::vector<anbox::input::Event> &touch_events, int slot){
    if (last_slot != slot) {
        touch_events.push_back({EV_ABS, ABS_MT_SLOT, slot});
        last_slot = slot;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_pushFingerUp(JNIEnv *env, jobject thiz, jint finger_id) {
    std::vector<anbox::input::Event> touch_events;
    int slot = find_touch_slot(finger_id);
    if (slot == -1)
        return;
    push_slot(touch_events, slot);
    touch_events.push_back({EV_ABS, ABS_MT_TRACKING_ID, -1});
    touch_events.push_back({EV_SYN, SYN_REPORT, 0});
    touch_slots[slot] = -1;
    touch_->send_events(touch_events);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_pushFingerDown(JNIEnv *env, jobject thiz, jint x, jint y, jint finger_id) {
    std::vector<anbox::input::Event> touch_events;
    int slot = find_touch_slot(-1);
    if (slot == -1) {
        DEBUG("no free slot!");
        return;
    }
    touch_slots[slot] = finger_id;
    push_slot(touch_events, slot);
    touch_events.push_back({EV_ABS, ABS_MT_TRACKING_ID, static_cast<std::int32_t>(finger_id % MAX_TRACKING_ID + 1)});
    touch_events.push_back({EV_ABS, ABS_MT_POSITION_X, x});
    touch_events.push_back({EV_ABS, ABS_MT_POSITION_Y, y});
    touch_events.push_back({EV_SYN, SYN_REPORT, 0});
    touch_->send_events(touch_events);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_pushFingerMotion(JNIEnv *env, jobject thiz, jint x, jint y,
                                               jint finger_id) {
    std::vector<anbox::input::Event> touch_events;
    int slot = find_touch_slot(finger_id);
    if (slot == -1)
        return;
    push_slot(touch_events, slot);
    touch_events.push_back({EV_ABS, ABS_MT_POSITION_X, x});
    touch_events.push_back({EV_ABS, ABS_MT_POSITION_Y, y});
    touch_events.push_back({EV_SYN, SYN_REPORT, 0});
    touch_->send_events(touch_events);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_destroyWindow(JNIEnv *env, jobject thiz) {
//    getRenderer()->destroyAllNativeWindow();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_createSurface(JNIEnv *env, jobject thiz, jobject surface) {
    native_window = ANativeWindow_fromSurface(env, surface);
    renderer_->createNativeWindow(native_window);
    auto composer_ = std::make_shared<anbox::graphics::LayerComposer>(renderer_, frame, native_window);
    registerLayerComposer(composer_);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_destroySurface(JNIEnv *env, jobject thiz) {
    unRegisterLayerComposer();
    renderer_->destroyNativeWindow(native_window);
    ANativeWindow_release(native_window);
    native_window = NULL;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_setPath(JNIEnv *env, jobject thiz, jstring path_) {
    const char *pathStr = env->GetStringUTFChars(path_, 0);
    memcpy(path, pathStr, strlen(pathStr) + 1);
    env->ReleaseStringUTFChars(path_, pathStr);
}

static inline jclass FindClassOrDie(JNIEnv* env, const char* class_name) {
    jclass clazz = env->FindClass(class_name);
    LOG_ALWAYS_FATAL_IF(clazz == NULL, "Unable to find class %s", class_name);
    return clazz;
}


static inline jfieldID GetFieldIDOrDie(JNIEnv* env, jclass clazz, const char* field_name,
                                       const char* field_signature) {
    jfieldID res = env->GetFieldID(clazz, field_name, field_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL,"Unable to find field %s with signature %s", field_name,field_signature);
    return res;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_Anbox_dumpParcel(JNIEnv *env, jobject thiz, jobject jparcel, jstring jpath) {
    const char* const kParcelPathName = "android/os/Parcel";
    jclass parcel_clazz = FindClassOrDie(env, kParcelPathName);
    jfieldID  parcel_mNativePtr = GetFieldIDOrDie(env, parcel_clazz, "mNativePtr", "J");

    Parcel* parcel = (Parcel*) env->GetLongField(jparcel, parcel_mNativePtr);
    if (parcel == nullptr) {
        ALOGI("error,  Parcel/mNativePtr is null");
        return;
    }

    ALOGI("mdata: %p", parcel->mData);
    ALOGI("mdataPos: %d", parcel->mDataPos);
    ALOGI("mdataSize: %d", parcel->mDataSize);
    ALOGI("mdataCapacity: %d", parcel->mDataCapacity);
    ALOGI("mObject: %p", *parcel->mObjects);
    ALOGI("mObjectSize: %d", parcel->mObjectsSize);

    const char *path = env->GetStringUTFChars(jpath, 0);
    int fd = open(path, O_CREAT | O_WRONLY, 0700);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "failed to open file, err :%d", errno);
    }
    else {
        write(fd, &parcel->mDataSize, sizeof(int64_t));
        write(fd, &parcel->mObjectsSize, sizeof(int64_t));
        write(fd, parcel->mData, parcel->mDataSize);
        write(fd, parcel->mObjects, parcel->mObjectsSize * sizeof(int64_t));
        close(fd);
    }
    env->ReleaseStringUTFChars(jpath, path);
}

// ---------------------------------------------------------------------------
// PtyNative: allocates a real pseudo-terminal for the in-app terminal, so the
// launched shell gets a controlling tty (job control, line discipline, local
// echo) instead of a pair of anonymous pipes. Modeled after termux-app's
// terminal-emulator/src/main/jni/termux.c.
// ---------------------------------------------------------------------------
#include <dirent.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>

#define PTY_TAG "PtyNative"

static int pty_throw_runtime_exception(JNIEnv* env, char const* message) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(exClass, message);
    return -1;
}

static int pty_create_subprocess(
        JNIEnv* env,
        char const* cmd,
        char const* cwd,
        char* const argv[],
        char** envp,
        int* pProcessId,
        jint rows,
        jint columns) {
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) return pty_throw_runtime_exception(env, "Cannot open /dev/ptmx");

    char devname[64];
    if (grantpt(ptm) || unlockpt(ptm) || ptsname_r(ptm, devname, sizeof(devname))) {
        close(ptm);
        return pty_throw_runtime_exception(
            env, "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    // Enable UTF-8 mode and disable flow control so Ctrl+S doesn't lock up input.
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(ptm, TCSANOW, &tios);

    struct winsize sz = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) columns,
    };
    ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid < 0) {
        close(ptm);
        return pty_throw_runtime_exception(env, "Fork failed");
    } else if (pid > 0) {
        *pProcessId = (int) pid;
        return ptm;
    } else {
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

        close(ptm);
        setsid();

        int pts = open(devname, O_RDWR);
        if (pts < 0) _exit(-1);
        ioctl(pts, TIOCSCTTY, 0);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        DIR* self_dir = opendir("/proc/self/fd");
        if (self_dir != nullptr) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent* entry;
            while ((entry = readdir(self_dir)) != nullptr) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }

        if (envp) {
            clearenv();
            for (char** e = envp; *e; ++e) putenv(*e);
        }

        if (chdir(cwd) != 0) {
            perror("chdir()");
            fflush(stderr);
        }

        execvp(cmd, argv);
        perror("exec()");
        _exit(1);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_cyanmint_anbox_terminal_PtyNative_createSubprocess(
        JNIEnv* env,
        jclass /* clazz */,
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray envVars,
        jintArray processIdArray,
        jint rows,
        jint columns) {
    jsize size = args ? env->GetArrayLength(args) : 0;
    char** argv = nullptr;
    if (size > 0) {
        argv = (char**) malloc((size + 1) * sizeof(char*));
        for (int i = 0; i < size; ++i) {
            auto arg_java_string = (jstring) env->GetObjectArrayElement(args, i);
            char const* arg_utf8 = env->GetStringUTFChars(arg_java_string, nullptr);
            argv[i] = strdup(arg_utf8);
            env->ReleaseStringUTFChars(arg_java_string, arg_utf8);
        }
        argv[size] = nullptr;
    }

    size = envVars ? env->GetArrayLength(envVars) : 0;
    char** envp = nullptr;
    if (size > 0) {
        envp = (char**) malloc((size + 1) * sizeof(char*));
        for (int i = 0; i < size; ++i) {
            auto env_java_string = (jstring) env->GetObjectArrayElement(envVars, i);
            char const* env_utf8 = env->GetStringUTFChars(env_java_string, nullptr);
            envp[i] = strdup(env_utf8);
            env->ReleaseStringUTFChars(env_java_string, env_utf8);
        }
        envp[size] = nullptr;
    }

    int procId = 0;
    char const* cmd_cwd = env->GetStringUTFChars(cwd, nullptr);
    char const* cmd_utf8 = env->GetStringUTFChars(cmd, nullptr);
    int ptm = pty_create_subprocess(env, cmd_utf8, cmd_cwd, argv, envp, &procId, rows, columns);
    env->ReleaseStringUTFChars(cmd, cmd_utf8);
    env->ReleaseStringUTFChars(cwd, cmd_cwd);

    if (argv) {
        for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
        free(argv);
    }
    if (envp) {
        for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
        free(envp);
    }

    if (ptm >= 0) {
        jint* pProcId = env->GetIntArrayElements(processIdArray, nullptr);
        pProcId[0] = procId;
        env->ReleaseIntArrayElements(processIdArray, pProcId, 0);
    }

    return ptm;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_terminal_PtyNative_setWindowSize(
        JNIEnv* /* env */, jclass /* clazz */, jint fd, jint rows, jint columns) {
    struct winsize sz = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) columns };
    ioctl(fd, TIOCSWINSZ, &sz);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_cyanmint_anbox_terminal_PtyNative_waitFor(
        JNIEnv* /* env */, jclass /* clazz */, jint pid) {
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return -WTERMSIG(status);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cyanmint_anbox_terminal_PtyNative_closeFd(
        JNIEnv* /* env */, jclass /* clazz */, jint fd) {
    close(fd);
}