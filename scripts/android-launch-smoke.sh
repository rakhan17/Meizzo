#!/usr/bin/env bash
set -euo pipefail

mode="${1:-debug}"
module="${2:-}"

assert_running() {
  local package="$1"
  sleep 12
  if ! adb shell pidof "$package" >/dev/null 2>&1 || ! adb shell dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | grep -q "$package"; then
    echo "::error::${package} MainActivity did not remain resumed after launch"
    adb shell dumpsys activity activities || true
    adb logcat -d -v time -s AndroidRuntime:E ActivityTaskManager:E || true
    return 1
  fi
  adb shell am force-stop "$package"
}

launch_app() {
  local package="$1"
  adb logcat -c
  adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb shell am start -W -n "$package/.MainActivity"
  assert_running "$package"
}

case "$mode" in
  debug)
    case "$module" in
      client)
        apk="apps/client/build/outputs/apk/debug/client-debug.apk"
        package="com.meizzo.client"
        ;;
      controller)
        apk="apps/controller/build/outputs/apk/debug/controller-debug.apk"
        package="com.meizzo.controller"
        ;;
      *)
        echo "Usage: $0 debug client|controller"
        exit 2
        ;;
    esac
    adb install -r "$apk"
    launch_app "$package"
    ;;
  release)
    tag="${MEIZZO_RELEASE_TAG:?MEIZZO_RELEASE_TAG is required for release smoke tests}"
    adb install -r "dist/MEIZZO-Client-$tag.apk"
    adb install -r "dist/MEIZZO-Controller-$tag.apk"
    launch_app "com.meizzo.client"
    launch_app "com.meizzo.controller"
    ;;
  *)
    echo "Usage: $0 debug client|controller | release"
    exit 2
    ;;
esac
