# 3.2.10 progress

versionCode 22, versionName `3.2.10-shrine`. Package `com.gathin.tvhome`. Signed with the shrine key.

Certificate SHA-256: `72c1b4accbce14120ce54f15605cdebc8307a13f1929ed509516a22b5f7a65a8`

APK SHA-256: `802ba56cd4c9f064a6705c0a5a2d879248f69a531cfbe9eb852dcaa823f1b47d`

## Emulator, Android TV 34

- `adb tcpip 5555` replied `restarting in TCP mode port: 5555`. `netstat` showed port 5555 listening before and after.
- Picking Alkhamis opened the system prompt "Allow USB debugging?" with an RSA fingerprint. No Conscrypt crash. After Allow, the picker check moved to Alkhamis Launcher. Before the switch, HOME was `com.google.android.tvlauncher/.MainActivity`. After a reboot the screen that came up was this launcher.
- Accepting that key drops the emulator's ADB bridge, so `pm list packages -d` and the Original row could not be driven on that same boot.
- `cmd package query-activities` for `LEANBACK_LAUNCHER` on this package finds one activity, `com.gathin.tvhome/.MainActivity`. `aapt dump badging` on the signed APK reports `application-label` and `application-label-ar` as Alkhamis Launcher, and one leanback activity, MainActivity.
- Settings screens opened in English and Arabic, including the Home Screen picker. No fatal exception in that walk.
- Monkey, 1000 events, English then Arabic, exit 0. No FATAL EXCEPTION lines.

## Delivery

`/opt/cursor/artifacts/3.2.10/CleanYourTV-Shrine.apk`, `SHA256.txt`, and the screenshots in that folder.
