"""Application-specific PartyMod summary service; no general API proxy routes.

Register the application with Hypixel before use. Keep the Hypixel key server-side.
Use HTTPS termination for remote clients. Run with Python 3.10+; stdlib only.
"""
import hmac
import json
import math
import os
import re
import threading
import time
import urllib.error
import urllib.request
from collections import OrderedDict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CLASSES = "arcanist assassin automaton blaze cow creeper dreadlord enderman golem herobrine hunter moleman phoenix pigman pirate renegade shaman shark skeleton snowman spider squid werewolf zombie angel dragon sheep".split()

def number(value, minimum=0, maximum=2**31-1):
    return value if type(value) in (int, float) and math.isfinite(value) and minimum <= value <= maximum else None

def integer(value):
    n = number(value, maximum=2**29-1)
    return int(n) if n is not None and n == int(n) else None

def mapping(value):
    return value if isinstance(value, dict) else {}

def summary(player, profile, now):
    """Transform fixed, relevant fields only. No missing field is changed to zero."""
    player = mapping(player)
    mw = mapping(mapping(player.get("stats")).get("Walls3"))
    exp = number(player.get("networkExp"), maximum=10**16)
    level = None
    if exp is not None:
        floor_level = math.floor(-2.5 + math.sqrt(12.25 + .0008 * exp))
        baseline = (1250 * (floor_level-2) + 10000) * (floor_level-1)
        level = floor_level + (exp-baseline)/(2500*(floor_level-1)+10000)
    quests = mapping(player.get("quests")) if isinstance(player.get("quests"), dict) else None
    completed = None if quests is None else sum(len(q["completions"]) for q in quests.values() if isinstance(q, dict) and isinstance(q.get("completions"), list))
    achievements = player.get("achievementsOneTime")
    legendary = None if not isinstance(achievements, list) else len({a for a in achievements if isinstance(a, str) and a.startswith("walls3_legendary_")})
    classes = {}
    for name in CLASSES:
        c = mapping(mapping(mw.get("classes")).get(name))
        if not c:
            continue
        classes[name] = {"unlocked": c.get("unlocked") if type(c.get("unlocked")) is bool else None,
                         "kit": integer(c.get("skill_level_d")), "ability": integer(c.get("skill_level_a")),
                         "passive1": integer(c.get("skill_level_b")), "passive2": integer(c.get("skill_level_c")),
                         "gathering": integer(c.get("skill_level_g"))}
    deaths = integer(mw.get("final_deaths"))
    if deaths is None:
        deaths = integer(mw.get("finalDeaths"))  # fallback, not an unverified sum
    return {"uuid": profile["id"], "name": profile["name"], "source": "Hypixel player data via registered PartyMod service",
            "fetchedAt": int(now*1000), "firstLogin": number(player.get("firstLogin"), maximum=now*1000),
            "networkLevel": level, "completedQuests": completed,
            "megaWalls": {"wins": integer(mw.get("wins")), "losses": integer(mw.get("losses")),
                          "finalKills": integer(mw.get("final_kills")), "finalDeaths": deaths,
                          "legendarySkins": legendary, "classes": classes}}

class ServiceError(Exception):
    def __init__(self, status, message, delay=10):
        self.status, self.message, self.delay = status, message, delay

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise ServiceError(502, "Unexpected upstream redirect")

class Service:
    def __init__(self, api_key, token, budget=20, ttl=7200):
        self.api_key, self.token = api_key, token
        self.budget, self.ttl = max(1, min(budget, 60)), max(900, ttl)
        self.cache, self.names, self.requests = OrderedDict(), OrderedDict(), deque()
        self.lock = threading.RLock()
        self.pause_until = 0

    def fetch(self, url, headers=None):
        request = urllib.request.Request(url, headers={"User-Agent": "PartyMod-Service/2.0.0", **(headers or {})})
        try:
            with urllib.request.build_opener(NoRedirect()).open(request, timeout=6) as response:
                deadline = time.monotonic()+8
                raw = bytearray()
                while True:
                    chunk = response.read(4096)
                    if not chunk:
                        break
                    raw.extend(chunk)
                    if len(raw) > 1024*1024 or time.monotonic() > deadline:
                        raise ServiceError(502, "Upstream response exceeded bounds")
                return json.loads(raw), response.headers
        except urllib.error.HTTPError as error:
            if error.code == 429:
                try:
                    delay = max(1, min(120, int(error.headers.get("Retry-After", error.headers.get("RateLimit-Reset", "60")))))
                except ValueError:
                    delay = 60
                self.pause_until = time.time()+delay
                raise ServiceError(429, "Upstream request budget exhausted", delay)
            raise ServiceError(503 if error.code in (403, 500, 502, 503, 504) else 404,
                               "Upstream data unavailable; no assessment made")
        except (OSError, ValueError):
            raise ServiceError(502, "Malformed or unavailable upstream data")

    def lookup(self, name):
        if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", name):
            raise ServiceError(400, "Invalid player name")
        # Serialize upstream checks; identical requests share the cache.
        with self.lock:
            now = time.time()
            key = name.lower()
            cached = self.cache.get(key)
            if cached and now-cached[0] < self.ttl:
                return cached[1]
            if now < self.pause_until:
                raise ServiceError(429, "Request budget paused", max(1, int(self.pause_until-now)))
            while self.requests and now-self.requests[0] >= 60:
                self.requests.popleft()
            if len(self.requests) >= self.budget:
                raise ServiceError(429, "Application request budget exhausted", max(1, int(60-(now-self.requests[0]))))
            self.requests.append(now)
            known = self.names.get(key)
            if known and now-known[0] < 86400:
                profile = known[1]
            else:
                profile, _ = self.fetch("https://api.mojang.com/users/profiles/minecraft/"+name)
                if not isinstance(profile, dict) or not re.fullmatch(r"[a-f0-9]{32}", str(profile.get("id", ""))) or str(profile.get("name", "")).lower() != key:
                    raise ServiceError(404, "Name identity unresolved")
                self.names[key] = (now, profile)
                while len(self.names) > 2048:
                    self.names.popitem(last=False)
            root, headers = self.fetch("https://api.hypixel.net/v2/player?uuid="+profile["id"], {"API-Key": self.api_key})
            if not isinstance(root, dict) or root.get("success") is not True or not isinstance(root.get("player"), dict):
                raise ServiceError(404, "Player data unavailable")
            if str(root["player"].get("uuid", "")).replace("-", "") != profile["id"]:
                raise ServiceError(502, "Player identity mismatch; no assessment made")
            try:
                if int(headers.get("RateLimit-Remaining", "1")) <= 0:
                    self.pause_until = time.time()+max(1, min(300, int(headers.get("RateLimit-Reset", "60"))))
            except ValueError:
                self.pause_until = time.time()+60
            result = summary(root["player"], profile, time.time())
            self.cache[key] = (time.time(), result)
            while len(self.cache) > 2048:
                self.cache.popitem(last=False)
            return result

def serve(service, host="127.0.0.1", port=8080):
    slots = threading.BoundedSemaphore(16)
    class BoundedServer(ThreadingHTTPServer):
        def process_request(self, request, address):
            if not slots.acquire(blocking=False):
                request.settimeout(1)
                try:
                    request.sendall(b"HTTP/1.0 503 Service busy\r\nContent-Length: 0\r\nRetry-After: 5\r\n\r\n")
                finally:
                    self.shutdown_request(request)
                return
            try:
                super().process_request(request, address)
            except Exception:
                slots.release()
                raise
        def process_request_thread(self, request, address):
            try:
                super().process_request_thread(request, address)
            finally:
                slots.release()
    class Handler(BaseHTTPRequestHandler):
        def handle(self):
            try:
                super().handle()
            except (ConnectionError, TimeoutError):
                pass  # A disconnected/slow client does not produce an access traceback.
        def setup(self):
            self.request.settimeout(6)
            super().setup()
        def log_message(self, *_):
            pass  # credentials and player lookups are not written to access logs
        def do_GET(self):
            try:
                supplied = self.headers.get("Authorization", "")
                if not hmac.compare_digest(supplied, "Bearer "+service.token):
                    raise ServiceError(401, "Application access token required")
                match = re.fullmatch(r"/partymod/v1/players/([A-Za-z0-9_]{1,16})", self.path)
                if not match:
                    raise ServiceError(404, "Unknown application route")
                self.respond(200, service.lookup(match[1]))
            except ServiceError as error:
                self.respond(error.status, {"error": error.message}, error.delay)
            except Exception:
                self.respond(500, {"error": "Service assessment failed"}, 10)
        def respond(self, status, body, delay=None):
            raw = json.dumps(body, allow_nan=False, separators=(",", ":")).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "private, max-age=0")
            if delay is not None:
                self.send_header("Retry-After", str(delay))
            self.end_headers()
            self.wfile.write(raw)
    server = BoundedServer((host, port), Handler)
    server.daemon_threads = True
    return server

if __name__ == "__main__":
    key = os.environ.get("HYPIXEL_API_KEY", "")
    token = os.environ.get("PARTYMOD_SERVICE_TOKEN", "")
    if not key or len(token) < 24:
        raise SystemExit("Set a registered HYPIXEL_API_KEY and a PARTYMOD_SERVICE_TOKEN of at least 24 characters. Keys remain server-side.")
    server = serve(Service(key, token, int(os.environ.get("PARTYMOD_REQUESTS_PER_MINUTE", "20"))),
                   os.environ.get("PARTYMOD_BIND", "127.0.0.1"), int(os.environ.get("PARTYMOD_PORT", "8080")))
    print("PartyMod application service listening. Use HTTPS termination for remote access.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
