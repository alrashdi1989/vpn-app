# Free VPN (Android)

A small Android app that lists free public VPN servers from
[VPN Gate](https://www.vpngate.net) (University of Tsukuba, Japan), sorted best
first, with country search and flags. Tap a server → **Connect** opens it in the
free, open-source **OpenVPN for Android** app, which handles the actual tunnel.

- Android 10 or newer
- No accounts, no ads, no tracking

## Get the APK (no computer setup needed)

1. Create a free account at https://github.com and make a **new repository**
   (e.g. `FreeVPN`, public or private).
2. On the repo page choose **Add file → Upload files** and upload everything in
   this folder, **including the hidden `.github` folder** (it contains the build
   script). Easiest from a computer: unzip, then drag the whole folder's
   contents into the upload page. Commit.
3. Open the **Actions** tab. The "Build APK" job starts automatically and takes
   about 3–5 minutes. (If Actions asks you to enable workflows, click enable,
   then "Run workflow".)
4. When it shows a green check, open **Releases → Free VPN (latest build)** and
   download `FreeVPN.apk` on your phone.

## Install and use

1. Open `FreeVPN.apk`; allow "Install unknown apps" for your browser/files app
   when Android asks.
2. Install **OpenVPN for Android** (`de.blinkt.openvpn`) from Google Play or
   F-Droid — the app offers a link if it's missing.
3. In Free VPN, tap a server → **Connect** → import in OpenVPN for Android →
   tap the profile → accept the VPN request.
4. If asked for a login: username `vpn`, password `vpn`.

Servers are run by volunteers and change constantly; pull down to refresh and
try another one if a server doesn't connect.

**Privacy:** volunteer servers can see unencrypted traffic and VPN Gate keeps
connection logs. Fine for unblocking content; use HTTPS and avoid sensitive
activity. VPN rules differ by country — check what applies where you are.

## Project layout

- `app/src/main/java/com/freevpn/app/VpnGateApi.kt` – downloads and parses the server list
- `app/src/main/java/com/freevpn/app/MainActivity.kt` – list screen, search, connect/save
- `.github/workflows/build.yml` – builds the APK on GitHub and publishes it as a release
