package farm.alert;

import farm.common.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only alert event store that preserves lifecycle history across restarts.
 * Active conditions are indexed by fingerprint to prevent duplicate open alerts.
 */
public final class AlertRepository {
    public record Creation(Alert alert, boolean created) { }

    private final Path file;
    private final Map<String, Alert> alerts = new LinkedHashMap<>();
    private final Map<String, String> activeByFingerprint = new LinkedHashMap<>();

    public AlertRepository(Path file) {
        this.file = file;
        if (file != null && Files.exists(file)) load();
    }

    public synchronized Creation createIfAbsent(String type, String severity, String nodeId, String metric,
                                                 Double observed, Double min, Double max, String message, Instant at) {
        String fingerprint = Alert.fingerprint(type, nodeId, metric);
        String activeId = activeByFingerprint.get(fingerprint);
        if (activeId != null) {
            Alert existing = alerts.get(activeId);
            if (existing != null && existing.active()) return new Creation(existing, false);
        }
        String id = "ALT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Alert alert = new Alert(id, type, severity, nodeId, metric, observed, min, max, message, at, "OPEN");
        alert.addEvent(new Alert.Event(at, "CREATED", "system", message), null);
        alerts.put(id, alert);
        activeByFingerprint.put(fingerprint, id);
        append(createEvent(alert));
        return new Creation(alert, true);
    }

    public synchronized Alert transition(String id, String status, String note, String actor, Instant at) {
        if (!List.of("OPEN", "ACKNOWLEDGED", "RESOLVED", "CLOSED").contains(status))
            throw new IllegalArgumentException("status phai la OPEN, ACKNOWLEDGED, RESOLVED hoac CLOSED");
        Alert alert = alerts.get(id);
        if (alert == null) throw new IllegalArgumentException("khong tim thay alert " + id);
        String safeActor = actor == null || actor.isBlank() ? "unknown" : actor;
        String safeNote = note == null ? "" : note;
        alert.addEvent(new Alert.Event(at, "STATUS_" + status, safeActor, safeNote), status);
        if (alert.active()) activeByFingerprint.put(alert.fingerprint(), id);
        else activeByFingerprint.remove(alert.fingerprint(), id);
        append(statusEvent(alert, at, status, safeActor, safeNote));
        return alert;
    }

    public synchronized Alert clearCondition(String type, String nodeId, String metric, Instant at) {
        String id = activeByFingerprint.get(Alert.fingerprint(type, nodeId, metric));
        if (id == null) return null;
        return transition(id, "RESOLVED", "Dieu kien canh bao da tro lai binh thuong", "system", at);
    }

    public synchronized Alert get(String id) { return alerts.get(id); }

    public synchronized List<Alert> list(String status, int limit) {
        return alerts.values().stream()
                .filter(a -> status == null || status.isBlank() || status.equals(a.status()))
                .sorted(Comparator.comparing(Alert::createdAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    public synchronized int size() { return alerts.size(); }

    private void load() {
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                try { applyStored(Json.obj(Json.parse(line))); }
                catch (RuntimeException ignored) { /* Giữ server chạy; dòng hỏng vẫn còn để điều tra. */ }
            }
        } catch (IOException e) { throw new UncheckedIOException("Khong doc duoc alert store " + file, e); }
    }

    private void applyStored(Map<String, Object> event) {
        if (event == null) return;
        String op = Json.str(event.get("op"));
        if ("CREATE".equals(op)) {
            String id = Json.str(event.get("id"));
            Instant at = Instant.parse(Json.str(event.get("at")));
            Alert a = new Alert(id, Json.str(event.get("type")), Json.str(event.get("severity")),
                    Json.str(event.get("nodeId")), Json.str(event.get("metric")), Json.num(event.get("observed")),
                    Json.num(event.get("min")), Json.num(event.get("max")), Json.str(event.get("message")), at, "OPEN");
            a.addEvent(new Alert.Event(at, "CREATED", "system", a.message()), null);
            alerts.put(id, a);
            activeByFingerprint.put(a.fingerprint(), id);
        } else if ("STATUS".equals(op)) {
            Alert a = alerts.get(Json.str(event.get("id")));
            if (a == null) return;
            String status = Json.str(event.get("status"));
            a.addEvent(new Alert.Event(Instant.parse(Json.str(event.get("at"))), "STATUS_" + status,
                    Json.str(event.get("actor")), Json.str(event.get("note"))), status);
            if (a.active()) activeByFingerprint.put(a.fingerprint(), a.id());
            else activeByFingerprint.remove(a.fingerprint(), a.id());
        }
    }

    private void append(String line) {
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { throw new UncheckedIOException("Khong ghi duoc alert store " + file, e); }
    }

    private static String createEvent(Alert a) {
        return "{\"op\":\"CREATE\",\"id\":" + Json.quote(a.id())
                + ",\"type\":" + Json.quote(a.type()) + ",\"severity\":" + Json.quote(a.severity())
                + ",\"nodeId\":" + Json.quote(a.nodeId()) + ",\"metric\":" + nullable(a.metric())
                + ",\"observed\":" + number(a.observed()) + ",\"min\":" + number(a.thresholdMin())
                + ",\"max\":" + number(a.thresholdMax()) + ",\"message\":" + Json.quote(a.message())
                + ",\"at\":" + Json.quote(a.createdAt().toString()) + "}";
    }

    private static String statusEvent(Alert a, Instant at, String status, String actor, String note) {
        return "{\"op\":\"STATUS\",\"id\":" + Json.quote(a.id()) + ",\"status\":" + Json.quote(status)
                + ",\"actor\":" + Json.quote(actor) + ",\"note\":" + Json.quote(note)
                + ",\"at\":" + Json.quote(at.toString()) + "}";
    }

    private static String nullable(String value) { return value == null ? "null" : Json.quote(value); }
    private static String number(Double value) { return value == null ? "null" : Double.toString(value); }
}
