package fr.alexdoru.partymod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.PlayerStats;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everyone the host has removed, kept across sessions so repeat offenders are spotted on rejoin. */
public final class BlockHistory {

    public static final class Entry {
        public String name = "", reason = "", by = "";
        public long at;

        /** "22:14" for today, "2 Oct" otherwise. */
        public String when(long now) {
            String day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(at));
            boolean today = day.equals(new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(now)));
            return new SimpleDateFormat(today ? "HH:mm" : "d MMM", Locale.ROOT).format(new Date(at));
        }

        /** True when the mod removed them on its own (no host click). */
        public boolean automatic() {
            return by.isEmpty();
        }
    }

    private static final int LIMIT = 1000;
    private final File file;
    /** lower-case name -> entry, oldest first. */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    public BlockHistory(File file) {
        this.file = file;
        load();
    }

    public synchronized void record(String name, String reason, String by, long now) {
        if (put(name, reason, by, now)) save();
    }

    /** Adds an imported entry without touching an existing one; call {@link #flush()} after a batch. */
    synchronized boolean importEntry(String name, String reason, String by, long at) {
        return get(name) == null && put(name, reason, by, at);
    }

    public synchronized void flush() {
        save();
    }

    private boolean put(String name, String reason, String by, long now) {
        if (!ChatEvents.validName(name)) return false;
        Entry e = new Entry();
        e.name = name;
        e.reason = reason == null ? "" : reason;
        e.by = by == null ? "" : by;
        e.at = now;
        String key = name.toLowerCase(Locale.ROOT);
        entries.remove(key); // re-insert so it becomes the newest
        entries.put(key, e);
        while (entries.size() > LIMIT) entries.remove(entries.keySet().iterator().next());
        return true;
    }

    public synchronized Entry get(String name) {
        return name == null ? null : entries.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean remove(String name) {
        if (name == null || entries.remove(name.toLowerCase(Locale.ROOT)) == null) return false;
        save();
        return true;
    }

    /** Newest first (by removal time, so imported entries slot in where they belong). */
    public synchronized List<Entry> recent(int limit) {
        List<Entry> all = new ArrayList<>(entries.values());
        Collections.reverse(all);
        all.sort((a, b) -> Long.compare(b.at, a.at));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    private void load() {
        if (file == null || !file.isFile()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonArray()) return;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Entry e = new Entry();
                e.name = PlayerStats.string(o, "name");
                if (!ChatEvents.validName(e.name)) continue;
                String reason = PlayerStats.string(o, "reason"), by = PlayerStats.string(o, "by");
                e.reason = reason == null ? "" : reason;
                e.by = by == null ? "" : by;
                Double at = PlayerStats.number(o, "at");
                e.at = at == null ? 0 : at.longValue();
                entries.put(e.name.toLowerCase(Locale.ROOT), e);
            }
        } catch (IOException | RuntimeException ignored) {
            // Corrupt history just starts empty; it is rewritten on the next removal.
        }
    }

    private void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            File tmp = new File(file.getPath() + ".tmp");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(entries.values()), writer);
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // History still applies in memory for this session.
        }
    }

    /** For tests. */
    Map<String, Entry> raw() {
        return entries;
    }
}
