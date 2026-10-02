# PartyMod 2.0.3: original-purpose comparison and bug review

Reviewed the supplied PartyMod-master source, the rewritten 2.0.2 source and the
installed 2.0.1 instance. This release fixes the concrete issues below. It does
not establish that every possible defect or live server format has been tested.

## Purpose retained

The mod is still a Mega Walls party organiser and join screener for events of up
to 100 players. New party joins are assessed through Mojang name resolution and
Hypixel player data. Statistical evidence is presented for the host to review.
OneConfig provides the controls, native HUD settings and organiser shortcut.

| Original behaviour | Current behaviour |
| --- | --- |
| Assess future joins | Assess new joins plus a manually synchronised existing roster |
| Network level below 80; recorded games below 150 | Same default thresholds, editable in OneConfig |
| Account inferred from old `_id` timestamp; younger than 90 days | Actual Hypixel `firstLogin`, unknown dates preserved; still a default 90-day review |
| FKD >3.5 at <=25 games, >5 at <=250, >8 at <=500; FKD x W/L >4.95 | Same defaults; evaluate every applicable bracket, including independently edited thresholds |
| Kit checks exclude network level >100, use <15 games and quest/level context | Restored those experience gates; zero-game kit/ability >=4 reviewed; maxed-class checks require observed unlocked state and all five upgrade fields |
| Legendary achievements x12 / max(1, games) >=1 | Restored zero-game legendary review; unknown games remain unknown |
| Stop after the first detailed statistic warning | Collect all applicable reasons with counts and limitations |
| Replace zero deaths/losses with one | Zero denominators are explicitly unknown; no invented ratio |
| Exclude ignores and removes together | Separate manual Remove, Block and Ignore controls; no automatic Ignore |
| No automatic account-age removal or blocking | User-requested new-join account-age removal and `/block add` after recognised kick confirmation; both editable in OneConfig |

Free starter classes are excluded from kit reviews. Statistics are review
heuristics, not proof of cheating. The account-age rule concerns the player's
first Hypixel login, not their Minecraft account creation date or a ban.
Existing members discovered through `/p list` do not become automatic-age-kick
candidates. An approved member overrides the automatic age rule until their
review evidence materially changes.

Direct mode still requires the user's Hypixel API key under OneConfig > Data,
or `/partymod setapikey <key>`. Credentials stay out of chat output and the
delivered source, but OneConfig masking does not encrypt its profile file.
The optional application service uses a separate token and is not deployed.

## Findings fixed

| Area | Defect and resulting fix | Evidence |
| --- | --- | --- |
| Original rules | Zero-game legendary warnings were suppressed | Failing reproduction now passes |
| Original rules | Network-level >100 kit exemption was lost | Failing reproduction now passes |
| Original rules | Quest context was dropped from class reviews | Failing reproduction now passes |
| Direct stats | Completed quests were never populated | Failing direct-response fixture now passes |
| Original rules | First-game kit/ability upgrades below fully maxed were missed | Source comparison; restored advanced zero-game check |
| Configurable ratios | A first matching game bracket could hide a stricter edited later bracket | Failing reproduction now passes |
| Automatic removal | Old stats attached to Pending/Unavailable members could authorise a kick | Failing reproduction plus status/freshness tests; require loaded/cached results fetched within 24 hours |
| Automatic removal | Manually edited age values beyond OneConfig limits could remove much older accounts | Reject age settings outside 1–365 days; invalid-config regression test |
| Freshness | Future roster timestamps and unbounded profile values could extend moderation authority | Reject future snapshots; enforce 15–300-second freshness range |
| Automatic removal | Marked a removal attempted when merely queued; cancelled unsent work became permanently ineligible | Mark attempt in send path; deduplicate both removal kinds while queued |
| Command queue | Manual and account-age removals occupied separate slots for the same member | Failing reproduction now passes |
| Command queue | Send exceptions escaped the client tick | Failing reproduction now passes; failure recorded without blind retry |
| Command queue | Cancelled unsent blocks suppressed a subsequent explicit action | Deduplication window now starts after send; cancellation/retry test |
| Blocking | Turning auto-block off did not stop already queued automatic blocks | Send-time toggle test; manual block remains available |
| Invitations | Removing a waitlist entry left its command queued | Bind invitation to the exact current waitlist entry; send-time context test |
| Invitations | Dropped/re-added names could receive old action outcomes | Invitation result updates only the original entry |
| Readiness | Starting twice before send replaced the round without broadcasting the new identifier | Reject another queued round before changing the identifier |
| Readiness | A queued round could be sent after leadership/round changed | Send-time leader, freshness and identifier tests |
| Sessions | Interrupted lookups remained Cancelled after activity resumed | Resume cancelled assessments during active ticks, with generation/assessment guards |
| Synchronisation | Login-spam fix prevented age-screen recovery after a genuinely new party formed | One bootstrap sync on a new-party join; bounded no-party/new-party fixture |
| Synchronisation | An ordinary member's age candidates could cause pointless recovery syncs | Known ordinary members do not initiate age-moderation recovery |
| Leadership | Cancelling old work after a complete leader-change roster also invalidated the new snapshot | Cancel old work, retain the newly committed roster freshness |
| Data worker | A throwing cancellation callback aborted cleanup | Failing reproduction now passes; subscriber callbacks isolated |
| Data worker | Expiry callback could change the map being iterated and terminate the scheduler | Collect expired jobs before delivering callbacks; reentrant regression test |
| API budget | Disable/cancel reset the next-request time, bypassing pauses | Failing reproduction now passes; pacing and rate-limit pauses retained |
| API budget | 429 handling ignored Hypixel reset headers without Retry-After | Respect reset headers, bounded backoff; no fixed quota assumption |
| Credentials | A malformed service token could be echoed through a header exception | Validate token before headers; sanitised errors; no request/secret-echo fixture |
| Credentials | A magic service URL selected direct-key handling | Separate explicit mode from URL; Hypixel key headers restricted to its HTTPS host |
| Resource bounds | Duplicate request subscribers were unbounded | Maximum 64 subscribers per job, 128 queued jobs, bounded cache; explicit overflow errors |
| Notes | Resizing recreated a blank note field that could overwrite saved text | Actual Minecraft resize/rejoin probe |
| Notes | Same-name rejoin could receive the previous membership's textbox contents | Rebind input to generation/membership/UUID; actual Minecraft input probe |
| Notes | Name fallback was abandoned if a UUID record already existed | Failing merge test now passes; retain notes, block flag, latest observations and counts |
| Notes | Combining two full-length notes would truncate evidence on reload | Preserve overflow as a retained name note/backing record; save/reload test |
| Notes | At the record limit, a new temporary record was returned on every access | Stable bounded unsaved records, visible warning, promotion after room is freed; save/reload test |
| Storage | Shutdown serialization could race structural edits | Synchronised record/waitlist mutation and flushing; save errors preserve dirty state |
| Waitlist | Duplicate saved names were accepted during loading | Deduplicate saved names, restore invitations as Waiting |
| UI | Players' Pending filter hid loaded members on the Review queue | Dedicated review predicate, tested independently of Players filter |
| OneConfig | Deprecated dependency calls produced a startup stack warning | Use supported hideIf conditions; irrelevant fields hidden in their current mode |
| UI | Resize reverted filters to the last saved state | Read saved settings on first initialisation only |
| UI | Dismissed warnings were labelled No configured flags | Failing label test now passes; show Dismissed / Warnings dismissed |
| Pre-queue | Cancelled assessments were omitted from unavailable counts | Include them in the advisory checklist and unassessed view |
| Rendering | Rounded cards overwrote blend/texture enable state and blend functions | Restore incoming states/functions; isolated client rendering check |
| Text | Mixed encodings produced broken punctuation | Normalised Java sources to UTF-8 |
| Optional service | Upstream player UUID was not checked against the resolved profile | Reject mismatch before caching; synthetic test |
| Optional service | Thread cap applied after connections had already created workers | Acquire bounded slots before worker creation; 17-connection local test |
| Optional service | Slow response reads had no elapsed-time bound | Bound response size and elapsed reads; per-connection timeout |

Twelve targeted reproductions were run against the pre-fix source and all twelve
failed. The release regression suite passes 98 Java tests and 11 Python service
tests. The original automated tests remain in the suite, including transactional
100-member rosters, permissions, stale results, serial command acknowledgements,
100 blocked-player timeout queues and login-spam prevention.

## Runtime verification and limits

The release is loaded in an isolated Java 8 / Forge 1.8.9 client with cached
OneConfig. The probe renders 100 synthetic members, organiser screens and the
OneConfig settings pages, opens the native HUD editor, checks note preservation
across reinitialisation and rejoin, and exercises enable/disable/enable.
See the accompanying verification file for the completed run and package checks.

Current `/v2/player` authentication and rate-limit headers were checked against
[Hypixel's official API reference](https://api.hypixel.net/). Real API keys and
upstream player calls were not used during testing. The official reference does
not fully specify all Mega Walls class fields or live party-chat strings.

Remaining practical limits:

- Actual Hypixel kick/block/ignore/invite replies, party-list wrapping, privacy
  settings, current class data and queue acceptance need live capture. An
  unrecognised reply remains unconfirmed; moderation is not blindly retried.
- Mojang name resolution does not prove a nicked player's underlying identity.
  Stats alone cannot establish cheating, account ownership or competitive bans.
- At the default budget, 100 uncached lookups can take several minutes. Rate
  limits shared with other applications can extend this. The UI shows pending
  or unavailable states instead of declaring a completed scan.
- Zero final deaths/losses have no finite ratio in this implementation. That
  deliberate change avoids fabricating denominators, but differs from the old
  source's ratio heuristics.
- Local MW sightings require recognised scoreboard evidence and a visible,
  non-spectating party member. A sighting count is neither complete match
  attendance nor a unique-game count. Missing outside history stays unknown.
- Saved notes have a 2,000-record limit. Unsaved overflow is explicitly labelled
  and can be retained during the session; free space before relying on persistence.
- Public distribution still needs an appropriate registered API design and
  compliance with current provider policies. This local direct-key release is
  not a claim of provider approval.
- Isolated rendering does not establish live-match HUD behaviour or compatibility
  with every other mod in the user's actual instance.

The observed `1.8.9 22` instance still contains PartyMod-2.0.1.jar, and its latest
log contains the old repeated no-party replies and command throttle. Replace the
old JAR with 2.0.3 while Minecraft is closed, keep only one PartyMod JAR, and
restart. This audit does not install or change the live instance.
