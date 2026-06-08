# Phase 23 – Datenexport / Backup (JSON)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 22 abgeschlossen.

---

## Kontext (kurz)

Vollständiges Backup und Restore der App-Daten als JSON-Datei. Alle Daten (Profile, Spiele, Runden, Karten, Scores) werden in einer Datei gespeichert und können auf demselben oder einem anderen Gerät importiert werden.

Das Format muss **versioniert** sein damit alte Backups nach App-Updates noch importierbar sind.

---

## Scope

### Drin
- Export: eine JSON-Datei mit allen Daten, via Android Share-Intent
- Import: JSON-Datei wählen (File-Picker), validieren, in DB laden
- Versions-Handling: Backup-Format hat eine `schemaVersion`, Migrations-Logik für zukünftige Versionen
- Konflikt-Strategie beim Import (bestehende Daten vs. importierte)
- UI in Settings-Tab

### Explizit NICHT drin
- Kein automatisches Cloud-Backup
- Kein inkrementelles Backup (immer vollständig)
- Keine verschlüsselte Datei

---

## Was am Ende funktionieren muss

1. Settings → Daten → "Backup exportieren" → Share-Sheet öffnet sich mit `realmscore_backup_2026-01-15.json`
2. Settings → Daten → "Backup importieren" → File-Picker → JSON wählen
3. Validierung: Schema-Version geprüft, Datei-Integrität geprüft
4. Import-Dialog: "Diese Aktion fügt die Backup-Daten zu deinen bestehenden Daten hinzu. Duplikate werden übersprungen."
5. Bestätigen → Daten werden importiert, Snackbar mit Zusammenfassung

---

## Backup-Format

```json
{
  "schemaVersion": 1,
  "appVersion": "1.2.0",
  "exportedAt": "2026-01-15T14:30:00Z",
  "deviceId": "abc-123",
  "profiles": [
    {
      "id": "...",
      "name": "Maria",
      "colorArgb": -10185235,
      "isLocalOwner": true,
      "isArchived": false,
      "createdAt": 1705320600000
    }
  ],
  "games": [
    {
      "id": "...",
      "displayName": null,
      "mode": "FIXED_ROUNDS",
      "targetRounds": 3,
      "targetPoints": null,
      "startedAt": 1705320600000,
      "closedAt": 1705327800000,
      "closedReason": "COMPLETED",
      "participants": [
        { "profileId": "...", "seatOrder": 0 }
      ],
      "rounds": [
        {
          "id": "...",
          "roundNumber": 1,
          "completedAt": 1705323600000,
          "discardScanned": false,
          "discardCards": [],
          "results": [
            {
              "profileId": "...",
              "totalScore": 87,
              "handCards": [
                {
                  "cardKey": "dragon",
                  "position": 0,
                  "jokerTargetCardKey": null
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## Konflikt-Strategie beim Import

**Strategie: Merge by UUID (safe)**
- Profile mit derselben `id`: überspringen (bestehendes hat Vorrang)
- Spiele mit derselben `id`: überspringen
- Runden/Ergebnisse mit derselben `id`: überspringen

Das bedeutet: Ein Backup kann gefahrlos mehrfach importiert werden ohne Duplikate zu erzeugen. Neue Daten (neue UUID) werden hinzugefügt.

**Exception:** `isLocalOwner = true` aus dem Backup wird ignoriert – der Owner des aktuellen Geräts bleibt der Owner.

---

## Implementation

```kotlin
// domain/backup/BackupExporter.kt
class BackupExporter(
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val handCardRepo: HandCardRepository
) {
    suspend fun export(): BackupData {
        // Alle Daten aggregieren
    }

    fun serialize(data: BackupData): String {
        return Json { prettyPrint = true }.encodeToString(data)
    }
}

// domain/backup/BackupImporter.kt
class BackupImporter(
    private val db: AppDatabase
) {
    suspend fun import(json: String): ImportResult {
        val data = Json.decodeFromString<BackupData>(json)
        validateSchema(data.schemaVersion)
        return db.runInTransaction {
            importProfiles(data.profiles)
            importGames(data.games)
            // ...
        }
    }
}

data class ImportResult(
    val profilesAdded: Int,
    val profilesSkipped: Int,
    val gamesAdded: Int,
    val gamesSkipped: Int
)
```

---

## UI in Settings

```kotlin
// In SettingsScreen, Sektion "Daten":

Button(onClick = { vm.exportBackup(context) }) {
    Text(stringResource(R.string.settings_export_backup))
}

Button(onClick = { filePickerLauncher.launch("application/json") }) {
    Text(stringResource(R.string.settings_import_backup))
}
```

**Export-Flow:**
1. `BackupExporter.export()` → JSON-String
2. Temp-Datei in `cacheDir` schreiben: `realmscore_backup_${date}.json`
3. `FileProvider` + `Intent.ACTION_SEND` → Share-Sheet

**Import-Flow:**
1. `ActivityResultContracts.GetContent("application/json")`
2. Datei lesen → `BackupImporter.import(json)`
3. Bestätigungs-Dialog vorher
4. Snackbar nach Import: "Import abgeschlossen: 3 Spiele hinzugefügt, 12 übersprungen"

---

## Akzeptanzkriterien

- [ ] Export erstellt valides JSON mit korrekter `schemaVersion`
- [ ] Share-Sheet öffnet sich mit der Backup-Datei
- [ ] Import-Datei-Picker öffnet sich
- [ ] Validierung: falsche Schema-Version → Fehlermeldung
- [ ] Validierung: kaputtes JSON → Fehlermeldung
- [ ] Import fügt neue Daten hinzu, überspringt Duplikate
- [ ] `isLocalOwner` aus Backup wird ignoriert
- [ ] Import-Zusammenfassung in Snackbar
- [ ] Mehrfacher Import derselben Datei erzeugt keine Duplikate
- [ ] Backup von Version 1 funktioniert auch nach App-Update (schema migration vorbereitet)

---

## Strings (Ergänzungen)

```xml
<string name="settings_export_backup">Backup exportieren</string>
<string name="settings_import_backup">Backup importieren</string>
<string name="backup_import_confirm_title">Backup importieren?</string>
<string name="backup_import_confirm_body">Die Backup-Daten werden zu deinen bestehenden Daten hinzugefügt. Duplikate werden übersprungen.</string>
<string name="backup_import_result">Import abgeschlossen: %1$d hinzugefügt, %2$d übersprungen</string>
<string name="backup_import_error_schema">Dieses Backup ist mit einer neueren App-Version kompatibel. Bitte zuerst die App aktualisieren.</string>
<string name="backup_import_error_invalid">Die Datei konnte nicht gelesen werden.</string>
<string name="backup_filename">realmscore_backup_%1$s.json</string>
```
