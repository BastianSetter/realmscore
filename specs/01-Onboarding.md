# Phase 01 – Onboarding

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` im selben Ordner fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Nach Abschluss soll die App startbar sein und der unten beschriebene Klick-Pfad funktionieren.

---

## Kontext (kurz)

Die App "Fantastische Reiche Scoring App" ist eine Android-App zum Punkte-Erfassen fuer das Kartenspiel **Fantastische Reiche** (Strohmann/WizKids). Sie ist offline, F-Droid-konform, und enthaelt spaeter Statistiken sowie eine Sandbox zum Experimentieren mit Karten-Kombinationen.

In dieser ersten Phase wird das **Projekt-Skelett** aufgesetzt **plus** der **Onboarding-Flow**: der User gibt seinen Namen ein, ein Owner-Profil wird angelegt, danach landet er auf einem Platzhalter-Bildschirm "Hauptmenu kommt hier hin".

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Komplettes Android-Studio-Projekt mit korrekter Gradle- und Dependency-Konfiguration
- Layered Ordnerstruktur (`data/`, `domain/`, `ui/`)
- Application-Container fuer manuelle Dependency Injection (kein Hilt)
- DataStore-basierter `DeviceUuidProvider` (einmalig generierte Device-UUID)
- Room-Datenbank mit `ProfileEntity` + `ProfileDao`
- Domain-Modell `Profile` + `ProfileRepository`-Interface + Impl
- `OnboardingScreen` (Compose) + `OnboardingViewModel`
- Navigation-Setup (Navigation Compose) mit Routing-Logik:
  - Wenn Owner existiert → direkt zum Platzhalter
  - Wenn nicht → Onboarding
- `HomePlaceholderScreen` (rein temporaer, zeigt "Hauptmenu kommt hier hin" + Owner-Name)
- String-Resourcen in `strings.xml` (alle Texte, keine Hardcoded-Strings)
- Material 3 Basis-Theme (kann minimal sein, Polish kommt in spaeterer Phase)

### Explizit NICHT drin
- Keine Bottom Navigation (kommt in Phase 02)
- Kein Settings-Screen
- Kein Spiel-/Karten-/Stats-Code
- Keine Farb-Palette mit allen 8 Farben in der Domain (nur die erste fuer den Owner reicht)
- Keine `existsByName`/Autocomplete-Logik (kommt erst wenn andere Profile gebraucht werden, Phase 07)
- Kein dunkles Theme manuell konfigurieren (System-Default reicht)
- Keine Tests fuer ViewModels/Repositories (Tests folgen ab Phase 06)

---

## Was am Ende funktionieren muss

**Klick-Pfad nach Abschluss:**

1. App-Start (erstmaliger Aufruf): `OnboardingScreen` erscheint
2. Begruessungstext sichtbar, Eingabefeld fuer Namen, Button "Weiter" (deaktiviert solange Name leer)
3. User tippt z.B. "Basti", drueckt "Weiter"
4. Profil wird in DB angelegt, Navigation zu `HomePlaceholderScreen`
5. Platzhalter zeigt "Hallo Basti! Hauptmenu kommt hier hin"
6. App schliessen und wieder oeffnen: landet **direkt** auf `HomePlaceholderScreen` (Onboarding ueberspringen)

---

## Tech-Stack fuer diese Phase

Versionen orientieren sich am aktuellen Compose-BOM. Konkrete Versionen in Version Catalog (`libs.versions.toml`) pflegen.

- **Kotlin** 2.0+ mit Compose Compiler Plugin
- **Jetpack Compose** (BOM-basiert) + **Material 3**
- **AndroidX Navigation Compose** fuer Routing
- **Room** (`runtime`, `ktx`, `compiler` via KSP)
- **DataStore Preferences** fuer `deviceUuid`
- **kotlinx.coroutines** + `lifecycle-viewmodel-compose`
- **kotlinx.serialization** (wird in spaeteren Phasen fuer Karten-JSON gebraucht; jetzt schon ins Catalog aufnehmen, aber noch nicht benutzen)
- Min SDK 26, Target SDK 34+, Gradle 8.5+
- **Kein** Hilt, **kein** Firebase, **kein** Google Play Services, **kein** ML Kit
- Build-Konfig: Kotlin DSL (`build.gradle.kts`), Version Catalog

---

## Architektur-Vorgaben (Pflicht)

### Ordnerstruktur

```
app/src/main/java/de/basti/fantasyrealms/
├── FantasyRealmsApp.kt              # Application-Klasse
├── di/
│   └── AppContainer.kt              # manuelle DI
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── dao/ProfileDao.kt
│   │   └── entity/ProfileEntity.kt
│   ├── repository/
│   │   └── ProfileRepositoryImpl.kt
│   └── datastore/
│       └── DeviceUuidProvider.kt
├── domain/
│   ├── model/
│   │   └── Profile.kt
│   ├── repository/
│   │   └── ProfileRepository.kt
│   └── util/
│       └── Clock.kt                 # Time-Abstraktion
└── ui/
    ├── MainActivity.kt
    ├── nav/
    │   ├── AppNavHost.kt
    │   └── Routes.kt
    ├── onboarding/
    │   ├── OnboardingScreen.kt
    │   └── OnboardingViewModel.kt
    ├── placeholder/
    │   └── HomePlaceholderScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

### Domain-Modell

```kotlin
// domain/model/Profile.kt
data class Profile(
    val id: String,                  // sha256(deviceUuid + "|" + name.lowercase()), 32 chars
    val name: String,                // Original-Capitalization beibehalten
    val colorArgb: Int,
    val isLocalOwner: Boolean,
    val isArchived: Boolean,         // im MVP immer false, Feld trotzdem schon vorhanden
    val archivedAt: Long?,           // null im MVP
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String       // dieselbe deviceUuid
)
```

```kotlin
// domain/repository/ProfileRepository.kt
interface ProfileRepository {
    suspend fun getLocalOwner(): Profile?
    suspend fun createOwner(name: String): Profile
}
```

> Weitere Methoden (`searchByNamePrefix`, `createProfile`, `updateProfile` etc.) werden in spaeteren Phasen ergaenzt – jetzt nicht implementieren.

### Datenpersistenz

- **`ProfileEntity`** spiegelt `Profile` 1:1, mit `@PrimaryKey` auf `id`
- **`ProfileDao`** hat in dieser Phase nur: `insert(entity)`, `getLocalOwner(): ProfileEntity?`, `observeAll(): Flow<List<ProfileEntity>>`
- **`AppDatabase`** Version 1, exportSchema = true (schema-Files in `app/schemas/`)
- **`DeviceUuidProvider`**: liest aus DataStore; falls leer, generiert `UUID.randomUUID().toString()` und persistiert es; gibt das Ergebnis zurueck. Methode: `suspend fun get(): String`.

### ID-Generierung

```kotlin
// in ProfileRepositoryImpl
private fun generateProfileId(deviceUuid: String, name: String): String {
    val input = "$deviceUuid|${name.lowercase()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(32)
}
```

### DI / AppContainer

`AppContainer` als einfache Klasse mit lazy Properties:
```kotlin
class AppContainer(private val applicationContext: Context) {
    val database: AppDatabase by lazy { ... }
    val deviceUuidProvider: DeviceUuidProvider by lazy { ... }
    val clock: Clock by lazy { SystemClock() }
    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(
            dao = database.profileDao(),
            deviceUuidProvider = deviceUuidProvider,
            clock = clock
        )
    }
}
```

`FantasyRealmsApp` haelt eine Instanz. ViewModels bekommen Repository via Factory.

### UI

**`OnboardingScreen`:**
- Vertikal zentriert: App-Name als Headline, kurzer Erklaertext (3-4 Zeilen), TextField, Button
- Text-Input: `OutlinedTextField`, single line, Auto-Capitalization auf "Words"
- Button "Weiter": disabled wenn `name.trim().isEmpty()`
- Bei Tap: `viewModel.onContinue()` → suspend-Aufruf, danach Navigation per Callback

**`OnboardingViewModel`:**
```kotlin
data class OnboardingUiState(
    val name: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null
)

class OnboardingViewModel(
    private val profileRepo: ProfileRepository
) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState>
    fun onNameChange(name: String)
    fun onContinue(onSuccess: () -> Unit)
}
```

**Routing in `AppNavHost`:**
- Start-Route: `splash` (einmalig) → prueft via Repository `getLocalOwner()`:
  - Wenn null → navigiert zu `onboarding`
  - Wenn nicht null → navigiert zu `home_placeholder`
- Onboarding nach Erfolg → `home_placeholder`, ohne Zurueck-Stack zur Onboarding-Route
- Splash kann eine einfache `LaunchedEffect` mit Loading-Indicator sein (kein Splash-Screen-API noetig in dieser Phase)

**`HomePlaceholderScreen`:**
- Zeigt: "Hallo {Owner-Name}!"
- Darunter: "Hauptmenu kommt hier hin"
- Kein weiterer Inhalt, keine Buttons
- Liest Owner via ViewModel (eigenes `HomePlaceholderViewModel`)

### Strings

`strings.xml`:
```xml
<resources>
    <string name="app_name">Fantastische Reiche</string>
    <string name="onboarding_headline">Willkommen!</string>
    <string name="onboarding_body">Diese App hilft dir beim Punkte-Zaehlen fuer Fantastische Reiche. Erzaehl mir kurz, wie du heisst.</string>
    <string name="onboarding_name_label">Dein Name</string>
    <string name="onboarding_continue">Weiter</string>
    <string name="onboarding_error_empty">Bitte gib einen Namen ein.</string>
    <string name="home_placeholder_greeting">Hallo %1$s!</string>
    <string name="home_placeholder_body">Hauptmenu kommt hier hin</string>
</resources>
```

---

## Akzeptanzkriterien

- [ ] Projekt baut fehlerfrei mit `./gradlew build`
- [ ] Min SDK 26, Target SDK 34+, Kotlin 2.0+, Compose BOM aktuell
- [ ] Keine Google Play Services / Firebase / ML Kit Dependencies (F-Droid-Check: `./gradlew app:dependencies | grep -E "(gms|firebase|mlkit)"` liefert nichts)
- [ ] Bei erstem Start erscheint `OnboardingScreen`
- [ ] "Weiter"-Button bleibt deaktiviert solange Name leer (nach Trim)
- [ ] Nach Eingabe + "Weiter": Owner-Profil ist in DB persistiert
  - `isLocalOwner = true`
  - `id` korrekt generiert (deterministisch aus `deviceUuid + name.lowercase()`)
  - `colorArgb` ist gesetzt (z.B. `0xFF6750A4.toInt()`)
- [ ] Navigation fuehrt zu `HomePlaceholderScreen`, der den eingegebenen Namen anzeigt
- [ ] Zurueck-Taste auf `HomePlaceholderScreen` schliesst die App (kein Sprung zurueck zum Onboarding)
- [ ] App-Neustart: Owner ist vorhanden → direkt `HomePlaceholderScreen`, kein Onboarding
- [ ] `deviceUuid` wird beim ersten Start einmalig generiert und persistiert; bei weiteren Starts identisch
- [ ] Alle sichtbaren Texte aus `strings.xml`
- [ ] Ordnerstruktur entspricht der oben definierten

---

## Hinweise zu Edge Cases

- **Leerzeichen im Namen**: Trim vor Speichern, aber Original-Capitalization beibehalten ("Basti" bleibt "Basti", "  basti  " wird "basti")
- **Doppel-Onboarding-Versuch (theoretisch)**: `createOwner` sollte `require(getLocalOwner() == null)` werfen – im UI nicht erreichbar in dieser Phase, aber defensiv programmieren
- **App-Crash waehrend Onboarding**: kein halbfertiges Profil moeglich, da `createOwner` einen einzigen Insert macht
- **Configuration Change (Rotation)**: ViewModel-State ueberlebt via `viewModelScope`, kein State-Verlust

---

## Abschluss-Test

Manueller Test, der bestanden werden muss:

1. App deinstallieren (falls vorher installiert)
2. App neu starten → `OnboardingScreen` muss erscheinen
3. Name "TestUser" eingeben → "Weiter" → `HomePlaceholderScreen` mit "Hallo TestUser!"
4. App-Switcher: App killen
5. App neu oeffnen → direkt `HomePlaceholderScreen`, kein Onboarding

Wenn alle Schritte gruen sind, ist Phase 01 abgeschlossen.
