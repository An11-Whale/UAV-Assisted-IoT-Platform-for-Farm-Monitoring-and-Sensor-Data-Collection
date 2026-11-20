package farm.sensor;

import farm.common.SensorType;

import java.time.Instant;
import java.util.List;

/** Simulates a DHT22 module that reports temperature and relative humidity together. */
public class TempHumiditySensor extends SimulatedSensor {
    private final double offset;

    public TempHumiditySensor(long seed, double tempOffset) {
        super(seed, 0.01);
        this.offset = tempOffset;
    }

    @Override public String name() { return "dht22"; }

    @Override
    public List<Reading> read(Instant now) {
        double h = hourOfDay(now);
        double temp = 27 + 5 * Math.sin(2 * Math.PI * (h - 9) / 24.0) + offset; // đỉnh ~15h, đáy ~3h
        double hum = 100 - 2.5 * (temp - 20) - 10;                               // nóng hơn thì khô hơn
        return List.of(
                measure(SensorType.TEMPERATURE, temp, 0.2, null),
                measure(SensorType.HUMIDITY, hum, 1.0, null));
    }
}
