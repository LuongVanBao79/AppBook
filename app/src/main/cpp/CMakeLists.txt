cmake_minimum_required(VERSION 3.10.2)

project("appbook-security")

add_library(
        app-security
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        app-security
        ${log-lib})