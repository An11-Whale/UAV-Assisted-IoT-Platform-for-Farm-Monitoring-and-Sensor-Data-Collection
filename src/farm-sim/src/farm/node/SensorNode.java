package farm.node;

import farm.common.Json;
import farm.sensor.Reading;
import farm.sensor.Sensor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Một node cảm biến ngoài đồng: gồm nhiều cảm biến, có định danh, khóa bí mật, vị trí cố định. */
public class SensorNode {
    private final NodeIdentity identity;
    private final byte[] key;
    private final List<Sensor> sensors;
    public final double x;
    public final double y;

    private long seq = 0;
    private double battery = 100;

    public SensorNode(NodeIdentity identity, byte[] key, double x, double y, List<Sensor> sensors) {
        this.identity = identity;
        this.key = key;
        this.x = x;
        this.y = y;
        this.sensors = sensors;
    }

    public String nodeId() { return identity.nodeId(); }

    /** F42 - Beacon quảng bá để gateway phát hiện node. */
    public String beaconJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\"type\":\"BEACON\",\"v\":1,\"nodeId\":").append(Json.quote(identity.nodeId()))
                .append(",\"model\":").append(Json.quote(identity.model()))
                .append(",\"fw\":").append(Json.quote(identity.firmware()))
                .append(",\"farmId\":").append(Json.quote(identity.farmId()))
                .append(",\"sensors\":[");
        boolean first = true;
        for (Sensor s : sensors) {
            if (!first) b.append(',');
            b.append(Json.quote(s.name()));
            first = false;
        }
        return b.append("]}").toString();
    }

    /** Gateway gửi yêu cầu thu thập kèm nonce -> node đọc cảm biến (F39), đóng gói (F40), ký (F43). */
    public synchronized String handleCollect(String nonce, Instant now) {
        List<Reading> all = new ArrayList<>();
        for (Sensor s : sensors) all.addAll(s.read(now));
        battery = Math.max(0, battery - 0.05);
        return PacketBuilder.build(identity.nodeId(), ++seq, nonce, now, battery, all, key);
    }
}
