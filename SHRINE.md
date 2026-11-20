# CleanYourTV shrine build

Personal build of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) **v3.1.0** (MIT, copyright Gathin; see [LICENSE](LICENSE)) with the Imam Hussain shrine theme included. Sideload the APK. Nothing in this build disables other packages, and it does not set itself as the default launcher.

The upstream project is unchanged in purpose. This tree vendors tag `v3.1.0` and adds the theme plus the notes below. It is not a pull request to the upstream GitHub repository.

## What you get on first launch

The launcher normally reads a theme from

`/sdcard/Android/data/com.gathin.tvhome/files/`

(`config.json` and an optional `wallpaper.jpg`), which `tools/theme.sh` writes after install. This build copies the same files out of the APK the first time Home starts and that folder has no `config.json` yet.

| Setting | Value |
|---|---|
| Wallpaper | `wallpaper.jpg`, the saved 1920×1080 shrine interior. One file. `wallpaper_rotation_minutes` is 0, and there is no `wallpapers/` collection, so it does not rotate. |
| `wallpaper_dim` | 45 |
| `dock_opacity` | 70 |
| `glass_style` | rim |
| `columns` | 6 |
| Dock order | YouTube, Shahid, Netflix, Prime Video, OSN+, Disney+ |

Package names, in that order: `com.google.android.youtube.tv`, `net.mbc.shahidTV`, `com.netflix.ninja`, `com.amazon.amazonvideo.livingroom`, `com.osn.go`, `com.disneyplus.mea`.

An app in that list that is not installed is skipped. The launcher only draws tiles for apps that are actually there, and `fill_dock` stays on (the theme does not set it, so the `config.xml` default applies). Free dock slots fill with other installed apps instead of leaving a broken icon or a hole. If none of the six are installed, the first row is just the TV's other apps.

A `config.json` that is already in the theme folder is left alone, so `tools/theme.sh` can still push a different theme later. Deleting `config.json` makes the next Home start copy the shrine theme back.

Wallpaper credit: Hassan Ehap, CC BY-SA 4.0. See `launcher/app/src/main/assets/bundled-theme/CREDITS.md`.

## What this build does not do

- **Prayer Times is not included.** The separate add-on `com.majnioui.tv.prayer` (Dammam, Aladhan method 0) is not built and not inside this APK. Upstream still has its sources under `addons/prayer-times/` if you want to build that add-on yourself.
- **No debloat.** Installing the APK does not run `pm disable-user` or any other package-disable step. `stock_home_redirect`, `power_on_home`, and `power_on_home_always` stay off. Do not use `launcher/build-and-install.sh` if you want to avoid that script's optional stock-launcher disable; install the APK on its own.
- **Not forced as the home app.** The activity is a HOME candidate, which is how Android TV lists a launcher. Nothing in the app calls the role APIs or `cmd package set-home-activity`. Pick it yourself: Settings → Apps → Default apps → Home, or the system Home picker the first time you press Home.

## Package name and signing

`applicationId` stays **`com.gathin.tvhome`**. `versionName` is `3.2-shrine`, `versionCode` 12.

Hold OK on a tile, or hold the Settings chip and choose **Featured dock**, to edit the first row. That order is stored in the app's private files, not in the theme, so the next launch does not put the shrine dock back. The same menu hides an app, opens the system uninstall dialog for apps you installed, and can delete a preinstalled app for the current user through this TV's own Network debugging (localhost only). Wireless pairing is not included. Settings, the system UI, Play services, the Play Store, and this launcher cannot be deleted that way.

A different id is a one-line Gradle property (`-PlauncherApplicationId=...`) and the Home-key accessibility service would still run, because it uses its own package at runtime. The component you enable would change, though. The project's install notes and the command that turns the helper on are written for this id:

```
settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
settings put secure accessibility_enabled 1
```

Some firmwares also only accept HOME from a package on an allowlist, and this id is the one that already ran on the Haier. Keeping it preserves that component name and those notes. The cost is the signing key.

This APK is signed with a new release key, not the upstream debug key and not the key from any build already on the TV. Android will refuse to upgrade an existing `com.gathin.tvhome` install. **Uninstall the existing CleanYourTV first**, then sideload this APK. Uninstalling clears the old app's data, including a theme that was pushed at runtime. The shrine theme is inside this APK, so the first launch puts it back. The same uninstall clears an enabled accessibility service; turn Home key access on again only if that remote needs it.

The release keystore is not in git. Its location is recorded with the build that produced `CleanYourTV-Shrine.apk`. Do not commit it.

## Build locally

Requirements: JDK 17 or newer, Android SDK 34, build-tools 34.

```bash
echo "sdk.dir=$ANDROID_HOME" > launcher/local.properties
cd launcher
./gradlew assembleRelease
```

`assembleRelease` writes an unsigned APK at `launcher/app/build/outputs/apk/release/app-release-unsigned.apk`. Sign it with your own keystore (apksigner, v2/v3). The debug signing config is not used.

```bash
python3 -m unittest tests.test_bundled_shrine_theme -v
```

## Sideload

Copy `CleanYourTV-Shrine.apk` to the TV with a USB stick or the Downloader app, then install it from the file manager. There is no stable public unauthenticated download URL for this personal APK.
