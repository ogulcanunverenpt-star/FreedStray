#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK_CLANG="/home/ogulcan/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi24-clang"

if [ ! -f "$NDK_CLANG" ]; then
    NDK_CLANG=$(which armv7a-linux-androideabi24-clang 2>/dev/null || which arm-linux-androideabi-gcc 2>/dev/null || echo "")
fi

if [ -z "$NDK_CLANG" ]; then
    echo "ERROR: Android NDK ARMv7 Clang cross-compiler not found!"
    exit 1
fi

echo "=================================================="
echo " Building Native ARMv7 (32-bit) Android Binary..."
echo " Compiler: $NDK_CLANG"
echo " Input C File: $SCRIPT_DIR/sample_tool.c"
echo "=================================================="

"$NDK_CLANG" -O2 -fPIE -pie "$SCRIPT_DIR/sample_tool.c" -lz -o "$SCRIPT_DIR/sample_tool_armv7"

echo "SUCCESS! Native ARMv7 Binary created at:"
echo " -> $SCRIPT_DIR/sample_tool_armv7"
file "$SCRIPT_DIR/sample_tool_armv7"
