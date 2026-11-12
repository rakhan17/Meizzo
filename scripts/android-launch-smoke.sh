#!/usr/bin/env bash
set -euo pipefail

mode="${1:-debug}"
module="${2:-}"

assert_running() {
  local package="$1"
  local pid=""
  local activities=""
  sleep 12
  pid="$(timeout 20s adb shell pidof "$package" 2>/dev/null | tr -d '\r' || true)"
  activities="$(timeout 20s adb shell dumpsys activity activities 2>/dev/null || true)"
  if [[ -z "$pid" ]] || ! printf '%s\n' "$activities" | grep -E 'topResumedActivity|mResumedActivity' | grep -F "$package" >/dev/null; then
    echo "::error::${package} MainActivity did not remain resumed after launch"
    echo "Process ID:"
    printf '%s\n' "$pid"
    echo "Matching activity records:"
    printf '%s\n' "$activities" | grep -C 4 -E "$package|topResumedActivity|mResumedActivity" || true
    echo "Android process exit history:"
    timeout 20s adb shell dumpsys activity exit-info "$package" || true
    echo "Package process state:"
    timeout 20s adb shell dumpsys package "$package" | grep -E 'userId=|enabled=|stopped=|firstInstallTime|lastUpdateTime' || true
    echo "Relevant crash and process logcat (filtered across the full buffer):"
    timeout 20s adb logcat -d -v threadtime | grep -E "$package|AndroidRuntime|FATAL EXCEPTION|Fatal signal|SIGSEGV|F/DEBUG|backtrace|#[0-9]+ pc|lmkd|ActivityTaskManager|Process .*died|Killing|Force finishing|RenderThread|libhwui|libEGL|libGLES" | tail -n 500 || true
    return 1
  fi
  timeout 20s adb shell am force-stop "$package"
}

launch_app() {
  local package="$1"
  timeout 15s adb logcat -c
  timeout 20s adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  timeout 30s adb shell am start -W -n "$package/.MainActivity"
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
    timeout 60s adb install -r "$apk"
    launch_app "$package"
    ;;
  release)
    tag="${MEIZZO_RELEASE_TAG:?MEIZZO_RELEASE_TAG is required for release smoke tests}"
    timeout 60s adb install -r "dist/MEIZZO-Client-$tag.apk"
    timeout 60s adb install -r "dist/MEIZZO-Controller-$tag.apk"
    launch_app "com.meizzo.client"
    launch_app "com.meizzo.controller"
    ;;
  *)
    echo "Usage: $0 debug client|controller | release"
    exit 2
    ;;
esac
