package farm.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** F52 - Exponential backoff có giới hạn và jitter để nhiều gateway không retry cùng lúc. */
public final class RetryPolicy {
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final double jitterRatio;
    private final DoubleSupplier random;

    public RetryPolicy(Duration initialDelay, Duration maxDelay, double multiplier, double jitterRatio) {
        this(initialDelay, maxDelay, multiplier, jitterRatio, () -> ThreadLocalRandom.current().nextDouble());
    }

    RetryPolicy(Duration initialDelay, Duration maxDelay, double multiplier,
                double jitterRatio, DoubleSupplier random) {
        if (initialDelay == null || initialDelay.isZero() || initialDelay.isNegative())
            throw new IllegalArgumentException("initialDelay phai > 0");
        if (maxDelay == null || maxDelay.compareTo(initialDelay) < 0)
            throw new IllegalArgumentException("maxDelay phai >= initialDelay");
        if (!Double.isFinite(multiplier) || multiplier < 1.0)
            throw new IllegalArgumentException("multiplier phai >= 1");
        if (!Double.isFinite(jitterRatio) || jitterRatio < 0 || jitterRatio > 1)
            throw new IllegalArgumentException("jitterRatio phai trong [0,1]");
        if (random == null) throw new IllegalArgumentException("random khong duoc null");
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.jitterRatio = jitterRatio;
        this.random = random;
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(Duration.ofSeconds(2), Duration.ofMinutes(2), 2.0, 0.20);
    }

    /** failureNumber bắt đầu từ 1; lần lỗi đầu dùng initialDelay. */
    public Duration delayForFailure(int failureNumber) {
        if (failureNumber < 1) throw new IllegalArgumentException("failureNumber phai >= 1");
        double exponent = Math.min(failureNumber - 1, 62);
        double baseMillis = Math.min(maxDelay.toMillis(),
                initialDelay.toMillis() * Math.pow(multiplier, exponent));
        double sample = Math.max(0, Math.min(1, random.getAsDouble()));
        double jitter = 1.0 - jitterRatio + (2.0 * jitterRatio * sample);
        long delayMillis = Math.max(1L, Math.min(maxDelay.toMillis(), Math.round(baseMillis * jitter)));
        return Duration.ofMillis(delayMillis);
    }

    public Instant nextAttempt(Instant now, int failureNumber) {
        if (now == null) throw new IllegalArgumentException("now khong duoc null");
        return now.plus(delayForFailure(failureNumber));
    }

    public Duration initialDelay() { return initialDelay; }
    public Duration maxDelay() { return maxDelay; }
    public double multiplier() { return multiplier; }
    public double jitterRatio() { return jitterRatio; }
}
