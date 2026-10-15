## 1. Toolchain and project

- [x] 1.1 On the Mac, install the Android SDK command-line and platform tools
      (`brew install --cask android-commandlinetools android-platform-tools`), then use `sdkmanager` to install the
      platform, build-tools and licences for the chosen compile SDK. Verify that `adb version` and `sdkmanager --list`
      work.
- [x] 1.2 Create `android-pos/`:
      - the Gradle wrapper;
      - `settings.gradle.kts`;
      - one `app` module, package `ro.amadya.pos`, min SDK 26, current stable AGP and Kotlin;
      - a `.gitignore` for `build/`, `local.properties` and `.gradle/`.

      Verify that `./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.

## 2. The app

- [x] 2.1 Add `ServerConfig`, the server address in `SharedPreferences`:
      - the `BuildConfig` default `https://amadya.alphatech.ro`;
      - validation (`https://` only in release);
      - `cashierUrl()` returns `<server>/cashier/`.

      Verify with a JVM unit test of URL validation and joining.
- [x] 2.2 Add `CashierActivity`:
      - a WebView with JavaScript and DOM storage on;
      - landscape, screen on, immersive;
      - Back in WebView history only;
      - a `WebViewClient` that keeps the same origin inside, sends `tel:` to the dialer and other origins to the browser.
- [x] 2.3 Add the offline banner: on a main-frame load error, show the banner with Retry. A `ConnectivityManager`
      callback reloads when the network returns.
- [x] 2.4 Add `SetupActivity`:
      - the address field;
      - "Test connection", which calls `GET /api/v1/settings/public` and shows OK or the error;
      - Save;
      - the app version.

      It opens on first launch and on a 3-second long press in the top-left corner. Its text is in Romanian and
      English, following the system language, with Romanian as the default.
- [x] 2.5 Add `AmadyaDevice`, a JavaScript interface:
      - `info()` and `print(text)`, the preview dialog;
      - added only while the page's origin is the configured server.

      Verify with a unit test of the origin check.
- [x] 2.6 Add a debug-only `network_security_config` that allows cleartext. Release stays HTTPS only.

## 3. Docs

- [x] 3.1 Write `android-pos/README.md`. It covers:
      - the tools to install;
      - building;
      - installing by USB cable (`./gradlew installDebug`);
      - installing by Wi-Fi (Wireless debugging, `adb pair`, `adb connect`), or copying the APK to the tablet;
      - pointing the app at the laptop with `pnpm --filter @amadya/cashier dev --host`;
      - updating Android System WebView.
- [x] 3.2 Add a row for "Amadya POS (Android)" to the README apps table, and the note in
      `docs/adr/0003-print-bridge.md` (the ADR note was added in the planning PR).

## 4. Verification on a tablet

- [ ] 4.1 Install by cable and by Wi-Fi. Then check:
      - log in as the demo cashier;
      - take and send a Cash order;
      - confirm a phone order;
      - the tap-to-call opens the dialer;
      - close and reopen the app, and the session is kept.
- [ ] 4.2 Start with Wi-Fi off: the banner shows. Turn Wi-Fi on: the cashier loads by itself.
- [ ] 4.3 Switch the server between production and the laptop dev server from the setup screen.
- [ ] 4.4 From the WebView devtools (`chrome://inspect`), check:
      - `AmadyaDevice.info()` returns the device details;
      - `AmadyaDevice.print("test")` shows the preview.

Emulator check on 2026-10-15, before the tablet. This was an Android 15 Pixel Tablet emulator with the debug build,
loading the cashier through a same-origin test proxy.
- **Worked:**
  - setup on first launch, with "Test connection" showing both OK and the error;
  - Save opened the cashier full-screen in landscape;
  - the 3-second corner hold opened setup, and the server switched;
  - login, the live feed ("Conectat"), the menu, and a cash order with change, which arrived PAID;
  - `AmadyaDevice` was present and frozen, and `info()` returned the device details.
- **Not covered, left for section 4:**
  - the print preview dialog;
  - the offline banner;
  - tap-to-call;
  - Back;
  - the session after a restart;
  - another origin getting no `AmadyaDevice`.
