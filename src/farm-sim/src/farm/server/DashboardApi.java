package farm.server;

import com.sun.net.httpserver.HttpExchange;
import farm.alert.AlertPolicy;
import farm.common.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cầu nối farm-sim -> dashboard React (src/dashboard).
 * Dịch dữ liệu gói node (sensor/value/unit) sang mô hình SensorNode mà dashboard dùng:
 *   GET /api/v1/sensors/latest
 *   GET /api/v1/sensors/{nodeId}/history?metric=soilMoisture&range=24h|7d
 *
 * Quy ước chuyển đổi (cùng ngưỡng với METRIC_CONFIGS trong App.tsx):
 *   - temperature "C" -> "°C", light "lux" -> "Lux"
 *   - water_level (% đầy bể) -> cm, giả định bể sâu 35 cm (trục biểu đồ dashboard là 0-35 cm)
 *   - ngoài khoảng [lo, hi] -> metric ABNORMAL (riêng ánh sáng: ngưỡng dưới chỉ áp dụng 06:00-18:00 ICT); có metric ABNORMAL hoặc pin < 20% -> node WARNING
 *   - số đo mới nhất cũ hơn 48h -> OFFLINE; node đã cấp phép nhưng chưa có dữ liệu -> NO_DATA
 */
public final class DashboardApi {
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private record Metric(String sensorKey, String dashKey, String unit, double scale) { }

    private static final List<Metric> METRICS = List.of(
            new Metric("soil_moisture", "soilMoisture", "%", 1),
            new Metric("temperature", "airTemperature", "\u00b0C", 1),
            new Metric("humidity", "airHumidity", "%", 1),
            new Metric("light", "lightIntensity", "Lux", 1),
            new Metric("ph", "ph", "pH", 1),
            new Metric("water_level", "waterLevel", "cm", 0.35));

    private record Point(Instant ts, double value) { }

    private static final class NodeState {
        double battery = 100;
        Instant lastMeasurement;
        Instant lastSync;
        final Map<String, Double> latest = new HashMap<>();          // sensorKey -> giá trị thô
        final Map<String, List<Point>> history = new HashMap<>();    // sensorKey -> chuỗi thời gian
    }

    private final Map<String, NodeState> nodes = new TreeMap<>();
    private final AlertPolicy policy;

    public DashboardApi() { this(AlertPolicy.inMemory()); }

    public DashboardApi(AlertPolicy policy) { this.policy = policy; }

    /** Khai báo node đã cấp phép để dashboard thấy NO_DATA trước chuyến bay đầu tiên. */
    public synchronized void register(String nodeId) {
        nodes.computeIfAbsent(nodeId, k -> new NodeState());
    }

    /** Gọi sau khi một gói đã qua validate + chống trùng. readings: chỉ các số đo status OK. */
    public synchronized void onPacket(String nodeId, Instant ts, Instant rxTs, double battery,
                                      Map<String, Double> okReadings, List<String> errorSensors) {
        NodeState st = nodes.computeIfAbsent(nodeId, k -> new NodeState());
        for (Map.Entry<String, Double> e : okReadings.entrySet())
            st.history.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Point(ts, e.getValue()));
        // Gói đến trễ/không theo thứ tự (store-and-forward): chỉ gói mới hơn mới cập nhật "mới nhất"
        if (st.lastMeasurement == null || ts.isAfter(st.lastMeasurement)) {
            st.lastMeasurement = ts;
            st.battery = battery;
            st.latest.putAll(okReadings);
            for (String s : errorSensors) st.latest.remove(s);
        }
        if (rxTs != null && (st.lastSync == null || rxTs.isAfter(st.lastSync))) st.lastSync = rxTs;
    }

    // ---------------------------------------------------------------- HTTP

    /** Dispatch cho context /api/v1/sensors */
    public void handle(HttpExchange ex) throws IOException {
        if ("OPTIONS".equals(ex.getRequestMethod())) {      // CORS preflight (dashboard gửi header Content-Type)
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) { reply(ex, 405, "{\"error\":\"GET only\"}"); return; }

        String path = ex.getRequestURI().getPath();
        String rest = path.length() > "/api/v1/sensors".length() ? path.substring("/api/v1/sensors".length()) : "";
        String[] seg = rest.replaceAll("^/+", "").split("/");
        if (seg.length == 1 && seg[0].equals("latest")) {
            reply(ex, 200, latestJson());
        } else if (seg.length == 2 && seg[1].equals("history")) {
            Map<String, String> q = query(ex.getRequestURI().getQuery());
            String json = historyJson(seg[0], q.getOrDefault("metric", "soilMoisture"), q.getOrDefault("range", "24h"));
            if (json == null) reply(ex, 404, "{\"error\":\"khong co node/metric nay\"}");
            else reply(ex, 200, json);
        } else {
            reply(ex, 404, "{\"error\":\"not found\"}");
        }
    }

    private synchronized String latestJson() {
        Instant now = Instant.now();
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, NodeState> e : nodes.entrySet()) {
            if (!first) b.append(',');
            first = false;
            appendNode(b, e.getKey(), e.getValue(), now);
        }
        return b.append(']').toString();
    }

    private void appendNode(StringBuilder b, String id, NodeState st, Instant now) {
        int n = nodeNumber(id);
        boolean zoneA = n <= 3;
        String status;
        boolean anyAbnormal = false;
        StringBuilder metrics = new StringBuilder();
        for (Metric m : METRICS) {
            Double raw = st.latest.get(m.sensorKey());
            if (raw == null) continue;
            double v = raw * m.scale();
            // Ánh sáng: ngưỡng dưới chỉ có nghĩa ban ngày (06:00-18:00 ICT), ban đêm tối là bình thường
            boolean lowCounts = !m.sensorKey().equals("light") || isDaytime(st.lastMeasurement);
            AlertPolicy.Rule rule = policy.ruleFor(id, m.sensorKey());
            boolean abnormal = rule != null && ((lowCounts && raw < rule.min()) || raw > rule.max());
            anyAbnormal |= abnormal;
            if (metrics.length() > 0) metrics.append(',');
            metrics.append(String.format(Locale.ROOT, "\"%s\":{\"value\":%.2f,\"unit\":%s,\"status\":\"%s\"}",
                    m.dashKey(), v, Json.quote(m.unit()), abnormal ? "ABNORMAL" : "NORMAL"));
        }
        if (st.lastMeasurement == null) status = "NO_DATA";
        else if (Duration.between(st.lastMeasurement, now).compareTo(policy.staleAfter()) > 0) status = "OFFLINE";
        else if (anyAbnormal || st.battery < policy.lowBatteryPct()) status = "WARNING";
        else status = "NORMAL";

        b.append("{\"id\":").append(Json.quote(id))
                .append(",\"name\":").append(Json.quote("Node c\u1ea3m bi\u1ebfn " + id))
                .append(",\"farmId\":\"farm-01\"")
                .append(",\"zoneId\":").append(Json.quote(zoneA ? "zone-01" : "zone-02"))
                .append(",\"zoneName\":").append(Json.quote(zoneA ? "Khu d\u00e2u t\u00e2y A" : "Khu c\u00e0 ph\u00ea \u0111\u1ed3i"))
                .append(String.format(Locale.ROOT, ",\"batteryPct\":%.1f", st.battery))
                .append(",\"lastMeasurementAt\":").append(st.lastMeasurement == null ? "null" : Json.quote(st.lastMeasurement.toString()))
                .append(",\"lastSyncAt\":").append(st.lastSync == null ? "null" : Json.quote(st.lastSync.toString()))
                .append(",\"status\":\"").append(status).append('"')
                .append(",\"metrics\":{").append(metrics).append("}}");
    }

    private synchronized String historyJson(String nodeId, String dashKey, String range) {
        NodeState st = nodes.get(nodeId);
        Metric metric = null;
        for (Metric m : METRICS) if (m.dashKey().equals(dashKey)) metric = m;
        if (st == null || metric == null) return null;

        boolean week = "7d".equals(range);
        List<Point> pts = st.history.getOrDefault(metric.sensorKey(), List.of());
        if (pts.isEmpty() || st.lastMeasurement == null) return "[]";
        // Cửa sổ tính lùi từ số đo mới nhất của node (dữ liệu mô phỏng nằm trong quá khứ)
        Instant from = st.lastMeasurement.minus(week ? Duration.ofDays(7) : Duration.ofHours(24));
        DateTimeFormatter label = DateTimeFormatter.ofPattern(week ? "dd/MM" : "HH:mm");

        Map<ZonedDateTime, double[]> buckets = new TreeMap<>();   // {tổng, số lượng}
        for (Point p : pts) {
            if (p.ts().isBefore(from)) continue;
            ZonedDateTime z = p.ts().atZone(ICT);
            ZonedDateTime key = week ? z.toLocalDate().atStartOfDay(ICT) : z.withMinute(0).withSecond(0).withNano(0);
            double[] acc = buckets.computeIfAbsent(key, k -> new double[2]);
            acc[0] += p.value();
            acc[1] += 1;
        }
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<ZonedDateTime, double[]> e : buckets.entrySet()) {
            if (!first) b.append(',');
            first = false;
            double avg = e.getValue()[0] / e.getValue()[1] * metric.scale();
            b.append(String.format(Locale.ROOT, "{\"timestamp\":%s,\"value\":%.2f}",
                    Json.quote(e.getKey().format(label)), avg));
        }
        return b.append(']').toString();
    }

    private static boolean isDaytime(Instant ts) {
        if (ts == null) return true;
        int h = ts.atZone(ICT).getHour();
        return h >= 6 && h < 18;
    }

    private static int nodeNumber(String id) {
        try { return Integer.parseInt(id.substring(id.lastIndexOf('-') + 1)); } catch (RuntimeException e) { return 99; }
    }

    private static Map<String, String> query(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String kv : q.split("&")) {
            String[] a = kv.split("=", 2);
            if (a.length == 2) m.put(a[0], a[1]);
        }
        return m;
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
