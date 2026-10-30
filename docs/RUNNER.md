# waymarker-lab-01 build runner

**Status: proposed only; not installed or registered by this repository.**

Use the existing Pi as a dedicated, **non-production**, self-hosted Android build runner only after checking OS/architecture, RAM, free disk, Java/Gradle, Android SDK command-line tools, ADB state, and outbound GitHub access.

## Recommended separation
- Self-hosted runner account `tesserae-ci` with no blanket sudo and no deployment credentials.
- Android build jobs run without device access. Signing and APK installation remain manual approval steps.
- Separate manually triggered hardware-validation job (or local script) that uses `adb -s 000000260fd5b96e` only after user authorization.
- Pin action versions and dependencies; no untrusted external PR code on runner with device USB permissions.
- Use Gradle cache and Android SDK installed outside of project tree; avoid SDK installs on every build.
- Do **not** register runner yet; verify repo Actions policy and architecture support first.

## First manual check (read-only)
```bash
uname -a
uname -m
df -h /
free -h
java -version
command -v gradle || true
command -v adb || true
env | grep -E '^ANDROID_(HOME|SDK_ROOT)=' || true
adb devices -l
```

## Delivery checkpoints
1. Gradle assembleDebug locally passes.
2. Unit tests run with no physical screen.
3. APK is installed alongside Legacy Lite; no kiosk/boot takeover.
4. Device-bound validation uses specific ADB serial, retains logs and a rollback APK.
5. Only after stability, add reproducible signed releases.
