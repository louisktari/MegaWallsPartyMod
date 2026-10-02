# PartyMod 2.0.4 — second review

Reviewed 1 October 2026. This pass follows the [2.0.3 source/purpose audit](REVIEW-2.0.3.md)
and focuses on interactions between the roster, async data, host decisions,
local records and rendered actions. The original source remains separate and
unchanged. Pasted ChatGPT suggestions were treated as background, not independent
instructions or evidence that their claimed tests were run here.

## Purpose and requested behaviour

The organiser still screens incoming party members using configurable network
level, recorded Mega Walls experience, first Hypixel login, FKD/WL, upgrades and
legendary skin checks. All applicable reasons are shown for host review. This is
still intended for a 100-player Mega Walls party; the UI/OneConfig/HUD surround
that workflow. Statistics alone do not prove cheating or a competitive ban.

The user's additions remain: masked direct API-key setup, automatic new-account
removal with current data/age/authority/membership guards, and `/block add <name>`
following a recognised kick confirmation. API failures, unknown data, voluntary
departures and local sightings do not establish a kick or ban. The prior bounded
login-sync correction remains in place. No live instance was replaced by this pass.

## Findings fixed

| Finding | Resulting behaviour | Verification |
| --- | --- | --- |
| A failed statistics refresh changed the review fingerprint and removed approval | Keep the last successful data reason fingerprint separately from current availability. Preserve approval through an outage, while actual loaded reason changes still invalidate it | Failure/recovery, unchanged and changed evidence tests |
| A known UUID could change without resetting approval/dismissal | Reset those decisions on a known identity change | UUID-change regression |
| A retained name record could lose its owner or transfer history to another UUID | Preserve ownership; isolate unresolved observations under `pending:<name>` when the name already has a known owner; only migrate compatible or unresolved records | Owner mismatch, unresolved access, restart and delete tests |
| A `max` merge lost new joins/sightings when canonical history already had a larger count | Track unresolved observation deltas and add them once to the canonical history; retain conservative max handling for overlapping legacy snapshots | 100-history + 1 join / + 2 sightings, repeated migration and reload |
| Overflow notes did not merge into an existing UUID record | Search the bounded overflow buffer during migration, promote into existing records and clear the capacity warning when appropriate | Full 2,000-record fixture |
| Deleting a resolved identity left its owned name aliases | Delete the UUID and all records bound to that identity; unresolved deletion cannot delete another known owner's alias | Resolved alias and unresolved deletion tests |
| Long or secondary notes could be lost during migration | Keep backing aliases when notes cannot fit or contain separately retained history; do not replay pending count deltas or duplicate merged note text. Resolved pending aliases move to identity-owned archive keys so a later unknown member cannot inherit them | Long/secondary-note migration, repeated access, identity isolation and reload tests |
| HTML-escaped notes could produce a valid saved file above the old 8 MB read cap | Raise the bounded read cap to 32 MB, consistent with the 2,000-record, two 1,000-character-note limits and worst-case escaping | Save/reload 1,400 maximal escaped notes |
| Case variants counted as separate roster entries but became one member on commit | Reject canonical-name duplicates before changing the current roster | Case-duplicate transactional roster test |
| A player called `None` was skipped even when rank/bullet evidence identified a player | Treat only the entire unadorned `None` row as an empty placeholder | Ranked and marked-name fixtures |
| Leadership transfer left readiness acknowledgements from the old leader | End the old readiness round; failed/cancelled sends also clear only their matching round | Transfer, failed send, newer-round and success tests |
| Named self-departure/removal could leave a partial current party | End the session and cancel pending actions; do not auto-block self | Runtime routing review; existing cancellation tests |
| Same-key notification deduplication hid changed enable/disable state | Deduplicate identical text/state only; keep rapid state changes | Enable → disable → enable test |
| Routine bursts hid an operational problem toast | Retain the operational toast for its display interval while logging routine events | 100-join burst test |
| An expired lookup callback could cancel work after the next HTTP job had already been selected | Recheck cancellation epoch and job ownership before starting the selected lookup | Local HTTP fixture observes zero requests |
| Displayed controls could act after identity or evidence changed between draw and click | Bind controls/confirmation to membership, generation, UUID and reasons; reject stale actions and resolve the current local record at execution | Ticket tests plus real isolated GUI stale-click rejection and fresh-click success |

Thirteen newly written reproductions failed against the pre-fix behaviour during
this pass. Additional tests cover interactions and behaviours already handled
correctly. A deeply nested JSON fixture passed the existing client and confirms
that a subsequent lookup still completes; no new JSON-depth fix is claimed.

## Verification and boundaries

- **126 Java regression tests**, zero failures/errors.
- **11 Python optional-service tests**, synthetic upstream and local HTTP only.
- Java 8 production build and reobfuscation; package verifier checks class major
  versions, metadata, loader manifest, duplicates and absence of test/probe classes.
- Isolated actual Minecraft 1.8.9 / Forge 11.15.1.2318 startup with cached OneConfig,
  100 synthetic roster members, organiser and settings screenshots, native HUD
  editor opening, enable/disable cycle, note resize/rejoin input, stale player
  action rejection and successful fresh action. No Hypixel connection or real key.
- Every production class in the delivered JAR is compared byte-for-byte with
  the JAR loaded by that isolated client. The source archive excludes runtime
  records, credentials, temporary clients and build/cache directories.

Current live Hypixel party formats, command acceptance/acknowledgements, role
permissions, privacy and statistics population, nick identity, 100 real members,
Mega Walls scoreboard sightings, HUD/quiet mode in a match and other modpacks
still require live validation. A launch/render probe cannot establish those.
The installed client previously reported was 2.0.1; this pass produces a local
2.0.4 release rather than silently replacing it.

Known design limits remain: old overlapping attendance snapshots use a
conservative maximum because their shared observations cannot be reconstructed;
new unresolved observations have explicit deltas. Local sightings are client
observations, not complete match attendance. Records are bounded to 2,000 stored
entries and 256 overflow entries, with an explicit unsaved-note warning at capacity.
One unadorned `None` row remains ambiguous without rank/status evidence. HTTP/API
errors remain unavailable data rather than evidence against a player. No audit
guarantees absence of every possible future bug or changed server response.

Use [the server checklist](LIVE-VALIDATION.md) for the remaining checks. Keep only
one PartyMod version installed when manually replacing the JAR.
