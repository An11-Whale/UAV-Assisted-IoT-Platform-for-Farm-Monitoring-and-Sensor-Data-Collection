package farm.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import farm.alert.AlertEngine;
import farm.alert.AlertPolicy;
import farm.alert.AlertRepository;
import farm.alert.AlertTimeoutMonitor;
import farm.alert.EmailNotifier;
import farm.common.Json;
import farm.common.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone ingestion and dashboard API used for end-to-end testing before the production backend is available.
 *   POST /api/ingest    nhận lô bản ghi từ UAV gateway, validate (F47), chống trùng theo nodeId+seq
 *   GET  /api/readings  trả các số đo đã nhận (lọc ?nodeId=&sensor=&limit=)
 *   GET  /api/v1/sensors/latest, /api/v1/sensors/{id}/history  -> API cho dashboard React (xem DashboardApi)
 *   GET  /api/health
 */
public class MockIngestionServer {
    private record Row(String nodeId, String sensor, double value, String unit, String ts, double lat, double lon) {
        String toJson() {
            return String.format(Locale.ROOT,
                    "{\"nodeId\":%s,\"sensor\":%s,\"value\":%.2f,\"unit\":%s,\"ts\":%s,\"lat\":%.6f,\"lon\":%.6f}",
                    Json.quote(nodeId), Json.quote(sensor), value, Json.quote(unit), Json.quote(ts), lat, lon);
        }
    }

    private final HttpServer srv;
    private final ExecutorService executor;
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final AlertPolicy policy;
    private final AlertRepository alertRepository;
    private final EmailNotifier emailNotifier;
    private final AlertEngine alertEngine;
    private final AlertTimeoutMonitor timeoutMonitor;
    private final DashboardApi dashboard;
    private final AlertApi alertApi;
    public int accepted = 0;
    public int duplicates = 0;
    public int rejected = 0;

    private MockIngestionServer(HttpServer srv, ExecutorService executor, Path dataDir,
                                String adminKey, Map<String, String> env) {
        this.srv = srv;
        this.executor = executor;
        this.policy = new AlertPolicy(dataDir.resolve("alert-rules.properties"));
        this.alertRepository = new AlertRepository(dataDir.resolve("alerts.jsonl"));
        this.emailNotifier = new EmailNotifier(dataDir.resolve("email-outbox.jsonl"), env);
        this.alertEngine = new AlertEngine(policy, alertRepository, emailNotifier);
        this.timeoutMonitor = new AlertTimeoutMonitor(alertEngine, Duration.ofSeconds(1));
        this.dashboard = new DashboardApi(policy);
        this.alertApi = new AlertApi(policy, alertRepository, alertEngine, adminKey);
    }

    public static MockIngestionServer start(int port) throws IOException {
        return start(port, Path.of("data"), System.getenv("FARM_ADMIN_KEY"), System.getenv());
    }

    /** Overload dùng cho test: mọi state được cô lập trong dataDir. */
    public static MockIngestionServer start(int port, Path dataDir, String adminKey,
                                            Map<String, String> env) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        MockIngestionServer m = new MockIngestionServer(s, executor, dataDir, adminKey, env);
        s.createContext("/api/ingest", m::ingest);
        s.createContext("/api/readings", m::readings);
        s.createContext("/api/v1/sensors", m.dashboard::handle);   // API cho dashboard React
        s.createContext("/api/v1/config/alert-rules", m.alertApi::handleRules);
        s.createContext("/api/v1/alerts", m.alertApi::handleAlerts);
        s.createContext("/api/health", ex -> reply(ex, 200, "{\"status\":\"UP\"}"));
        s.setExecutor(executor);
        s.start();
        m.timeoutMonitor.start();
        Log.i("SERVER", "Mock ingestion API dang chay: http://localhost:" + port + "/api/ingest");
        return m;
    }

    public void stop() {
        timeoutMonitor.close();
        try { emailNotifier.flush(); }
        finally {
            srv.stop(0);
            executor.shutdownNow();
        }
    }

    /** Dashboard API (khai báo node, đọc trạng thái). */
    public DashboardApi dashboard() { return dashboard; }

    /** Khai báo node cho cả dashboard và bộ phát hiện timeout. */
    public void registerNode(String nodeId) {
        dashboard.register(nodeId);
        alertEngine.register(nodeId, Instant.now());
    }

    public AlertPolicy policy() { return policy; }
    public AlertRepository alerts() { return alertRepository; }
    public EmailNotifier email() { return emailNotifier; }

    public synchronized int rowCount() { return rows.size(); }

    private void ingest(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { reply(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> batch;
        try {
            batch = Json.obj(Json.parse(text));
        } catch (RuntimeException e) {
            reply(ex, 400, "{\"error\":" + Json.quote(e.getMessage()) + "}");
            return;
        }
        List<Object> records = batch == null ? null : Json.arr(batch.get("records"));
        if (records == null) { reply(ex, 400, "{\"error\":\"thieu records[]\"}"); return; }

        int acc = 0, dup = 0;
        StringBuilder rej = new StringBuilder();
        int rejCount = 0;
        Instant now = Instant.now();
        synchronized (this) {
            for (int i = 0; i < records.size(); i++) {
                Object rec = records.get(i);
                List<String> errors = SensorDataValidator.validate(rec, now);
                if (!errors.isEmpty()) {
                    if (rejCount++ > 0) rej.append(',');
                    rej.append("{\"index\":").append(i).append(",\"errors\":[");
                    for (int k = 0; k < errors.size(); k++) rej.append(k > 0 ? "," : "").append(Json.quote(errors.get(k)));
                    rej.append("]}");
                    continue;
                }
                Map<String, Object> r = Json.obj(rec);
                Map<String, Object> p = Json.obj(r.get("packet"));
                Map<String, Object> pos = Json.obj(r.get("pos"));
                String nodeId = Json.str(p.get("nodeId"));
                String key = nodeId + ":" + Json.num(p.get("seq")).longValue();
                if (!seen.add(key)) { dup++; continue; }   // gửi lại do mạng lỗi -> bỏ qua, không lưu trùng
                Map<String, Double> okReadings = new java.util.LinkedHashMap<>();
                List<String> errorSensors = new ArrayList<>();
                for (Object o : Json.arr(p.get("readings"))) {
                    Map<String, Object> rd = Json.obj(o);
                    if (!"OK".equals(Json.str(rd.get("status")))) { errorSensors.add(Json.str(rd.get("sensor"))); continue; }
                    okReadings.put(Json.str(rd.get("sensor")), Json.num(rd.get("value")));
                    rows.add(new Row(nodeId, Json.str(rd.get("sensor")), Json.num(rd.get("value")),
                            Json.str(rd.get("unit")), Json.str(p.get("ts")),
                            Json.num(pos.get("lat")), Json.num(pos.get("lon"))));
                }
                Instant gatewayReceivedAt = Instant.parse(Json.str(r.get("rxTs")));
                Instant measuredAt = Instant.parse(Json.str(p.get("ts")));
                Double battery = Json.num(p.get("battery"));
                double batteryPct = battery == null ? 100.0 : battery;
                dashboard.onPacket(nodeId, measuredAt, gatewayReceivedAt, batteryPct, okReadings, errorSensors);
                alertEngine.onPacket(nodeId, measuredAt, gatewayReceivedAt, now, batteryPct, okReadings, errorSensors);
                acc++;
            }
            accepted += acc;
            duplicates += dup;
            rejected += rejCount;
        }
        reply(ex, 202, "{\"batchId\":" + Json.quote(String.valueOf(batch.get("batchId")))
                + ",\"accepted\":" + acc + ",\"duplicates\":" + dup + ",\"rejected\":[" + rej + "]}");
    }

    private void readings(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        String nodeId = null, sensor = null;
        int limit = 100;
        if (q != null) {
            for (String kv : q.split("&")) {
                String[] a = kv.split("=", 2);
                if (a.length < 2) continue;
                if (a[0].equals("nodeId")) nodeId = a[1];
                else if (a[0].equals("sensor")) sensor = a[1];
                else if (a[0].equals("limit")) {
                    try { limit = Math.max(1, Integer.parseInt(a[1])); } catch (NumberFormatException ignored) { }
                }
            }
        }
        List<String> out = new ArrayList<>();
        synchronized (this) {
            for (Row r : rows) {
                if (nodeId != null && !nodeId.equals(r.nodeId())) continue;
                if (sensor != null && !sensor.equals(r.sensor())) continue;
                out.add(r.toJson());
            }
        }
        if (out.size() > limit) out = out.subList(out.size() - limit, out.size());
        reply(ex, 200, "[" + String.join(",", out) + "]");
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
