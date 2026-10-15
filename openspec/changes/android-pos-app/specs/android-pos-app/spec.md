## Purpose

The Android counter app: it runs the cashier web app full-screen on a tablet and gives it access to the device.
Printing and offline selling plug into it in later changes.

## ADDED Requirements

### Requirement: Runs the cashier app
The app SHALL open `<server>/cashier/` full-screen in a WebView, where `<server>` is the configured server address. The
cashier SHALL behave as in a browser: login, the staff session kept across app restarts, live order updates and
taking orders. Links to the same origin SHALL stay in the app. `tel:` links SHALL open the phone dialer. Links to
other origins SHALL open in the system browser.

#### Scenario: Cashier works in the app
- **WHEN** a cashier opens the app on a tablet and logs in
- **THEN** the counter screen opens and the cashier can take and send an order, as in the browser

#### Scenario: Session survives a restart
- **WHEN** the cashier closes and reopens the app
- **THEN** the cashier is still logged in

#### Scenario: Tap to call
- **WHEN** the cashier taps a phone order's phone number
- **THEN** the dialer opens with that number

### Requirement: Server address setup
The app SHALL keep the server address on the device. It SHALL default to production. A setup screen SHALL let the user
change it, test it, and see the app version. The setup screen SHALL open on first launch and after a 3-second long
press in the top-left corner. Debug builds SHALL accept `http://` addresses. Release builds SHALL accept only `https://`.

#### Scenario: Test the connection
- **WHEN** the user enters an address and taps "Test connection"
- **THEN** the app shows whether the server answered, or the error

#### Scenario: Point at the laptop
- **WHEN** a debug build is set to `http://<laptop-ip>:5178` and saved
- **THEN** the app loads the cashier from the laptop's dev server

#### Scenario: Plain HTTP in a release build
- **WHEN** a release build is given an `http://` address
- **THEN** the setup screen refuses it

### Requirement: Counter mode
The app SHALL stay in landscape and keep the screen on. It SHALL hide the system bars. Back SHALL navigate back inside
the cashier and SHALL never close the app.

#### Scenario: Back on the first page
- **WHEN** the cashier presses Back on the cashier's first page
- **THEN** the app stays open on that page

### Requirement: Offline banner
When the cashier page can't load, the app SHALL show a banner with a Retry button over the page. It SHALL reload by
itself when the network comes back.

#### Scenario: No network at start
- **WHEN** the app starts with Wi-Fi off
- **THEN** the offline banner shows, and when Wi-Fi comes back the cashier loads without a tap

### Requirement: Device API
The app SHALL expose `window.AmadyaDevice` to pages from the configured server origin only, with:
- `info()`: the app version, the device model, the Android version and the list of printers, empty for now;
- `print(text)`: in this change, shows the text in a preview dialog and reports that it was a preview.

#### Scenario: Web page asks for device info
- **WHEN** the cashier page calls `AmadyaDevice.info()`
- **THEN** it gets the app version, the device model, the Android version and an empty printer list

#### Scenario: Print preview
- **WHEN** the page calls `AmadyaDevice.print("#B-042")`
- **THEN** a dialog shows the text in a monospace font, and the call returns `ok` with `preview: true`

#### Scenario: Other origins get nothing
- **WHEN** a page from another origin is open
- **THEN** `window.AmadyaDevice` is not available to it
