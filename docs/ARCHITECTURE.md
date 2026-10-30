# Architecture and protocol decisions

Authoritative contract: https://docs.tesserae.ink/dev/client-protocol/

## Layers
- `NativeTesseraeClient`: HTTP discovery, pairing, frame descriptor fetch, optional frame download; no Android imports.
- `DeviceSettings`: local server, device identity, token, orientation.
- `MainActivity`: simple on-device setup, status, software Bitmap rendering. No system rotation.
- Future `MqttFrameNotifier`: subscribes to Tesserae frame topic and triggers the same frame retrieval code.

## Registration
Zero-touch `POST /api/v1/device/discover`, with `device_id` matching `^[a-z][a-z0-9_-]{1,31}$`, genuine MAC, kind `circuitpython_generic` as provisional test kind, dimensions 2880×2160, `rotation` [0,90,180,270], `gamut=gray_16`, `format=png`. When `registered=false`, honor `retry_after_s`; when true, save token and authoritative id.

Pair code: `POST /api/v1/device/register` with `X-Pairing-Code`, same identity. Code issued in Tesserae Settings → Devices; never show token.

Orientation and calibration are exclusively Tesserae server responsibilities. The Android client has no orientation UX or mutable orientation setting. Initial discovery reports rotation=0 as a protocol baseline; never attempt to override subsequent server-side configuration.

## Frame and presentation
Use `Authorization: Bearer <token>`, optionally `If-None-Match: <etag>` for `GET /api/v1/device/{id}/frame`. 200 JSON includes `url`, `format`, `render_id`, `panel_w/h`, optionally `native_w/h`, and potentially `rotate` for PNG. 204 = not ready; 304 = unchanged, *but a fresh process without the cached bitmap must not suppress its first paint*. Retrieve artifact from trusted same-host HTTPS URL, reject unsupported formats, decode PNG and display via software Android Views. Do not rotate twice: Tesserae registration rotation may be baked into the delivered frame; verify `native_w/h` and `rotate` on hardware before client transforms.

## Security
- HTTPS only for remote servers; never globally disable TLS verification.
- Reject cross-host frame URLs by default; allow HTTP-to-HTTPS upgrade only for the configured server host.
- Device token in private app storage; never log credentials.
- Protect code and image download from redirects to untrusted hosts.
- Registration endpoints rate-limited; avoid aggressive retries.

## Verification plan
- Mock discovery false→true and pairing success/error.
- Frame 200→304→200, 204, 401, 403, 500 and expired image URL.
- 2880×2160 PNG 4-bit grayscale memory and decode.
- Avalue screen landscape, physical portrait, app resume, WAN outage, device reboot.
- Confirm vendor firmware recovery/ADB before installing APK.
