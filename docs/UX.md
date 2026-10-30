# UX — Tesserae EPD-42S

## Principle
This is a dedicated e-ink appliance, not a miniature Android tablet. Display the dashboard by default, keep configuration accessible by deliberate touch or ADB, and avoid unnecessary redraws.

## Flow 1: first boot
1. Welcome: “Connect to Tesserae”. Discover server on LAN (optional mDNS), or enter HTTPS URL.
2. Choose registration: **Find this display in Tesserae** (default), or **I have a pairing code**.
3. Display identity and capability summary: Avalue EPD-42S, 2880×2160, 16 grey levels, MAC (masked in normal UI).
4. Orientation calibration: show a conspicuous UP marker and select 0°, 90°, 180°, or 270°. Default 0° until user sets it.
5. Discover sends identity; waiting screen “Approve this display in Tesserae → Settings → Devices”; periodically retry per retry_after_s.
6. Pairing code sends code once; show expired/invalid error with retry, not an infinite loop.
7. On receipt of token: display “Connected”; begin native frame retrieval. If no dashboard assigned (204), show waiting state.

## Flow 2: steady state
- Full-bleed dashboard, no status overlay in normal operation.
- 200 with new frame: fetch PNG, validate, render, remember ETag.
- 304: keep image; never repaint.
- 204: preserve last image; if nothing painted yet show “Waiting for first dashboard”.
- Network failure: retain last image, back off and retry; diagnostics available via settings.

## Flow 3: settings
Deliberate screen tap/hold opens minimal settings:
- Connection (server, device id, registered/unregistered).
- Transport: REST (now), MQTT (later).
- Orientation: visual UP calibration.
- Refresh now; network diagnostics.
- Reset registration (explicit confirmation; clears local token only).

## Flow 4: Tesserae Push
With REST: next poll discovers changed ETag. With MQTT: retained frame announcement triggers authenticated frame fetch, then unchanged-frame suppression. Both paths share rendering code.

## Exceptional states
- Token rejected (401): show reconnect screen; do not spam pairing requests.
- Wrong device ID (403): show identity diagnostic and recovery.
- Bad frame / unexpected dimensions: keep last good bitmap.
- Display services restart: recreate app without forcing global orientation.
- mDNS blocked by Docker/VLAN: manual URL always available.
