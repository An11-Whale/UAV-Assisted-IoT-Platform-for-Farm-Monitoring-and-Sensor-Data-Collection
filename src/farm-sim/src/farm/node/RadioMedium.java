package farm.node;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Môi trường vô tuyến giả lập (thay cho LoRa/BLE/Wi-Fi): chỉ nghe/trả lời được khi trong tầm,
 * có tỉ lệ mất gói. Đổi sang UDP/MQTT thật chỉ cần viết lại lớp này.
 */
public class RadioMedium {
    public record Heard(String beaconJson, double rssi) {}

    public record Response(String payload, double rssi) {}

    private final Map<String, SensorNode> nodes = new LinkedHashMap<>();
    private final double lossRate;
    private final Random rnd;

    public RadioMedium(double lossRate, long seed) {
        this.lossRate = lossRate;
        this.rnd = new Random(seed);
    }

    public void register(SensorNode n) {
        if (nodes.putIfAbsent(n.nodeId(), n) != null)
            throw new IllegalArgumentException("nodeId bi trung: " + n.nodeId());
    }

    private double rssi(double dist) {
        return -40 - 20 * Math.log10(Math.max(dist, 1)) - rnd.nextDouble() * 3;
    }

    /** F42 - Gateway nghe beacon trong bán kính range. */
    public List<Heard> discover(double x, double y, double range) {
        List<Heard> out = new ArrayList<>();
        for (SensorNode n : nodes.values()) {
            double d = Math.hypot(n.x - x, n.y - y);
            if (d <= range) out.add(new Heard(n.beaconJson(), rssi(d)));
        }
        return out;
    }

    /** F45 - Gửi yêu cầu thu thập tới 1 node; trả null nếu ngoài tầm hoặc mất gói. */
    public Response request(String nodeId, String nonce, Instant now, double x, double y, double range) {
        SensorNode n = nodes.get(nodeId);
        if (n == null) return null;
        double d = Math.hypot(n.x - x, n.y - y);
        if (d > range || rnd.nextDouble() < lossRate) return null;
        return new Response(n.handleCollect(nonce, now), rssi(d));
    }
}
