cmake_minimum_required(VERSION 3.22.1)

project("hyperbounce_native")

find_library(
    log-lib
    log
)

# 1. Main LibXposed native library (SystemUI / ART processes)
add_library(
    hyperbounce_native
    SHARED
    hyperbounce_native.cpp
)
target_link_libraries(
    hyperbounce_native
    ${log-lib}
)

# 2. HyperBounce Native Agent (injected into com.miui.home via Root Daemon)
add_library(
    hyperbounce_agent
    SHARED
    hyperbounce_agent.cpp
)
target_link_libraries(
    hyperbounce_agent
    ${log-lib}
)
