package farm.sensor;

import farm.common.SensorType;

import java.time.Instant;
import java.util.List;

/** Simulates a BH1750 light sensor with daylight cycles and randomized cloud cover. */
public class LightSensor extends SimulatedSensor {
    public LightSensor(long seed) { super(seed, 0.005); }

    @Override public String name() { return "bh1750"; }

    @Override
    public List<Reading> read(Instant now) {
        double h = hourOfDay(now);
        double sun = (h >= 6 && h <= 18) ? Math.sin(Math.PI * (h - 6) / 12.0) : 0;
        double cloud = 0.6 + 0.4 * rnd.nextDouble();
        return List.of(measure(SensorType.LIGHT, 100000 * sun * cloud, 50, null));
    }
}
