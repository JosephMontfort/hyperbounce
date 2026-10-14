#include <stdint.h>
#include <string.h>
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <atomic>

#define LOG_TAG "HyperBounce_Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// LibXposed Native Hooking API definitions (API 102)
typedef int (*HookFunType)(void *func, void *replace, void **backup);
typedef int (*UnhookFunType)(void *func);

typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

typedef struct {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
} NativeAPIEntries;

static HookFunType g_hook_func = nullptr;
static UnhookFunType g_unhook_func = nullptr;

// Unlock animation physics configuration
struct UnlockConfigNative {
    std::atomic<bool> enabled{true};
    std::atomic<int> anim_mode{1}; // 0 = iOS Radial, 1 = Y-Axis Cascade
    std::atomic<float> damping{0.75f};
    std::atomic<float> response{0.45f};
    std::atomic<float> velocity_boost{1.0f};
};

static UnlockConfigNative g_unlock_cfg;

// Original function pointers
typedef void (*Bundle_insert_f32_t)(void *bundle, const char *key, float value);
static Bundle_insert_f32_t orig_Bundle_insert_f32 = nullptr;

typedef void (*WindowManager_send_wallpaper_command_t)(
    void *token, const char *action, int action_len,
    int x, int y, int z, void *bundle, int sync);
static WindowManager_send_wallpaper_command_t orig_send_wallpaper_command = nullptr;

// Intercept Bundle_insert_f32 to inject customized spring parameters
static void hooked_Bundle_insert_f32(void *bundle, const char *key, float value) {
    if (g_unlock_cfg.enabled.load(std::memory_order_relaxed)) {
        if (key != nullptr) {
            if (strcmp(key, "damping") == 0) {
                float custom_damping = g_unlock_cfg.damping.load(std::memory_order_relaxed);
                LOGD("Bundle_insert_f32(damping) intercepted: %f -> %f", value, custom_damping);
                if (orig_Bundle_insert_f32) {
                    orig_Bundle_insert_f32(bundle, key, custom_damping);
                    return;
                }
            } else if (strcmp(key, "response") == 0) {
                float custom_response = g_unlock_cfg.response.load(std::memory_order_relaxed);
                LOGD("Bundle_insert_f32(response) intercepted: %f -> %f", value, custom_response);
                if (orig_Bundle_insert_f32) {
                    orig_Bundle_insert_f32(bundle, key, custom_response);
                    return;
                }
            }
        }
    }
    if (orig_Bundle_insert_f32) {
        orig_Bundle_insert_f32(bundle, key, value);
    }
}

// Intercept WindowManager_send_wallpaper_command
static void hooked_send_wallpaper_command(
    void *token, const char *action, int action_len,
    int x, int y, int z, void *bundle, int sync) {
    if (action != nullptr && strncmp(action, "miui.wallpaper.animation", 24) == 0) {
        LOGI("WindowManager_send_wallpaper_command(miui.wallpaper.animation) intercepted");
    }
    if (orig_send_wallpaper_command) {
        orig_send_wallpaper_command(token, action, action_len, x, y, z, bundle, sync);
    }
}

static void hook_bundle_library(void *handle) {
    if (!g_hook_func || !handle) return;
    void *sym = dlsym(handle, "Bundle_insert_f32");
    if (sym && !orig_Bundle_insert_f32) {
        int res = g_hook_func(sym, (void *)hooked_Bundle_insert_f32, (void **)&orig_Bundle_insert_f32);
        LOGI("Hooked Bundle_insert_f32 at %p, result=%d", sym, res);
    }
}

static void hook_ui_public_library(void *handle) {
    if (!g_hook_func || !handle) return;
    void *sym = dlsym(handle, "WindowManager_send_wallpaper_command");
    if (sym && !orig_send_wallpaper_command) {
        int res = g_hook_func(sym, (void *)hooked_send_wallpaper_command, (void **)&orig_send_wallpaper_command);
        LOGI("Hooked WindowManager_send_wallpaper_command at %p, result=%d", sym, res);
    }
}

static void on_module_loaded(const char *name, void *handle) {
    if (!name || !handle) return;

    if (strstr(name, "libhyper_os_schema_public.so") != nullptr ||
        strstr(name, "libhyper_os_bundle_public.so") != nullptr) {
        hook_bundle_library(handle);
    }

    if (strstr(name, "libhyper_os_ui_public.so") != nullptr) {
        hook_ui_public_library(handle);
    }
}

extern "C" {

[[gnu::visibility("default")]]
[[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    if (!entries) {
        LOGE("native_init received null entries");
        return nullptr;
    }
    g_hook_func = entries->hook_func;
    g_unhook_func = entries->unhook_func;
    LOGI("HyperBounce Native API 102 initialized successfully (framework version: %u)", entries->version);

    void *schema_lib = dlopen("libhyper_os_schema_public.so", RTLD_NOLOAD);
    if (schema_lib) {
        hook_bundle_library(schema_lib);
        dlclose(schema_lib);
    }

    void *ui_lib = dlopen("libhyper_os_ui_public.so", RTLD_NOLOAD);
    if (ui_lib) {
        hook_ui_public_library(ui_lib);
        dlclose(ui_lib);
    }

    return on_module_loaded;
}

[[gnu::visibility("default")]]
JNIEXPORT void JNICALL
Java_dev_hyperios_app_launcher_NativeHookBridge_updateUnlockConfig(
    JNIEnv *env, jclass clazz, jboolean enabled, jint mode, jfloat damping, jfloat response, jfloat velocity) {
    g_unlock_cfg.enabled.store(enabled, std::memory_order_relaxed);
    g_unlock_cfg.anim_mode.store(mode, std::memory_order_relaxed);
    g_unlock_cfg.damping.store(damping, std::memory_order_relaxed);
    g_unlock_cfg.response.store(response, std::memory_order_relaxed);
    g_unlock_cfg.velocity_boost.store(velocity, std::memory_order_relaxed);
    LOGD("Native unlock config updated: enabled=%d, mode=%d, damping=%f, response=%f, velocity=%f",
         enabled, mode, damping, response, velocity);
}

}
