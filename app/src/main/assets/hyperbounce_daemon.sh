#!/system/bin/sh
# HyperBounce HyperOS 4 Native Root Daemon
# Automatically manages injection of libhyperbounce_agent.so into com.miui.home

LOG_TAG="HyperBounce_Daemon"
log_info() {
    log -p i -t "$LOG_TAG" "$1"
}
log_error() {
    log -p e -t "$LOG_TAG" "$1"
}

log_info "HyperBounce Root Daemon starting..."

LOCAL_DIR="/data/local/tmp"
HOME_FILES="/data/data/com.miui.home/files"

AGENT_NAME="libhyperbounce_agent.so"
INJECTOR_NAME="hyperbounce_injector"

# Locate injector
INJECTOR_BIN=""
for p in "$LOCAL_DIR/$INJECTOR_NAME" "/data/adb/service.d/$INJECTOR_NAME" "/system/bin/$INJECTOR_NAME"; do
    if [ -f "$p" ] && [ -x "$p" ]; then
        INJECTOR_BIN="$p"
        break
    fi
done

if [ -z "$INJECTOR_BIN" ]; then
    log_error "Injector binary $INJECTOR_NAME not found!"
else
    log_info "Found injector at $INJECTOR_BIN"
fi

LAST_PID=""

while true; do
    PID=$(pidof com.miui.home)
    if [ -n "$PID" ]; then
        if [ "$PID" != "$LAST_PID" ]; then
            log_info "Detected com.miui.home (PID: $PID)"
            
            # Wait for libapp.so (MiuiHome.apk mapped at offset 00528000)
            TRIES=0
            while [ $TRIES -lt 30 ]; do
                if grep -q "00528000.*MiuiHome.apk" "/proc/$PID/maps" 2>/dev/null; then
                    break
                fi
                sleep 0.5
                TRIES=$((TRIES + 1))
            done

            # Check if already injected
            if ! grep -q "$AGENT_NAME" "/proc/$PID/maps" 2>/dev/null; then
                mkdir -p "$HOME_FILES"
                
                # Check for agent source in package nativeLibraryDir or /data/local/tmp
                AGENT_SRC=""
                HB_LIB=$(pm path dev.hyperios.app 2>/dev/null | sed -E 's/package:(.*)\/base\.apk/\1\/lib\/arm64/')
                if [ -n "$HB_LIB" ] && [ -f "$HB_LIB/$AGENT_NAME" ]; then
                    AGENT_SRC="$HB_LIB/$AGENT_NAME"
                elif [ -f "$LOCAL_DIR/$AGENT_NAME" ]; then
                    AGENT_SRC="$LOCAL_DIR/$AGENT_NAME"
                fi

                if [ -n "$AGENT_SRC" ]; then
                    cp -f "$AGENT_SRC" "$HOME_FILES/$AGENT_NAME"
                fi
                
                if [ -f "$HOME_FILES/$AGENT_NAME" ]; then
                    HOME_UID=$(stat -c %u "/data/data/com.miui.home" 2>/dev/null || echo 10135)
                    HOME_GID=$(stat -c %g "/data/data/com.miui.home" 2>/dev/null || echo 10135)
                    HOME_CTX=$(ls -Zd "/data/data/com.miui.home" 2>/dev/null | awk '{print $1}')
                    
                    chown "$HOME_UID:$HOME_GID" "$HOME_FILES/$AGENT_NAME"
                    chmod 755 "$HOME_FILES/$AGENT_NAME"
                    if [ -n "$HOME_CTX" ]; then
                        chcon "$HOME_CTX" "$HOME_FILES/$AGENT_NAME" 2>/dev/null
                    else
                        chcon u:object_r:app_data_file:s0:c512,c768 "$HOME_FILES/$AGENT_NAME" 2>/dev/null
                    fi

                    if [ -n "$INJECTOR_BIN" ]; then
                        log_info "Injecting $AGENT_NAME into PID $PID via $INJECTOR_BIN..."
                        "$INJECTOR_BIN" "$PID" "$HOME_FILES/$AGENT_NAME"
                        if [ $? -eq 0 ]; then
                            log_info "Successfully injected $AGENT_NAME into PID $PID"
                            LAST_PID="$PID"
                        else
                            log_error "Injection failed for PID $PID"
                        fi
                    else
                        log_error "Cannot inject: injector binary not found"
                    fi
                else
                    log_error "Agent $AGENT_NAME not found in $HOME_FILES or sources"
                fi
            else
                log_info "PID $PID already has $AGENT_NAME injected"
                LAST_PID="$PID"
            fi
        fi
    else
        LAST_PID=""
    fi
    sleep 2
done
