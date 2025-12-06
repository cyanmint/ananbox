#include <jni.h>
#include <string>
#include <cstdint>
#include <cstdlib>
#include <unistd.h>
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

// Server mode includes
#include <iostream>
#include <csignal>
#include <cstring>
#include <getopt.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <limits.h>
#include <thread>
#include <linux/input.h>
#include "anbox/logger.h"
#include "anbox/application/sensors_state.h"
#include "anbox/application/gps_info_broker.h"
#include "server/streaming_server.h"
#include "server/streaming_protocol.h"
#include "server/streaming_layer_composer.h"
#include "server/adb_forwarder.h"

#define TAG "libAnbox"

//const char *const path = "/data/data/com.github.ananbox/files";

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
Java_com_github_ananbox_Anbox_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_startRuntime(
        JNIEnv *env,
        jobject thiz) {
    rt->start();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_stopRuntime(JNIEnv *env, jobject thiz) {
    if (rt != nullptr) {
        rt->stop();
        rt = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_ananbox_Anbox_initRuntime(
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
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_startContainer(JNIEnv *env, jobject thiz, jstring proot_) {
    // Command buffer needs space for: 'sh ' + path (255 max) + '/rootfs/run.sh ' + path (255 max) + ' ' + proot (PATH_MAX)
    // This requires two path (255 bytes max) strings and one PATH_MAX-sized string plus the script command overhead
    static const size_t CMD_PATH_COUNT = 3;  // path, path, proot in the command
    static const size_t CMD_OVERHEAD = 32;   // "sh " + "/rootfs/run.sh " + spaces
    char cmd[PATH_MAX * CMD_PATH_COUNT + CMD_OVERHEAD];
    if (fork() != 0) {
        return;
    }
    sigset_t signals_to_unblock;
    sigfillset(&signals_to_unblock);
    sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);
    
    const char *proot = env->GetStringUTFChars(proot_, 0);
    
    // Extract the native library directory from the proot path
    // proot path is like "/data/app/.../lib/arm64/libproot.so"
    char native_lib_dir[PATH_MAX];
    size_t proot_len = strlen(proot);
    
    // Validate the path length to prevent buffer overflow
    if (proot_len >= PATH_MAX) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "proot path too long");
        env->ReleaseStringUTFChars(proot_, proot);
        _exit(1);
    }
    
    // Use snprintf for safe string copying
    snprintf(native_lib_dir, sizeof(native_lib_dir), "%s", proot);
    char *last_slash = strrchr(native_lib_dir, '/');
    if (last_slash != nullptr) {
        *last_slash = '\0';  // Remove the filename, keep directory
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Invalid proot path: no directory separator found");
        env->ReleaseStringUTFChars(proot_, proot);
        _exit(1);
    }
    
    // Set PROOT_TMP_DIR to app's tmp directory (writable but noexec)
    // This is used for proot's temporary files that don't need exec permission
    char tmp_dir[PATH_MAX];
    snprintf(tmp_dir, sizeof(tmp_dir), "%s/tmp", path);
    setenv("PROOT_TMP_DIR", tmp_dir, 1);
    
    // Set PROOT_LOADER to the pre-built loader in the native library directory
    // The native lib dir has exec permission, so the loader can run from there
    // This bypasses proot's need to extract the loader to PROOT_TMP_DIR
    char loader_path[PATH_MAX];
    snprintf(loader_path, sizeof(loader_path), "%s/libproot-loader.so", native_lib_dir);
    setenv("PROOT_LOADER", loader_path, 1);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Using PROOT_LOADER: %s", loader_path);
    
    // Use snprintf for safe command string construction
    snprintf(cmd, sizeof(cmd), "sh %s/rootfs/run.sh %s %s", path, path, proot);
    env->ReleaseStringUTFChars(proot_, proot);
    execl("/system/bin/sh", "sh", "-c", cmd, 0);
    __android_log_print(ANDROID_LOG_ERROR, TAG, "proot executed failed: %s", strerror(errno));
 }
extern "C"
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_resetWindow(JNIEnv *env, jobject thiz, jint height, jint width) {
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
Java_com_github_ananbox_Anbox_pushFingerUp(JNIEnv *env, jobject thiz, jint finger_id) {
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
Java_com_github_ananbox_Anbox_pushFingerDown(JNIEnv *env, jobject thiz, jint x, jint y, jint finger_id) {
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
Java_com_github_ananbox_Anbox_pushFingerMotion(JNIEnv *env, jobject thiz, jint x, jint y,
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
Java_com_github_ananbox_Anbox_destroyWindow(JNIEnv *env, jobject thiz) {
//    getRenderer()->destroyAllNativeWindow();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_createSurface(JNIEnv *env, jobject thiz, jobject surface) {
    native_window = ANativeWindow_fromSurface(env, surface);
    renderer_->createNativeWindow(native_window);
    auto composer_ = std::make_shared<anbox::graphics::LayerComposer>(renderer_, frame, native_window);
    registerLayerComposer(composer_);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_destroySurface(JNIEnv *env, jobject thiz) {
    unRegisterLayerComposer();
    renderer_->destroyNativeWindow(native_window);
    ANativeWindow_release(native_window);
    native_window = NULL;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_ananbox_Anbox_setPath(JNIEnv *env, jobject thiz, jstring path_) {
    const char *pathStr = env->GetStringUTFChars(path_, 0);
    memcpy(path, pathStr, strlen(pathStr) + 1);
    env->ReleaseStringUTFChars(path_, pathStr);
}

// ============================================================================
// Standalone Server Mode
// When executed as a standalone binary (not loaded as JNI library), this
// provides a complete server for running Android containers with network
// streaming support.
// ============================================================================

static volatile bool server_running = true;
static pid_t container_pid = -1;
static std::shared_ptr<anbox::server::StreamingLayerComposer> streaming_composer_;

// Logging callback for emugl in server mode
static void server_emugl_logger(const emugl::LogLevel& level, const char* format, ...) {
    char message[2048];
    va_list args;
    va_start(args, format);
    vsnprintf(message, sizeof(message) - 1, format, args);
    va_end(args);
    
    switch (level) {
        case emugl::LogLevel::WARNING:
            WARNING("%s", message);
            break;
        case emugl::LogLevel::ERROR:
        case emugl::LogLevel::FATAL:
            ERROR("%s", message);
            break;
        case emugl::LogLevel::DEBUG:
            DEBUG("%s", message);
            break;
        default:
            break;
    }
}

static void server_signal_handler(int signum) {
    INFO("Received signal %d, shutting down...", signum);
    server_running = false;
    
    if (container_pid > 0) {
        // Send signal to child process
        ::kill(container_pid, signum);
    }
}

static void print_server_usage(const char* program) {
    std::cout << "Usage: " << program << " [options]\n"
              << "\n"
              << "Ananbox standalone server - runs Android containers with network streaming\n"
              << "\n"
              << "Options:\n"
              << "  -a, --address <ip>      Listen address (default: 0.0.0.0)\n"
              << "  -p, --port <port>       Listen port (default: " << anbox::server::DEFAULT_PORT << ")\n"
              << "  -w, --width <pixels>    Display width (default: 1280)\n"
              << "  -h, --height <pixels>   Display height (default: 720)\n"
              << "  -d, --dpi <dpi>         Display DPI (default: 160)\n"
              << "  -b, --base <path>       Base path (parent of rootfs)\n"
              << "  -P, --proot <path>      Path to proot/libproot.so\n"
              << "  -t, --tmpdir <path>     Temporary directory for proot\n"
              << "  -A, --adb-address <ip>  ADB listen address\n"
              << "  -D, --adb-port <port>   ADB listen port (default: 5555)\n"
              << "  -v, --verbose           Enable verbose logging\n"
              << "  -?, --help              Show this help message\n"
              << std::endl;
}

static std::string normalize_path(const std::string& p) {
    std::string result = p;
    while (result.length() > 1 && result.back() == '/') {
        result.pop_back();
    }
    return result;
}

static bool ensure_directory(const std::string& p) {
    struct stat st;
    if (stat(p.c_str(), &st) == 0) {
        return S_ISDIR(st.st_mode);
    }
    return mkdir(p.c_str(), 0755) == 0;
}

// Main entry point for standalone server mode
int main(int argc, char* argv[]) {
    char cwd[PATH_MAX];
    if (getcwd(cwd, sizeof(cwd)) == nullptr) {
        std::cerr << "Failed to get current directory" << std::endl;
        return 1;
    }
    
    std::string listen_address = "0.0.0.0";
    uint16_t listen_port = anbox::server::DEFAULT_PORT;
    int display_width = 1280;
    int display_height = 720;
    int display_dpi = 160;
    std::string base_path = cwd;
    std::string proot_path = "./libproot.so";
    std::string startup_script;
    std::string tmp_dir;
    std::string adb_address;
    uint16_t adb_port = 5555;
    std::string adb_socket_path = "/dev/socket/adbd";

    static struct option long_options[] = {
        {"address", required_argument, 0, 'a'},
        {"port",    required_argument, 0, 'p'},
        {"width",   required_argument, 0, 'w'},
        {"height",  required_argument, 0, 'h'},
        {"dpi",     required_argument, 0, 'd'},
        {"base",    required_argument, 0, 'b'},
        {"proot",   required_argument, 0, 'P'},
        {"script",  required_argument, 0, 's'},
        {"tmpdir",  required_argument, 0, 't'},
        {"adb-address", required_argument, 0, 'A'},
        {"adb-port",    required_argument, 0, 'D'},
        {"adb-socket",  required_argument, 0, 'S'},
        {"verbose", no_argument,       0, 'v'},
        {"help",    no_argument,       0, '?'},
        {0, 0, 0, 0}
    };

    int opt;
    int option_index = 0;
    while ((opt = getopt_long(argc, argv, "a:p:w:h:d:b:P:s:t:A:D:S:v?", long_options, &option_index)) != -1) {
        switch (opt) {
            case 'a': listen_address = optarg; break;
            case 'p': listen_port = static_cast<uint16_t>(std::stoi(optarg)); break;
            case 'w': display_width = std::stoi(optarg); break;
            case 'h': display_height = std::stoi(optarg); break;
            case 'd': display_dpi = std::stoi(optarg); break;
            case 'b': base_path = normalize_path(optarg); break;
            case 'P': proot_path = optarg; break;
            case 's': startup_script = optarg; break;
            case 't': tmp_dir = optarg; break;
            case 'A': adb_address = optarg; break;
            case 'D': adb_port = static_cast<uint16_t>(std::stoi(optarg)); break;
            case 'S': adb_socket_path = optarg; break;
            case 'v': break;
            case '?':
                print_server_usage(argv[0]);
                return 0;
            default:
                print_server_usage(argv[0]);
                return 1;
        }
    }

    signal(SIGINT, server_signal_handler);
    signal(SIGTERM, server_signal_handler);
    signal(SIGCHLD, SIG_IGN);

    base_path = normalize_path(base_path);
    
    struct stat st;
    if (stat(base_path.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
        std::cerr << "Base directory does not exist: " << base_path << std::endl;
        return 1;
    }

    std::string rootfs_path = base_path + "/rootfs";
    if (stat(rootfs_path.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
        std::cerr << "Rootfs directory does not exist: " << rootfs_path << std::endl;
        return 1;
    }

    if (startup_script.empty()) startup_script = rootfs_path + "/run.sh";
    // Use PROOT_TMP_DIR from environment if tmp_dir not explicitly set
    // This allows the parent process (e.g., Android app) to specify an executable directory
    if (tmp_dir.empty()) {
        const char* env_tmp_dir = getenv("PROOT_TMP_DIR");
        if (env_tmp_dir != nullptr && env_tmp_dir[0] != '\0') {
            tmp_dir = env_tmp_dir;
        } else {
            tmp_dir = base_path + "/tmp";
        }
    }
    if (adb_address.empty()) adb_address = listen_address;
    
    if (!ensure_directory(tmp_dir)) {
        std::cerr << "Failed to create tmp directory: " << tmp_dir << std::endl;
        return 1;
    }
    ensure_directory(rootfs_path + "/tmp");

    std::cout << "Ananbox Server v0.2.0 starting..." << std::endl;
    std::cout << "Listen: " << listen_address << ":" << listen_port << std::endl;

    set_emugl_logger(server_emugl_logger);
    set_emugl_cxt_logger(server_emugl_logger);

    rt = anbox::Runtime::create();
    renderer_ = std::make_shared<::Renderer>();
    frame = std::make_shared<anbox::graphics::Rect>();
    frame->resize(display_width, display_height);
    
    auto display_info = anbox::graphics::emugl::DisplayInfo::get();
    display_info->set_resolution(display_width, display_height);
    display_info->set_dpi(display_dpi);

    bool egl_initialized = renderer_->initialize(EGL_DEFAULT_DISPLAY);
    if (!egl_initialized) {
        std::cout << "EGL initialization failed - running in software mode" << std::endl;
        enableSoftwareRenderer(true);
    } else {
        registerRenderer(renderer_);
    }

    std::shared_ptr<anbox::server::StreamingServer> streaming_server;
    std::shared_ptr<anbox::server::AdbForwarder> adb_forwarder;
    
    try {
        rt->start();
        
        streaming_server = std::make_shared<anbox::server::StreamingServer>(rt, listen_address, listen_port);
        streaming_server->set_display_config(display_width, display_height, display_dpi);
        
        streaming_composer_ = std::make_shared<anbox::server::StreamingLayerComposer>(
            egl_initialized ? renderer_ : nullptr, frame);
        streaming_composer_->set_frame_callback(
            [streaming_server](const void* data, uint32_t w, uint32_t h, uint32_t stride) {
                streaming_server->send_frame(data, w, h, anbox::server::PIXEL_FORMAT_RGBA8888, stride);
            });
        registerLayerComposer(streaming_composer_);
        
        streaming_server->start();
        std::cout << "Streaming server ready on " << listen_address << ":" << listen_port << std::endl;
        
        if (adb_port > 0) {
            adb_forwarder = std::make_shared<anbox::server::AdbForwarder>(
                rt, adb_address, adb_port, adb_socket_path, rootfs_path);
            adb_forwarder->start();
            std::cout << "ADB forwarding ready on " << adb_address << ":" << adb_port << std::endl;
        }
    } catch (const std::exception& e) {
        std::cerr << "Failed to start server: " << e.what() << std::endl;
        if (rt) rt->stop();
        return 1;
    }

    auto input_manager = std::make_shared<anbox::input::Manager>(
        rt, anbox::utils::string_format("%s/dev/input", rootfs_path.c_str()));
    
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
    touch_->set_abs_max(ABS_MT_POSITION_X, display_width);
    touch_->set_abs_bit(ABS_MT_POSITION_Y);
    touch_->set_abs_max(ABS_MT_POSITION_Y, display_height);
    touch_->set_abs_bit(ABS_MT_TRACKING_ID);
    touch_->set_abs_max(ABS_MT_TRACKING_ID, 10);
    touch_->set_prop_bit(INPUT_PROP_DIRECT);

    streaming_server->set_input_callback(
        [](const anbox::server::TouchEvent& event) {
            if (!touch_) return;
            std::vector<anbox::input::Event> touch_events;
            static int server_last_slot = -1;
            int slot = event.finger_id % 10;
            if (server_last_slot != slot) {
                touch_events.push_back({EV_ABS, ABS_MT_SLOT, slot});
                server_last_slot = slot;
            }
            switch (event.action) {
                case anbox::server::TOUCH_ACTION_DOWN:
                    touch_events.push_back({EV_ABS, ABS_MT_TRACKING_ID, static_cast<int32_t>(event.finger_id % 10 + 1)});
                    touch_events.push_back({EV_ABS, ABS_MT_POSITION_X, static_cast<int32_t>(event.x)});
                    touch_events.push_back({EV_ABS, ABS_MT_POSITION_Y, static_cast<int32_t>(event.y)});
                    break;
                case anbox::server::TOUCH_ACTION_MOVE:
                    touch_events.push_back({EV_ABS, ABS_MT_POSITION_X, static_cast<int32_t>(event.x)});
                    touch_events.push_back({EV_ABS, ABS_MT_POSITION_Y, static_cast<int32_t>(event.y)});
                    break;
                case anbox::server::TOUCH_ACTION_UP:
                    touch_events.push_back({EV_ABS, ABS_MT_TRACKING_ID, -1});
                    break;
            }
            touch_events.push_back({EV_SYN, SYN_REPORT, 0});
            touch_->send_events(touch_events);
        });

    auto sensors_state = std::make_shared<anbox::application::SensorsState>();
    auto gps_info_broker = std::make_shared<anbox::application::GpsInfoBroker>();
    std::string socket_file = anbox::utils::string_format("%s/qemu_pipe", base_path.c_str());
    unlink(socket_file.c_str());
    qemu_pipe_connector_ = std::make_shared<anbox::network::PublishedSocketConnector>(
        socket_file, rt,
        std::make_shared<anbox::qemu::PipeConnectionCreator>(renderer_, rt, sensors_state, gps_info_broker));

    if (access(startup_script.c_str(), F_OK) == 0) {
        std::cout << "Starting container..." << std::endl;
        container_pid = fork();
        if (container_pid < 0) {
            std::cerr << "fork() failed" << std::endl;
            rt->stop();
            return 1;
        } else if (container_pid == 0) {
            sigset_t signals_to_unblock;
            sigfillset(&signals_to_unblock);
            sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);
            if (chdir(base_path.c_str()) != 0) {
                exit(1);
            }
            setenv("PROOT_TMP_DIR", tmp_dir.c_str(), 1);
            
            // Forward PROOT_LOADER if set by parent process
            // This allows using a pre-built loader binary from a directory with exec permission
            // (like the native library directory) instead of extracting to PROOT_TMP_DIR
            const char* proot_loader = getenv("PROOT_LOADER");
            if (proot_loader != nullptr && proot_loader[0] != '\0') {
                setenv("PROOT_LOADER", proot_loader, 1);
                std::cout << "Using PROOT_LOADER: " << proot_loader << std::endl;
            }
            
            const char* args[] = {"sh", startup_script.c_str(), base_path.c_str(), proot_path.c_str(), nullptr};
            execvp("sh", const_cast<char* const*>(args));
            exit(1);
        } else {
            std::cout << "Container started with PID " << container_pid << std::endl;
        }
    }

    std::cout << "Server running. Press Ctrl+C to stop." << std::endl;
    
    bool send_test_frames = !egl_initialized;
    std::vector<uint8_t> test_frame;
    int test_frame_counter = 0;
    if (send_test_frames) {
        test_frame.resize(display_width * display_height * 4);
    }

    while (server_running) {
        if (container_pid > 0) {
            int status;
            pid_t result = waitpid(container_pid, &status, WNOHANG);
            if (result == container_pid) {
                std::cout << "Container exited" << std::endl;
                container_pid = -1;
            }
        }
        
        if (send_test_frames && streaming_server->has_clients()) {
            test_frame_counter++;
            for (int y = 0; y < display_height; y++) {
                for (int x = 0; x < display_width; x++) {
                    size_t offset = (y * display_width + x) * 4;
                    test_frame[offset + 0] = static_cast<uint8_t>((x + test_frame_counter) % 256);
                    test_frame[offset + 1] = static_cast<uint8_t>((y + test_frame_counter) % 256);
                    test_frame[offset + 2] = static_cast<uint8_t>(((x + y) / 2 + test_frame_counter * 2) % 256);
                    test_frame[offset + 3] = 255;
                }
            }
            streaming_server->send_frame(test_frame.data(), display_width, display_height,
                                         anbox::server::PIXEL_FORMAT_RGBA8888, display_width * 4);
            std::this_thread::sleep_for(std::chrono::milliseconds(33));
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    std::cout << "Shutting down..." << std::endl;
    unRegisterLayerComposer();
    streaming_composer_.reset();
    if (adb_forwarder) adb_forwarder->stop();
    if (streaming_server) streaming_server->stop();
    if (rt) rt->stop();
    if (container_pid > 0) {
        ::kill(container_pid, SIGTERM);
        int status;
        waitpid(container_pid, &status, 0);
    }
    std::cout << "Server stopped." << std::endl;
    return 0;
}