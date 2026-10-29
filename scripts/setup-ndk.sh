#!/usr/bin/env bash
# =============================================================================
# Install Android NDK 27 into a known location.
# CI uses android-actions/setup-android which handles this; this script is a
# convenience for local dev.
# =============================================================================
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
NDK_VERSION="27.0.12077973"

mkdir -p "$SDK_ROOT"
echo "Installing NDK $NDK_VERSION into $SDK_ROOT/ndk/$NDK_VERSION…"

# Use sdkmanager if available
if command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager --sdk_root="$SDK_ROOT" "ndk;$NDK_VERSION" || true
elif [[ -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_ROOT" "ndk;$NDK_VERSION" || true
else
  echo "❌ sdkmanager not found. Install Android command-line tools first:"
  echo "   https://developer.android.com/tools"
  exit 1
fi

export ANDROID_NDK_HOME="$SDK_ROOT/ndk/$NDK_VERSION"
echo "✓ ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
echo "Add this to your shell profile:"
echo "  export ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
