package farm.gateway;

import farm.common.Auth;
import farm.common.Json;
import farm.common.Log;
import farm.common.SimClock;
import farm.node.RadioMedium;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * F45 - Gateway Data Collection. Mỗi tick: phát hiện node (F42) -> gửi yêu cầu kèm nonce ->
 * nhận gói -> xác thực (F43: chữ ký, nonce, seq, lệch giờ) -> lưu cục bộ (F48).
 */
public class DataCollector {
    private static final long MAX_SKEW_SEC = 30;

    private final String gatewayId;
    private final KeyStore keys;
    private final RadioMedium radio;
    private final LocalStore store;
    private final SimClock clock;
    private final SecureRandom rnd = new SecureRandom();

    private final Map<String, Long> lastSeq = new HashMap<>();
    private final Set<String> done = new HashSet<>();      // đã thu trong chuyến bay này
    private final Set<String> blocked = new HashSet<>();   // bị từ chối trong chuyến bay này

    public int collected = 0;
    public int rejected = 0;
    public int lost = 0;

    public DataCollector(String gatewayId, KeyStore keys, RadioMedium radio, LocalStore store, SimClock clock) {
        this.gatewayId = gatewayId;
        this.keys = keys;
        this.radio = radio;
        this.store = store;
        this.clock = clock;
    }

    public void newMission() {
        done.clear();
        blocked.clear();
    }

    public void poll(UavGateway uav) {
        List<RadioMedium.Heard> heard = radio.discover(uav.x, uav.y, uav.rangeM);
        for (RadioMedium.Heard h : heard) {
            String nodeId;
            try {
                Map<String, Object> beacon = Json.obj(Json.parse(h.beaconJson()));
                nodeId = beacon == null ? null : Json.str(beacon.get("nodeId"));
                if (nodeId == null) throw new IllegalArgumentException("beacon thieu nodeId");
            } catch (RuntimeException e) {
                Log.i("REJECT", "beacon sai dinh dang: " + e.getMessage());
                rejected++;
                continue;
            }
            if (done.contains(nodeId) || blocked.contains(nodeId)) continue;

            byte[] key = keys.get(nodeId);
            if (key == null) {
                reject(nodeId, "node chua duoc cap phep (unknown node)");
                continue;
            }

            String nonce = randomHex(8);
            RadioMedium.Response resp = radio.request(nodeId, nonce, clock.now(), uav.x, uav.y, uav.rangeM);
            if (resp == null) {
                lost++;
                Log.i("COLLECT", nodeId + " mat goi, se thu lai o tick sau");
                continue;
            }

            String error = verify(nodeId, nonce, key, resp.payload());
            if (error != null) {
                reject(nodeId, error);
                continue;
            }

            store.append(wrap(uav, resp));
            done.add(nodeId);
            collected++;
            Log.i("COLLECT", String.format(Locale.ROOT, "%s OK  rssi=%.0fdBm  pos=(%.0f,%.0f)  hang doi=%d",
                    nodeId, resp.rssi(), uav.x, uav.y, store.size()));
        }
    }

    /** Trả null nếu hợp lệ, ngược lại trả lý do từ chối. */
    private String verify(String nodeId, String nonce, byte[] key, String payload) {
        try {
            String[] parts = Auth.split(payload);
            if (parts == null) return "goi sai dinh dang (khong co chu ky)";
            if (!Auth.verify(key, parts[0], parts[1])) return "chu ky HMAC khong hop le";
            Map<String, Object> body = Json.obj(Json.parse(parts[0]));
            if (body == null) return "body khong phai JSON object";
            if (!nodeId.equals(Json.str(body.get("nodeId")))) return "nodeId trong goi khac voi beacon";
            if (!nonce.equals(Json.str(body.get("nonce")))) return "nonce khong khop (nghi replay)";
            Double seq = Json.num(body.get("seq"));
            if (seq == null || seq.longValue() <= lastSeq.getOrDefault(nodeId, 0L)) return "seq cu/trung (nghi replay)";
            Instant ts = Instant.parse(Json.str(body.get("ts")));
            if (Math.abs(Duration.between(ts, clock.now()).getSeconds()) > MAX_SKEW_SEC)
                return "timestamp lech qua " + MAX_SKEW_SEC + "s";
            lastSeq.put(nodeId, seq.longValue());
            return null;
        } catch (RuntimeException e) {
            return "goi khong hop le: " + e.getMessage();
        }
    }

    private void reject(String nodeId, String reason) {
        blocked.add(nodeId);
        rejected++;
        Log.i("REJECT", nodeId + ": " + reason);
    }

    /** Bọc gói gốc bằng metadata của gateway: ai thu, lúc nào, ở đâu, tín hiệu thế nào. */
    private String wrap(UavGateway uav, RadioMedium.Response r) {
        return String.format(Locale.ROOT,
                "{\"gatewayId\":%s,\"rxTs\":%s,\"pos\":{\"lat\":%.6f,\"lon\":%.6f,\"alt\":%.0f},\"rssi\":%.1f,\"packet\":%s}",
                Json.quote(gatewayId), Json.quote(clock.now().toString()),
                uav.lat(), uav.lon(), uav.altitude, r.rssi(), r.payload());
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        rnd.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
