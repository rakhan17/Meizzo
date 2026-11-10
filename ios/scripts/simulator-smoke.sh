#!/usr/bin/env bash
set -euo pipefail

app_path="${1:?Usage: simulator-smoke.sh APP_PATH BUNDLE_ID}"
bundle_id="${2:?Usage: simulator-smoke.sh APP_PATH BUNDLE_ID}"

device_id="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/{print $2; exit}')"
test -n "$device_id"

state="$(xcrun simctl list devices | awk -F '[()]' -v id="$device_id" '$2 == id {print $3}' | tr -d ' ')"
if [ "$state" != "Booted" ]; then
  xcrun simctl boot "$device_id" 2>/dev/null || true
  xcrun simctl bootstatus "$device_id" -b
fi

xcrun simctl install "$device_id" "$app_path"
xcrun simctl launch "$device_id" "$bundle_id"
xcrun simctl terminate "$device_id" "$bundle_id" >/dev/null
echo "Simulator smoke test passed: $bundle_id installed and launched on $device_id"
