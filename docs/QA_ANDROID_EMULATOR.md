# QA Android emulator

The shared Windows project workspace contains a local Android SDK and a Pixel 6
emulator profile at `.qa-android/`. The profile uses Android 14 (API 34), a Google
Play x86_64 image with arm64-v8a translation, 4 GB RAM, and a hardware keyboard.
Google Play services are present. Windows Hypervisor Platform acceleration was
verified on this host.

From PowerShell in the project root:

```powershell
.\scripts\qa-android-emulator.ps1 start
.\scripts\qa-android-emulator.ps1 status
.\scripts\qa-android-emulator.ps1 stop
```

Use `start -Headless` when no desktop is available. The SDK tools are at
`.qa-android/sdk/platform-tools/adb.exe` and `.qa-android/sdk/emulator/emulator.exe`.
The AVD data is at `.qa-android/avd/PeelIt_API34.avd`. A first boot can take a few
minutes. `status` reports `boot_completed=1` when Android is ready.

English is the initial system language. For Hebrew checks, use Android **Settings
→ System → Languages → System languages → Add a language → עברית**. Keep English
in the list, and move either language to the top for that test. The keyboard is
enabled in the emulator profile; enable the Peel-It input method under Android
Settings after installing the release candidate.

Only synthetic test fixtures may be copied into the emulator. Do not use a real
WhatsApp export, personal sticker library, or personal photos. Wipe the emulator
user data before another tester uses it if any test data needs to be cleared.
