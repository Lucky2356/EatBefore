#!/usr/bin/env bash
# Ставит Android SDK в облачную среду разработки. Вызывается один раз при создании
# контейнера (onCreateCommand в devcontainer.json).
#
# Ставится только то, без чего Gradle не начнёт: sdkmanager, платформа и
# platform-tools. Остальное — build-tools нужной версии — AGP скачивает сам, и лучше
# пусть решает он: версия зависит от AGP, а не от нашего представления о ней.
set -euo pipefail

SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

if [ -x "$SDKMANAGER" ]; then
  echo "Android SDK уже на месте: $SDK"
  exit 0
fi

sudo mkdir -p "$SDK"
sudo chown -R "$(id -u):$(id -g)" "$SDK"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Скачиваю command-line tools…"
curl -fsSL -o "$tmp/tools.zip" "$TOOLS_URL"
unzip -q "$tmp/tools.zip" -d "$tmp"
mkdir -p "$SDK/cmdline-tools"
mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"

# Лицензии принимаются здесь, чтобы AGP мог дотягивать недостающие пакеты молча:
# иначе первая же сборка встанет на вопросе, которого никто не увидит.
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

echo "Ставлю платформу и platform-tools…"
"$SDKMANAGER" --install "platform-tools" "platforms;android-36" >/dev/null

echo "Готово. ANDROID_HOME=$SDK"
echo "Проверка: ./gradlew :app:assembleDebug"
