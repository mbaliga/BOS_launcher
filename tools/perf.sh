#!/usr/bin/env bash
# NFR-01 to NFR-05: quick adb-only latency probes for the mid-range device, to use between macrobenchmark runs.
# Uses adb (platform-tools) to talk to the attached device.
# The authoritative numbers come from the macrobenchmark module (benchmark/), which needs Android Studio or a CI runner
# with network access to resolve androidx.benchmark. This script uses `am start -W` (TotalTime / WaitTime) and gfxinfo.
# Usage: tools/perf.sh [runs=20]
set -u
. "$(dirname "$0")/_common.sh"; need_device
RUNS="${1:-20}"
p95() { sort -n | awk '{a[NR]=$1} END {i=int(0.95*NR+0.999999); if (i<1) i=1; print a[i]}'; }
cold=(); warm=()
for i in $(seq 1 "$RUNS"); do
  $ADB shell am force-stop "$PKG"; sleep 1
  cold+=("$($ADB shell am start -W -n "$ACT" | awk '/TotalTime/ {print $2}' | tr -d '\r')")
  go_home; sleep 1
  warm+=("$($ADB shell am start -W -n "$ACT" | awk '/TotalTime/ {print $2}' | tr -d '\r')")
done
c=$(printf '%s\n' "${cold[@]}" | p95); w=$(printf '%s\n' "${warm[@]}" | p95)
echo "NFR-02 cold start p95: $c ms (budget 700)"; echo "NFR-01 warm home p95: $w ms (budget 150)"
$ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
sleep 10
$ADB shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th"
echo "NFR-05: janky frames must be at most 1%. NFR-03/04 (palette) are measured by PaletteBenchmark in benchmark/."
fail=0; [ "$c" -le 700 ] || fail=1; [ "$w" -le 150 ] || fail=1; exit $fail
