# Tesserae EPD-42S

A purpose-built **Android 5.1.1 client** for the Avalue EPD-42S 42-inch e-ink panel, using the **native Tesserae protocol** rather than TRMNL compatibility.

**Status:** Early development; source scaffold and protocol client only. Not yet verified on hardware. Do not uninstall the working TRMNL Legacy Lite app.

## Goals
- Zero-touch discovery with administrator approval **or** six-digit pairing-code registration.
- Native REST frame polling, bearer authentication, ETag/304 change detection, device status.
- Future MQTT frame announcement subscription for near-immediate update after Tesserae Push.
- Full 2880×2160 software-rendered grayscale frames and Tesserae-managed portrait/landscape orientation; no app calibration UI.
- Never change Android system orientation. Never force hardware acceleration.
- Recover gracefully after network interruption; protect tokens; avoid unnecessary e-ink refreshes.

## Documents
- [Product requirements](docs/PRD.md)
- [User experience flows](docs/UX.md)
- [Architecture, transport, and testing](docs/ARCHITECTURE.md)
- [Build runner plan](docs/RUNNER.md)

## Development
Use an Android SDK and JDK 11; build with `gradle assembleDebug` (or a verified local Gradle wrapper). Min SDK 22. No AndroidX dependency.

The Android client is intentionally independent of proprietary e-ink hardware APIs: it displays decoded bitmaps through software Android Views to reuse the working OEM firmware pipeline.

Upstream: https://docs.tesserae.ink/dev/client-protocol/

## Hardware constraints
- Board: Freescale/NXP i.MX7, Android 5.1.1, 2880×2160 Android framebuffer.
- Android SurfaceFlinger has previously crashed when forcing portrait. Do not request orientation changes.
- Advertised accelerometer/gyro drivers fail Linux chip-ID probe; automatic physical orientation sensing is not reliable.
- First tests must be read-only or install alongside the existing app; preserve recovery access.

## License
No license chosen yet; verify ownership and compatibility before selecting one.
