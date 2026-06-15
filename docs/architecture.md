# Architektur

## Layer-Trennung

Der gesamte Code liegt unter `app/src/main/java/de/morzo/realmscore/`:

```
domain/   — reines Kotlin, keine Android-Imports (Modelle, Scoring-Engine, Stats, Use-Cases, Repository-Interfaces)
data/     — Room, DataStore, Asset-Laden, Repository-Implementierungen
ui/       — Compose Screens, ViewModels, Navigation, Theme
di/       — AppContainer (manuelle DI)
```

- `domain/` darf **keine** Android-Abhängigkeit haben (Ausnahme: `data/stats/random/*Provider` halten
  einen `Context` für lokalisierte Strings — siehe unten).
- Repository-Interfaces leben in `domain/repository/`, Implementierungen in `data/repository/`.

## Manuelle DI: `AppContainer`

`di/AppContainer.kt` ist der zentrale Objekt-Graph — **kein Hilt/Dagger**. Eine einzige Klasse mit
`lazy`-Properties, gehalten von der `Application`-Klasse `FantasyRealmsApp`.

Bereitgestellte Singletons (Auszug):

| Property | Typ | Zweck |
|---|---|---|
| `database` | `AppDatabase` | Room, siehe `data-model.md` |
| `deviceUuidProvider` | `DeviceUuidProvider` | persistente Geräte-UUID (DataStore) |
| `clock` | `Clock` (`SystemClock`) | testbare Zeitquelle (epoch millis) |
| `cardLookup` | `CardLookup` | lädt die 53 Karten aus `assets/cards/` |
| `profileRepository` / `gameRepository` / `roundRepository` / `handCardRepository` | Repos | CRUD |
| `sandboxFavoriteRepository` | Repo | gespeicherte Sandbox-Hände |
| `statsRepository` | Repo | berechnet Statistiken aus der Historie |
| `settingsRepository` | Repo | DataStore-Settings (Sprache, Theme, Defaults …) |
| `backupRepository` | Repo | JSON-Export/Import |
| `getGameStateUseCase` | UseCase | Live-Spielstand aus Runden berechnen |
| `resetUseCase` | UseCase | App-Reset (DB + Settings wipen) |
| `scoringEngine` | `ScoringEngine` | Punkte-Pipeline (siehe `scoring-engine.md`) |
| `optimalSolver` | `OptimalSolver` | Brute-Force über Joker-/Spieler-Wahlen |
| `pickRandomStatUseCase` + `randomStatProviders` | — | Zufalls-Stat auf dem Home-Tab |

**ViewModels** werden über simple `ViewModelProvider.Factory`-Objekte erzeugt, die die nötigen
Repos aus dem `AppContainer` durchgereicht bekommen (z. B. `HomeViewModel.Factory(...)` in
`MainScaffold`). Es gibt keinen DI-Codegen.

## App-Start & Routing

`ui/MainActivity.kt` → `AppNavHost(container)` (`ui/nav/AppNavHost.kt`):

1. `Routes.SPLASH` — `SplashRoute` ruft `profileRepository.getLocalOwner()`.
   - Owner vorhanden → `Routes.MAIN`
   - kein Owner → `Routes.ONBOARDING`
   - Splash & Onboarding werden jeweils aus dem Back-Stack gepoppt.
2. `Routes.ONBOARDING` — Sprachauswahl (Flaggen) + Owner-Name anlegen → `Routes.MAIN`.
3. `Routes.MAIN` — `MainScaffold` mit Bottom-Navigation und eigenem `NavController` für alle
   inneren Routen.

Es gibt also **zwei NavHosts**: den äußeren (Splash/Onboarding/Main) und den inneren in
`MainScaffold` (alle Tabs + Detail-Screens). Details aller Routen: `navigation-and-screens.md`.

## Lokalisierung (i18n)

- Alle UI-Texte aus `res/values/strings.xml` (DE, Default) bzw. `res/values-en/strings.xml` (EN).
- Karten-Texte kommen aus `assets/cards/base_game.json` (DE) + optionalem Override
  `assets/cards/base_game_en.json` (EN); fehlt der Override, fällt die Karte auf DE zurück
  (`CardLookup`).
- **Sprachwahl** (`AppLanguage`: `SYSTEM` / `GERMAN` / `ENGLISH`) wird in DataStore gehalten und in
  `MainActivity.attachBaseContext` + `recreate()` angewendet — **nicht** als Composition-Override.
  Dadurch erben auch Dialoge/Popups die Sprache.
- Die **Scoring-Engine trägt nur sprachneutrale Keys** (`effectiveCardKey`, `nameCardKey`,
  `descriptionKey`), nie fertige Texte. Die UI lokalisiert über `cardLookup` + `stringResource`.
  (Begründung: spec 25.7.)

## Sync-Vorbereitung (noch ungenutzt, aber im Datenmodell verankert)

Auch wenn P2P-Sync (Phase 28) nicht umgesetzt ist, ist das Datenmodell darauf vorbereitet:
String-UUIDs als Primary Keys, `originDeviceId` + `createdAt`/`updatedAt` auf schreibenden
Entities, deterministische Profil-ID `sha256(deviceUuid + "|" + name.lowercase()).take(32)`,
`isLocalOwner` (genau ein Owner pro Gerät). Siehe `data-model.md`.
