#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_PATH="/data/local/tmp/sample_tool_arm64"

# Step 1: Compile binary if it doesn't exist
if [ ! -f "$SCRIPT_DIR/sample_tool_arm64" ]; then
    bash "$SCRIPT_DIR/build_android.sh"
fi

echo "=================================================="
echo " Pushing Binary to Connected Android Device via ADB"
echo " Destination: $TARGET_PATH"
echo "=================================================="

adb push "$SCRIPT_DIR/sample_tool_arm64" "$TARGET_PATH"
adb shell chmod +x "$TARGET_PATH"

echo "Success! The file is now executable at: $TARGET_PATH"
echo "In Web UI (agent.ejs), enter this path in 'Run Binary / File':"
echo " -> $TARGET_PATH"
