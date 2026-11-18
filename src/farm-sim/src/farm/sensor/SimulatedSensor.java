package farm.sensor;

import farm.common.SensorType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.function.DoubleToIntFunction;

/** Lớp nền cho cảm biến giả lập: lấy nhiều mẫu rồi trung bình (F39), thỉnh thoảng báo lỗi đọc. */
public abstract class SimulatedSensor implements Sensor {
    private static final int SAMPLES = 5;
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    protected final Random rnd;
    private final double errorRate;

    protected SimulatedSensor(long seed, double errorRate) {
        this.rnd = new Random(seed);
        this.errorRate = errorRate;
    }

    protected Reading measure(SensorType t, double truth, double noiseSd, DoubleToIntFunction rawFn) {
        if (rnd.nextDouble() < errorRate) return new Reading(t, Double.NaN, -1, "ERROR");
        double sum = 0;
        for (int k = 0; k < SAMPLES; k++) sum += truth + rnd.nextGaussian() * noiseSd;
        double v = Math.max(t.min, Math.min(t.max, sum / SAMPLES));
        int raw = rawFn == null ? -1 : rawFn.applyAsInt(v);
        return new Reading(t, v, raw, "OK");
    }

    protected static double hourOfDay(Instant now) {
        ZonedDateTime z = now.atZone(ICT);
        return z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
    }

    protected static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
