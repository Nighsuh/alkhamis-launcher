# Progress: Alkhamis Launcher 3.5.0-shrine

Port of the 3.2.10-shrine tree onto CleanYourTV v3.5.0 (`d11bd68c9ac45b2fb38480941586d17930137e9d`).

## Done

- Upstream v3.5.0 is the tree. The shrine delta from v3.1.0 (`1684678064aa7bbb4881e6069e4cfbd4b5fc9df7`) was re-applied with a three-way merge.
- Conflict resolutions kept upstream's memory reading, glass-rectangle check, move-mode dimming, and clock format, and put the shrine dock, prayer cards, and glass-follow behaviour back.
- versionName `3.5.0-shrine`, versionCode 23. Release build is unsigned in Gradle. The shrine keystore stays outside the repo.
- All 29 upstream themes are in `themes/` and bundled under `launcher/app/src/main/assets/upstream-themes/`, with English and Arabic names in `theme-catalog.json`. Shrine stays the default, and switching back to it works.
- APK size gate raised to 40 MB. The unsigned file is `app-release-unsigned.apk`. The signed APK is about 21 MB.
- Signed APK certificate SHA-256 matches the shrine key (`72:C1:B4:AC:...:65:A8`).

## QA on Android TV emulator API 30 (`tv_1080p`, android-tv/x86)

- Installed signed 3.2.10-shrine, set columns to 5, added the prayer cards, then installed signed 3.5.0-shrine over it. versionCode 23. The prayer cards were still on Home, and the columns chip stayed selected on 5.
- Home in English and Arabic, settings in both languages, theme picker in both languages (each row shows both names, UI language first).
- Applied Aurora, Istanbul, and Twilight, then switched back to shrine. Each home screenshot shows that wallpaper. The prayer cards stayed.
- Home Screen picker lists Alkhamis Launcher and Original. Choosing Alkhamis opened the network-debugging popup because this image does not listen on 127.0.0.1:5555 (including after `adb tcpip 5555`, which only restarts the host bridge). The popup is bilingual and offers Take me there, Show steps only, and Test connection. Free My TV was not touched.
- Dream preview (opened from Screensaver, not the system dream) shows the clock and the prayer line: `Asr · 2:28 PM · in 6 min`.
- Monkey, 1000 events, Arabic then English. Both finished with exit 0. No `FATAL EXCEPTION` for `com.gathin.tvhome`.
- Memory after eight column changes: 3.2.10 TOTAL PSS 43,072 KB (that session still had 8 activities). 3.5.0 TOTAL PSS 50,492 KB with 2 activities. The extra is about 7 MB, in line with the shrine wallpaper and the prayer cards, not a growing activity leak.

## 3.5.1-shrine

versionCode 24, same key and package, on the same branch.

- Long-press on the top-bar Settings shortcut and on the Settings dock tile opens the menu with Alkhamis Launcher settings first and focused. Checked in English and Arabic.
- Arabic theme wording is ثيم / ثيمات. The Arabic theme picker title is الثيمات, and the shrine row is استخدام ثيم الضريح.
- Six dummy leanback activities were installed on the API 30 emulator. With four columns, Down moves the page: the prayer cards slide up with the dock and then leave the screen. The clock stays.
- Upgrade from 3.5.0-shrine to 3.5.1-shrine installed as versionCode 24.
- Monkey, 500 events, Arabic: exit 0, no fatal exception for this package.

## Not done

- API 34 system image was not installed. API 30 was the image that finished in time.
- The prayer cards did not scroll off the page on this image. Only three leanback apps are installed, so they all sit on the dock and Down does not move the grid. The cards are inside the grid and survived the upgrade and the theme changes.
- A real system dream was not started. The in-app preview shows the prayer line. Starting `Somnambulator` has killed earlier runs, so that step was left out after the preview was confirmed.
