# Theme catalog

The shrine picture ships inside the launcher. Later themes can be downloaded without an app update.
Settings always shows **Themes**. The row stays grey, with the note "More themes in future updates",
until the online index lists at least one theme that is not the built-in shrine theme.

## Index

Default URL, changeable on the Themes screen once that screen is open:

`https://raw.githubusercontent.com/Nighsuh/cleanyourtv-shrine/main/themes/index.json`

```json
{
  "themes": [
    {
      "id": "shrine",
      "name": "Imam Hussain shrine",
      "builtin": true
    },
    {
      "id": "evening-courtyard",
      "name": "Evening courtyard",
      "preview": "https://example.com/themes/evening-courtyard/preview.jpg",
      "download": "https://example.com/themes/evening-courtyard.zip"
    }
  ]
}
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Short token. `shrine`, `imam-hussain` and `imam-hussain-inside` are the built-in theme and do not unlock the row. |
| `name` | no | Label in the list. Defaults to `id`. |
| `builtin` | no | `true` marks a theme that is already in the app. It does not unlock the row. |
| `preview` | no | `https` image shown as a note. The launcher does not depend on it. |
| `download` | for remote themes | `https` URL of a zip. This is what counts as a theme beyond the shrine. |

An empty list, a failed download, or an index that only describes the shrine leaves the row grey.

## Theme zip

A zip with these files at the top, or inside one folder:

| File | Required | Meaning |
|---|---|---|
| `config.json` | yes | Same keys as a CleanYourTV theme (`themes/README.md` in the upstream tree). Unknown keys are ignored. |
| `wallpaper.jpg` | no | Picture behind Home. JPEG, PNG or WebP, at most 15 MB. |
| `icon.png` | no | Optional mark stored with the theme for later use. It does not replace the launcher icon. |
| `accent` | no | Optional `#RRGGBB` in `config.json` as `accent_color`. The launcher already reads that key for a festive skin. |

Example `config.json` for the built-in shrine, which also lives at `launcher/app/src/main/assets/bundled-theme/`:

```json
{
  "dock_apps": [
    "com.google.android.youtube.tv",
    "net.mbc.shahidTV",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "com.osn.go",
    "com.disneyplus.mea"
  ],
  "columns": 6,
  "wallpaper": true,
  "wallpaper_dim": 45,
  "dock_opacity": 70,
  "glass_style": "rim",
  "wallpaper_rotation_minutes": 0
}
```

Applying a zip copies `config.json` and `wallpaper.jpg` into the launcher's theme folder and restarts Home.
The dock order the owner set on the TV is kept. Wallpaper dim chosen in Wallpaper settings is cleared so the
theme's own dim is used.
