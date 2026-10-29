#!/usr/bin/env bash
# =============================================================================
# Build the Rust core for all Android ABIs via cargo-ndk.
# Called by Gradle's `buildRustCore` task; can also be run manually for debugging.
#
# Outputs:
#   $OUTPUT_DIR/<abi>/libsineminer_core.so  for each ABI
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
CORE_DIR="$PROJECT_ROOT/core"
OUTPUT_DIR="${OUTPUT_DIR:-$PROJECT_ROOT/app/build/intermediates/rust_jniLibs}"

# Android ABIs and their Rust target triples
declare -a ABIS=("arm64-v8a" "x86_64")
declare -a TARGETS=("aarch64-linux-android" "x86_64-linux-android")

# Validate NDK
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_NDK_PATH:-}}}"
if [[ -z "$NDK_HOME" ]]; then
  echo "❌ ANDROID_NDK_HOME is not set. Install NDK 27 and export ANDROID_NDK_HOME."
  exit 1
fi
echo "✓ Using NDK at $NDK_HOME"

# Validate cargo-ndk
if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "❌ cargo-ndk not found. Install with: cargo install cargo-ndk --locked --version ^3"
  exit 1
fi

# Optional: use the CI profile for faster builds when CI=true
PROFILE_ARGS=()
if [[ "${CI:-}" == "true" ]]; then
  PROFILE_ARGS=(--profile ci)
fi

for i in "${!ABIS[@]}"; do
  abi="${ABIS[$i]}"
  target="${TARGETS[$i]}"
  out_dir="$OUTPUT_DIR/$abi"
  mkdir -p "$out_dir"
  echo ""
  echo "⚙  Building $abi ($target)…"
  cargo ndk \
    -t "$target" \
    -o "$out_dir" \
    -- \
    build \
    --manifest-path "$CORE_DIR/Cargo.toml" \
    --release \
    "${PROFILE_ARGS[@]}"
  if [[ ! -f "$out_dir/libsineminer_core.so" ]]; then
    echo "❌ Build for $abi failed — .so file not produced"
    exit 1
  fi
  echo "✓ $abi → $out_dir/libsineminer_core.so"
done

echo ""
echo "✅ All ABIs built. Output in $OUTPUT_DIR"
