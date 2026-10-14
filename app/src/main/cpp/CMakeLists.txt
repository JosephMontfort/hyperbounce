cmake_minimum_required(VERSION 3.22.1)

project("hyperbounce_native")

add_library(
    hyperbounce_native
    SHARED
    hyperbounce_native.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    hyperbounce_native
    ${log-lib}
)
