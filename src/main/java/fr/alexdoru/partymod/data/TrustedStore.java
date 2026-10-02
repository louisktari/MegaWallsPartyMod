package fr.alexdoru.partymod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.alexdoru.partymod.core.ChatEvents;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Players the host always keeps: never flagged, never auto-removed. Saved to disk. */
public final class TrustedStore {
    private final File file;
    /** lower-case key -> display name, sorted for a stable file. */
    private final TreeMap<String, String> names = new TreeMap<>();

    public TrustedStore(File file) {
        this.file = file;
        load();
    }

    public synchronized boolean isTrusted(String name) {
        return name != null && names.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean add(String name) {
        if (!addQuietly(name)) return false;
        save();
        return true;
    }

    /** Adds without writing the file; call {@link #flush()} after a batch. */
    synchronized boolean addQuietly(String name) {
        if (!ChatEvents.validName(name) || isTrusted(name)) return false;
        names.put(name.toLowerCase(Locale.ROOT), name);
        return true;
    }

    public synchronized void flush() {
        save();
    }

    public synchronized boolean remove(String name) {
        if (name == null || names.remove(name.toLowerCase(Locale.ROOT)) == null) return false;
        save();
        return true;
    }

    public synchronized List<String> all() {
        return new ArrayList<>(names.values());
    }

    /** Replaces the whole list (used when the host edits it in OneConfig). Invalid names are skipped. */
    public synchronized void setAll(Iterable<String> newNames) {
        names.clear();
        for (String n : newNames) {
            String name = n == null ? "" : n.trim();
            if (ChatEvents.validName(name)) names.put(name.toLowerCase(Locale.ROOT), name);
        }
        save();
    }

    /** Parses free text (one name per line, or separated by commas/spaces). */
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String token : text.split("[\\s,;]+")) if (!token.isEmpty()) out.add(token);
        return out;
    }

    private void load() {
        if (file == null || !file.isFile()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonArray()) return;
            for (JsonElement e : root.getAsJsonArray()) {
                if (e.isJsonPrimitive() && ChatEvents.validName(e.getAsString())) {
                    names.put(e.getAsString().toLowerCase(Locale.ROOT), e.getAsString());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A corrupt file just means an empty trusted list; it is rewritten on the next change.
        }
    }

    private void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            JsonArray array = new JsonArray();
            for (String name : names.values()) array.add(new com.google.gson.JsonPrimitive(name));
            File tmp = new File(file.getPath() + ".tmp");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(array, writer);
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // Keep running; the in-memory list still applies this session.
        }
    }
}
