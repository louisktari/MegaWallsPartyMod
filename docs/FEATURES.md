# Requested improvements and implementation

This table maps the 24 requested improvements to the delivered source. Offline
fixtures and an isolated client run establish implementation/build/rendering,
not live Hypixel behaviour. A public service has not been registered or deployed.

| # | Delivered behaviour | Evidence / boundary |
| --- | --- | --- |
| 1 | Repeatable enable/disable, pending requests/commands cancelled | Generation cancellation tests; actual client toggle cycle |
| 2 | Server/party generations, membership IDs and assessment serials bind delayed work and confirmations | Session, rejoin, stale command and request cancellation tests |
| 3 | Joins, departures, kicks, promotions, transfers, disbands and disconnect/reconnect state | Anchored parser fixtures; current server formats need capture |
| 4 | Transactional complete `/p list` roster; one-shot sync on activation; manual recovery | 100-member/incomplete/changed-roster tests; completion stops timeout timer |
| 5 | Rank-tolerant anchored parser and recovery sync | Synthetic unranked/VIP/MVP/YOUTUBE/GM/ADMIN-style fixtures; live capture pending |
| 6 | Not assessed, pending, loaded/cached, review, unavailable, failed, cancelled | Typed result/state tests; unknown is not an accusation |
| 7 | One bounded worker, budget, bounded cache/queue, duplicate sharing, controlled backoff/retry | Local HTTP tests for cache, malformed responses, identity mismatch, cancellation and 429 recovery |
| 8 | User-requested direct API-key mode plus optional application-specific backend | Direct credential/header tests and backend auth/no-proxy tests; public distribution policy is not claimed |
| 9 | Actual firstLogin, nullable typed numbers and explicit unavailable results | Missing/malformed/firstLogin/denominator tests; no `_id` timestamp inference |
| 10 | 27 classes including Angel, Dragon, Sheep; unlocked state, advanced zero-game upgrades and complete maxed upgrades | Class/locked/partial/Dragon field tests; live population of fields unverified |
| 11 | Configurable review rules, sample counts, free starter exclusions and honest progression caveats | Rule tests and native OneConfig sliders; alternative legitimate progression remains possible |
| 12 | All applicable reasons, source and freshness in scrollable detail panel | Multi-reason tests; actual roster screen render |
| 13 | Separate Remove, Block, Ignore and local Host blocklist; no automatic ignore | Confirmation UI and runtime routing; requested automatic block is explained on removal |
| 14 | Fresh membership/role checks, serialized commands, duplicate suppression and matching acknowledgements | Queue/authority/ack tests; hundred-block timeout burst test; live server acknowledgements pending |
| 15 | Search, filters, manual stable sorting, identity-based selection and details | Actual synthetic 100-member GUI render; live user interaction check remains advisable |
| 16 | Review queue stays available across screen changes, with local host decisions | Material reason/dismissal tests; event decisions are not falsely restored as current roster on restart |
| 17 | Grouped review bursts, deduplicated toasts, operational event log and quiet routine messages | Bounded notification implementation; log lasts for the client session |
| 18 | Advisory pre-queue roster, role, review, data, readiness and disconnect checklist | Actual event-desk render; no server-access guarantee |
| 19 | New random readiness identifier per round, exact sender/roster matching and manual ready mark | Current-round/old-round/nonmember tests; live party-chat availability unknown |
| 20 | Visible disconnect grace and local waitlist with host-triggered invitations | Bounded/deduplicated/persistence tests; no automatic removal or reserved server capacity |
| 21 | Local notes, host blocklist, separate party joins/MW sightings, UUID migration and deletion | Storage and migration tests, atomic saves and corrupt-file backup; witnessed sightings are not complete match attendance |
| 22 | Calendar-month or day activity gap from dated local MW evidence | Unknown-activity and honest-evidence tests; no inferred competitive-ban lookup |
| 23 | P shortcut, OneConfig sections/native HUD, keyboard navigation, quiet mode and remembered filter/sort | Actual settings/native editor opened; HUD defaults off; live-match rendering still needs checking |
| 24 | Regression suite, artifact checks, isolated real Minecraft load/render and a server checklist | 126 Java + 11 Python tests; actual 100-player Hypixel event and commands have not been run |

The auto-block requirement is additional to the original 24 items. Only a
recognised kick confirmation triggers it. Repeated kick events share a per-session
deduplication window; voluntary left, disconnect and roster disappearance do not.
Disabling or changing the session cancels queued automatic blocks. The toggle
is on by default under OneConfig > Actions.

The detailed original audit remains a description of the supplied source,
including findings that this rewrite addresses. It is not a statement that every
future server format, legitimate statistical pattern or modpack conflict has
been tested. The build does not contain the old unsafe exclude command or the
20-worker API client. Direct mode uses the bounded single-worker client. See API-KEY-SETUP.md for the 2.0.1 addition.
