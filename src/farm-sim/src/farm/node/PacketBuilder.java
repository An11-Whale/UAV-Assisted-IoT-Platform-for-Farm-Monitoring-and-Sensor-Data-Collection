package farm.node;

import farm.common.Auth;
import farm.common.Json;
import farm.sensor.Reading;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Builds the versioned sensor-data envelope and attaches its HMAC signature (F40/F43).
 * The canonical JSON contains node identity, sequence, request nonce, timestamp, battery
 * level and readings before the final {@code sig} field is added.
 */
public final class PacketBuilder {
    private PacketBuilder() {}

    public static String build(String nodeId, long seq, String nonce, Instant ts,
                               double battery, List<Reading> readings, byte[] key) {
        StringBuilder b = new StringBuilder();
        b.append("{\"v\":1,\"nodeId\":").append(Json.quote(nodeId))
                .append(",\"seq\":").append(seq)
                .append(",\"nonce\":").append(Json.quote(nonce))
                .append(",\"ts\":").append(Json.quote(ts.toString()))
                .append(",\"battery\":").append(String.format(Locale.ROOT, "%.1f", battery))
                .append(",\"readings\":[");
        for (int i = 0; i < readings.size(); i++) {
            if (i > 0) b.append(',');
            b.append(readings.get(i).toJson());
        }
        b.append("]}");
        String body = b.toString();
        return Auth.attachSignature(body, Auth.sign(key, body));
    }
}
