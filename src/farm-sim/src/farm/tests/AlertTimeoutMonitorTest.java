package farm.tests;

import farm.alert.AlertEngine;
import farm.alert.AlertPolicy;
import farm.alert.AlertRepository;
import farm.alert.AlertTimeoutMonitor;
import farm.alert.EmailNotifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Deterministic acceptance test for F87: timeout detection runs without an API request. */
public final class AlertTimeoutMonitorTest {
    private AlertTimeoutMonitorTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("farm-timeout-monitor-test-");
        try {
            AlertPolicy policy = AlertPolicy.inMemory();
            policy.applyPatch(Map.of("staleAfterSeconds", 60), "test");
            AlertRepository alerts = new AlertRepository(root.resolve("alerts.jsonl"));
            EmailNotifier email = new EmailNotifier(root.resolve("email-outbox.jsonl"), Map.of());
            AlertEngine engine = new AlertEngine(policy, alerts, email);
            Instant registeredAt = Instant.parse("2026-11-23T01:00:00Z");
            engine.register("NODE-001", registeredAt);

            MutableClock clock = new MutableClock(registeredAt.plusSeconds(61));
            ManualScheduler scheduler = new ManualScheduler();
            AlertTimeoutMonitor monitor = new AlertTimeoutMonitor(
                    engine, clock, Duration.ofSeconds(1), scheduler);

            monitor.start();
            monitor.start();
            check(scheduler.scheduleCount == 1, "start() must schedule exactly one recurring task");

            // This is the scheduled callback itself: no Alert API method is invoked.
            scheduler.runNextTick();
            check(alerts.list("OPEN", 100).size() == 1, "background tick must create timeout alert");
            var timeout = alerts.list("OPEN", 100).get(0);
            check("DATA_TIMEOUT".equals(timeout.type()), "background alert must have DATA_TIMEOUT type");
            int initialHistorySize = timeout.history().size();

            clock.set(registeredAt.plusSeconds(180));
            scheduler.runNextTick();
            check(alerts.size() == 1, "repeated background ticks must deduplicate an active timeout");

            Instant backfillReceivedAt = clock.instant();
            engine.onPacket("NODE-001", backfillReceivedAt.minusSeconds(99), backfillReceivedAt,
                    90.0, Map.of(), List.of());
            check("OPEN".equals(alerts.get(timeout.id()).status()),
                    "99-second-old backfill must not resolve the existing timeout when the limit is 60s");
            check(alerts.get(timeout.id()).history().size() == initialHistorySize,
                    "stale backfill must not add a status event to the existing timeout");

            clock.set(backfillReceivedAt.plusSeconds(1));
            scheduler.runNextTick();
            check(alerts.size() == 1 && "OPEN".equals(alerts.get(timeout.id()).status()),
                    "the next tick must keep the same timeout open without creating a duplicate");
            System.out.println("[PASS] F87 99s-old backfill keeps the same timeout OPEN (60s limit, no duplicate)");

            Instant recoveredAt = clock.instant().plusSeconds(1);
            clock.set(recoveredAt);
            engine.onPacket("NODE-001", recoveredAt, recoveredAt, 90.0, Map.of(), List.of());
            check(alerts.list("OPEN", 100).isEmpty(), "new packet must resolve the timeout alert");
            check("RESOLVED".equals(alerts.get(timeout.id()).status())
                            && alerts.get(timeout.id()).history().size() == initialHistorySize + 1,
                    "fresh data must resolve the original timeout and record the status once");

            clock.set(recoveredAt.plusSeconds(1));
            engine.onPacket("NODE-001", backfillReceivedAt.minusSeconds(98), clock.instant(),
                    90.0, Map.of(), List.of());
            scheduler.runNextTick();
            check(alerts.size() == 1 && alerts.list("OPEN", 100).isEmpty(),
                    "an out-of-order old packet must not overwrite the fresh measurement");
            System.out.println("[PASS] F87 fresh data resolves the original timeout; later old data does not reopen it");

            clock.set(recoveredAt.plusSeconds(61));
            scheduler.runNextTick();
            check(alerts.list("OPEN", 100).size() == 1,
                    "a later stale period must create a new timeout incident");
            check(alerts.size() == 2, "recurrent timeout after recovery must receive a new alert record");

            monitor.close();
            check(scheduler.cancelled && scheduler.closed,
                    "close() must cancel the task and release the scheduler");
            scheduler.runNextTick();
            check(alerts.size() == 2, "closed monitor must not run another timeout check");

            System.out.println("[PASS] F87 background timeout detection without API request");
        } finally {
            deleteTree(root);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void set(Instant value) {
            now = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class ManualScheduler implements AlertTimeoutMonitor.Scheduler {
        private Runnable task;
        private int scheduleCount;
        private boolean cancelled;
        private boolean closed;

        @Override
        public AlertTimeoutMonitor.Cancellation scheduleWithFixedDelay(
                Runnable value, Duration initialDelay, Duration delay) {
            check(value != null, "scheduled task must not be null");
            check(!initialDelay.isNegative() && !initialDelay.isZero(), "initial delay must be positive");
            check(!delay.isNegative() && !delay.isZero(), "fixed delay must be positive");
            task = value;
            scheduleCount++;
            return () -> cancelled = true;
        }

        private void runNextTick() {
            if (!cancelled && !closed && task != null) task.run();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
