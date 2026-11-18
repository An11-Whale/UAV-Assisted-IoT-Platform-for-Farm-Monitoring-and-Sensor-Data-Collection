package farm.gateway;

import java.util.List;

/**
 * Simulates the UAV-mounted mobile gateway moving through a waypoint mission (F44).
 * Positions are maintained in local metres for radio-range calculations and converted to
 * latitude/longitude when collection records need geographic metadata.
 */
public class UavGateway {
    private static final double LAT0 = 10.7769;     // gốc toạ độ (TP.HCM) để quy ra lat/lon
    private static final double LON0 = 106.7009;

    public final String id;
    public final double altitude;
    public final double rangeM;       // tầm thu sóng radio
    private final double speed;       // m/s
    private final List<double[]> waypoints;
    private int wp = 0;
    public double x = 0;
    public double y = 0;

    public UavGateway(String id, double speed, double altitude, double rangeM, List<double[]> waypoints) {
        this.id = id;
        this.speed = speed;
        this.altitude = altitude;
        this.rangeM = rangeM;
        this.waypoints = waypoints;
    }

    public void startMission() { wp = 0; }

    /** Bay thêm dt giây; trả true khi đã hoàn thành đường bay (về tới waypoint cuối = base). */
    public boolean step(double dt) {
        if (wp >= waypoints.size()) return true;
        double[] t = waypoints.get(wp);
        double dx = t[0] - x, dy = t[1] - y;
        double d = Math.hypot(dx, dy);
        double move = speed * dt;
        if (d <= move) {
            x = t[0];
            y = t[1];
            wp++;
        } else {
            x += dx / d * move;
            y += dy / d * move;
        }
        return wp >= waypoints.size();
    }

    public double lat() { return LAT0 + y / 111320.0; }

    public double lon() { return LON0 + x / (111320.0 * Math.cos(Math.toRadians(LAT0))); }
}
