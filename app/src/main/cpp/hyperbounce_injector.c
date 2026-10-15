#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <dlfcn.h>
#include <elf.h>
#include <dirent.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <linux/elf.h>
#include <asm/ptrace.h>
#include <android/log.h>

#define LOG_TAG "HyperBounce_Injector"
#define LOGI(...) do { printf(__VA_ARGS__); printf("\n"); __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); } while(0)
#define LOGE(...) do { fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); } while(0)

#ifndef PTRACE_GETREGSET
#define PTRACE_GETREGSET 0x4204
#endif
#ifndef PTRACE_SETREGSET
#define PTRACE_SETREGSET 0x4205
#endif
#ifndef NT_PRSTATUS
#define NT_PRSTATUS 1
#endif

#define SLACK_OFFSET 0x1b47ff0

static uintptr_t get_module_base(pid_t pid, const char *module_name) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *fp = fopen(path, "r");
    if (!fp) return 0;

    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, module_name) != NULL) {
            if (sscanf(line, "%lx-", &base) == 1) {
                break;
            }
        }
    }
    fclose(fp);
    return base;
}

static uintptr_t get_libapp_base(pid_t pid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *fp = fopen(path, "r");
    if (!fp) return 0;

    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "MiuiHome.apk") != NULL && strstr(line, "00528000") != NULL) {
            if (sscanf(line, "%lx-", &base) == 1) {
                break;
            }
        }
    }
    fclose(fp);
    return base;
}

static uint64_t call_remote_func(pid_t pid, uintptr_t break_addr, uintptr_t func_addr, uint64_t *args, int num_args) {
    struct user_pt_regs regs, orig_regs;
    struct iovec io;

    io.iov_base = &regs;
    io.iov_len = sizeof(regs);
    if (ptrace(PTRACE_GETREGSET, pid, (void *)NT_PRSTATUS, &io) < 0) {
        LOGE("PTRACE_GETREGSET failed: %s", strerror(errno));
        return 0;
    }
    memcpy(&orig_regs, &regs, sizeof(regs));

    for (int i = 0; i < num_args && i < 8; i++) {
        regs.regs[i] = args[i];
    }
    regs.pc = func_addr;
    regs.regs[30] = break_addr;
    regs.sp = (regs.sp - 128) & ~15;

    io.iov_base = &regs;
    io.iov_len = sizeof(regs);
    if (ptrace(PTRACE_SETREGSET, pid, (void *)NT_PRSTATUS, &io) < 0) {
        LOGE("PTRACE_SETREGSET failed: %s", strerror(errno));
        return 0;
    }

    if (ptrace(PTRACE_CONT, pid, NULL, NULL) < 0) {
        LOGE("PTRACE_CONT failed: %s", strerror(errno));
        return 0;
    }

    int status = 0;
    waitpid(pid, &status, 0);

    io.iov_base = &regs;
    io.iov_len = sizeof(regs);
    ptrace(PTRACE_GETREGSET, pid, (void *)NT_PRSTATUS, &io);
    uint64_t retval = regs.regs[0];

    // Restore original regs
    io.iov_base = &orig_regs;
    io.iov_len = sizeof(orig_regs);
    ptrace(PTRACE_SETREGSET, pid, (void *)NT_PRSTATUS, &io);

    return retval;
}

static void pause_threads(pid_t pid, pid_t main_tid) {
    char dirpath[64];
    snprintf(dirpath, sizeof(dirpath), "/proc/%d/task", pid);
    DIR *d = opendir(dirpath);
    if (!d) return;
    struct dirent *de;
    while ((de = readdir(d)) != NULL) {
        if (de->d_name[0] == '.') continue;
        pid_t tid = (pid_t)atoi(de->d_name);
        if (tid != main_tid && tid > 0) {
            ptrace(PTRACE_ATTACH, tid, NULL, NULL);
        }
    }
    closedir(d);
}

static void resume_threads(pid_t pid, pid_t main_tid) {
    char dirpath[64];
    snprintf(dirpath, sizeof(dirpath), "/proc/%d/task", pid);
    DIR *d = opendir(dirpath);
    if (!d) return;
    struct dirent *de;
    while ((de = readdir(d)) != NULL) {
        if (de->d_name[0] == '.') continue;
        pid_t tid = (pid_t)atoi(de->d_name);
        if (tid != main_tid && tid > 0) {
            ptrace(PTRACE_DETACH, tid, NULL, NULL);
        }
    }
    closedir(d);
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        printf("Usage: %s <pid> <so_path>\n", argv[0]);
        return 1;
    }

    pid_t target_pid = (pid_t)atoi(argv[1]);
    const char *so_path = argv[2];

    if (kill(target_pid, 0) != 0) {
        LOGE("Process %d does not exist", target_pid);
        return 1;
    }

    // Check if so is already mapped in target
    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", target_pid);
    FILE *maps_fp = fopen(maps_path, "r");
    if (maps_fp) {
        char line[512];
        const char *basename = strrchr(so_path, '/');
        basename = basename ? basename + 1 : so_path;
        while (fgets(line, sizeof(line), maps_fp)) {
            if (strstr(line, basename) != NULL) {
                LOGI("Module %s already injected in PID %d", basename, target_pid);
                fclose(maps_fp);
                return 0;
            }
        }
        fclose(maps_fp);
    }

    uintptr_t libapp_base = get_libapp_base(target_pid);
    if (!libapp_base) {
        LOGE("Failed to find libapp.so base in PID %d", target_pid);
        return 1;
    }
    uintptr_t break_addr = libapp_base + SLACK_OFFSET;

    // Resolve local and remote bases
    pid_t self_pid = getpid();
    uintptr_t local_libc = get_module_base(self_pid, "libc.so");
    uintptr_t remote_libc = get_module_base(target_pid, "libc.so");
    uintptr_t local_libdl = get_module_base(self_pid, "libdl.so");
    uintptr_t remote_libdl = get_module_base(target_pid, "libdl.so");

    if (!remote_libc || !remote_libdl) {
        LOGE("Failed to find libc/libdl in target PID %d", target_pid);
        return 1;
    }

    void *local_mmap_ptr = (void *)mmap;
    void *local_dlopen_ptr = (void *)dlopen;
    void *local_dlerror_ptr = (void *)dlerror;

    uintptr_t remote_mmap = remote_libc + ((uintptr_t)local_mmap_ptr - local_libc);
    uintptr_t remote_dlopen = remote_libdl + ((uintptr_t)local_dlopen_ptr - local_libdl);
    uintptr_t remote_dlerror = remote_libdl + ((uintptr_t)local_dlerror_ptr - local_libdl);

    LOGI("Target PID: %d, libapp_base: 0x%lx, break_addr: 0x%lx", target_pid, libapp_base, break_addr);

    // Write BRK #0 instruction (0xd4200000) to private slack space
    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", target_pid);
    int mem_fd = open(mem_path, O_RDWR);
    if (mem_fd < 0) {
        LOGE("open %s failed: %s", mem_path, strerror(errno));
        return 1;
    }
    uint32_t brk_insn = 0xd4200000;
    pwrite(mem_fd, &brk_insn, sizeof(brk_insn), break_addr);

    pause_threads(target_pid, target_pid);

    if (ptrace(PTRACE_ATTACH, target_pid, NULL, NULL) < 0) {
        LOGE("Failed to attach to PID %d: %s", target_pid, strerror(errno));
        resume_threads(target_pid, target_pid);
        close(mem_fd);
        return 1;
    }

    int status = 0;
    waitpid(target_pid, &status, 0);

    // Call remote mmap(0, 4096, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0)
    uint64_t mmap_args[6] = {0, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, (uint64_t)-1, 0};
    uint64_t remote_buf = call_remote_func(target_pid, break_addr, remote_mmap, mmap_args, 6);

    if (remote_buf == 0 || (int64_t)remote_buf == -1) {
        LOGE("Remote mmap failed in PID %d", target_pid);
        ptrace(PTRACE_DETACH, target_pid, NULL, NULL);
        resume_threads(target_pid, target_pid);
        close(mem_fd);
        return 1;
    }

    LOGI("Allocated remote buffer at: 0x%lx", (uintptr_t)remote_buf);

    // Write so_path into remote_buf
    pwrite(mem_fd, so_path, strlen(so_path) + 1, remote_buf);

    // Call dlopen(remote_buf, RTLD_NOW)
    uint64_t dlopen_args[2] = {remote_buf, RTLD_NOW};
    uint64_t handle = call_remote_func(target_pid, break_addr, remote_dlopen, dlopen_args, 2);

    if (handle == 0) {
        uint64_t err_ptr = call_remote_func(target_pid, break_addr, remote_dlerror, NULL, 0);
        char err_buf[256] = {0};
        if (err_ptr != 0) {
            pread(mem_fd, err_buf, sizeof(err_buf) - 1, err_ptr);
        }
        LOGE("Remote dlopen failed: %s", err_buf[0] ? err_buf : "unknown error");
        ptrace(PTRACE_DETACH, target_pid, NULL, NULL);
        resume_threads(target_pid, target_pid);
        close(mem_fd);
        return 1;
    }

    LOGI("Successfully injected %s into PID %d (handle: 0x%lx)", so_path, target_pid, (uintptr_t)handle);

    // Clean up breakpoint in slack space
    uint32_t zero = 0;
    pwrite(mem_fd, &zero, sizeof(zero), break_addr);
    close(mem_fd);

    ptrace(PTRACE_DETACH, target_pid, NULL, NULL);
    resume_threads(target_pid, target_pid);
    return 0;
}
