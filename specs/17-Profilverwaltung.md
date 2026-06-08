# Phase 17 – Profilverwaltung

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: MVP (Phasen 01–15) + Release (Phase 16) abgeschlossen.

---

## Kontext (kurz)

Im MVP entstehen Profile implizit beim Spielanlegen. In dieser Phase bekommt der User einen dedizierten **Profilverwaltungs-Screen** unter Settings. Aktionen: umbenennen, Farbe ändern, archivieren (unsichtbar, Daten bleiben), und zwei Profile zusammenführen (Merge).

---

## Scope

### Drin
- `ProfileManagementScreen` erreichbar aus Settings-Tab
- Liste aller aktiven Profile (nicht-archivierte)
- Pro Profil: Avatar, Name, Farbe, Aktions-Menü (3-Punkte)
- **Umbenennen:** neuer Name muss eindeutig sein (case-insensitive)
- **Farbe ändern:** ColorPicker mit der bestehenden 8er-Palette
- **Archivieren:** Profil verschwindet aus aktiver Liste, Spieldaten bleiben erhalten
  - Bestätigungs-Dialog: "Dieses Profil wird ausgeblendet. Alle Spiele mit diesem Profil bleiben erhalten."
- **Merge:** zwei Profile zusammenführen (siehe Merge-Flow unten)
- Sektion "Archivierte Profile" am Ende der Liste (zugeklappt, aufklappbar)
  - Archivierte Profile können reaktiviert werden

### Explizit NICHT drin
- Kein dauerhaftes Löschen mit Datenverlust (nur Archivierung)
- Keine Profilbilder/Fotos
- Kein Import aus anderen Systemen

---

## Was am Ende funktionieren muss

1. Settings → "Profile verwalten" → `ProfileManagementScreen`
2. Profil antippen → Aktions-Menü erscheint
3. "Umbenennen" → Dialog mit Textfeld, Validierung, Speichern
4. "Farbe ändern" → ColorPicker-Dialog, Auswahl, Speichern
5. "Archivieren" → Bestätigung → Profil verschwindet aus aktiver Liste
6. "Mit anderem Profil zusammenführen" → Merge-Flow (siehe unten)
7. Sektion "Archivierte Profile" aufklappen → reaktivierbar per Button

---

## Merge-Flow

Merge bedeutet: Alle Spieldaten von Profil B werden auf Profil A umgeschrieben. Profil B wird danach archiviert.

**Schritte im UI:**
1. Auf Profil A → "Mit anderem Profil zusammenführen"
2. Liste aller anderen aktiven Profile → User wählt Profil B
3. Vorschau-Dialog:
   - "Profil A (15 Spiele) + Profil B (8 Spiele) → Profil A (23 Spiele)"
   - Name, Farbe von Profil A bleibt erhalten
   - "Profil B wird archiviert"
   - Zwei Buttons: "Zusammenführen" / "Abbrechen"
4. Bei Bestätigung: atomar in einer DB-Transaktion
   - Alle `game_participants` mit `profileId = B` → auf `profileId = A` umschreiben
   - Alle `round_results` mit `profileId = B` → auf `profileId = A`
   - Profil B: `isArchived = true`, `archivedAt = now()`
   - `Profile A: updatedAt = now()`

**Merge-Repository-Methode:**
```kotlin
interface ProfileRepository {
    // NEU:
    suspend fun mergeProfiles(keepId: String, discardId: String)
    suspend fun archiveProfile(id: String)
    suspend fun unarchiveProfile(id: String)
    suspend fun updateName(id: String, newName: String)
    suspend fun updateColor(id: String, colorArgb: Int)
    suspend fun searchByNamePrefix(prefix: String): List<Profile>
    suspend fun existsByName(name: String): Boolean
}
```

---

## ColorPicker

Wiederverwendet aus Phase 06 (NewGame hat schon Farb-Zuweisung). Hier als Dialog:

```kotlin
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_color_picker_title)) },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(DefaultProfileColors) { color ->
                    ColorSwatch(
                        color = color,
                        isSelected = color == currentColor,
                        onClick = { onColorSelected(color) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}
```

---

## Settings-Tab Erweiterung

In `SettingsScreen.kt` den bestehenden Profil-Abschnitt ergänzen:

```kotlin
// Vorher: nur "Username aendern"
// Jetzt zusaetzlich:
Button(onClick = { navController.navigate(Routes.PROFILE_MANAGEMENT) }) {
    Text(stringResource(R.string.settings_manage_profiles))
}
```

Route:
```kotlin
const val PROFILE_MANAGEMENT = "settings/profiles"
```

---

## Akzeptanzkriterien

- [ ] `ProfileManagementScreen` aus Settings erreichbar
- [ ] Alle aktiven Profile werden angezeigt
- [ ] Umbenennen: Validierung (nicht leer, nicht Duplikat), Speichern funktioniert
- [ ] Farbe ändern: ColorPicker zeigt 8 Farben, Auswahl wird gespeichert
- [ ] Archivieren: Bestätigungs-Dialog, danach aus aktiver Liste verschwunden
- [ ] Sektion "Archivierte Profile" aufklappbar, reaktivierbar
- [ ] Merge: Vorschau-Dialog zeigt korrekte Zahlen
- [ ] Merge: atomar in DB-Transaktion, alle Spieldaten auf Profil A umgeschrieben
- [ ] Merge: Profil B danach archiviert
- [ ] Owner-Profil kann archiviert werden (Warnung: "Du bist dann ohne Owner-Profil")
- [ ] Alle Texte aus `strings.xml`
- [ ] Unit-Tests: `mergeProfiles` korrekt transaktional

---

## Strings (Ergänzungen)

```xml
<string name="settings_manage_profiles">Profile verwalten</string>
<string name="profile_management_title">Profile</string>
<string name="profile_management_archived_section">Archivierte Profile</string>
<string name="profile_action_rename">Umbenennen</string>
<string name="profile_action_change_color">Farbe ändern</string>
<string name="profile_action_archive">Archivieren</string>
<string name="profile_action_merge">Mit anderem Profil zusammenführen</string>
<string name="profile_action_unarchive">Reaktivieren</string>
<string name="profile_archive_confirm_title">Profil archivieren?</string>
<string name="profile_archive_confirm_body">Das Profil wird ausgeblendet. Alle Spiele bleiben erhalten.</string>
<string name="profile_merge_title">Profile zusammenführen</string>
<string name="profile_merge_preview">%1$s (%2$d Spiele) + %3$s (%4$d Spiele) → %1$s (%5$d Spiele)</string>
<string name="profile_merge_confirm">Zusammenführen</string>
<string name="profile_color_picker_title">Farbe wählen</string>
```
