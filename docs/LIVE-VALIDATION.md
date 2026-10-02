# Server validation still required

Use a test party and cooperating players before relying on this build for a
100-player event. The supplied automated tests do not execute Hypixel commands.
Record the normal server response text when a format does not match; keep private
chat, tokens and unrelated player details out of any report.

1. Start with one active PartyMod JAR. Confirm Forge 1.8.9/Java 8, OneConfig load,
   P and `/partymod settings`. Test your actual modpack and screen/GUI scales.
2. Enable midway through a party. Capture `/p list` output with leader, moderators,
   members, all rank types and a large list. Check the roster commits exactly
   once, counts/roles agree and it remains fresh after ten seconds.
3. Test join/left/kick/disband, self leaving/joining another party, promotion,
   leadership transfer and disconnect/reconnect. A lost message or incomplete
   list must leave a visible sync requirement, not false complete membership.
4. With auto-block enabled, remove a cooperating test member. Check that the
   **server-confirmed kick** queues exactly one `/block add <name>` and that its
   actual success/already-blocked message finishes the action. Check normal
   `/p remove` outside the GUI and another moderator's kick too. This changes
   your block list; restore the cooperating account's previous state afterward.
5. Check voluntary departures, disconnects, party disband and roster omission
   send no automatic block. Turn auto-block off and confirm a kick sends none.
   Ignore must always require its own action. Check unranked, short and maximum
   length names; player chat quoting a kick must not trigger it.
6. Check role/membership changes while a removal dialog or queued command waits.
   Check duplicate clicks, refused commands, permission errors and slow/no server
   acknowledgement. Unconfirmed must not be described as successful or retried
   automatically. Disable/disconnect/party-change must cancel unsent work.
7. With a registered test service, verify actual Hypixel normal, missing, private
   and newly added class data. Compare counts/firstLogin against raw fields on the
   operator side. Missing/locked/upgrades remain typed unknown; no nick discovery.
   Validate actual quotas and header resets before increasing request budgets.
8. Run a 100-real-member join/sync burst. Confirm no UI stalls, no duplicate
   lookups, bounded queues, useful burst notifications and eventual explicit
   outcomes. Check disabled or previous-party results cannot populate the new
   session. Inject controlled 429/503/malformed responses only in your test service.
9. Start two readiness rounds. Only the current round's exact phrase from a
   current member can acknowledge it; old messages and nonmembers must fail.
   Test unavailable party chat and the manual selected-member fallback.
10. Validate disconnect grace, waitlist order, capacity, invitation message
    formats/timeouts and reconnect transitions. No automatic replacement/removal
    should happen. A configured capacity is local advisory information.
11. Enter an actual MW lobby and match. Confirm the lobby does not create match
    sightings; inspect the match scoreboard detector, visible nonspectator
    sightings and quiet mode. A player joining the party without entering the
    match must not gain a match sighting. Outside games and ban status stay unknown.
12. Enable and move the HUD in a world. Check counters/freshness at your GUI scale,
    after toggling and with other HUD mods. The isolated client opened the native
    editor from the title screen; that is not a live-match HUD check.
13. Restart and inspect notes/UUID migration/deletion and saved filters. Old current
    membership and readiness must not appear restored as live state. Check local
    disk-save failures are operational problems, not flags against players.

When a queue refusal does not identify a player, keep attribution unknown. No
successful local review/checklist proves server access or competitive-ban status.
