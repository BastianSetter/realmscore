# RealmScore

RealmScore is a score keeper for the card game *Fantasy Realms*. The app handles the round scoring, manages ongoing games and collects statistics about your own playing group. It works entirely offline.

> Unofficial fan app. Not affiliated with Strohmann Games, WizKids or the game's publishers.

---

## What it is about

In *Fantasy Realms*, each player forms a hand of seven cards that influence one another through bonuses, penalties and effects. This makes the final scoring laborious and error-prone. RealmScore records the hands, calculates the score automatically according to the official rules and shows clearly how each result comes about.

---

## Features

### Managing games

- Games with 2 to 6 players.
- Two game modes: a fixed number of rounds, or a point limit where play continues until someone reaches the threshold.
- Several games can be kept open at the same time and continued later.
- An overview shows the running total across all rounds.

### Entering cards

- Cards are chosen through a search with a filter by card suit.
- All special and joker cards are supported, including Doppelgänger, Mirage, Shapeshifter, the Book of Changes and the Necromancer.
- Entries can be corrected as long as the round has not been completed.
- The discard pile can be recorded as well, which is relevant for certain card effects.

### Calculating the score

- The game's full rule logic is built in: bonuses, penalties, blanking effects and jokers are evaluated in the correct order.
- For each card it can be broken down how much it contributes to the overall result.
- An optimization function determines the highest-scoring assignment of the joker cards for a given hand.
- A ring diagram presents the hand graphically and uses connecting lines to show which cards strengthen or weaken one another.

### Reveal

- All players enter their cards without seeing the others' scores.
- When revealing, the results are shown one after another from the lowest to the highest score, and the round winner is highlighted.
- A result that has already been shown can be viewed again.

### Sandbox

- A free mode for trying out any card combination without an ongoing game.
- The score updates with every change, including a breakdown.
- Interesting combinations can be saved as favorites and loaded again later.
- Two hands can be compared side by side.

### Profiles

- Players are kept as profiles and can be renamed, colored, archived or merged.
- Archived profiles no longer appear in the selection, but their game data is preserved.

### Statistics

- A global overview of games played, rounds and participating players.
- A player ranking as well as detailed evaluations with win rate, average points, best hands and preferred cards.
- Evaluations of individual cards, such as how often they were played and how much they contribute on average.
- A direct comparison of two players.

### Language and data

- The app is available in German and English; the language can be chosen in the settings.
- All data can be backed up to a file and imported again, including on another device.
- No data is sent to any server; the app needs no internet connection, no account, and contains no ads or trackers.

---

## Planned

These features are intended but not yet included:

- Capturing the hand cards via camera.
- Support for the "Cursed Hoard" expansion.
- Shared entry, where each player enters their cards on their own device and the devices sync with one another.

---

## Installation

The latest version is available as an APK for direct installation under [Releases](https://github.com/BastianSetter/realmscore/releases). Android 8.0 or newer is required.

---

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

## Source

https://github.com/BastianSetter/realmscore
