package farm.sensor;

import farm.common.SensorType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** F36 - Soil Moisture Sensor Prototype (mô phỏng cảm biến điện dung v1.2 nối ADC 12-bit). */
public class SoilMoistureSensor extends SimulatedSensor {
    public static final int DRY = 3000;   // ADC ngoài không khí
    public static final int WET = 1400;   // ADC khi nhúng nước

    private double moisture;
    private Instant last;

    public SoilMoistureSensor(long seed, double startPercent) {
        super(seed, 0.005);
        this.moisture = startPercent;
    }

    @Override public String name() { return "soil-moisture-capacitive-v1.2"; }

    @Override
    public List<Reading> read(Instant now) {
        if (last != null) {
            double hours = Duration.between(last, now).toSeconds() / 3600.0;
            moisture -= 3.0 * hours;                                       // đất khô dần ~3%/giờ
            if (rnd.nextDouble() < 0.15 * hours) moisture += 20 + rnd.nextDouble() * 15; // mưa/tưới
            moisture = clamp(moisture, 5, 95);
        }
        last = now;
        return List.of(measure(SensorType.SOIL_MOISTURE, moisture, 0.6,
                v -> (int) Math.round(DRY - v / 100.0 * (DRY - WET))));
    }
}
