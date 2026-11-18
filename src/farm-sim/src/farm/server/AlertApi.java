package farm.server;

import com.sun.net.httpserver.HttpExchange;
import farm.alert.Alert;
import farm.alert.AlertEngine;
import farm.alert.AlertPolicy;
import farm.alert.AlertRepository;
import farm.common.Json;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST API cho F86 (policy) và F90 (alert management). */
public final class AlertApi {
    private final AlertPolicy policy;
    private final AlertRepository alerts;
    private final AlertEngine engine;
    private final String adminKey;

    public AlertApi(AlertPolicy policy, AlertRepository alerts, AlertEngine engine, String adminKey) {
        this.policy = policy;
        this.alerts = alerts;
        this.engine = engine;
        this.adminKey = adminKey == null ? "" : adminKey;
    }

    public void handleRules(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if ("GET".equals(ex.getRequestMethod())) { reply(ex, 200, policy.toJson()); return; }
        if (!"PUT".equals(ex.getRequestMethod()) && !"PATCH".equals(ex.getRequestMethod())) {
            reply(ex, 405, "{\"error\":\"GET, PUT hoac PATCH only\"}"); return;
        }
        if (!authorized(ex)) return;
        try {
            Map<String, Object> patch = Json.obj(Json.parse(readBody(ex)));
            policy.applyPatch(patch, actor(ex));
            reply(ex, 200, policy.toJson());
        } catch (RuntimeException e) {
            reply(ex, 400, "{\"error\":" + Json.quote(e.getMessage()) + "}");
        }
    }

    public void handleAlerts(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        engine.checkTimeouts(Instant.now());
        String root = "/api/v1/alerts";
        String path = ex.getRequestURI().getPath();
        String rest = path.length() > root.length() ? path.substring(root.length()).replaceAll("^/+", "") : "";

        if (rest.isEmpty() && "GET".equals(ex.getRequestMethod())) {
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            int limit = integer(q.get("limit"), 100);
            List<Alert> list = alerts.list(q.get("status"), limit);
            String body = "[" + String.join(",", list.stream().map(Alert::toJson).toList()) + "]";
            reply(ex, 200, body);
            return;
        }

        String[] seg = rest.split("/");
        if (seg.length == 1 && "GET".equals(ex.getRequestMethod())) {
            Alert alert = alerts.get(seg[0]);
            if (alert == null) reply(ex, 404, "{\"error\":\"khong tim thay alert\"}");
            else reply(ex, 200, alert.toJson());
            return;
        }
        if (seg.length == 2 && "status".equals(seg[1])
                && ("PATCH".equals(ex.getRequestMethod()) || "PUT".equals(ex.getRequestMethod()))) {
            if (!authorized(ex)) return;
            try {
                Map<String, Object> body = Json.obj(Json.parse(readBody(ex)));
                String status = body == null ? null : Json.str(body.get("status"));
                if (status == null) throw new IllegalArgumentException("thieu status");
                String note = Json.str(body.get("note"));
                Alert updated = alerts.transition(seg[0], status, note, actor(ex), Instant.now());
                reply(ex, 200, updated.toJson());
            } catch (RuntimeException e) {
                reply(ex, 400, "{\"error\":" + Json.quote(e.getMessage()) + "}");
            }
            return;
        }
        reply(ex, 404, "{\"error\":\"not found\"}");
    }

    private boolean authorized(HttpExchange ex) throws IOException {
        if (adminKey.isBlank()) {
            reply(ex, 503, "{\"error\":\"FARM_ADMIN_KEY chua duoc cau hinh; API ghi dang khoa an toan\"}");
            return false;
        }
        String supplied = ex.getRequestHeaders().getFirst("X-Admin-Key");
        boolean ok = supplied != null && MessageDigest.isEqual(
                adminKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
        if (!ok) reply(ex, 401, "{\"error\":\"X-Admin-Key khong hop le\"}");
        return ok;
    }

    private static String actor(HttpExchange ex) {
        String actor = ex.getRequestHeaders().getFirst("X-Actor");
        return actor == null || actor.isBlank() ? "farm-admin" : actor;
    }

    private static boolean preflight(HttpExchange ex) throws IOException {
        if (!"OPTIONS".equals(ex.getRequestMethod())) return false;
        cors(ex);
        ex.sendResponseHeaders(204, -1);
        ex.close();
        return true;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String pair : raw.split("&")) {
            String[] a = pair.split("=", 2);
            String key = URLDecoder.decode(a[0], StandardCharsets.UTF_8);
            String value = a.length == 2 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : "";
            out.put(key, value);
        }
        return out;
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (RuntimeException e) { return fallback; }
    }

    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, PATCH, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Admin-Key, X-Actor");
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        cors(ex);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
