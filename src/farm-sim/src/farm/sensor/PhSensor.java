package farm.sensor;

import farm.common.SensorType;

import java.time.Instant;
import java.util.List;

/** F38 - pH sensor (mô phỏng đầu dò pH analog): trôi chậm quanh giá trị khởi đầu. */
public class PhSensor extends SimulatedSensor {
    private final double center;
    private double drift = 0;

    public PhSensor(long seed, double center) {
        super(seed, 0.01);
        this.center = center;
    }

    @Override public String name() { return "ph-probe-analog"; }

    @Override
    public List<Reading> read(Instant now) {
        drift = clamp(drift + rnd.nextGaussian() * 0.02, -0.5, 0.5);
        return List.of(measure(SensorType.PH, center + drift, 0.03,
                v -> (int) clamp(Math.round(3300 - v * 180), 0, 4095)));
    }
}
