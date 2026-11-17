# 3.2.9 progress

versionCode 21, versionName `3.2.9-shrine`. Same package and signing key as 3.2.8.

## What Free My TV actually does

Read from `simbasjungles/free-my-tv` `app/src/io/freemytv/HomeMode.java`, `app/AndroidManifest.xml`, and `app/res/xml/remote_key_service.xml` (package `io.freemytv`, MIT).

It does not catch KEYCODE_HOME. The home activity has no intent-filter priority and no lockTaskMode. It is `exported`, `singleTask`, `stateNotNeeded`, `clearTaskOnLaunch`, `resumeWhilePausing`, `taskAffinity=""`, categories HOME and DEFAULT only.

Becoming Home is local ADB, in this order:

1. `cmd role get-role-holders --user 0 android.app.role.HOME`
2. `cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME`
3. `cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME` when role holders are missing
4. Refuse if Google TV setup (`com.google.android.tungsten.setupwraith`) or the current home package saved the Wi-Fi (`dumpsys wifi` `cname=`)
5. `cmd package set-home-activity <pkg>/.HomeActivity` and require `Success`
6. `pm disable-user --user 0` for, in order: setup only when it answers HOME, the current home package, `com.google.android.apps.tv.launcherx` if it is a different package, `com.google.android.tvrecommendations` only when the home package is `com.google.android.tvlauncher`, then (on the Shield, assistant is turned off too) `com.google.android.katniss` and `com.android.hotwordenrollment.okgoogle`
7. If the holder is still not us, `cmd package set-home-activity` again
8. Undo is `pm enable <pkg>` (`new state: enabled`). Setup is enabled last
9. Original mode re-enables those packages, so this flow has to run after Free My TV is set back to Original

The accessibility service only filters remote app-button keys (`canRequestFilterKeyEvents`). It is not how Home is taken. The screen blink is `am start -W` timing the home screen three times. A failed measurement does not undo the switch, so 3.2.9 does not copy that blink.

## Done

- Mechanism above, recorded so it is not only in the chat
- `HomeSwitch` runs the commands: set-home-activity, role, disable-user, enable, resolve. It stops when Free My TV still holds Home, and when a package saved the Wi-Fi
- Default home screen, English and Arabic: the two-step order, confirm before turning a package off, undo, the one-line warning, the accessibility row, and an offer if a package we turned off is back on
- `MainActivity` uses `resumeWhilePausing` and an empty `taskAffinity`. About names Free My TV’s MIT licence

## Done since the screen landed

- Prayer line is calculated in the dream when the cache is missing or stored as clock text. The system clock dream shows it too, and a photo dream shows it with the clock overlay off.
- Settings and prayer prefs survive a value saved as the wrong type. The widget host’s listen/stop can no longer take the process down. An old `prayer` or JSON widget file becomes `native:prayer`.

## QA so far

- System clock dream (`service call dreams 1`, component `com.gathin.tvhome/.ShrineDream`, mode clock, no prayer-line.json): screenshot `/opt/cursor/artifacts/screensaver-prayer-clock.png` shows `Asr · 14:31 · in 39 min` under the clock. No FATAL lines.
- Slides capture in that same script was a 10 KB black frame (dream had not drawn yet after force-stop). Retry after the rest of QA, still backgrounded.
- Debug APKs built: `/tmp/apk-3.2.7.apk` (versionCode 19), `/tmp/apk-3.2.8.apk` (versionCode 20).
- Settings walk, monkey, upgrade install, and stock-launcher flow are in `/tmp/qa-rest.sh` (log `/tmp/qa-rest.log`).

## QA so far (continued)

- Upgrade uninstall → 3.2.7 debug → prefs (Arabic, 5 columns, `native:prayer`, screensaver prayer on) → 3.2.8 → 3.2.9 debug. Widget file, columns, locale, and screensaver prefs were still there. Launcher settings opened with no FATAL.
- Stock launcher: `pm enable com.google.android.tvlauncher` made HOME resolve to `com.google.android.tvlauncher/.MainActivity`. `pm disable-user --user 0` plus `cmd package set-home-activity com.gathin.tvhome/.MainActivity` made HOME resolve back to Alkhamis. `pm enable` undo gave the stock launcher Home again. That is the Shield behaviour.
- Monkey without `-c` aborted (no `LAUNCHER` activity). A 15-event smoke with `-c android.intent.category.LEANBACK_LAUNCHER` finished. Full 3000-event EN and AR runs are in `/tmp/monkey-qa.sh`.
- Signed candidate: `/opt/cursor/artifacts/CleanYourTV-Shrine.apk` versionCode 21, sha256 `2552ccbd7225d2d356ece340b2677a57d8c438e5ecac678f38fffe11578c8948`. Rebuild if the monkey finds a crash.

## QA so far (continued)

- Slides dream screenshot `/opt/cursor/artifacts/screensaver-prayer-slides.png` shows the shrine photo, the clock, and `Asr · 2:31 PM · in 27 min`.
- English monkey with `-c android.intent.category.LEANBACK_LAUNCHER --ignore-timeouts` injected 3000 events and finished. No `FATAL EXCEPTION` and no `CRASH` in its log. The earlier abort was the emulator timing out its own `meminfo` dump, which made monkey stop; those ANRs were “no focused window” while the emulator was stalled, not a Java crash.
- Arabic 3000-event monkey is running (`/tmp/monkey-ar2.log`).

## QA so far (continued)

- Arabic monkey also injected 3000 events and finished. No `CRASH` in that log and no `FATAL EXCEPTION` in logcat afterwards.
- Delivery APK is signed. Debug-only activity exports used for QA are not in the release and are not committed.

## Delivery

- `/opt/cursor/artifacts/CleanYourTV-Shrine.apk` versionCode 21, `3.2.9-shrine`
- sha256 `2552ccbd7225d2d356ece340b2677a57d8c438e5ecac678f38fffe11578c8948`
- Screenshots: `settings-with-widget-ar.png`, `screensaver-prayer-clock.png`, `screensaver-prayer-slides.png`, `default-home-ar.png`
