#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <pthread.h>
#include <android/log.h>
#include <atomic>

#define LOG_TAG "HyperBounce_Agent"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SOCKET_NAME "\0hyperbounce.launcher.control"
#define SOCKET_NAME_LEN 29 // strlen("hyperbounce.launcher.control") + 1

#define CMD_PING 0x00
#define CMD_UPDATE_CONFIG 0x01
#define CMD_TRIGGER_UNLOCK 0x02

struct UnlockConfig {
    std::atomic<bool> enabled{true};
    std::atomic<int> mode{1}; // 0 = Radial, 1 = Cascade
    std::atomic<double> damping{0.75};
    std::atomic<double> response{0.45};
    std::atomic<double> velocity{1.0};
};

static UnlockConfig g_cfg;
static uintptr_t g_libapp_base = 0;

// Offsets in libapp.so (verified via .gnu_debugdata symbols)
#define OFFSET_GET_DAMPING_RESPONSE 0x13d08a4
#define OFFSET_SHOW_USER_PRESENT_2 0x1a7b628
#define OFFSET_CALL_GET_DAMPING 0x1a7b770
#define OFFSET_IS_LOW_LEVEL 0xd11a7c
#define OFFSET_SHOULD_SHOW_USER_PRESENT 0xd12fc0
#define OFFSET_ON_USER_PRESENT 0xe55ab0
#define OFFSET_SLACK_TRAMPOLINE 0x1b47800

extern "C" void patch_dart_doubles(void *damping_obj, void *response_obj) {
    if (!g_cfg.enabled.load(std::memory_order_relaxed)) {
        return;
    }
    if (damping_obj != nullptr && response_obj != nullptr) {
        double custom_damp = g_cfg.damping.load(std::memory_order_relaxed);
        double custom_resp = g_cfg.response.load(std::memory_order_relaxed);

        double *p_damp = (double *)((char *)damping_obj + 7);
        double *p_resp = (double *)((char *)response_obj + 7);

        LOGD("patch_dart_doubles: orig(damping=%f, resp=%f) -> custom(damping=%f, resp=%f, mode=%d)",
             *p_damp, *p_resp, custom_damp, custom_resp, g_cfg.mode.load(std::memory_order_relaxed));

        *p_damp = custom_damp;
        *p_resp = custom_resp;
    }
}

static bool unprotect_memory(uintptr_t addr, size_t size) {
    uintptr_t page_start = addr & ~(4096 - 1);
    uintptr_t page_end = (addr + size + 4095) & ~(4096 - 1);
    return mprotect((void *)page_start, page_end - page_start, PROT_READ | PROT_WRITE | PROT_EXEC) == 0;
}

static bool find_libapp_base() {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) {
        LOGE("Failed to open /proc/self/maps");
        return false;
    }

    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "MiuiHome.apk") != nullptr && strstr(line, "00528000") != nullptr) {
            uintptr_t start, end;
            if (sscanf(line, "%lx-%lx", &start, &end) == 2) {
                g_libapp_base = start;
                LOGI("Found libapp.so base in MiuiHome.apk: 0x%lx", g_libapp_base);
                fclose(fp);
                return true;
            }
        }
    }
    fclose(fp);
    LOGE("Could not locate libapp.so (offset 00528000) in /proc/self/maps");
    return false;
}

static void install_hooks() {
    if (g_libapp_base == 0) return;

    // 1. Hook DeviceLevelUtils.isLowLevelOrLiteDeviceOrSpecialDevice (0xd11a7c)
    // Replace with: add x0, x22, #0x20; ret; (forces false in Dart AOT)
    uintptr_t is_low_level_addr = g_libapp_base + OFFSET_IS_LOW_LEVEL;
    if (unprotect_memory(is_low_level_addr, 8)) {
        uint32_t *code = (uint32_t *)is_low_level_addr;
        code[0] = 0x910082c0; // add x0, x22, #0x20
        code[1] = 0xd65f03c0; // ret
        __builtin___clear_cache((char *)is_low_level_addr, (char *)is_low_level_addr + 8);
        LOGI("Patched DeviceLevelUtils.isLowLevel at 0x%lx (forces false)", is_low_level_addr);
    } else {
        LOGE("Failed to unprotect isLowLevel memory at 0x%lx", is_low_level_addr);
    }

    // 2. Bypass suppression checks in UnlockAnimGetxController.shouldShowUserPresentAnimation (0xd12fc0)
    // NOP out the tbz branches that lead to suppression at 0xd13040 and 0xd13048
    uintptr_t should_show_addr = g_libapp_base + OFFSET_SHOULD_SHOW_USER_PRESENT;
    if (unprotect_memory(should_show_addr, 0x100)) {
        uint32_t *code = (uint32_t *)should_show_addr;
        code[0x80 / 4] = 0xd503201f; // nop (was b.eq 0xd1314c)
        code[0x88 / 4] = 0xd503201f; // nop (was tbz w0, #4, 0xd1314c)
        __builtin___clear_cache((char *)should_show_addr, (char *)should_show_addr + 0x100);
        LOGI("Patched UnlockAnimGetxController.shouldShowUserPresentAnimation at 0x%lx (bypassed suppression)", should_show_addr);
    }

    // 3. Hook getDampingResponse callsite at showUserPresentAnimation2 (0x1a7b770)
    // Write trampoline into slack space at 0x1b47800
    uintptr_t callsite = g_libapp_base + OFFSET_CALL_GET_DAMPING;
    uintptr_t ret_target = callsite + 4;
    uintptr_t tramp_addr = g_libapp_base + OFFSET_SLACK_TRAMPOLINE;

    if (unprotect_memory(tramp_addr, 0x100)) {
        uint32_t *t = (uint32_t *)tramp_addr;
        t[0] = 0xa9be7bfd; // stp x29, x30, [sp, #-32]!
        t[1] = 0xa90153f3; // stp x19, x20, [sp, #16]
        t[2] = 910003fd;   // mov x29, sp
        t[3] = 0x97e22429; // bl 0x13d08a4 (calls getDampingResponse)
        t[4] = 0xaa0003f3; // mov x19, x0
        t[5] = 0xaa0103f4; // mov x20, x1
        t[6] = 0x58000110; // ldr x16, [pc, #32] -> lit_patch
        t[7] = 0xd63f0200; // blr x16
        t[8] = 0xaa1303e0; // mov x0, x19
        t[9] = 0xaa1403e1; // mov x1, x20
        t[10] = 0xa94153f3; // ldp x19, x20, [sp, #16]
        t[11] = 0xa8c27bfd; // ldp x29, x30, [sp], #32
        t[12] = 0x58000090; // ldr x16, [pc, #16] -> lit_ret
        t[13] = 0xd61f0200; // br x16

        *(uint64_t *)(&t[14]) = (uint64_t)patch_dart_doubles;
        *(uint64_t *)(&t[16]) = (uint64_t)ret_target;

        __builtin___clear_cache((char *)tramp_addr, (char *)tramp_addr + 0x100);

        // At callsite (0x1a7b770): b 0x1b47800
        if (unprotect_memory(callsite, 4)) {
            uint32_t *cs = (uint32_t *)callsite;
            *cs = 0x14030024; // b 0x1b47800
            __builtin___clear_cache((char *)callsite, (char *)callsite + 4);
            LOGI("Hooked getDampingResponse callsite at 0x%lx -> trampoline at 0x%lx", callsite, tramp_addr);
        } else {
            LOGE("Failed to unprotect callsite at 0x%lx", callsite);
        }
    } else {
        LOGE("Failed to unprotect slack trampoline at 0x%lx", tramp_addr);
    }
}

static void trigger_unlock_animation() {
    LOGI("trigger_unlock_animation called: dispatching USER_PRESENT intent to launcher");
    pid_t pid = fork();
    if (pid == 0) {
        execl("/system/bin/am", "am", "broadcast", "-a", "android.intent.action.USER_PRESENT", "-p", "com.miui.home", "--receiver-foreground", nullptr);
        _exit(0);
    }
}

static void *socket_server_thread(void *) {
    LOGI("Starting HyperBounce IPC socket server thread");

    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        LOGE("Failed to create UNIX domain socket: %d", errno);
        return nullptr;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    memcpy(addr.sun_path, SOCKET_NAME, SOCKET_NAME_LEN);

    socklen_t addr_len = sizeof(sa_family_t) + SOCKET_NAME_LEN;
    if (bind(server_fd, (struct sockaddr *)&addr, addr_len) < 0) {
        LOGE("Failed to bind abstract socket @hyperbounce.launcher.control: %d", errno);
        close(server_fd);
        return nullptr;
    }

    if (listen(server_fd, 4) < 0) {
        LOGE("Failed to listen on abstract socket: %d", errno);
        close(server_fd);
        return nullptr;
    }

    LOGI("HyperBounce IPC server listening on @hyperbounce.launcher.control");

    while (true) {
        int client_fd = accept(server_fd, nullptr, nullptr);
        if (client_fd < 0) {
            continue;
        }

        uint8_t cmd = 0;
        ssize_t n = read(client_fd, &cmd, 1);
        if (n <= 0) {
            close(client_fd);
            continue;
        }

        if (cmd == CMD_PING) {
            uint8_t pong = 0x01;
            write(client_fd, &pong, 1);
        } else if (cmd == CMD_UPDATE_CONFIG) {
            struct ConfigPayload {
                float damping;
                float response;
                int32_t mode;
                float velocity;
                uint8_t enabled;
            } __attribute__((packed)) payload;

            if (read(client_fd, &payload, sizeof(payload)) == sizeof(payload)) {
                g_cfg.enabled.store(payload.enabled != 0, std::memory_order_relaxed);
                g_cfg.mode.store(payload.mode, std::memory_order_relaxed);
                g_cfg.damping.store((double)payload.damping, std::memory_order_relaxed);
                g_cfg.response.store((double)payload.response, std::memory_order_relaxed);
                g_cfg.velocity.store((double)payload.velocity, std::memory_order_relaxed);

                LOGI("Received Config Update: enabled=%d, mode=%d, damping=%f, resp=%f, vel=%f",
                     payload.enabled, payload.mode, payload.damping, payload.response, payload.velocity);

                uint8_t ack = 0x01;
                write(client_fd, &ack, 1);
            }
        } else if (cmd == CMD_TRIGGER_UNLOCK) {
            LOGI("Received CMD_TRIGGER_UNLOCK from SystemUI");
            trigger_unlock_animation();
            uint8_t ack = 0x01;
            write(client_fd, &ack, 1);
        }

        close(client_fd);
    }

    close(server_fd);
    return nullptr;
}

__attribute__((constructor))
void hyperbounce_agent_init(void) {
    LOGI("HyperBounce Agent injected into PID %d!", getpid());

    if (find_libapp_base()) {
        install_hooks();
    } else {
        LOGE("Failed to find libapp.so base");
    }

    pthread_t thread;
    pthread_create(&thread, nullptr, socket_server_thread, nullptr);
    pthread_detach(thread);
}
