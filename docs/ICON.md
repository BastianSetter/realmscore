# App-Icon-Slots (RealmScore)

Das Launcher-Icon ist als **Adaptive Icon** aufgebaut. Der User ersetzt nur die
unten markierten Dateien durch sein KI-generiertes Icon – die Verdrahtung im
Manifest (`android:icon="@mipmap/ic_launcher"`,
`android:roundIcon="@mipmap/ic_launcher_round"`) bleibt unverändert.

## Was das Icon ausmacht

| Datei | Rolle | Ersetzen? |
|-------|-------|-----------|
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive-Icon-Definition (verweist auf Vorder-/Hintergrund) | nein, nur falls Struktur geändert wird |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | dito, runde Variante | nein |
| `drawable/ic_launcher_foreground.xml` | **Vordergrund** (das Motiv), 108×108 dp Vektor, Motiv in den inneren 72×72 dp ("safe zone") | **JA** – hier das neue Motiv einsetzen |
| `values/colors.xml` → `ic_launcher_background` | **Hintergrundfarbe** (#6750A4) | optional anpassen |
| `drawable/ic_launcher_foreground.xml` (als `<monochrome>`) | themed/monochrome Icon (Android 13+) | wird automatisch mitgenutzt |

## Wenn statt eines Vektors PNGs geliefert werden

Dann pro Dichte ein `mipmap-<dpi>/ic_launcher.png` ablegen:

| Ordner | Größe |
|--------|-------|
| `mipmap-mdpi`    | 48×48 |
| `mipmap-hdpi`    | 72×72 |
| `mipmap-xhdpi`   | 96×96 |
| `mipmap-xxhdpi`  | 144×144 |
| `mipmap-xxxhdpi` | 192×192 |

Empfohlen ist jedoch der vorhandene Vektor-Weg (eine Datei, alle Dichten).

## Zusätzlich für die F-Droid-Store-Seite

`fastlane/metadata/android/de-DE/images/icon.png` – **512×512 px** (separat vom
App-Icon, siehe `PLACEHOLDER.md` dort).
