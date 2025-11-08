#!/usr/bin/env bash
set -euo pipefail

# =========================
# Android SDK bootstrapper
# - Installs JDK 17
# - Installs Android cmdline-tools (sdkmanager)
# - Accepts licenses
# - Installs platform-tools, build-tools, platforms
# =========================

# --- Config (override via env if you want) ---
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_ZIP_URL="${CMDLINE_ZIP_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"
PLATFORM_API_LEVEL="${PLATFORM_API_LEVEL:-android-34}"

# --- Helpers ---
log() { printf "\n\033[1;32m[setup]\033[0m %s\n" "$*"; }
warn() { printf "\n\033[1;33m[warn]\033[0m %s\n" "$*"; }
die() { printf "\n\033[1;31m[error]\033[0m %s\n\n" "$*"; exit 1; }

need_cmd() { command -v "$1" >/dev/null 2>&1; }

append_to_bashrc_once() {
  local line="$1"
  local file="$HOME/.bashrc"
  grep -Fqx "$line" "$file" 2>/dev/null || echo "$line" >> "$file"
}

# --- Apt packages (Codespaces/Ubuntu) ---
log "Installing base packages (sudo apt-get)..."
if ! need_cmd sudo; then
  die "sudo is not available. Run this on Ubuntu/Debian with sudo, or install prerequisites manually."
fi
sudo apt-get update -y
sudo apt-get install -y unzip zip wget curl git ca-certificates

# --- JDK 17 ---
if need_cmd java; then
  log "Java already present: $(java -version 2>&1 | head -n1)"
else
  log "Installing OpenJDK 17..."
  sudo apt-get install -y openjdk-17-jdk
fi

# --- Create SDK root structure ---
log "Preparing ANDROID_SDK_ROOT at: $ANDROID_SDK_ROOT"
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
mkdir -p "$ANDROID_SDK_ROOT/platform-tools"

# --- Download & install commandline-tools if missing ---
TOOLS_BIN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"
if [[ -x "$TOOLS_BIN/sdkmanager" ]]; then
  log "cmdline-tools already installed at: $TOOLS_BIN"
else
  TMP_DIR="$(mktemp -d)"
  log "Downloading Android commandline-tools..."
  ( cd "$TMP_DIR" && wget -q "$CMDLINE_ZIP_URL" -O cmdline-tools.zip )
  log "Unzipping to $ANDROID_SDK_ROOT/cmdline-tools ..."
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$ANDROID_SDK_ROOT/cmdline-tools"
  rm -rf "$TMP_DIR"

  # The zip unpacks to a folder named 'cmdline-tools'. Move to 'latest' as expected by PATH.
  if [[ -d "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" ]]; then
    mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  elif [[ -d "$ANDROID_SDK_ROOT/cmdline-tools/tools" ]]; then
    # Some older zips use 'tools'—normalize it:
    mv "$ANDROID_SDK_ROOT/cmdline-tools/tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  fi

  [[ -x "$TOOLS_BIN/sdkmanager" ]] || die "Failed to place sdkmanager at $TOOLS_BIN"
fi

# --- PATH wiring (current shell + persistence) ---
export ANDROID_SDK_ROOT
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

append_to_bashrc_once "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\""
append_to_bashrc_once "export PATH=\"\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools:\$ANDROID_SDK_ROOT/emulator:\$PATH\""

log "ANDROID_SDK_ROOT set to: $ANDROID_SDK_ROOT"
log "sdkmanager path: $(command -v sdkmanager || echo 'not found')"

# --- Accept licenses & install core packages ---
log "Accepting Android SDK licenses..."
yes | sdkmanager --licenses >/dev/null

log "Installing platform-tools, build-tools;${BUILD_TOOLS_VERSION}, platforms;${PLATFORM_API_LEVEL} ..."
sdkmanager "platform-tools" "build-tools;${BUILD_TOOLS_VERSION}" "platforms;${PLATFORM_API_LEVEL}"

# --- Verify ---
log "Verifying installation..."
command -v sdkmanager >/dev/null || die "sdkmanager not on PATH"
command -v adb >/dev/null || die "adb (platform-tools) not on PATH"

sdkmanager --version || true
adb --version || true

log "Done. Open a NEW shell or run: 'source ~/.bashrc' to persist PATH."
log "Examples:
  sdkmanager --list | head -n 40
  adb version
"
