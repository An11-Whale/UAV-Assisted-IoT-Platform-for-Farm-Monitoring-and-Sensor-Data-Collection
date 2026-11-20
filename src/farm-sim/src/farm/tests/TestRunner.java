package farm.tests;

import farm.alert.Alert;
import farm.alert.AlertEngine;
import farm.alert.AlertPolicy;
import farm.alert.AlertRepository;
import farm.alert.EmailNotifier;
import farm.common.Auth;
import farm.common.SensorType;
import farm.config.FarmConfig;
import farm.gateway.KeyStore;
import farm.gateway.LocalStore;
import farm.node.PacketBuilder;
import farm.sensor.Reading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java acceptance tests for gateway storage, authentication and alert behavior.
 * Does not require JUnit or a network socket.
 *
 * Chạy sau khi compile toàn bộ source:
 *   java -cp out farm.tests.TestRunner
 */
public final class TestRunner {
    @FunctionalInterface
    private interface CheckedTest { void run() throws Exception; }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    private static Path suiteRoot;
    private static int passed;
    private static int failed;

    private TestRunner() { }

    public static void main(String[] args) throws Exception {
        suiteRoot = Files.createTempDirectory("farm-sim-tests-").toAbsolutePath().normalize();
        AssertionError firstFailure = null;
        try {
            firstFailure = run("AlertPolicy validation, persistence and node override",
                    TestRunner::testAlertPolicy, firstFailure);
            firstFailure = run("AlertRepository dedupe, lifecycle and reload",
                    TestRunner::testAlertRepository, firstFailure);
            firstFailure = run("AlertEngine abnormal, low-battery and timeout dedupe",
                    TestRunner::testAlertEngine, firstFailure);
            firstFailure = run("EmailNotifier durable outbox without SMTP",
                    TestRunner::testEmailOutbox, firstFailure);
            firstFailure = run("LocalStore persistence and capacity",
                    TestRunner::testLocalStore, firstFailure);
            firstFailure = run("Packet HMAC split and verification",
                    TestRunner::testPacketHmac, firstFailure);
            firstFailure = run("FarmConfig load and generated KeyStore persistence",
                    TestRunner::testFarmConfigAndKeyStore, firstFailure);
        } finally {
            deleteTree(suiteRoot);
        }

        System.out.printf("%nRESULT: %d PASS, %d FAIL%n", passed, failed);
        if (failed > 0) {
            AssertionError summary = new AssertionError(failed + " test(s) failed");
            if (firstFailure != null) summary.addSuppressed(firstFailure);
            throw summary;
        }
    }

    private static AssertionError run(String name, CheckedTest test, AssertionError firstFailure) {
        try {
            test.run();
            passed++;
            System.out.println("[PASS] " + name);
            return firstFailure;
        } catch (Throwable error) {
            failed++;
            AssertionError failure = error instanceof AssertionError assertion
                    ? assertion : new AssertionError(name + ": " + error, error);
            System.out.println("[FAIL] " + name + " -> " + failure.getMessage());
            failure.printStackTrace(System.out);
            return firstFailure == null ? failure : firstFailure;
        }
    }

    private static void testAlertPolicy() throws Exception {
        Path dir = caseDir("alert-policy");
        Path policyFile = dir.resolve("alert-policy.properties");
        AlertPolicy policy = new AlertPolicy(policyFile);

        expectThrows(IllegalArgumentException.class,
                () -> policy.applyPatch(Map.of("metric", "ph", "min", 7.0, "max", 6.0), "tester"),
                "min >= max must be rejected");
        expectThrows(IllegalArgumentException.class,
                () -> policy.applyPatch(Map.of("lowBatteryPct", 101), "tester"),
                "battery threshold above 100 must be rejected");
        expectThrows(IllegalArgumentException.class,
                () -> policy.applyPatch(Map.of("metric", "unknown", "min", 0, "max", 1), "tester"),
                "unsupported metric must be rejected");

        policy.applyPatch(Map.of("metric", "ph", "min", 6.0, "max", 7.0), "admin");
        policy.applyPatch(Map.of("metric", "ph", "nodeId", "NODE-001", "min", 6.2, "max", 6.8),
                "admin");
        policy.applyPatch(Map.of("staleAfterSeconds", 90, "lowBatteryPct", 25.0), "admin");

        AlertPolicy reloaded = new AlertPolicy(policyFile);
        equal(Duration.ofSeconds(90), reloaded.staleAfter(), "stale timeout must persist");
        close(25.0, reloaded.lowBatteryPct(), 0.0001, "battery threshold must persist");
        close(6.0, reloaded.ruleFor("NODE-002", "ph").min(), 0.0001,
                "other nodes must use global minimum");
        close(7.0, reloaded.ruleFor("NODE-002", "ph").max(), 0.0001,
                "other nodes must use global maximum");
        close(6.2, reloaded.ruleFor("NODE-001", "ph").min(), 0.0001,
                "node override minimum must persist");
        close(6.8, reloaded.ruleFor("NODE-001", "ph").max(), 0.0001,
                "node override maximum must persist");
        check(Files.size(policyFile) > 0, "policy file must be written");
        Path audit = policyFile.resolveSibling("alert-policy-audit.jsonl");
        check(Files.exists(audit) && Files.readAllLines(audit).size() == 3,
                "each valid patch must produce one audit event");
    }

    private static void testAlertRepository() throws Exception {
        Path store = caseDir("alert-repository").resolve("alerts.jsonl");
        Instant t0 = Instant.parse("2026-11-17T01:00:00Z");
        AlertRepository repository = new AlertRepository(store);

        AlertRepository.Creation first = repository.createIfAbsent(
                "LOW_BATTERY", "WARNING", "NODE-001", "battery",
                12.0, 0.0, 20.0, "battery low", t0);
        check(first.created(), "first active condition must create an alert");
        AlertRepository.Creation duplicate = repository.createIfAbsent(
                "LOW_BATTERY", "CRITICAL", "NODE-001", "battery",
                5.0, 0.0, 20.0, "battery still low", t0.plusSeconds(1));
        check(!duplicate.created(), "same active fingerprint must be deduplicated");
        equal(first.alert().id(), duplicate.alert().id(), "dedupe must return the active alert");
        equal(1, repository.size(), "dedupe must not increase repository size");

        Alert acknowledged = repository.transition(first.alert().id(), "ACKNOWLEDGED",
                "technician assigned", "operator", t0.plusSeconds(2));
        equal("ACKNOWLEDGED", acknowledged.status(), "alert must enter ACKNOWLEDGED state");
        equal(2, acknowledged.history().size(), "create and acknowledge must both be audited");

        AlertRepository reloaded = new AlertRepository(store);
        equal(1, reloaded.size(), "repository must reload created alert");
        Alert loaded = reloaded.get(first.alert().id());
        check(loaded != null, "reloaded alert must be addressable by id");
        equal("ACKNOWLEDGED", loaded.status(), "status must survive reload");
        equal(2, loaded.history().size(), "history must survive reload");

        Alert resolved = reloaded.clearCondition("LOW_BATTERY", "NODE-001", "battery",
                t0.plusSeconds(3));
        check(resolved != null, "clearing an active condition must resolve it");
        equal("RESOLVED", resolved.status(), "clearCondition must set RESOLVED");
        AlertRepository.Creation reopened = reloaded.createIfAbsent(
                "LOW_BATTERY", "WARNING", "NODE-001", "battery",
                10.0, 0.0, 20.0, "battery low again", t0.plusSeconds(4));
        check(reopened.created(), "condition recurring after resolution must create a new alert");
        check(!reopened.alert().id().equals(first.alert().id()), "recurrence must get a new id");

        AlertRepository finalReload = new AlertRepository(store);
        equal(2, finalReload.size(), "both lifecycle instances must reload");
        equal("RESOLVED", finalReload.get(first.alert().id()).status(),
                "old alert must remain resolved after reload");
        equal("OPEN", finalReload.get(reopened.alert().id()).status(),
                "recurring alert must remain open after reload");
    }

    private static void testAlertEngine() throws Exception {
        Path dir = caseDir("alert-engine");
        AlertPolicy policy = AlertPolicy.inMemory();
        policy.applyPatch(Map.of("staleAfterSeconds", 60, "lowBatteryPct", 20.0), "test");
        AlertRepository repository = new AlertRepository(dir.resolve("alerts.jsonl"));
        EmailNotifier email = new EmailNotifier(dir.resolve("outbox.jsonl"), Map.of());
        AlertEngine engine = new AlertEngine(policy, repository, email);
        Instant t0 = Instant.parse("2026-11-17T01:00:00Z");

        engine.register("NODE-001", t0);
        engine.onPacket("NODE-001", t0, t0, 10.0,
                Map.of("temperature", 50.0), List.of());
        equal(2, repository.size(), "abnormal temperature and low battery must create two alerts");
        equal(Set.of("ABNORMAL_SENSOR_DATA", "LOW_BATTERY"), types(repository.list("OPEN", 100)),
                "engine must create the expected alert types");

        engine.onPacket("NODE-001", t0, t0.plusSeconds(1), 8.0,
                Map.of("temperature", 55.0), List.of());
        equal(2, repository.size(), "repeated abnormal packet must not duplicate active alerts");

        engine.checkTimeouts(t0.plusSeconds(61));
        equal(3, repository.size(), "stale node must create one timeout alert");
        engine.checkTimeouts(t0.plusSeconds(180));
        equal(3, repository.size(), "repeated timeout checks must not duplicate active alert");
        equal(Set.of("ABNORMAL_SENSOR_DATA", "LOW_BATTERY", "DATA_TIMEOUT"),
                types(repository.list("OPEN", 100)), "all three conditions must be active");

        Instant recovered = t0.plusSeconds(181);
        engine.onPacket("NODE-001", recovered, recovered, 90.0,
                Map.of("temperature", 25.0), List.of());
        equal(0, repository.list("OPEN", 100).size(),
                "normal packet must resolve timeout, battery and abnormal alerts");
        equal(3, repository.list("RESOLVED", 100).size(),
                "all generated conditions must have a resolved audit state");
    }

    private static void testEmailOutbox() throws Exception {
        Path dir = caseDir("email-outbox");
        Path outbox = dir.resolve("email-outbox.jsonl");
        AlertRepository alerts = new AlertRepository(null);
        Alert alert = alerts.createIfAbsent("DATA_TIMEOUT", "WARNING", "NODE-002", null,
                120.0, null, 60.0, "node stale", Instant.parse("2026-11-17T02:00:00Z")).alert();

        Map<String, String> envWithoutSmtp = Map.of("ALERT_EMAIL_TO", "farmer@example.test");
        EmailNotifier notifier = new EmailNotifier(outbox, envWithoutSmtp);
        notifier.enqueue(alert);
        equal(1, notifier.pendingCount(),
                "recipient configured without SMTP must keep notification in outbox");
        equal(0, notifier.flush(), "flush without SMTP host must not attempt a socket or remove mail");
        check(Files.exists(outbox) && Files.size(outbox) > 0, "outbox must be persisted");
        String persisted = Files.readString(outbox);
        check(persisted.contains("farmer@example.test") && persisted.contains(alert.id()),
                "outbox record must contain recipient and alert identity");

        EmailNotifier reloaded = new EmailNotifier(outbox, envWithoutSmtp);
        equal(1, reloaded.pendingCount(), "pending mail must survive notifier restart");
        equal(0, reloaded.flush(), "reloaded outbox must remain pending while SMTP is absent");
    }

    private static void testLocalStore() throws Exception {
        Path queueFile = caseDir("local-store").resolve("pending.jsonl");
        LocalStore first = new LocalStore(queueFile, 2);
        first.append("{\"id\":1}");
        first.append("{\"id\":2}");
        equal(List.of("{\"id\":1}", "{\"id\":2}"), first.snapshot(),
                "queue must preserve FIFO insertion order");

        LocalStore reloaded = new LocalStore(queueFile, 2);
        equal(first.snapshot(), reloaded.snapshot(), "queue records must survive reload");
        reloaded.append("{\"id\":3}");
        equal(List.of("{\"id\":2}", "{\"id\":3}"), reloaded.snapshot(),
                "capacity overflow must drop the oldest record");
        equal(1, reloaded.dropped(), "overflow must increment dropped counter");
        expectThrows(IllegalStateException.class,
                () -> reloaded.appendAllStrict(List.of("{\"id\":4}")),
                "strict store must reject overflow instead of dropping old data");
        equal(List.of("{\"id\":2}", "{\"id\":3}"), reloaded.snapshot(),
                "failed strict append must leave queue unchanged");

        LocalStore afterCapacity = new LocalStore(queueFile, 2);
        equal(List.of("{\"id\":2}", "{\"id\":3}"), afterCapacity.snapshot(),
                "capacity result must be durable");
        afterCapacity.removeFirst(1);
        LocalStore afterAck = new LocalStore(queueFile, 2);
        equal(List.of("{\"id\":3}"), afterAck.snapshot(),
                "acknowledged FIFO removal must be durable");
    }

    private static void testPacketHmac() {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] wrongKey = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        String packet = PacketBuilder.build("NODE-001", 7, "0011223344556677",
                Instant.parse("2026-11-17T03:00:00Z"), 88.5,
                List.of(new Reading(SensorType.PH, 6.5, 1878, "OK")), key);

        String[] split = Auth.split(packet);
        check(split != null && split.length == 2, "signed packet must split into body and signature");
        check(split[0].contains("\"nodeId\":\"NODE-001\"") && split[0].contains("\"seq\":7"),
                "signed body must preserve packet identity and sequence");
        check(Auth.verify(key, split[0], split[1]), "correct key must verify packet HMAC");
        check(!Auth.verify(wrongKey, split[0], split[1]), "wrong key must fail packet HMAC");
        String tamperedBody = split[0].replace("\"value\":6.50", "\"value\":7.50");
        check(!tamperedBody.equals(split[0]), "test must actually alter the packet body");
        check(!Auth.verify(key, tamperedBody, split[1]), "tampered payload must fail HMAC");
        check(Auth.split("{\"v\":1}") == null, "unsigned packet must not split as authenticated");
    }

    private static void testFarmConfigAndKeyStore() throws Exception {
        Path dir = caseDir("farm-config");
        Path configDir = Files.createDirectories(dir.resolve("config"));
        Path runtimeDir = dir.resolve("runtime").toAbsolutePath().normalize();
        Path configFile = configDir.resolve("farm-test.properties");
        String properties = """
                paths.base-dir=../runtime
                gateway.id=GW-TEST-01
                gateway.speed-mps=5
                gateway.altitude-m=30
                gateway.radio-range-m=40
                gateway.waypoints=0,0;10,20
                backend.ingest-url=http://127.0.0.1:9999/api/ingest
                backend.connect-timeout-ms=100
                backend.request-timeout-ms=200
                storage.queue-file=queue/pending.jsonl
                storage.dead-letter-file=queue/dead-letter.jsonl
                storage.capacity=25
                security.key-file=secrets/node-keys.properties
                radio.packet-loss-rate=0.10
                radio.random-seed=42
                retry.initial-delay-ms=100
                retry.max-delay-ms=1000
                retry.multiplier=2.0
                retry.jitter-ratio=0.20
                nodes.ids=NODE-001,NODE-002
                node.NODE-001.model=ESP32-TEST
                node.NODE-001.firmware=2.0.0
                node.NODE-001.farm-id=FARM-TEST
                node.NODE-001.x=1
                node.NODE-001.y=2
                node.NODE-001.simulation-seed=101
                node.NODE-002.farm-id=FARM-TEST
                node.NODE-002.x=3
                node.NODE-002.y=4
                node.NODE-002.simulation-seed=202
                """;
        Files.writeString(configFile, properties, StandardCharsets.ISO_8859_1);

        FarmConfig config = FarmConfig.load(configFile);
        equal(runtimeDir, config.baseDir(), "relative base directory must resolve from config directory");
        equal("GW-TEST-01", config.gateway().id(), "gateway id must load");
        equal(2, config.gateway().waypoints().size(), "waypoints must load");
        equal(2, config.sensorNodes().size(), "node list must load");
        equal("ESP32-SIM", config.sensorNodes().get(1).model(), "optional node model must use default");
        equal(runtimeDir.resolve("queue/pending.jsonl"), config.storage().queueFile(),
                "queue path must resolve under base directory");
        equal(25, config.storage().capacity(), "storage capacity must load");
        equal(Duration.ofMillis(100), config.backend().connectTimeout(), "connect timeout must load");
        close(0.20, config.retryPolicy().jitterRatio(), 0.0001, "retry jitter must load");

        KeyStore generated = config.loadOrCreateKeyStore();
        byte[] node1 = generated.get("NODE-001");
        byte[] node2 = generated.get("NODE-002");
        check(node1 != null && node1.length == 32, "NODE-001 must receive a generated 32-byte key");
        check(node2 != null && node2.length == 32, "NODE-002 must receive a generated 32-byte key");
        check(!Arrays.equals(node1, node2), "different nodes must not share a generated key");
        check(Files.exists(config.keyFile()) && Files.size(config.keyFile()) > 0,
                "generated key file must be persisted");
        String firstFile = Files.readString(config.keyFile());

        KeyStore secondLoad = config.loadOrCreateKeyStore();
        check(Arrays.equals(node1, secondLoad.get("NODE-001")),
                "loadOrCreate must preserve an existing key across restart");
        check(Arrays.equals(node2, secondLoad.get("NODE-002")),
                "all existing node keys must survive restart");
        equal(firstFile, Files.readString(config.keyFile()),
                "loading a complete key store must not rewrite it");

        byte original = node1[0];
        node1[0] ^= 0x7f;
        equal(original, secondLoad.get("NODE-001")[0],
                "get() must return a defensive copy of secret key bytes");
        KeyStore directReload = KeyStore.load(config.keyFile());
        check(Arrays.equals(secondLoad.get("NODE-001"), directReload.get("NODE-001")),
                "direct KeyStore reload must preserve generated bytes");
    }

    private static Set<String> types(List<Alert> alerts) {
        Set<String> result = new HashSet<>();
        for (Alert alert : alerts) result.add(alert.type());
        return Set.copyOf(result);
    }

    private static Path caseDir(String name) throws IOException {
        return Files.createDirectories(suiteRoot.resolve(name));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void close(double expected, double actual, double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static <T extends Throwable> T expectThrows(Class<T> expected, CheckedAction action,
                                                         String message) throws Exception {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return expected.cast(actual);
            throw new AssertionError(message + " (expected " + expected.getSimpleName()
                    + ", got " + actual.getClass().getSimpleName() + ")", actual);
        }
        throw new AssertionError(message + " (expected " + expected.getSimpleName() + ")");
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
