package fr.alexdoru.partymod.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.PlayerStats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Looks players up directly against the Mojang and Hypixel public APIs on one
 * background thread, spacing requests and caching results. The Hypixel API key is
 * only ever sent to api.hypixel.net.
 */
public final class HypixelClient {

    public static final class Result {
        public final String name;
        public final PlayerStats stats;
        public final String error;

        Result(String name, PlayerStats stats, String error) {
            this.name = name;
            this.stats = stats;
            this.error = error;
        }
    }

    private static final class Cached {
        final PlayerStats stats;
        final long at;

        Cached(PlayerStats stats, long at) {
            this.stats = stats;
            this.at = at;
        }
    }

    private static final int MAX_BODY = 2 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MegaWallsPartyMod-api");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final String userAgent;
    private long nextRequestAt;

    public HypixelClient(String version) {
        userAgent = "MegaWallsPartyMod/" + version;
    }

    /**
     * Queues a lookup. The callback runs on the worker thread.
     *
     * @param knownUuid the player's UUID if already known (e.g. from the tab list), which
     *                  skips the Mojang lookup; null to resolve it from the name
     *
     * @return false if the same player is already being looked up
     */
    public synchronized boolean lookup(String name, String knownUuid, String apiKey, int cacheMinutes,
                                       int requestsPerMinute, Consumer<Result> callback) {
        String key = name.toLowerCase(Locale.ROOT);
        Cached hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at < cacheMinutes * 60_000L) {
            callback.accept(new Result(name, hit.stats, ""));
            return true;
        }
        if (!inFlight.add(key)) return false;
        long spacing = 60_000L / Math.max(1, requestsPerMinute);
        worker.submit(() -> {
            Result result = fetch(name, knownUuid, apiKey, spacing);
            synchronized (this) {
                inFlight.remove(key);
                if (result.stats != null) {
                    cache.put(key, new Cached(result.stats, System.currentTimeMillis()));
                    while (cache.size() > 500) cache.remove(cache.keySet().iterator().next());
                }
            }
            try {
                callback.accept(result);
            } catch (RuntimeException ignored) {
                // A UI callback failure must not kill the worker.
            }
        });
        return true;
    }

    public synchronized void forget(String name) {
        cache.remove(name.toLowerCase(Locale.ROOT));
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    private Result fetch(String name, String knownUuid, String apiKey, long spacing) {
        if (!ChatEvents.validName(name)) return new Result(name, null, "Invalid username");
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) return new Result(name, null, "No Hypixel API key set (/mwp setkey <key>)");
        if (!key.matches("[A-Za-z0-9-]{8,64}")) return new Result(name, null, "Hypixel API key looks malformed");
        try {
            waitForSlot(spacing);
            String uuid, realName;
            if (knownUuid != null && knownUuid.matches("[0-9a-fA-F]{32}")) {
                uuid = knownUuid;
                realName = name;
            } else {
                JsonObject profile = get("https://api.mojang.com/users/profiles/minecraft/" + name, null);
                uuid = profile == null ? null : PlayerStats.string(profile, "id");
                realName = profile == null ? null : PlayerStats.string(profile, "name");
            }
            if (uuid == null || !uuid.matches("[0-9a-fA-F]{32}")) {
                return new Result(name, null, "Unknown Minecraft account");
            }
            JsonObject response = get("https://api.hypixel.net/v2/player?uuid=" + uuid, key);
            if (response == null || !Boolean.TRUE.equals(bool(response, "success"))) {
                return new Result(name, null, "Hypixel API returned no data");
            }
            return new Result(name, PlayerStats.fromHypixel(uuid, realName == null ? name : realName, response,
                    System.currentTimeMillis()), "");
        } catch (ApiError e) {
            return new Result(name, null, e.getMessage());
        } catch (IOException | RuntimeException e) {
            return new Result(name, null, "Lookup failed (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(name, null, "Cancelled");
        }
    }

    private void waitForSlot(long spacing) throws InterruptedException {
        long wait;
        synchronized (this) {
            long now = System.currentTimeMillis();
            wait = Math.max(0, nextRequestAt - now);
            nextRequestAt = Math.max(now, nextRequestAt) + spacing;
        }
        if (wait > 0) Thread.sleep(wait);
    }

    private static final class ApiError extends IOException {
        private static final long serialVersionUID = 1L;

        ApiError(String message) {
            super(message);
        }
    }

    /** Returns parsed JSON, or null for "no such player" (Mojang 204/404). */
    private JsonObject get(String address, String hypixelKey) throws IOException {
        URL url = new URL(address);
        if (hypixelKey != null && !url.getHost().equals("api.hypixel.net")) throw new IOException("Refusing to send key");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("Accept", "application/json");
        if (hypixelKey != null) c.setRequestProperty("API-Key", hypixelKey);
        try {
            int status = c.getResponseCode();
            if (hypixelKey == null && (status == 204 || status == 404)) return null;
            if (status == 429) {
                synchronized (this) {
                    nextRequestAt = Math.max(nextRequestAt, System.currentTimeMillis() + 60_000L);
                }
                throw new ApiError(hypixelKey == null ? "Mojang rate limit hit; retry shortly" : "Hypixel rate limit hit; pausing lookups for 60s");
            }
            if (hypixelKey != null && (status == 401 || status == 403)) throw new ApiError("Hypixel rejected the API key");
            if (status != 200) throw new ApiError("HTTP " + status + " from " + url.getHost());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > MAX_BODY) throw new IOException("Response too large");
                    out.write(buffer, 0, n);
                }
            }
            JsonElement json = new JsonParser().parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            if (!json.isJsonObject()) throw new IOException("Unexpected response");
            return json.getAsJsonObject();
        } finally {
            c.disconnect();
        }
    }

    private static Boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
    }
}
