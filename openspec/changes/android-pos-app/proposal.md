# Proposal

## Why

The counter has to print over Bluetooth or USB: the bon fiscal on the fiscal printer (AMEF) and order slips on a
thermal printer. Later it should keep selling when the internet drops. A browser can't do either:
- Web Bluetooth only speaks BLE. Most ESC/POS printers and AMEFs use Bluetooth Classic (SPP), USB or serial.
- A web page can't keep working reliably without the server.

ADR 0003 plans a Kotlin print bridge and mentions an Android build later. The owner chose to start there, step by step:
- **Android only.** No iOS is planned. iOS can't reach most of these printers anyway.
- **The browser cashier app stays as it is.** It presents well and keeps working on any PC or tablet.
- **One scope at a time.** This first change is only the Android app that runs the cashier. Printing comes next.

## What Changes

- **New Android app "Amadya POS"** in a new Gradle project `android-pos/` (Kotlin, min SDK 26):
  - It opens the existing cashier app (`/cashier`) full-screen in a WebView. No UI is rewritten.
  - **Server address** is a setting. The default is production. A local address on the same Wi-Fi is allowed for
    testing against the laptop.
  - **Setup screen**, shown on first launch and by a long press in the top-left corner, with:
    - the server address;
    - a "Test connection" button;
    - the app version.
  - **Counter mode:** landscape, the screen stays on, and Back never leaves the cashier.
  - **Offline banner** with Retry when the page can't load.
  - **Device API stub:** `window.AmadyaDevice` with `info()` and `print(text)`. In this change `print` only shows the
    text in a preview dialog. The next change sends it to a real printer behind the same call.
- **Install guide** `android-pos/README.md`: by USB cable or Wi-Fi (wireless debugging), or by copying the APK to the
  tablet.
- **ADR 0003 note:** the Android app is the first host of the print bridge.

## Capabilities

### New Capabilities
- `android-pos-app`: the Android counter app: what it loads, its setup, counter-mode behavior, the offline banner and the
  Device API stub.

### Modified Capabilities
None.

## Impact

- **New:** `android-pos/` (Gradle wrapper, one `app` module, README).
- **Docs:** a note in `docs/adr/0003-print-bridge.md`, and a row for the Android app in the README apps table.
- **No changes** to the backend, the API contract, the cashier web app, Docker images, Caddy or the release workflow.
- **Laptop setup** for whoever builds it: the Android SDK command-line and platform tools (or Android Studio).

## Out of scope

Each item below is a later, separate change, in this order:
1. Bluetooth/USB ESC/POS printing of a counter slip through `AmadyaDevice.print`.
2. Fiscal receipts on the AMEF (Datecs first, ADR 0003).
3. The tablet as the restaurant's print bridge for kitchen tickets (outbound WSS, ADR 0003).
4. Offline counter selling with sync.

Also out of scope:
- iOS.
- Publishing on Google Play, and signed release builds. Debug APKs are enough for testing.
- Full kiosk lock (device-owner mode). Back is blocked inside the app, but Home still works.
- Cash drawer and card terminal.

## Decisions for the owner

Each decision lists the recommended option, which this change assumes.

1. **Where the cashier UI comes from.** *Recommended: loaded from the server* (`https://amadya.alphatech.ro/cashier/`).
   Updates to the cashier reach the tablet with a normal deploy, with no new APK. Bundling the UI inside the APK only
   matters for offline selling, so it waits for that change.
2. **Default server address.** *Recommended: production*, changeable on the setup screen.
3. **Testing against the laptop.** *Recommended: plain HTTP to the laptop, in debug builds only.* There are two ways:
   - **The local stack:** set `AMADYA_LAN_SITE=http://<laptop-ip>` in `.env`, then the server address is
     `http://<laptop-ip>`.
   - **The Vite dev server, for live edits:** run `pnpm --filter @amadya/cashier dev --host`, then the address is
     `http://<laptop-ip>:5178`.

   Release builds stay HTTPS only.
4. **Opening the setup screen.** *Recommended: a 3-second long press in the top-left corner.* A PIN can be added when
   the app reaches real counters.
5. **Package name.** *Recommended: `ro.amadya.pos`*, matching the backend's `ro.amadya` packages.
