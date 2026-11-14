#!/usr/bin/env bash
# NFR-07: battery use at rest, budget 0.5% of battery per day, no wake locks held by the launcher.
# Uses adb (platform-tools) to talk to the attached device.
# Usage: tools/measure_battery.sh [hours=8]   (leave the device idle on home, unplugged, screen off, for the period)
# Step 1 (run now):    tools/measure_battery.sh start
# Step 2 (run later):  tools/measure_battery.sh report
set -u
. "$(dirname "$0")/_common.sh"; need_device
case "${1:-report}" in
  start)
    $ADB shell dumpsys batterystats --enable full-wake-history >/dev/null
    $ADB shell dumpsys batterystats --reset >/dev/null
    date +%s > /tmp/liftoff_battery_start; echo "reset batterystats; leave the device idle and unplugged, then run: $0 report" ;;
  report)
    uid=$($ADB shell dumpsys package "$PKG" | awk -F= '/userId=/ {print $2; exit}' | tr -d '\r ')
    start=$(cat /tmp/liftoff_battery_start 2>/dev/null || echo 0); now=$(date +%s)
    hours=$(awk -v s="$start" -v n="$now" 'BEGIN {printf "%.2f", (n-s)/3600}')
    echo "window: $hours h, uid $uid"
    $ADB shell dumpsys batterystats --charged "$PKG" | grep -E "Uid u0a|Estimated power use|Wake lock|wakelock|Foreground|Cpu" | head -30
    echo "--- wake locks held by $PKG (must be none) ---"
    $ADB shell dumpsys power | grep -i "$PKG" | grep -i wake || echo "none"
    echo "Compare 'Uid ... mAh' to battery capacity: percent = mAh / capacity_mAh * 100 * (24 / $hours); budget 0.5 per day." ;;
esac
