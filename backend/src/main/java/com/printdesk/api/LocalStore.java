package com.printdesk.api;

import com.printdesk.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny document store for running the UI locally (tickets, cases, settings, knowledge base).
 * Collections of JSON documents, kept in memory and written to one JSON file on every change.
 * In production this would be PostgreSQL or similar; the API shape stays the same.
 */
public final class LocalStore {
    private final Path file;
    private final Map<String, Map<String, Object>> data = new LinkedHashMap<>();

    public LocalStore(Path file) throws IOException {
        this.file = file;
        if (file != null && Files.exists(file)) {
            for (Map.Entry<String, Object> e : Json.parseObject(Files.readString(file, StandardCharsets.UTF_8)).entrySet()) {
                @SuppressWarnings("unchecked") Map<String, Object> docs = (Map<String, Object>) e.getValue();
                data.put(e.getKey(), new LinkedHashMap<>(docs));
            }
        }
    }

    public synchronized List<Object> list(String collection) {
        return List.copyOf(data.getOrDefault(collection, Map.of()).values());
    }

    public synchronized Object get(String collection, String id) {
        return data.getOrDefault(collection, Map.of()).get(id);
    }

    public synchronized void put(String collection, String id, Object doc) throws IOException {
        data.computeIfAbsent(collection, k -> new LinkedHashMap<>()).put(id, doc);
        save();
    }

    public synchronized boolean delete(String collection, String id) throws IOException {
        Map<String, Object> c = data.get(collection);
        boolean removed = c != null && c.remove(id) != null;
        if (removed) save();
        return removed;
    }

    private void save() throws IOException {
        if (file == null) return;
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(data), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
