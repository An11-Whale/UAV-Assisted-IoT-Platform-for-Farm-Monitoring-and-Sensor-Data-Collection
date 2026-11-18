package farm.alert;

import farm.common.Json;
import farm.common.SensorType;
import farm.node.NodeIdentity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** F86-F89 - Cấu hình ngưỡng, timeout và pin yếu, lưu ngoài mã nguồn. */
public final class AlertPolicy {
    public record Rule(double min, double max) {
        public Rule {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max)
                throw new IllegalArgumentException("min phai nho hon max");
        }
        public boolean abnormal(double value) { return value < min || value > max; }
    }

    private static final long DEFAULT_STALE_SECONDS = Duration.ofHours(48).toSeconds();
    private static final double DEFAULT_LOW_BATTERY = 20.0;
    private final Path file;
    private final Path auditFile;
    private final Map<String, Rule> global = new LinkedHashMap<>();
    private final Map<String, Map<String, Rule>> nodeOverrides = new LinkedHashMap<>();
    private long staleAfterSeconds = DEFAULT_STALE_SECONDS;
    private double lowBatteryPct = DEFAULT_LOW_BATTERY;

    public AlertPolicy(Path file) {
        this.file = file;
        this.auditFile = file == null ? null : file.resolveSibling("alert-policy-audit.jsonl");
        installDefaults();
        if (file != null && Files.exists(file)) load();
        else if (file != null) save();
    }

    public static AlertPolicy inMemory() { return new AlertPolicy(null); }

    private void installDefaults() {
        global.put("soil_moisture", new Rule(30, 70));
        global.put("temperature", new Rule(18, 32));
        global.put("humidity", new Rule(50, 85));
        global.put("light", new Rule(10_000, 45_000));
        global.put("ph", new Rule(5.5, 7.0));
        // Packet dùng %, dashboard đổi sang cm với hệ số 0.35.
        global.put("water_level", new Rule(8.0 / 0.35, 25.0 / 0.35));
    }

    public synchronized Duration staleAfter() { return Duration.ofSeconds(staleAfterSeconds); }
    public synchronized double lowBatteryPct() { return lowBatteryPct; }

    public synchronized Rule ruleFor(String nodeId, String metric) {
        Map<String, Rule> overrides = nodeOverrides.get(nodeId);
        return overrides != null && overrides.containsKey(metric) ? overrides.get(metric) : global.get(metric);
    }

    /** Patch: {metric,min,max,nodeId?}, {staleAfterSeconds} hoặc {lowBatteryPct}. */
    public synchronized void applyPatch(Map<String, Object> patch, String actor) {
        if (patch == null) throw new IllegalArgumentException("body phai la JSON object");
        boolean changed = false;
        Object stale = patch.get("staleAfterSeconds");
        if (stale == null && patch.get("staleAfterMinutes") != null) {
            Double minutes = Json.num(patch.get("staleAfterMinutes"));
            if (minutes == null) throw new IllegalArgumentException("staleAfterMinutes phai la so");
            stale = minutes * 60.0;
        }
        if (stale != null) {
            Double value = Json.num(stale);
            if (value == null || !Double.isFinite(value) || value < 1 || value > Duration.ofDays(365).toSeconds())
                throw new IllegalArgumentException("staleAfterSeconds phai trong khoang 1..31536000");
            staleAfterSeconds = Math.round(value);
            changed = true;
        }
        if (patch.containsKey("lowBatteryPct")) {
            Double value = Json.num(patch.get("lowBatteryPct"));
            if (value == null || !Double.isFinite(value) || value < 0 || value > 100)
                throw new IllegalArgumentException("lowBatteryPct phai trong khoang 0..100");
            lowBatteryPct = value;
            changed = true;
        }
        String metric = Json.str(patch.get("metric"));
        if (metric != null) {
            if (SensorType.fromKey(metric) == null) throw new IllegalArgumentException("metric khong duoc ho tro: " + metric);
            Double min = Json.num(patch.get("min"));
            Double max = Json.num(patch.get("max"));
            if (min == null || max == null) throw new IllegalArgumentException("rule metric can ca min va max");
            Rule rule = new Rule(min, max);
            String nodeId = Json.str(patch.get("nodeId"));
            if (nodeId == null || nodeId.isBlank() || "*".equals(nodeId)) global.put(metric, rule);
            else {
                if (!NodeIdentity.isValidId(nodeId)) throw new IllegalArgumentException("nodeId sai dinh dang NODE-001");
                nodeOverrides.computeIfAbsent(nodeId, ignored -> new LinkedHashMap<>()).put(metric, rule);
            }
            changed = true;
        } else if (patch.containsKey("min") || patch.containsKey("max") || patch.containsKey("nodeId")) {
            throw new IllegalArgumentException("thieu metric cho rule min/max/nodeId");
        }
        if (!changed) throw new IllegalArgumentException("khong co truong cau hinh hop le");
        save();
        audit(actor == null || actor.isBlank() ? "unknown" : actor, patch);
    }

    public synchronized String toJson() {
        StringBuilder b = new StringBuilder("{\"staleAfterSeconds\":").append(staleAfterSeconds)
                .append(",\"lowBatteryPct\":").append(String.format(Locale.ROOT, "%.2f", lowBatteryPct))
                .append(",\"global\":{");
        appendRules(b, global);
        b.append("},\"nodeOverrides\":{");
        boolean first = true;
        for (Map.Entry<String, Map<String, Rule>> e : nodeOverrides.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(Json.quote(e.getKey())).append(":{");
            appendRules(b, e.getValue());
            b.append('}');
        }
        return b.append("}}").toString();
    }

    private static void appendRules(StringBuilder b, Map<String, Rule> rules) {
        boolean first = true;
        for (Map.Entry<String, Rule> e : rules.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(Json.quote(e.getKey())).append(String.format(Locale.ROOT,
                    ":{\"min\":%.4f,\"max\":%.4f}", e.getValue().min(), e.getValue().max()));
        }
    }

    private void load() {
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) { p.load(in); }
        catch (IOException e) { throw new UncheckedIOException("Khong doc duoc alert policy " + file, e); }
        staleAfterSeconds = parseLong(p, "staleAfterSeconds", DEFAULT_STALE_SECONDS, 1, Duration.ofDays(365).toSeconds());
        lowBatteryPct = parseDouble(p, "lowBatteryPct", DEFAULT_LOW_BATTERY, 0, 100);
        for (SensorType t : SensorType.values()) {
            Rule d = global.get(t.key);
            double min = parseDouble(p, "metric." + t.key + ".min", d.min(), -Double.MAX_VALUE, Double.MAX_VALUE);
            double max = parseDouble(p, "metric." + t.key + ".max", d.max(), -Double.MAX_VALUE, Double.MAX_VALUE);
            global.put(t.key, new Rule(min, max));
        }
        for (String name : p.stringPropertyNames()) {
            if (!name.startsWith("node.") || !name.endsWith(".min")) continue;
            String middle = name.substring("node.".length(), name.length() - ".min".length());
            int marker = middle.indexOf(".metric.");
            if (marker < 0) continue;
            String nodeId = middle.substring(0, marker);
            String metric = middle.substring(marker + ".metric.".length());
            if (!NodeIdentity.isValidId(nodeId) || SensorType.fromKey(metric) == null) continue;
            String prefix = "node." + nodeId + ".metric." + metric;
            double min = parseDouble(p, prefix + ".min", Double.NaN, -Double.MAX_VALUE, Double.MAX_VALUE);
            double max = parseDouble(p, prefix + ".max", Double.NaN, -Double.MAX_VALUE, Double.MAX_VALUE);
            if (Double.isFinite(min) && Double.isFinite(max))
                nodeOverrides.computeIfAbsent(nodeId, ignored -> new LinkedHashMap<>()).put(metric, new Rule(min, max));
        }
    }

    private void save() {
        if (file == null) return;
        Properties p = new Properties();
        p.setProperty("staleAfterSeconds", Long.toString(staleAfterSeconds));
        p.setProperty("lowBatteryPct", Double.toString(lowBatteryPct));
        for (Map.Entry<String, Rule> e : global.entrySet()) {
            p.setProperty("metric." + e.getKey() + ".min", Double.toString(e.getValue().min()));
            p.setProperty("metric." + e.getKey() + ".max", Double.toString(e.getValue().max()));
        }
        for (Map.Entry<String, Map<String, Rule>> node : nodeOverrides.entrySet()) {
            for (Map.Entry<String, Rule> e : node.getValue().entrySet()) {
                String prefix = "node." + node.getKey() + ".metric." + e.getKey();
                p.setProperty(prefix + ".min", Double.toString(e.getValue().min()));
                p.setProperty(prefix + ".max", Double.toString(e.getValue().max()));
            }
        }
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                p.store(out, "UAV farm alert rules - edit through API when possible");
            }
            atomicReplace(tmp, file);
        } catch (IOException e) { throw new UncheckedIOException("Khong luu duoc alert policy " + file, e); }
    }

    private void audit(String actor, Map<String, Object> patch) {
        if (auditFile == null) return;
        String line = "{\"at\":" + Json.quote(Instant.now().toString()) + ",\"actor\":" + Json.quote(actor)
                + ",\"patch\":" + mapToJson(patch) + "}\n";
        try { Files.writeString(auditFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { throw new UncheckedIOException("Khong ghi duoc audit policy", e); }
    }

    private static String mapToJson(Map<String, Object> map) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(Json.quote(e.getKey())).append(':');
            Object v = e.getValue();
            if (v == null) b.append("null");
            else if (v instanceof Number || v instanceof Boolean) b.append(v);
            else b.append(Json.quote(String.valueOf(v)));
        }
        return b.append('}').toString();
    }

    private static long parseLong(Properties p, String key, long fallback, long min, long max) {
        try {
            long value = Long.parseLong(p.getProperty(key, Long.toString(fallback)));
            if (value < min || value > max) throw new IllegalArgumentException(key + " ngoai khoang hop le");
            return value;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " khong phai so nguyen", e); }
    }

    private static double parseDouble(Properties p, String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(p.getProperty(key, Double.toString(fallback)));
            if (!Double.isFinite(value) || value < min || value > max)
                throw new IllegalArgumentException(key + " ngoai khoang hop le");
            return value;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " khong phai so", e); }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
