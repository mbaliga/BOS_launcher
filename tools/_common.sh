#!/usr/bin/env bash
# Shared helpers for the adb-based measurement scripts. Needs one device or emulator attached and adb on PATH.
PKG="${LIFTOFF_PKG:-com.bos.sphere}"
ACT="${LIFTOFF_ACTIVITY:-$PKG/.MainActivity}"
ADB="adb ${ANDROID_SERIAL:+-s $ANDROID_SERIAL}"
need_device() { $ADB get-state >/dev/null 2>&1 || { echo "no adb device attached" >&2; exit 2; }; }
go_home() { $ADB shell input keyevent KEYCODE_HOME; }
