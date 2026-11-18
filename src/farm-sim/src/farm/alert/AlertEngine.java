package farm.alert;

import farm.common.Log;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F87-F90 - Phát hiện timeout/pin yếu/giá trị bất thường và tạo alert chống trùng. */
public final class AlertEngine {
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AlertPolicy policy;
    private final AlertRepository alerts;
    private final EmailNotifier email;
    private final Map<String, Instant> registeredAt = new LinkedHashMap<>();
    private final Map<String, Instant> lastMeasurement = new LinkedHashMap<>();

    public AlertEngine(AlertPolicy policy, AlertRepository alerts, EmailNotifier email) {
        this.policy = policy;
        this.alerts = alerts;
        this.email = email;
    }

    public synchronized void register(String nodeId, Instant at) {
        registeredAt.putIfAbsent(nodeId, at);
    }

    public synchronized void onPacket(String nodeId, Instant measuredAt, Instant receivedAt, double batteryPct,
                                      Map<String, Double> readings, List<String> errorSensors) {
        register(nodeId, receivedAt);
        Instant previous = lastMeasurement.get(nodeId);
        if (previous == null || measuredAt.isAfter(previous)) lastMeasurement.put(nodeId, measuredAt);
        clear("DATA_TIMEOUT", nodeId, null, receivedAt);

        double batteryLimit = policy.lowBatteryPct();
        if (batteryPct < batteryLimit) {
            String severity = batteryPct < batteryLimit / 2.0 ? "CRITICAL" : "WARNING";
            raise("LOW_BATTERY", severity, nodeId, "battery", batteryPct, 0.0, batteryLimit,
                    String.format("Pin %.1f%% thap hon nguong %.1f%%", batteryPct, batteryLimit), receivedAt);
        } else clear("LOW_BATTERY", nodeId, "battery", receivedAt);

        for (Map.Entry<String, Double> e : readings.entrySet()) {
            String metric = e.getKey();
            double value = e.getValue();
            AlertPolicy.Rule rule = policy.ruleFor(nodeId, metric);
            boolean lowCounts = !"light".equals(metric) || daytime(measuredAt);
            boolean abnormal = rule != null && ((lowCounts && value < rule.min()) || value > rule.max());
            if (abnormal) {
                double span = Math.max(0.001, rule.max() - rule.min());
                double distance = value < rule.min() ? rule.min() - value : value - rule.max();
                String severity = distance > span * 0.5 ? "CRITICAL" : "WARNING";
                raise("ABNORMAL_SENSOR_DATA", severity, nodeId, metric, value, rule.min(), rule.max(),
                        String.format("%s=%.2f ngoai nguong [%.2f, %.2f]", metric, value, rule.min(), rule.max()), receivedAt);
            } else clear("ABNORMAL_SENSOR_DATA", nodeId, metric, receivedAt);
            clear("SENSOR_READ_ERROR", nodeId, metric, receivedAt);
        }
        for (String metric : errorSensors) {
            raise("SENSOR_READ_ERROR", "WARNING", nodeId, metric, null, null, null,
                    "Cam bien " + metric + " bao loi doc du lieu", receivedAt);
        }
        safeFlushEmail();
    }

    public synchronized void checkTimeouts(Instant now) {
        Duration staleAfter = policy.staleAfter();
        for (Map.Entry<String, Instant> entry : registeredAt.entrySet()) {
            String nodeId = entry.getKey();
            Instant last = lastMeasurement.getOrDefault(nodeId, entry.getValue());
            Duration age = Duration.between(last, now);
            if (!age.isNegative() && age.compareTo(staleAfter) > 0) {
                String severity = age.compareTo(staleAfter.multipliedBy(2)) > 0 ? "CRITICAL" : "WARNING";
                raise("DATA_TIMEOUT", severity, nodeId, null, (double) age.toSeconds(), null,
                        (double) staleAfter.toSeconds(), "Khong co du lieu moi trong " + age.toMinutes() + " phut", now);
            } else clear("DATA_TIMEOUT", nodeId, null, now);
        }
        safeFlushEmail();
    }

    private void raise(String type, String severity, String nodeId, String metric, Double observed,
                       Double min, Double max, String message, Instant at) {
        AlertRepository.Creation result = alerts.createIfAbsent(type, severity, nodeId, metric,
                observed, min, max, message, at);
        if (!result.created()) return;
        Log.i("ALERT", result.alert().id() + " " + severity + " " + type + " " + nodeId);
        try { email.enqueue(result.alert()); }
        catch (RuntimeException e) { Log.i("EMAIL", "Khong enqueue duoc email cho " + result.alert().id() + ": " + e.getMessage()); }
    }

    private void clear(String type, String nodeId, String metric, Instant at) {
        Alert resolved = alerts.clearCondition(type, nodeId, metric, at);
        if (resolved != null) Log.i("ALERT", resolved.id() + " RESOLVED " + type + " " + nodeId);
    }

    private void safeFlushEmail() {
        try { email.flush(); }
        catch (RuntimeException e) { Log.i("EMAIL", "Retry email gap loi: " + e.getMessage()); }
    }

    private static boolean daytime(Instant at) {
        int hour = at.atZone(ICT).getHour();
        return hour >= 6 && hour < 18;
    }
}
