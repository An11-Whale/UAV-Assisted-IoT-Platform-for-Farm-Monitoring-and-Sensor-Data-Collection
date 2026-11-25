package farm.alert;

import farm.common.Log;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Periodically asks {@link AlertEngine} to evaluate registered nodes for stale data.
 *
 * <p>The production constructor owns one daemon scheduler thread. The injectable
 * constructor keeps the same lifecycle while allowing acceptance tests to drive
 * ticks with a fixed clock, without sleeping or issuing an HTTP request.</p>
 */
public final class AlertTimeoutMonitor implements AutoCloseable {
    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    public interface Scheduler extends AutoCloseable {
        Cancellation scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay);

        @Override
        void close();
    }

    private final AlertEngine engine;
    private final Clock clock;
    private final Duration interval;
    private final Scheduler scheduler;
    private Cancellation scheduledTask;
    private boolean closed;

    /** Creates a monitor backed by one daemon thread and the system UTC clock. */
    public AlertTimeoutMonitor(AlertEngine engine, Duration interval) {
        this(engine, Clock.systemUTC(), interval, new ExecutorScheduler());
    }

    /** Injectable constructor used by deterministic tests and alternate runtimes. */
    public AlertTimeoutMonitor(AlertEngine engine, Clock clock, Duration interval, Scheduler scheduler) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.interval = requirePositive(interval);
    }

    /** Starts exactly one recurring check. Repeated calls are harmless. */
    public synchronized void start() {
        if (closed) throw new IllegalStateException("timeout monitor da dong");
        if (scheduledTask != null) return;
        scheduledTask = scheduler.scheduleWithFixedDelay(this::checkSafely, interval, interval);
        Log.i("ALERT", "Timeout monitor bat dau, chu ky " + interval.toMillis() + " ms");
    }

    /** Cancels the recurring task and releases the scheduler thread. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (scheduledTask != null) scheduledTask.cancel();
        scheduler.close();
    }

    private void checkSafely() {
        try {
            engine.checkTimeouts(clock.instant());
        } catch (RuntimeException error) {
            // Do not let one persistence or notification failure terminate all future checks.
            Log.i("ALERT", "Timeout monitor gap loi: " + error.getMessage());
        }
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() < 1)
            throw new IllegalArgumentException("interval phai it nhat 1 ms");
        return value;
    }

    private static final class ExecutorScheduler implements Scheduler {
        private final ScheduledExecutorService executor;

        private ExecutorScheduler() {
            ThreadFactory factory = task -> {
                Thread thread = new Thread(task, "farm-alert-timeout-monitor");
                thread.setDaemon(true);
                return thread;
            };
            executor = Executors.newSingleThreadScheduledExecutor(factory);
        }

        @Override
        public Cancellation scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
            var future = executor.scheduleWithFixedDelay(task, initialDelay.toMillis(), delay.toMillis(),
                    TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
