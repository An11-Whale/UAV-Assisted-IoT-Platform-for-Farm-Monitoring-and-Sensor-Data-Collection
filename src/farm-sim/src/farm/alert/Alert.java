package farm.alert;

import farm.common.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Alert domain model containing the detected condition, lifecycle status and immutable audit history.
 * Instances can be serialized for the alert API and reconstructed by the repository event log.
 */
public final class Alert {
    public record Event(Instant at, String action, String actor, String note) { }

    private final String id;
    private final String type;
    private final String severity;
    private final String nodeId;
    private final String metric;
    private final Double observed;
    private final Double thresholdMin;
    private final Double thresholdMax;
    private final String message;
    private final Instant createdAt;
    private String status;
    private final List<Event> history = new ArrayList<>();

    Alert(String id, String type, String severity, String nodeId, String metric,
          Double observed, Double thresholdMin, Double thresholdMax, String message,
          Instant createdAt, String status) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.nodeId = nodeId;
        this.metric = metric;
        this.observed = observed;
        this.thresholdMin = thresholdMin;
        this.thresholdMax = thresholdMax;
        this.message = message;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String id() { return id; }
    public String type() { return type; }
    public String severity() { return severity; }
    public String nodeId() { return nodeId; }
    public String metric() { return metric; }
    public Double observed() { return observed; }
    public Double thresholdMin() { return thresholdMin; }
    public Double thresholdMax() { return thresholdMax; }
    public String message() { return message; }
    public Instant createdAt() { return createdAt; }
    public String status() { return status; }
    public List<Event> history() { return Collections.unmodifiableList(history); }

    boolean active() { return "OPEN".equals(status) || "ACKNOWLEDGED".equals(status); }

    void addEvent(Event event, String newStatus) {
        history.add(event);
        if (newStatus != null) status = newStatus;
    }

    String fingerprint() { return fingerprint(type, nodeId, metric); }

    static String fingerprint(String type, String nodeId, String metric) {
        return type + "|" + nodeId + "|" + (metric == null ? "-" : metric);
    }

    public String toJson() {
        StringBuilder b = new StringBuilder("{\"id\":").append(Json.quote(id))
                .append(",\"type\":").append(Json.quote(type))
                .append(",\"severity\":").append(Json.quote(severity))
                .append(",\"nodeId\":").append(Json.quote(nodeId))
                .append(",\"metric\":").append(metric == null ? "null" : Json.quote(metric))
                .append(",\"observed\":").append(number(observed))
                .append(",\"thresholdMin\":").append(number(thresholdMin))
                .append(",\"thresholdMax\":").append(number(thresholdMax))
                .append(",\"message\":").append(Json.quote(message))
                .append(",\"createdAt\":").append(Json.quote(createdAt.toString()))
                .append(",\"status\":").append(Json.quote(status))
                .append(",\"history\":[");
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) b.append(',');
            Event e = history.get(i);
            b.append("{\"at\":").append(Json.quote(e.at().toString()))
                    .append(",\"action\":").append(Json.quote(e.action()))
                    .append(",\"actor\":").append(Json.quote(e.actor()))
                    .append(",\"note\":").append(Json.quote(e.note())).append('}');
        }
        return b.append("]}").toString();
    }

    private static String number(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.4f", value);
    }
}
