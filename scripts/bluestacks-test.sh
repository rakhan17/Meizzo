#!/usr/bin/env bash
set -euo pipefail

CLIENT_APK="${1:?usage: $0 CLIENT_APK CONTROLLER_APK}"
CONTROLLER_APK="${2:?usage: $0 CLIENT_APK CONTROLLER_APK}"
CLIENT_PACKAGE="com.meizzo.client"
CONTROLLER_PACKAGE="com.meizzo.controller"

mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if (( ${#DEVICES[@]} < 3 )); then
  echo "Expected at least three authorized ADB devices; found ${#DEVICES[@]}" >&2
  adb devices >&2
  exit 1
fi

echo "Controller: ${DEVICES[0]}"
echo "Client A:   ${DEVICES[1]}"
echo "Client B:   ${DEVICES[2]}"

adb -s "${DEVICES[0]}" install -r "$CONTROLLER_APK"
adb -s "${DEVICES[1]}" install -r "$CLIENT_APK"
adb -s "${DEVICES[2]}" install -r "$CLIENT_APK"

adb -s "${DEVICES[0]}" shell am force-stop "$CONTROLLER_PACKAGE"
adb -s "${DEVICES[1]}" shell am force-stop "$CLIENT_PACKAGE"
adb -s "${DEVICES[2]}" shell am force-stop "$CLIENT_PACKAGE"
adb -s "${DEVICES[0]}" shell monkey -p "$CONTROLLER_PACKAGE" 1 >/dev/null
adb -s "${DEVICES[1]}" shell monkey -p "$CLIENT_PACKAGE" 1 >/dev/null
adb -s "${DEVICES[2]}" shell monkey -p "$CLIENT_PACKAGE" 1 >/dev/null

echo "Installed and launched MEIZZO on three ADB devices."
echo "Use the Client A and Client B displayed codes in the Controller, then collect logs with:"
echo "  adb -s ${DEVICES[0]} logcat -d -v threadtime | grep -i meizzo"
