#!/usr/bin/env bash
# Fails if a built APK requests INTERNET, or any permission that isn't on the allowlist below.
# The manifest removes INTERNET, but a library could add other permissions through manifest
# merging, so we check the final artifact rather than the source manifest.
#
# Takes one or more APKs and fails if any of them asks for something it shouldn't. The Play bundle
# is checked by passing every APK bundletool generates from it (base, the config splits and the
# asset pack), since any of those manifests could carry a permission the base one doesn't.
#
# Usage: scripts/check-apk-permissions.sh path/to/app.apk [more.apk ...]
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 path/to/app.apk [more.apk ...]" >&2
  exit 2
fi

# Permissions the app may hold. Everything else fails the build; add entries deliberately, with a
# reason, in the same change that needs them.
allowed=(
  # WorkManager: background indexing and rescheduling after reboot.
  "android.permission.WAKE_LOCK"
  "android.permission.RECEIVE_BOOT_COMPLETED"
  "android.permission.FOREGROUND_SERVICE"
  # Indexing started from the app runs as a foreground job, so Android doesn't throttle it.
  "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
  # Progress notifications for indexing and describing stickers.
  "android.permission.POST_NOTIFICATIONS"
  # WorkManager declares this for network constraints. It only reads connectivity state; it can't
  # open sockets without INTERNET.
  "android.permission.ACCESS_NETWORK_STATE"
  # androidx.core: protects the app's own non-exported dynamic receivers.
  "com.eyal98.stickerfinder.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
)

if [[ -n "${AAPT2:-}" ]]; then
  aapt2="$AAPT2"
else
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$aapt2" || ! -x "$aapt2" ]]; then
  echo "error: aapt2 not found; set ANDROID_HOME or AAPT2" >&2
  exit 2
fi

status=0

for apk in "$@"; do
  dump="$("$aapt2" dump permissions "$apk")"
  if ! grep -q '^package:' <<<"$dump"; then
    echo "error: unexpected aapt2 output for $apk:" >&2
    echo "$dump" >&2
    exit 2
  fi

  requested="$(grep -E '^uses-permission' <<<"$dump" | sed -E "s/.*name='([^']+)'.*/\1/" | sort -u || true)"

  echo "Permissions requested by $apk:"
  echo "${requested:-  (none)}" | sed 's/^/  /'

  while IFS= read -r perm; do
    [[ -z "$perm" ]] && continue
    if [[ "$perm" == "android.permission.INTERNET" ]]; then
      echo "::error::$apk requests INTERNET. The app must never have network access." >&2
      status=1
    elif ! printf '%s\n' "${allowed[@]}" | grep -qxF "$perm"; then
      echo "::error::$apk requests $perm, which is not on the allowlist in $0." >&2
      status=1
    fi
  done <<<"$requested"
done

if [[ $status -eq 0 ]]; then
  echo "OK: no network access and no unexpected permissions in $# APK(s)."
fi
exit $status
