package fr.alexdoru.partymod.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.PlayerStats;

import java.util.List;

/**
 * Exports and imports the trusted and blocked lists so co-hosts can share them.
 * Importing always <b>merges</b>: nothing you already have is removed or overwritten.
 */
public final class ListShare {
    public static final String FORMAT = "megawallspartymod-lists";
    static final int MAX_TEXT = 1_000_000, MAX_ENTRIES = 2000;

    public static final class Result {
        public int trustedAdded, blockedAdded, skipped;
        public String error = "";

        public boolean ok() {
            return error.isEmpty();
        }
    }

    private ListShare() {}

    public static String export(TrustedStore trusted, BlockHistory history, String exportedBy, long now) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", 1);
        root.addProperty("exportedAt", now);
        root.addProperty("exportedBy", exportedBy == null ? "" : exportedBy);
        JsonArray t = new JsonArray();
        for (String name : trusted.all()) t.add(new JsonPrimitive(name));
        root.add("trusted", t);
        JsonArray b = new JsonArray();
        List<BlockHistory.Entry> entries = history.recent(MAX_ENTRIES);
        for (int i = entries.size() - 1; i >= 0; i--) { // oldest first, so import keeps the order
            BlockHistory.Entry e = entries.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name);
            o.addProperty("reason", stripColour(e.reason));
            o.addProperty("by", e.by);
            o.addProperty("at", e.at);
            b.add(o);
        }
        root.add("blocked", b);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * Merges an export into the local lists. Players you already trust are never added
     * to the blocked list, and existing entries are left untouched.
     */
    public static Result importInto(String text, TrustedStore trusted, BlockHistory history) {
        Result r = new Result();
        if (text == null || text.trim().isEmpty()) {
            r.error = "Nothing to import";
            return r;
        }
        if (text.length() > MAX_TEXT) {
            r.error = "That export is too large";
            return r;
        }
        JsonObject root;
        try {
            JsonElement parsed = new JsonParser().parse(text.trim());
            if (!parsed.isJsonObject()) throw new IllegalArgumentException();
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            r.error = "That isn't a Mega Walls Party Mod export";
            return r;
        }
        if (!FORMAT.equals(PlayerStats.string(root, "format"))) {
            r.error = "That isn't a Mega Walls Party Mod export";
            return r;
        }
        JsonElement t = root.get("trusted");
        if (t != null && t.isJsonArray()) {
            int seen = 0;
            for (JsonElement e : t.getAsJsonArray()) {
                if (++seen > MAX_ENTRIES) break;
                String name = e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
                if (!ChatEvents.validName(name)) r.skipped++;
                else if (trusted.addQuietly(name)) r.trustedAdded++;
            }
        }
        JsonElement b = root.get("blocked");
        if (b != null && b.isJsonArray()) {
            int seen = 0;
            for (JsonElement e : b.getAsJsonArray()) {
                if (++seen > MAX_ENTRIES) break;
                JsonObject o = e.isJsonObject() ? e.getAsJsonObject() : null;
                String name = o == null ? null : PlayerStats.string(o, "name");
                if (!ChatEvents.validName(name)) {
                    r.skipped++;
                    continue;
                }
                if (trusted.isTrusted(name) || history.get(name) != null) continue;
                String reason = PlayerStats.string(o, "reason"), by = PlayerStats.string(o, "by");
                Double at = PlayerStats.number(o, "at");
                if (history.importEntry(name, limit(stripColour(reason), 200), limit(by, 16), at == null ? 0 : at.longValue())) r.blockedAdded++;
            }
        }
        if (r.trustedAdded > 0) trusted.flush();
        if (r.blockedAdded > 0) history.flush();
        return r;
    }

    static String stripColour(String s) {
        return s == null ? "" : ChatEvents.stripFormatting(s);
    }

    private static String limit(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
