# Optional PartyMod application service

This reference backend supplies only the fields used by PartyMod reviews. It is
not deployed or registered, and no real API key was used during development.
The local organiser does not need it to run.

## Operator setup

1. Register your application and obtain its server-side credentials through the
   [Hypixel developer dashboard](https://developer.hypixel.net/). Check the
   [current policy](https://developer.hypixel.net/policies/) before distributing
   or operating the application. This code does not grant API approval.
2. Install Python 3.10+ on the service machine. No Python packages are required.
3. Set `HYPIXEL_API_KEY` to that application's key on the service machine only.
   Set `PARTYMOD_SERVICE_TOKEN` to a distinct, randomly generated application
   token of at least 24 characters. Never embed either in distributed source.
4. Run `python partymod_service.py`. Defaults: `127.0.0.1:8080` and 20 uncached
   lookups/minute. Optional environment settings are `PARTYMOD_BIND`,
   `PARTYMOD_PORT` and `PARTYMOD_REQUESTS_PER_MINUTE`.
5. For your own loopback development instance, enter `http://127.0.0.1:8080` and
   the application token under OneConfig > Data. Remote clients need an HTTPS
   reverse proxy in front of this loopback service. Use proxy connection limits,
   request timeouts and secret management; the Python HTTP server is a reference
   backend, not a hardened public edge server.

The client URL is the base URL. It appends
`/partymod/v1/players/<username>`. A base path may be used when the reverse proxy
maps it to the backend route. Do not paste a Hypixel key into OneConfig's token
field. The mod sends its application token only to the configured service.

## Behaviour and limits

Only the fixed authenticated GET route above is available. It resolves a standard
Mojang username, reads `/v2/player`, and returns a normalised summary. There are
no generic proxy, nickname discovery, online-status tracking or continuous
polling routes. No local notes or attendance records leave the client.

Upstream work is serialised. Name lookups cache for 24 hours; player summaries
cache for two hours. Each cache is bounded to 2,048 entries. The application
budget and upstream rate-limit headers govern uncached checks. Sixteen request
handlers may enter the application at once; excess work receives 503. Configure
the reverse proxy's connection bounds too, because the stdlib HTTP server creates
a thread before application admission. Missing or wrongly typed fields remain
null. Failures give explicit unavailable responses, not zero-valued assessments.

Upstream URLs are fixed. API keys use a header, never a query parameter, and
redirects are rejected. Responses are size-bounded. Access logs are disabled to
avoid logging tokens and player lookups. The application token is shared in this
reference implementation; an operator may add separate client tokens, revocation
and abuse control before opening it to a large audience.

The summary includes identity, source, fetched timestamp, firstLogin, network
level, quest count, MW win/loss/final counts, legendary achievement count and
the five typed upgrade fields for 27 classes. The tests prove transformation
against synthetic payloads; they do not establish current live field population
for every class or privacy setting. Null unlocked/upgrade data does not assert
that a class is maxed. Standard Mojang identity is not proof of a nick's identity.

Run `python -m unittest discover -s service -v` from the project root to execute
the isolated tests. No real upstream requests or keys are used by those tests.
