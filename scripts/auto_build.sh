#!/usr/bin/env bash
# BestCut — এক কমান্ডে সম্পূর্ণ অটো বিল্ড (Linux / macOS)
#   ./scripts/auto_build.sh
# JDK 17, Android SDK, Gradle সব দরকার হলে নিজে ডাউনলোড করে APK বানিয়ে dist/BestCut.apk এ রাখবে।
set -euo pipefail

cd "$(dirname "$0")/.."

GRADLE_VER="8.7"
CMDLINE_TOOLS_VER="11076708"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
TOOLS_DIR="$HOME/.bestcut"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }
SUDO=""; if [ "$(id -u)" -ne 0 ] && have sudo; then SUDO="sudo"; fi

# 1) JDK 17 ----------------------------------------------------------------
java_ok() { have java && java -version 2>&1 | grep -qE 'version "(17|21)'; }
if ! java_ok; then
  log "JDK 17 ইনস্টল হচ্ছে"
  if have apt-get; then
    $SUDO apt-get update -y
    $SUDO apt-get install -y openjdk-17-jdk-headless unzip curl
  elif have brew; then
    brew install openjdk@17
    export JAVA_HOME="$(brew --prefix openjdk@17)"
    export PATH="$JAVA_HOME/bin:$PATH"
  else
    echo "JDK 17 পাওয়া যায়নি। অনুগ্রহ করে নিজে JDK 17 ইনস্টল করুন।" >&2
    exit 1
  fi
fi
have unzip || { echo "unzip দরকার" >&2; exit 1; }
have curl  || { echo "curl দরকার" >&2; exit 1; }

# 2) Android SDK -----------------------------------------------------------
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "Android command-line tools ডাউনলোড হচ্ছে"
  OS="linux"; [ "$(uname)" = "Darwin" ] && OS="mac"
  mkdir -p "$SDK_DIR/cmdline-tools"
  curl -fL -o /tmp/bestcut-cmdtools.zip \
    "https://dl.google.com/android/repository/commandlinetools-${OS}-${CMDLINE_TOOLS_VER}_latest.zip"
  unzip -q -o /tmp/bestcut-cmdtools.zip -d "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f /tmp/bestcut-cmdtools.zip
fi
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

log "SDK লাইসেন্স ও প্যাকেজ"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$SDK_DIR" > local.properties

# 3) Gradle wrapper (না থাকলে বানিয়ে নেবে) --------------------------------
if [ ! -f gradlew ] || [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  log "Gradle $GRADLE_VER সেটআপ"
  GRADLE_HOME="$TOOLS_DIR/gradle-$GRADLE_VER"
  if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$TOOLS_DIR"
    curl -fL -o /tmp/bestcut-gradle.zip \
      "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip"
    unzip -q -o /tmp/bestcut-gradle.zip -d "$TOOLS_DIR"
    rm -f /tmp/bestcut-gradle.zip
  fi
  "$GRADLE_HOME/bin/gradle" wrapper --gradle-version "$GRADLE_VER" --no-daemon
fi
chmod +x gradlew

# 4) বিল্ড -----------------------------------------------------------------
log "APK বিল্ড হচ্ছে (প্রথমবার ৫–১০ মিনিট লাগতে পারে)"
./gradlew --no-daemon assembleRelease

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/BestCut.apk

log "সম্পন্ন! APK: $(pwd)/dist/BestCut.apk"
