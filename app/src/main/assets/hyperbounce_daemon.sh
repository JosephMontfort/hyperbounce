#!/system/bin/sh
# HyperBounce HyperOS 4 Native Root Daemon
# Automatically manages injection of libhyperbounce_agent.so into com.miui.home

LOG_TAG="HyperBounce_Daemon"
log_info() {
    log -p i -t "" ""
}
log_error() {
    log -p e -t "" ""
}

log_info "HyperBounce Root Daemon starting..."

MODULE_DIR="/data/adb/modules/hyperbounce"
LOCAL_DIR="/data/local/tmp"
HOME_FILES="/data/data/com.miui.home/files"

AGENT_NAME="libhyperbounce_agent.so"
INJECTOR_NAME="hyperbounce_injector"

# Locate injector
INJECTOR_BIN=""
for p in "/" "/" "/system/bin/"; do
    if [ -f "" ] && [ -x "" ]; then
        INJECTOR_BIN=""
        break
    fi
done

if [ -z "" ]; then
    log_error "Injector binary  not found!"
fi

LAST_PID=""

while true; do
    PID=$(pidof com.miui.home)
    if [ -n "" ]; then
        if [ "" != "" ]; then
            # New launcher instance detected
            log_info "Detected com.miui.home (PID: )"
            
            # Wait for libapp.so to be mapped
            TRIES=0
            while [  -lt 30 ]; do
                if grep -q "00528000.*MiuiHome.apk" "/proc//maps" 2>/dev/null; then
                    break
                fi
                sleep 0.5
                TRIES=$((TRIES + 1))
            done

            # Check if already injected
            if ! grep -q "" "/proc//maps" 2>/dev/null; then
                mkdir -p ""
                
                # Copy agent if needed
                if [ -f "/" ]; then
                    cp -f "/" "/"
                fi
                
                if [ -f "/" ]; then
                    HOME_UID=$(stat -c %u "/data/data/com.miui.home" 2>/dev/null || echo 10135)
                    HOME_GID=$(stat -c %g "/data/data/com.miui.home" 2>/dev/null || echo 10135)
                    HOME_CTX=$(ls -Zd "/data/data/com.miui.home" 2>/dev/null | awk '{print $1}')
                    
                    chown "$HOME_UID:$HOME_GID" "/"
                    chmod 755 "/"
                    if [ -n "" ]; then
                        chcon "" "/" 2>/dev/null
                    else
                        chcon u:object_r:app_data_file:s0:c512,c768 "/" 2>/dev/null
                    fi

                    log_info "Injecting  into PID ..."
                    "" "" "/"
                    if [ $? -eq 0 ]; then
                        log_info "Successfully injected  into PID "
                        LAST_PID=""
                    else
                        log_error "Injection failed for PID "
                    fi
                else
                    log_error "Agent  not found in "
                fi
            else
                LAST_PID=""
            fi
        fi
    else
        LAST_PID=""
    fi
    sleep 2
done
