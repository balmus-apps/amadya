# Amadya POS (Android)

The counter app for an Android tablet. It opens the cashier web app (`/cashier`) full-screen and gives it access to the
device through `window.AmadyaDevice`. In this version, `print` only shows a preview. Bluetooth/USB printing comes in
the next change (OpenSpec `android-pos-app`, ADR 0003).

- **Server address:** production (`https://amadya.alphatech.ro`) by default. You can change it on the setup screen.
- **Setup screen:** it opens on first launch. Afterwards, press and hold the **top-left corner** of the cashier for
  3 seconds.
- **Counter mode:** landscape, the screen stays on, the system bars are hidden, and Back never leaves the cashier.
- **No connection:** a full-screen notice with **Retry**. The app also reloads by itself when the network comes back.

## 1. Tools on the Mac (once)

```bash
brew install --cask android-commandlinetools android-platform-tools
yes | sdkmanager --licenses
sdkmanager "platforms;android-37.0" "platform-tools"
```

Gradle needs to know where the SDK is. Do one of these:
- add `export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` to `~/.zshrc`;
- or create `android-pos/local.properties` with `sdk.dir=/opt/homebrew/share/android-commandlinetools`. Git ignores
  this file.

Java 17 or newer is required. The Homebrew `openjdk` works.

## 2. Build

```bash
cd android-pos
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # URL and origin rules
./gradlew lintDebug
```

The debug build is named **Amadya POS** and has the ID `ro.amadya.pos.debug`. It accepts `http://` addresses, so it
can reach the laptop. A release build (`assembleRelease`) accepts only `https://`.

## 3. Put it on the tablet

First enable Developer options on the tablet, once: go to **Settings → About tablet** and tap **Build number** 7 times.

### By USB cable

1. On the tablet, go to **Settings → Developer options** and turn on **USB debugging**.
2. Connect the cable, then accept the "Allow USB debugging?" prompt on the tablet.
3. On the Mac, run:

   ```bash
   adb devices            # the tablet is listed as "device"
   ./gradlew installDebug
   ```

### By Wi-Fi with wireless debugging (Android 11 and newer)

The tablet and the Mac must be on the same Wi-Fi.
1. On the tablet, go to **Developer options → Wireless debugging**, turn it on, then tap **Pair device with pairing code**.
2. On the Mac, pair using the IP, port and code shown on the tablet:

   ```bash
   adb pair 192.168.1.50:37123      # the pairing IP:port, then type the 6-digit code
   ```

3. Connect using the IP and port on the main **Wireless debugging** screen. It is a different port from pairing:

   ```bash
   adb connect 192.168.1.50:41234
   ./gradlew installDebug
   ```

Pairing is needed only once. After that, `adb connect` is enough.

### By Wi-Fi, downloading the APK

Use this when debugging can't be enabled on the tablet.

```bash
cd android-pos/app/build/outputs/apk/debug
ipconfig getifaddr en0                 # the Mac's IP, e.g. 192.168.1.193
python3 -m http.server 8000
```

1. On the tablet, open `http://<mac-ip>:8000/app-debug.apk` in Chrome and download it.
2. Open the downloaded file. Allow "Install unknown apps" for Chrome when Android asks.
3. Stop the server on the Mac with Ctrl+C.

The same file can also be copied over a cable or a USB stick.

**Updating** with any of these methods keeps the app's data, including the cashier login and the server address.
**Removing** the app: `adb uninstall ro.amadya.pos.debug`, or long-press its icon and choose Uninstall.

## 4. Point it at the laptop (testing)

On the setup screen, use one of these addresses. The tablet must be on the laptop's Wi-Fi.

| What runs on the laptop | Server address on the tablet |
|---|---|
| The local stack (`docker compose up -d`), with `AMADYA_LAN_SITE=http://<laptop-ip>` in `.env`, then `docker compose up -d caddy` | `http://<laptop-ip>` |
| The cashier dev server, for live edits: `pnpm --filter @amadya/cashier dev --host` (the API on `localhost:8080`) | `http://<laptop-ip>:5178` |

To get the laptop's IP, run `ipconfig getifaddr en0`.

- **The local stack is the easier route.** The cashier and the API share one origin, as in production.
- **`AMADYA_LAN_SITE` must start with `http://`.** Without it, Caddy redirects to HTTPS, which the tablet won't trust.
- **The dev server route needs one more setting.** Vite passes the page's origin on to the API, and the API refuses
  origins it doesn't know with "Invalid CORS request" (HTTP 403). The cashier then says the password is wrong. Add
  `CORS_ALLOWED_ORIGINS=http://<laptop-ip>:5178` to `.env` and run `docker compose up -d api`.

1. Tap **Test connection**. It calls `GET /api/v1/settings/public` and should say "Conexiune OK (HTTP 200)".
2. Tap **Save**. The cashier opens at the new address.

The demo login is `admin@amadya.local` / `Admin12345!`.

## 5. Debugging

- **Console and network:** with the tablet connected through `adb`, open `chrome://inspect` in Chrome on the Mac and
  click **inspect** under Amadya POS. This works in debug builds only.
- **Device API:** try it from that console.

  ```js
  await AmadyaDevice.info()   // { appVersion, model, androidVersion, printers: [] }
  await AmadyaDevice.print("#B-042\n1 x Burger\n")   // shows the preview, resolves { ok: true, preview: true }
  ```

  `AmadyaDevice` exists only on pages from the configured server, in the main frame.
- **Blank or broken page on an older tablet:** update **Android System WebView** (or Chrome) from the Play Store.
  The Device API needs a WebView recent enough for origin-restricted messaging. Otherwise the cashier works and
  `AmadyaDevice` is simply missing.
