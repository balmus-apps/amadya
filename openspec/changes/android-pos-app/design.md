## Context

- **The cashier web app** (`frontend/apps/cashier`) is Vite + React, built with `base: "/cashier/"`. Caddy serves it on
  the same origin as the API (`infra/caddy/Caddyfile`), and `@amadya/api-client` calls the relative `/api/v1`. A WebView
  that opens `https://<server>/cashier/` works unchanged.
- **Staff login** (`@amadya/staff-auth`) keeps the access token in memory and the refresh token in `localStorage`. The
  WebView needs DOM storage on, and its storage survives app restarts, so the cashier stays logged in like in a browser.
- **The live feed** uses fetch-based SSE (`apps/cashier/src/lib/feed.ts`). A WebView supports it as Chrome does.
- **The dev server** proxies `/api` to `localhost:8080` and listens on localhost only. `--host` exposes it on the
  Wi-Fi.
- **Print bridge** (ADR 0003): a Kotlin agent with ESC/POS and fiscal drivers. Nothing is built. This app becomes its
  first host in later changes.
- **The laptop** has Java (Homebrew OpenJDK 25) but no Android SDK or `adb`.

## Goals / Non-Goals

**Goals:**
- A tablet can install the app and work the counter exactly as in the browser.
- The app has a stable place (`window.AmadyaDevice`) where printing plugs in next, with no change to the call the web
  app makes.
- Builds and installs from the command line, with no Android Studio required.

**Non-Goals:**
- Printing, fiscal, offline selling, iOS, Google Play, device-owner kiosk mode.
- Any change to the cashier web app, the backend or deploys.

## Decisions

1. **WebView shell, not a native UI.** One cashier UI for browser and tablet. The native side only does what the
   browser can't: hardware now, offline storage later.
2. **Plain Android Views, no Jetpack Compose.** The app has two screens: the WebView activity and the setup activity.
   Views keep the build small and fast.
3. **Toolchain:**
   - Gradle wrapper in `android-pos/`, with the Kotlin DSL, like `backend/`;
   - Android Gradle Plugin and Kotlin at current stable versions, compile and target SDK at the latest stable level,
     min SDK 26 (Android 8);
   - JDK 17 or newer to build.

   `android-pos/` stays out of the frontend pnpm workspace and the backend Gradle build. CI does not build it in this
   change.
4. **Server address in `SharedPreferences`.**
   - The default `https://amadya.alphatech.ro` comes from a `BuildConfig` field.
   - The app always opens `<server>/cashier/`.
   - The setup screen checks the address with `GET <server>/api/v1/settings/public` and shows OK or the error.
5. **HTTP only in debug.** A debug-only network security config allows cleartext, so the tablet can reach
   `http://<laptop-ip>:5178`. Release builds are HTTPS only.
6. **Navigation stays inside the server.**
   - Links to the same origin open in the WebView.
   - `tel:` links (the tap-to-call on phone orders) open the dialer.
   - Any other origin opens in the system browser.
7. **Counter mode:**
   - `screenOrientation="sensorLandscape"` and `FLAG_KEEP_SCREEN_ON`;
   - full-screen immersive mode;
   - Back goes back in WebView history and never closes the activity.
8. **Offline banner.**
   - A main-frame load error shows a native banner over the WebView, with Retry.
   - A connectivity callback retries automatically when the network returns.
   - The cashier's own live/offline indicator still handles drops after the page has loaded.
9. **Device API stub** via `addJavascriptInterface(..., "AmadyaDevice")`, exposed only to pages from the configured
   server origin:
   - `info()` returns JSON with `appVersion`, `model`, `androidVersion` and `printers: []`.
   - `print(text)` shows the text in a monospace preview dialog and returns `{"ok":true,"preview":true}`.

   Later changes keep these names. `print` will take the printer-neutral document from ADR 0003 alongside plain text.
10. **Setup screen access:** a 3-second long press on an invisible 48 dp area in the top-left corner, and first launch.

## Risks / Trade-offs

- **The WebView depends on the tablet's Android System WebView version.** Old tablets with an outdated WebView may
  fail on modern JS. Mitigation: min SDK 26, and the README says to update "Android System WebView" from Play.
- **Home still leaves the app.** Full lock needs device-owner provisioning. It is deferred until a real counter device
  is chosen.
- **A JavaScript interface exposed to the wrong page is a risk.** It is exposed only on the configured origin, and it
  does nothing harmful in this change.

## Migration Plan

Nothing to migrate. The browser cashier keeps working. To roll back, uninstall the app.

## Open Questions

- The test tablet's model and Android version. If it is older than Android 8, min SDK changes.
