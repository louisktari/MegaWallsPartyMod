# PartyMod 2.0.4 — API key and new-account kicks

## Set up statistics

1. Install `PartyMod-2.0.4.jar` in your Forge 1.8.9 instance, replacing any earlier PartyMod JAR.
2. Open `/partymod settings` and choose **Data > Stats setup**.
3. Set **Stats source** to **Direct Hypixel API** (the default).
4. Enter your key in **Hypixel API key**, then click **Check roster stats**.
   Alternatively use `/partymod setapikey <key>`, which selects direct mode,
   saves the key locally and starts roster assessments when active on Hypixel.
5. Enable the organiser and sync the current party. New joins are checked
   automatically; **Data > Retry assessments** checks the current roster again.

Direct mode contacts Mojang for name/UUID resolution, then Hypixel `/v2/player`.
Only the Hypixel request gets an `API-Key` header. Redirects are disabled. Keys
are masked in OneConfig and never printed by the command confirmation; the
profile file stores them locally without encryption. Do not share that profile.
Clearing the key cancels old work/cache and leaves statistics not assessed.
403/rejected-key responses report a setup problem, never a player violation.
Budgeting, caching, bounded queues, retries and stale-session checks remain.

No backend is required for direct mode. The optional application service remains
available as the other source option. Its access token is separate from the API
key. Source changes invalidate old cached results and in-flight checks.

Get credentials through the [Hypixel developer dashboard](https://developer.hypixel.net/).
Authentication/quotas are described in the [API documentation](https://api.hypixel.net/).
The direct mode was restored for this requested local build. It is not a claim
that key collection by a distributed public mod meets the current
[API policy](https://developer.hypixel.net/policies/); the backend option remains
the distribution-oriented design.

## Configure new-account removal

Under **Actions > New-account screening**:

- **Automatically kick new Hypixel accounts:** on by default.
- **Minimum Hypixel account age (days):** 90 by default; configurable from 1–365.

Age means elapsed time since the API's `firstLogin` on Hypixel. It does not mean
Minecraft account creation, account purchase, cheating or a competitive ban.
Accounts younger than the configured number of days are eligible; an account
exactly at the cutoff is not.

Only newly observed party joins in this client session are automatic-kick
candidates. Existing members discovered through a party-list sync are assessed
but not automatically removed. Duplicated join messages do not create a new
attempt. Unknown, missing, malformed, future or zero dates and API failures
cannot qualify someone for removal.

The organiser requires a fresh roster and your leader/moderator removal authority.
It refreshes a stale roster before proceeding. It rechecks membership, account
age, authority, the automatic-kick toggle and host approval immediately before
sending the queued `/p remove <name>`. **Approve for event** overrides the action
while that approval remains current. Disabling or changing the party/server
cancels unsent commands. No blind automatic retry occurs after an attempted kick.

After a recognised server kick confirmation, the existing **Block after confirmed
kicks** option queues `/block add <name>`. That option is independently configurable
and remains on by default. Failed/unknown removals do not imply a block or ignore.

## Verification

The update adds tests for direct response fields, identity mismatch, nullable data,
date/age boundaries, typed Dragon upgrades, header/redirect handling, source
cancellation and automatic-kick authority/approval/threshold guards.
The complete Java suite contains 126 tests. Isolated Minecraft rendering checks
use a synthetic roster and no real key or Hypixel connection. Live API/key
acceptance and actual party removal/block responses still need server testing.
