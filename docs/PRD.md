# Product Requirements — Tesserae EPD-42S

Version: 0.1 (2026-10-30) · Status: Draft for implementation

## Problem
The Avalue EPD-42S supports a working Android 5.1.1 framebuffer, but the vendor OS has unstable system rotation and an obsolete app environment. TRMNL compatibility can show images but does not implement Tesserae's native identity, frame change detection, scheduling, and event-driven delivery.

## Product outcome
A signage appliance that starts after boot, joins a Tesserae server without copying API tokens, displays correctly oriented dashboards, stays responsive to Tesserae Push, and recovers from connectivity failures without losing the last good image.

## Target hardware
i.MX7 / Android 5.1.1 / 2880×2160 landscape framebuffer / ethernet / 16 grey levels (validate device rendering). Mechanical mounting may be portrait. Accelerometer and gyroscope are not dependable (kernel probe failures).

## P0 / MVP
1. HTTPS server URL enrollment; server auto-discovery is a *convenience*, not required.
2. Registration A: POST /api/v1/device/discover with device_id, kind, panel_w=2880, panel_h=2160, rotation, gamut=gray_16, format=png, MAC; show pending admin approval; honor retry_after_s; adopt returned device_id and device_token.
3. Registration B: six-digit code via X-Pairing-Code and POST /api/v1/device/register; securely persist returned device token.
4. Fetch GET /api/v1/device/{id}/frame with Bearer token; If-None-Match; respect 200/204/304; validate expected PNG and buffer dimensions; retain last good frame across 304/errors.
5. Software-render bitmap only; disabled hardware acceleration; no setRequestedOrientation, no Activity.recreate, no Android system settings changes.
6. Four orientation calibrations 0/90/180/270, with preview of physical UP and explicit save. Distinguish framebuffer rotation from dashboard composition; Tesserae server can bake rotation in its render for native frame output.
7. Explicit Refresh now; logs that omit tokens/PII; network error/backoff; connection status.
8. Track heartbeat/status with last paint outcome and follow server next_poll_s; implement only documented fields.
9. Preserve existing TRMNL Legacy Lite install for rollback.

## P1
- MQTT transport (retained frame topic) for low-latency server Push, with HTTPS fallback and reconnect.
- Boot autostart and robust foreground/always-on operation.
- Touch interactions mapped to Tesserae protocol v2; only if touch hardware/input mapping validated.
- mDNS NSD discovery where LAN multicast is actually reachable.
- Device-managed reconnection, token-loss recovery, OTA APK update with signature verification.

## P2
- Deck cache, offline frame persistence, diagnostics export, configurable ghosting/full-refresh policies only after OEM-specific validation.

## Acceptance criteria
- A fresh install pairs by both paths on real Tesserae; no key manually copied.
- After approval, an image is displayed at target resolution and grayscale; no SurfaceFlinger crash.
- A server push results in one new paint, never another paint for a 304.
- Portrait dashboards are legible and upright while Android orientation stays 0.
- Network outage retains last image; recovery occurs automatically; no token in logs.
- First release installation leaves original app/OS recoverable.

## Non-goals
Custom Android 9 firmware; OS-level rotation; modifying e-ink kernel drivers; automatic sensing from broken motion sensor drivers; server-side Tesserae UI changes.

## Dependencies & risks
- Upstream device kind: a compatible generic kind must be present. The maintainer offered to add a specific kind; use generic only if it actually renders 2880×2160 gray_16 PNG.
- Proxy-generated render URLs may be HTTP; trusted scheme handling and signed asset retrieval must be tested through Cloudflare/Caddy.
- Native frame envelope's rotate hint differs from registration rotation; never blindly double-rotate.
- E-ink ghosting and OEM update driver characteristics require hardware validation.
- GitHub self-hosted runner is untrusted code execution; isolate user, credentials, and hardware access.
