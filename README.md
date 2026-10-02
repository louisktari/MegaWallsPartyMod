# Mega Walls Party Mod

[![Build](https://github.com/louisktari/MegaWallsPartyMod/actions/workflows/build.yml/badge.svg)](https://github.com/louisktari/MegaWallsPartyMod/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9-brightgreen)](https://files.minecraftforge.net/)

A Forge 1.8.9 client mod for running Hypixel Mega Walls parties. Every player who joins
your party is stat-checked against the Hypixel API, gets a colour-coded stat card and a verdict,
and can be removed with one click or one key. Removing a player sends `/block add <name>` followed by `/p kick <name>`.

## What it does

- **Stat checks on join.** New members are looked up on Hypixel straight away. The UUID comes
  from the tab list when the player is in your lobby, which saves a Mojang lookup.
  **Sync** (or `/mwp sync`) checks everyone already in the party.
- **Strong and weak flags.** Each card gets a verdict:
  - **KICK?** (red): a *strong* flag. The player has never joined Hypixel, the account is under 7 days old, or you removed them before.
  - **REVIEW** (gold): a *weak* flag. No rank, low network level, few Mega Walls games, or unusually low or high FKDR/WLR.
  - **OK** (green) / **TRUSTED** (aqua)

  All thresholds can be changed. Pick a **Lenient / Balanced / Strict** preset, then fine-tune.
  FKDR and WLR rules only apply once a player has at least 10 games.
- **Block + kick.** Removing a player always sends `/block add`, then `/p kick`, spaced apart so
  Hypixel doesn't throttle them. Every removal can be **undone for 10 seconds**: the commands are
  cancelled if they haven't been sent yet, otherwise the player is unblocked and re-invited.
- **Competitive-ban auto-removal.** If queueing fails with
  `You cannot queue for this mode due to <name> not being able to play competitive games!`,
  that player is blocked and kicked straight away.
- **Queue guard.** If you type `/play ...` while someone is still waiting for review, the mod asks
  whether to **[Kick them + queue]** or **[Queue anyway]** first.
- **Remembers removals.** Everyone you remove is saved, so if they rejoin later their card shows
  "Removed before (2 Oct)" as a strong flag.
- **Trusted players.** Regulars you trust are never flagged. Manage the list with the Trust buttons
  or in OneConfig.
- **Share lists with co-hosts.** Export your trusted and blocked lists to the clipboard (paste into
  Discord) or to a file, and import a co-host's. Importing only ever *adds*: your existing entries are
  kept, and anyone you trust is never pulled into your blocked list. Imported blocked players are
  flagged "Removed before" if they join. The import doesn't `/block` them on Hypixel.

## Hosting without commands

Four panels sit on your screen like extra chat windows:

| Panel | Shows | Buttons |
|---|---|---|
| **To review** | A stat card per flagged player, strong flags first, with reasons in red or gold. The title flashes while anyone is waiting. | **Kick**, **Keep**, **Trust** on each card; **Kick all** (click twice) |
| **Party** | A stat card for every member, newest first, with a verdict badge. New joins are tagged **NEW** for a minute. Shows a warning if your API key is missing or rejected. | **Sync**, **Re-check all**, **Settings**; hover a card for **Kick**, **Trust**, **Check** |
| **Party log** | Joins (green), leaves (red), flags and actions, in rank colours with timestamps. Newest at the bottom, like chat. | **Undo** on removals (10 s), **Clear** |
| **Blocked players** | Everyone you've removed (saved across sessions), why, when, and by whom | hover for **Unblock**; **Copy lists**, **Paste lists**, **Forget all** |

Each stat card reads like Hypixel. The name is coloured by rank, and the stats use traffic-light colours based on your flag thresholds: **green** is fine, **yellow** is borderline, **red** matches a flag.

```
NEW [MVP+] Halloweenify                     [OK]
    Level 403 | Hypixel age 9.1y | Games 3,356
    FKDR 1.39 | WLR 0.43 | Finals 3,780
```

Press **P** (or open chat) and the panels become interactive:

- **K / J / T**: kick, keep or trust the top player in the review queue. This works only on the P screen, not in chat.
- **Click** a button to act; **hover** a card for full colour-coded stats.
- **Scroll** over a panel to see more; **drag** a title bar to move a panel; **click** a title to fold it.

While you play the panels only display, and during a Mega Walls match they shrink to a small
"N waiting for review" badge. Flagged players also get a chat line with
**[Block + Kick] [Keep] [Trust]** buttons, plus a sound that's lower-pitched for strong flags.

## Setup

1. Install Forge for 1.8.9 and put the jar from
   [Releases](https://github.com/louisktari/MegaWallsPartyMod/releases) in your `mods` folder.
   OneConfig is downloaded automatically on first launch.
2. Open **OneConfig → Mega Walls Party Mod → Getting started**. Paste your key from
   [developer.hypixel.net](https://developer.hypixel.net) and press **Test key**. `/mwp setkey <key>` also works.
3. Join a party, press **P**, and drag the panels where you want them.

## Settings (OneConfig)

| Page | What's there |
|---|---|
| Getting started | API key + **Test key**, panel keybind + **Open**, flag preset |
| Flags | Each rule's on/off switch next to its threshold; defaults are shown in the hover description |
| Actions | Competitive auto-removal (on), auto-remove strong flags (off), queue guard (on), chat prompt, sound, keybinds, command gap |
| Overlays | Panels on/off, quiet during matches, scale, width, entries per panel, opacity, reset positions |
| Trusted players | Editable list, one name per line; **Copy / Paste** and **Export / Import** lists to share with co-hosts |
| Advanced | Lookups per minute, cache, re-check party, clear saved blocked history |

Panels are positioned by dragging them in-game, **not** with OneConfig's Edit HUD.

## Commands

You shouldn't need these, because every action has a button. They're there for keybind macros:

| Command | Action |
|---|---|
| `/mwp panels` | open the interactive panels (same as P) |
| `/mwp remove <name>` | `/block add` then `/p kick` the player |
| `/mwp dismiss <name>` | keep a flagged player |
| `/mwp trust <name>` / `untrust <name>` | never flag a player / flag them again |
| `/mwp unblock <name>` | `/block remove` a player |
| `/mwp check <name>` | re-run a player's stat check |
| `/mwp sync` | send `/p list` and check every member |
| `/mwp clear` | clear the party log |
| `/mwp export` / `export file` | copy your trusted + blocked lists to the clipboard / save `export-<date>.json` |
| `/mwp import` / `import file` / `import <name>.json` | merge from the clipboard / the newest export file / a named file in `config/megawallspartymod/` |
| `/mwp setkey <key>` / `testkey` | save / test your Hypixel API key |
| `/mwp settings` | open OneConfig |

## Building

CI builds every push. Pushes to `main` publish a GitHub Release with the jar attached.
To bump the version, edit `modVersion` in `gradle.properties`.

Building locally needs JDK 8:

```bash
./gradlew build      # jar in build/libs/, unit tests included
```

Data files live in `config/megawallspartymod/` (`trusted.json`, `blocked-history.json`).

## Credits

Based on Alexdoru's PartyMod (`fr.alexdoru.partymod`). OneConfig belongs to Polyfrost; see
[THIRD-PARTY.md](THIRD-PARTY.md) and `licenses/`.
