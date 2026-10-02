# Mega Walls Party Mod

[![Build](https://github.com/louisktari/MegaWallsPartyMod/actions/workflows/build.yml/badge.svg)](https://github.com/louisktari/MegaWallsPartyMod/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9-brightgreen)](https://files.minecraftforge.net/)

A Forge 1.8.9 client mod for running Hypixel Mega Walls parties. Every player who joins
your party is stat-checked against the Hypixel API. Suspicious players are flagged, and
you can remove them with one click: `/block add <name>` followed by `/p kick <name>`.

## What it does

- **Stat checks on join.** Each new party member is looked up through the Mojang and Hypixel APIs.
  `/mwp sync` sends `/p list` and checks everyone who is already in the party.
- **Flags.** A player is flagged for review when they match any enabled rule:
  - has never joined Hypixel
  - new Hypixel account (default: under 30 days old)
  - no rank
  - low network level (default: under 10)
  - few Mega Walls games (default: under 50 wins + losses)
  - low Mega Walls stats (default: FKDR under 0.5)
  - high Mega Walls stats (default: FKDR over 5 or WLR over 2.5)

  FKDR and WLR rules only apply once a player has at least 10 games.
- **Block + kick.** Removing a player always sends `/block add <name>`, then `/p kick <name>`,
  in that order and spaced apart so Hypixel doesn't throttle them.
- **Competitive-ban auto-removal.** If queueing fails with
  `You cannot queue for this mode due to <name> not being able to play competitive games!`,
  that player is blocked and kicked straight away.
- **Trusted players.** Trust your regulars once and they are never flagged again (saved to
  `config/megawallspartymod/trusted.json`).

## Hosting without commands

Four panels sit on your screen like extra chat windows:

| Panel | Shows | Buttons |
|---|---|---|
| **To review** | Flagged players and why. The title flashes gold while someone is waiting. | **Kick**, **Keep**, **Trust** on each player; **Kick all** (click twice to confirm) |
| **Party members** | Everyone in the party, colour-coded (green checked, gold flagged, aqua trusted, yellow pending) with rank, games and FKDR | **Sync**, **Re-check**, **Settings**; hover a player for **Kick**, **Trust**, **Check** |
| **Party log** | Joins, leaves, flags and actions, with timestamps | **Clear** |
| **Blocked this session** | Everyone removed, and why | hover a player for **Unblock** |

Open chat (`T`), or press **`P`**, and the panels become interactive:

- **Click** a button to act.
- **Hover** a player to see their stats (rank, level, first login, games, FKDR, WLR, finals) and flag reasons.
- **Drag** a panel by its title bar to move it.

While you play they're display-only, so they never get in the way. Flagged players also get
a chat line with clickable **[Block + Kick]**, **[Keep]** and **[Trust]** buttons, and a sound.

## Setup

1. Install Forge for 1.8.9 and put the jar from
   [Releases](https://github.com/louisktari/MegaWallsPartyMod/releases) in your `mods` folder.
   OneConfig is downloaded automatically on first launch.
2. Get a Hypixel API key from [developer.hypixel.net](https://developer.hypixel.net) and run
   `/mwp setkey <key>`, or paste it into **OneConfig → Mega Walls Party Mod → Hypixel API**.
3. Join a party and press **P** to drag the panels where you want them.

## Commands

| Command | Action |
|---|---|
| `/mwp remove <name>` | `/block add` then `/p kick` the player |
| `/mwp dismiss <name>` | keep a flagged player and take them out of review |
| `/mwp trust <name>` / `untrust <name>` | never flag a player / flag them again |
| `/mwp unblock <name>` | `/block remove` a player |
| `/mwp check <name>` | re-run a player's stat check |
| `/mwp sync` | send `/p list` and check every member |
| `/mwp clear` | clear the log and blocked overlays |
| `/mwp setkey <key>` | save your Hypixel API key |
| `/mwp settings` | open the OneConfig settings |

Every command has a button in the panels. You can also set keybinds in OneConfig to kick or keep the next player waiting for review.

## Settings (OneConfig)

| Section | What you can change |
|---|---|
| Flags | Turn each rule on or off and adjust its threshold |
| Actions | Competitive auto-removal (on), auto-remove flagged joins (off), chat prompt, sound, keybinds, gap between commands |
| Overlays | Interactive overlay key (P), turn each panel on or off, scale, width, max rows, background opacity, hide empty panels, reset positions |
| Hypixel API | API key, lookups per minute, cache duration, re-check the whole party |

## Building

CI builds every push. Pushes to `main` publish a GitHub Release with the jar attached.
To bump the version, edit `modVersion` in `gradle.properties`.

Building locally needs JDK 8:

```bash
./gradlew build      # jar in build/libs/, unit tests included
```

## Credits

Based on Alexdoru's PartyMod (`fr.alexdoru.partymod`). OneConfig belongs to Polyfrost; see
[THIRD-PARTY.md](THIRD-PARTY.md) and `licenses/`.
