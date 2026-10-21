cmake_minimum_required(VERSION 3.22.1)

project("freedstray_native")

add_library(
    freedstray_native
    SHARED
    native-crypto.c
    native-audio.c
    native-video.c
)

find_library(
    log-lib
    log
)

target_link_libraries(
    freedstray_native
    ${log-lib}
)
