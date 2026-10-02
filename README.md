# PartyMod 2.0.4

2.0.4 adds a second review of interactions between roster, statistics, actions
and saved records. It fixes approval resets on API failures, UUID ownership
and attendance migration, stale UI clicks, readiness, notifications and
request cancellation. The original Mega Walls screening purpose and the
requested age kicks and block-after-kick behaviour remain. See
[the second-pass audit](docs/REVIEW-2.0.4.md).

A local organiser for Forge **Minecraft 1.8.9** parties, with a searchable roster,
review queue, event desk, OneConfig settings and an optional native OneConfig HUD.
Based on the supplied Alexdoru PartyMod source. This is a local development build.

## Install and open

1. Close Minecraft. Remove the old PartyMod JAR from that instance's `mods` folder.
2. Put **PartyMod-2.0.4.jar** in the same folder. Use Forge 1.8.9 and Java 8.
3. Start Minecraft. The embedded OneConfig stage-0 loader loads OneConfig; its
   first run needs internet access if OneConfig is not already cached.
4. Press **P**, or run **/partymod**, to open the organiser. Configure the key in
   OneConfig if another mod already uses P.
5. Run **/partymod settings**, or use the organiser's OneConfig button.

The organiser sends a single `/p list` when it becomes active on Hypixel. You can
also choose **Sync roster**. It does not continuously poll party lists. Membership
updates come from recognised server chat. If a response is incomplete or an event
is missed, sync again; moderation requires a complete, fresh roster.

## OneConfig organisation

| Section | Settings |
| --- | --- |
| Party | Open organiser, shortcuts, target size, roster freshness, disconnect grace |
| Reviews | Account history, ratios, progression, activity evidence, reassessment |
| Actions | Block after confirmed kicks, manual confirmations, command spacing |
| Notifications | Notifications and quiet mode during detected MW matches |
| HUD | Enable, move, scale, style and choose the displayed counters |
| Data | Direct API-key setup or optional application service, request budget/cache, retry and local save |

The HUD starts **off**. Enable **HUD > Party overview**, then use **Edit HUD** to
position it. OneConfig also supplies an Edit HUD button in its sidebar. The
organiser starts enabled; its native OneConfig mod toggle or enable/disable
commands control it. Changing profiles is detected by the next client tick.

## Roster and reviews

The charcoal/violet organiser has Players, Review queue, Event log and Event desk
tabs. Search names; cycle filters/sorting; use arrow keys to select and Tab to
switch tabs. Mouse-wheel scrolling works independently in the player detail
panel. **Refresh order** applies changed sorting/filter results: rows do not
automatically jump under the pointer when asynchronous results arrive.

The detail panel shows all applicable reasons, the underlying counts, source and
data timestamp. Review suggestions do not establish cheating. Unknown data stays
unknown. FKD and W/L with zero denominators remain unavailable. Games mean
recorded wins plus losses; this is not a complete match history.

Approve for event and Dismiss reasons are local decisions for the current member
and evidence. A material reason change invalidates approval/dismissal. The review
queue persists across screen changes; current membership, event approvals,
readiness rounds and the event log are not restored as live state on restart.

## Removal, blocking and ignore

**Block after confirmed kicks is ON by default, as requested.** A recognised server
kick confirmation queues `/block add <username>`, including kicks initiated using
normal party commands or by another party host. The mod must be enabled, connected
to Hypixel and receiving that confirmation. Voluntary departures, disconnects,
disbands and missing roster entries do not trigger a block.

Remove, Block, Ignore and local Host blocklist are separate controls. Remove
requires current membership, a fresh roster and leader/moderator authority; those
conditions are checked again immediately before sending. Confirmation dialogs
are on by default. Ignoring is never implied by removal. Auto-block is the explicit
exception: the Remove dialog explains it, and the Actions toggle disables it.

Commands are serialised at a default minimum spacing of 1,250 ms. Duplicate
commands and repeated kick notifications are suppressed. Server acknowledgements
complete actions; an unrecognised response times out as **Unconfirmed** after ten
seconds and is not retried blindly. A large block burst gets a bounded longer
queue allowance; manual moderation expires after two minutes. Disabling, changing
servers or ending the party cancels unsent work. A command already sent cannot be
withdrawn. Live Hypixel command acceptance and response formats remain untested.

## Event desk and local records

The pre-queue checklist is advisory. It displays roster freshness, leader status,
reviews, unavailable/pending assessments, readiness and disconnected members. It
does not guarantee server acceptance or claim a player's competitive-ban status.

The leader can start a readiness round. Replies must be `ready PM-...` with the
exact current identifier, from a recognised party-chat sender who is currently
on the roster. The selected player can also be marked ready manually. Online
status and silence do not establish readiness or AFK status.

Disconnect grace timers and a local waitlist support host decisions. Invitations
are host-triggered and checked against role, current membership and configured
capacity. No automatic replacement, kick, reserved server slot or permission
change is implied. Invitations have queued/sent/unconfirmed states.

Notes, local host blocklist entries, party-join observations and local MW sightings
are stored in `config/partymod/local-records.json`. They use a resolved UUID when
available, otherwise a clearly limited name fallback. UUID migration preserves
the fallback note. Delete local record removes that identity's local history.
The waitlist, selected filter and sort are also saved; pending invitation state
is reset to Waiting on restart. Current party membership is never restored.

MW sightings require a detected in-match MW scoreboard and a nonspectator member
visible in your client's world. They establish only **local MW sightings**, not
complete match attendance, every server participant, completed matches or games
outside your events. Party joins are recorded separately. Activity-gap reviews
default to three UTC calendar months, with a separate days option. Unknown
activity does not become ban evidence. The client does not poll to manufacture
long-term player histories, de-anonymise nicknames or automate gameplay.

Records save atomically with a short debounce. An unreadable file is retained and
backed up before a later save. There is a 2,000-record limit; reaching it keeps
existing notes and reports an operational warning instead of silently deleting
them. New identities cannot be retained until records are deleted. The history
file should stay local; no notes or attendance are uploaded to the service.

## Statistics setup and new-account kicks

Direct Hypixel API mode is now the default. Enter your key under
**OneConfig > Data > Stats setup**, or use `/partymod setapikey <key>`.
No backend is needed in direct mode. The key is masked in the settings UI, saved
locally without encryption, and sent only to Hypixel in the `API-Key` header.
The existing application-service source remains an optional alternative.

**Actions > New-account screening** controls automatic removal of newly observed
party joins whose first Hypixel login is below the configured age. It starts on,
with a 90-day minimum. Unknown dates/API failures never trigger removal. Existing
members found through `/p list` are assessed without automatic removal. The action
requires a fresh roster and removal authority, and rechecks eligibility before
sending. Approve for event overrides it while that approval remains current.
The separate block-after-confirmed-kick setting continues to apply afterward.

See [complete API-key setup and behaviour](docs/API-KEY-SETUP.md) for exact limits,
key storage, age meaning and the distinction between this requested local direct
mode and the backend design for distribution. Live key/API and server moderation
acceptance have not been tested. There is no key embedded in this build.

## Commands

`/partymod` or `/pmorganiser`: open organiser.

`/partymod settings`, `enable`, `disable`, `sync`, `ready`, `precheck`,
`wait <username>`, `setapikey <key>`, `help`: the corresponding local organiser controls.

## Build and verification

Set JAVA_HOME to a JDK 8 installation and put its `bin` directory first in PATH.
Run `gradlew.bat setupDecompWorkspace` on an initial legacy Forge workspace, then
`gradlew.bat build`. The project pins Forge 11.15.1.2318/stable_22, Gradle 2.7 and
bundled OneConfig API/loader dependencies. A fresh dependency cache still needs
working upstream repositories; the delivered build used existing cached Forge
dependencies with `--offline`.

Production output is `build/libs/PartyMod-2.0.4.jar`. `smokeJar reobfSmokeJar`
builds a separate, non-production probe; **never install the smoke probe in your
regular Minecraft instance**. `tools/Launch-Smoke.ps1` launches a separate local
test directory, seeds a synthetic 100-member roster and captures actual Minecraft
screens before exiting. Supply your own Minecraft/OneConfig cache and Java paths.
It does not log into Hypixel, use your account token or alter installed mods.

The release passed **126 Java regression tests and 11 Python service tests**.
An isolated Forge 1.8.9/Java 8 client loaded the production JAR, rendered the
100-member fixture and OneConfig sections, opened the native HUD editor and
completed an enable/disable cycle. See `docs/FEATURES.md`,
`docs/LIVE-VALIDATION.md` and the delivered verification report for exact scope.
Actual Hypixel messages, 100 real members, removal/block acknowledgement,
MW scoreboard sightings and HUD appearance during a live match remain unverified.

Third-party attribution and pinned dependencies are documented in
`THIRD-PARTY.md`. The original user-supplied ZIP is unchanged.
