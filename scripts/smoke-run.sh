#!/usr/bin/env bash
# Runs the on-device smoke test on a connected emulator (see .github/workflows/smoke.yml).
# A hang (the instrumentation crashing on start can leave Gradle waiting) fails after 20 minutes,
# and the emulator's log is saved either way, with the app's and test runner's lines printed.
set -u
out="${RUNNER_TEMP:-/tmp}"
adb logcat -c || true
timeout 20m ./gradlew --no-daemon connectedMinifiedAndroidTest
status=$?
[ "$status" -eq 124 ] && echo "::error::The smoke test timed out after 20 minutes"
adb logcat -d -v time > "$out/logcat.txt" 2>&1 || true
echo "---- emulator log (app, test runner, crashes) ----"
grep -E "AndroidRuntime|FATAL|DEBUG|ModelSmokeTest|MemoryBudget|TestRunner|stickerfinder|UnsatisfiedLink|NoSuchMethod|ClassNotFound" "$out/logcat.txt" | tail -n 200 || true
exit "$status"
