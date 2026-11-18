package farm.sensor;

import farm.common.SensorType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Simulates a reservoir level that decreases over time and refills below its low point. */
public class WaterLevelSensor extends SimulatedSensor {
    private double level;
    private Instant last;

    public WaterLevelSensor(long seed, double startPercent) {
        super(seed, 0.005);
        this.level = startPercent;
    }

    @Override public String name() { return "water-level-analog"; }

    @Override
    public List<Reading> read(Instant now) {
        if (last != null) {
            double hours = Duration.between(last, now).toSeconds() / 3600.0;
            level -= 2.0 * hours;
            if (level < 15) level = 95;     // bơm châm nước
        }
        last = now;
        return List.of(measure(SensorType.WATER_LEVEL, level, 0.5, v -> (int) Math.round(v / 100.0 * 4095)));
    }
}
