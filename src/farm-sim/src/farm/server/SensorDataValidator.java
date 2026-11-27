package farm.server;

import farm.common.Json;
import farm.common.SensorType;
import farm.node.NodeIdentity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates uploaded sensor records for schema, types, units, ranges and timestamps. */
public final class SensorDataValidator {
    private static final Duration MAX_FUTURE = Duration.ofMinutes(5);

    private SensorDataValidator() {}

    /** Trả danh sách lỗi; rỗng = hợp lệ. */
    public static List<String> validate(Object recordObj, Instant now) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> rec = Json.obj(recordObj);
        if (rec == null) return List.of("ban ghi phai la JSON object");

        if (Json.str(rec.get("gatewayId")) == null) errors.add("thieu gatewayId");
        Map<String, Object> pos = Json.obj(rec.get("pos"));
        Double lat = pos == null ? null : Json.num(pos.get("lat"));
        Double lon = pos == null ? null : Json.num(pos.get("lon"));
        if (lat == null || lon == null) errors.add("thieu pos.lat/pos.lon");
        else if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
            errors.add("pos.lat/pos.lon ngoai khoang hop le");
        try { Instant.parse(Json.str(rec.get("rxTs"))); }
        catch (Exception e) { errors.add("rxTs khong phai ISO-8601"); }

        Map<String, Object> p = Json.obj(rec.get("packet"));
        if (p == null) {
            errors.add("thieu packet");
            return errors;
        }
        Double v = Json.num(p.get("v"));
        if (v == null || v != 1.0) errors.add("version khong ho tro (can v=1)");
        if (!NodeIdentity.isValidId(Json.str(p.get("nodeId")))) errors.add("nodeId sai dinh dang");
        Double seq = Json.num(p.get("seq"));
        if (seq == null || seq < 1 || seq != Math.floor(seq)) errors.add("seq phai la so nguyen >= 1");
        Double battery = Json.num(p.get("battery"));
        if (battery == null || !Double.isFinite(battery) || battery < 0 || battery > 100)
            errors.add("battery phai trong khoang 0..100");

        try {
            Instant ts = Instant.parse(Json.str(p.get("ts")));
            if (ts.isAfter(now.plus(MAX_FUTURE))) errors.add("ts nam o tuong lai");
        } catch (Exception e) {
            errors.add("ts khong phai ISO-8601");
        }

        List<Object> readings = Json.arr(p.get("readings"));
        if (readings == null || readings.isEmpty()) {
            errors.add("readings rong hoac thieu");
            return errors;
        }
        for (int i = 0; i < readings.size(); i++) {
            Map<String, Object> r = Json.obj(readings.get(i));
            String where = "readings[" + i + "]";
            if (r == null) { errors.add(where + " khong phai object"); continue; }
            SensorType t = SensorType.fromKey(Json.str(r.get("sensor")));
            if (t == null) { errors.add(where + ": sensor khong ho tro"); continue; }
            if (!t.unit.equals(Json.str(r.get("unit")))) errors.add(where + ": sai don vi (can " + t.unit + ")");
            String status = Json.str(r.get("status"));
            if ("OK".equals(status)) {
                Double val = Json.num(r.get("value"));
                if (val == null || val.isNaN() || val.isInfinite()) errors.add(where + ": value khong phai so");
                else if (val < t.min || val > t.max)
                    errors.add(where + ": " + t.key + "=" + val + " ngoai khoang [" + t.min + ", " + t.max + "]");
            } else if (!"ERROR".equals(status)) {
                errors.add(where + ": status phai la OK hoac ERROR");
            }
        }
        return errors;
    }
}
