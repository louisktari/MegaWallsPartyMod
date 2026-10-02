# PartyMod login command loop correction

Introduced in 2.0.2 and retained in the audited 2.0.3 release.

The affected installed build was PartyMod 2.0.1 in the MultiMC `1.8.9 22`
instance. Its SHA-256 matched the delivered 2.0.1 JAR.

The current log recorded `You are not currently in a party.` at 22:14:47 and
twice at 22:14:48, followed by `Woah slow down, you're doing that too fast!`.
The code path was `/p list` -> no-party response -> party reset set
`wasActive=false` -> next tick treated the still-connected client as newly
active -> another `/p list`. Cancellation also cleared the command spacing
timer, so the loop could send faster than the configured interval.

The correction separates connection activation from party replies. A no-party
or disband response clears the roster and pending work without restarting login
sync. A temporary world transition also cannot repeat that login request. A
genuine reconnect or explicit re-enable may initiate one fresh check.

Automatic new-account recovery may request one roster refresh. A failed or
incomplete attempt does not poll forever; use Sync roster to retry deliberately.
A completed sync or a genuinely new party permits a later recovery attempt.
Cancelling pending work retains command spacing. Both the captured `Woah slow
down` message and the existing command-throttle message pause queued commands
for ten seconds; rejected commands are not blindly resent.

Six regression tests cover the captured no-party response loop, temporary world
loss, reconnect/enable, failed recovery and pacing/cooldown across cancellation.
The complete Java test suite contains 68 tests. Verification also uses an
isolated Minecraft client with no real Hypixel connection. The original live
failure is established from the logs; a post-fix server login remains to be
verified by replacing the installed JAR and reconnecting.

Replace 2.0.1 with 2.0.3; keep just one PartyMod JAR in that instance's mods folder.
API-key setup, new-account checks and the independent block-after-kick option
remain available. No real credentials are embedded in this release.
