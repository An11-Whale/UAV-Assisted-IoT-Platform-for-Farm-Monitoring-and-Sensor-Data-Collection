package farm.config;

import farm.gateway.KeyStore;
import farm.gateway.RetryPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads and validates gateway topology, sensor nodes, storage, backend, radio and retry settings.
 * Secrets remain outside source control; relative paths resolve from {@code paths.base-dir}, which is
 * itself resolved against the configuration file directory.
 */
public final class FarmConfig {
    private static final Pattern GATEWAY_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$");
    private static final Pattern NODE_ID = Pattern.compile("^NODE-\\d{3}$");

    public record Point(double x, double y) { }

    public record Gateway(String id, double speedMps, double altitudeM, double radioRangeM,
                          List<Point> waypoints) {
        public Gateway {
            waypoints = List.copyOf(waypoints);
        }
    }

    public record SensorNode(String id, String model, String firmware, String farmId,
                             double x, double y, long simulationSeed) { }

    public record Storage(Path queueFile, Path deadLetterFile, int capacity) { }

    public record Backend(URI ingestUri, Duration connectTimeout, Duration requestTimeout) { }

    public record Radio(double packetLossRate, long randomSeed) { }

    private final Path sourceFile;
    private final Path baseDir;
    private final Gateway gateway;
    private final List<SensorNode> sensorNodes;
    private final Storage storage;
    private final Backend backend;
    private final Radio radio;
    private final Path keyFile;
    private final RetryPolicy retryPolicy;

    private FarmConfig(Path sourceFile, Path baseDir, Gateway gateway, List<SensorNode> sensorNodes,
                       Storage storage, Backend backend, Radio radio, Path keyFile,
                       RetryPolicy retryPolicy) {
        this.sourceFile = sourceFile;
        this.baseDir = baseDir;
        this.gateway = gateway;
        this.sensorNodes = List.copyOf(sensorNodes);
        this.storage = storage;
        this.backend = backend;
        this.radio = radio;
        this.keyFile = keyFile;
        this.retryPolicy = retryPolicy;
    }

    public static FarmConfig loadDefault() {
        String configured = System.getProperty("farm.sim.config");
        if (configured == null || configured.isBlank()) configured = System.getenv("FARM_SIM_CONFIG");
        Path path;
        if (configured == null || configured.isBlank()) {
            path = Path.of("config", "farm-sim.properties");
            if (!Files.exists(path)) {
                Path fromRepositoryRoot = Path.of("src", "farm-sim", "config", "farm-sim.properties");
                if (Files.exists(fromRepositoryRoot)) path = fromRepositoryRoot;
            }
        } else {
            path = Path.of(configured);
        }
        return load(path);
    }

    public static FarmConfig load(Path file) {
        if (file == null) throw new IllegalArgumentException("config file khong duoc null");
        Path source = file.toAbsolutePath().normalize();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(source)) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("khong doc duoc config: " + source, e);
        }

        Path configDir = source.getParent();
        Path base = resolve(configDir, optional(p, "paths.base-dir", "."));

        String gatewayId = required(p, "gateway.id");
        if (!GATEWAY_ID.matcher(gatewayId).matches())
            throw invalid("gateway.id", "chi dung chu, so, _, - va dai 3..64 ky tu");
        double speed = positive(p, "gateway.speed-mps");
        double altitude = nonNegative(p, "gateway.altitude-m");
        double range = positive(p, "gateway.radio-range-m");
        List<Point> waypoints = parseWaypoints(required(p, "gateway.waypoints"));
        if (waypoints.size() < 2) throw invalid("gateway.waypoints", "can it nhat 2 diem");
        Gateway gateway = new Gateway(gatewayId, speed, altitude, range, waypoints);

        List<SensorNode> nodes = parseNodes(p);
        if (nodes.isEmpty()) throw invalid("nodes.ids", "can it nhat 1 node");

        int capacity = integer(p, "storage.capacity", 1, 10_000_000);
        Path queue = resolve(base, required(p, "storage.queue-file"));
        Path deadLetter = resolve(base, required(p, "storage.dead-letter-file"));
        if (queue.equals(deadLetter))
            throw invalid("storage.dead-letter-file", "phai khac queue-file");
        Storage storage = new Storage(queue, deadLetter, capacity);

        URI ingest = parseHttpUri(required(p, "backend.ingest-url"));
        Duration connectTimeout = millis(p, "backend.connect-timeout-ms");
        Duration requestTimeout = millis(p, "backend.request-timeout-ms");
        Backend backend = new Backend(ingest, connectTimeout, requestTimeout);

        double loss = decimal(p, "radio.packet-loss-rate");
        if (loss < 0 || loss > 1) throw invalid("radio.packet-loss-rate", "phai trong [0,1]");
        Radio radio = new Radio(loss, longValue(p, "radio.random-seed"));

        Path keyFile = resolve(base, required(p, "security.key-file"));
        Duration initial = millis(p, "retry.initial-delay-ms");
        Duration maximum = millis(p, "retry.max-delay-ms");
        double multiplier = decimal(p, "retry.multiplier");
        double jitter = decimal(p, "retry.jitter-ratio");
        RetryPolicy retry = new RetryPolicy(initial, maximum, multiplier, jitter);

        return new FarmConfig(source, base, gateway, nodes, storage, backend, radio, keyFile, retry);
    }

    public KeyStore loadOrCreateKeyStore() {
        return KeyStore.loadOrCreate(keyFile, sensorNodes.stream().map(SensorNode::id).toList());
    }

    public Path sourceFile() { return sourceFile; }
    public Path baseDir() { return baseDir; }
    public Gateway gateway() { return gateway; }
    public List<SensorNode> sensorNodes() { return sensorNodes; }
    public Storage storage() { return storage; }
    public Backend backend() { return backend; }
    public Radio radio() { return radio; }
    public Path keyFile() { return keyFile; }
    public RetryPolicy retryPolicy() { return retryPolicy; }

    private static List<SensorNode> parseNodes(Properties p) {
        String raw = required(p, "nodes.ids");
        List<SensorNode> nodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String token : raw.split(",")) {
            String id = token.trim().toUpperCase(Locale.ROOT);
            if (!NODE_ID.matcher(id).matches()) throw invalid("nodes.ids", "nodeId sai: " + id);
            if (!seen.add(id)) throw invalid("nodes.ids", "node trung lap: " + id);
            String prefix = "node." + id + ".";
            nodes.add(new SensorNode(id,
                    optional(p, prefix + "model", "ESP32-SIM"),
                    optional(p, prefix + "firmware", "1.0.0"),
                    required(p, prefix + "farm-id"),
                    decimal(p, prefix + "x"),
                    decimal(p, prefix + "y"),
                    longValue(p, prefix + "simulation-seed")));
        }
        return nodes;
    }

    private static List<Point> parseWaypoints(String raw) {
        List<Point> points = new ArrayList<>();
        for (String token : raw.split(";")) {
            String[] xy = token.trim().split(",", -1);
            if (xy.length != 2) throw invalid("gateway.waypoints", "dinh dang moi diem la x,y");
            try {
                double x = Double.parseDouble(xy[0].trim());
                double y = Double.parseDouble(xy[1].trim());
                if (!Double.isFinite(x) || !Double.isFinite(y)) throw new NumberFormatException();
                points.add(new Point(x, y));
            } catch (NumberFormatException e) {
                throw invalid("gateway.waypoints", "toa do khong hop le: " + token);
            }
        }
        return points;
    }

    private static Path resolve(Path parent, String raw) {
        Path path = Path.of(raw.trim());
        return (path.isAbsolute() ? path : parent.resolve(path)).toAbsolutePath().normalize();
    }

    private static URI parseHttpUri(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException e) {
            throw invalid("backend.ingest-url", "can URL http/https day du");
        }
    }

    private static Duration millis(Properties p, String key) {
        long value = longValue(p, key);
        if (value < 1) throw invalid(key, "phai >= 1 ms");
        return Duration.ofMillis(value);
    }

    private static double positive(Properties p, String key) {
        double value = decimal(p, key);
        if (value <= 0) throw invalid(key, "phai > 0");
        return value;
    }

    private static double nonNegative(Properties p, String key) {
        double value = decimal(p, key);
        if (value < 0) throw invalid(key, "phai >= 0");
        return value;
    }

    private static int integer(Properties p, String key, int min, int max) {
        try {
            int value = Integer.parseInt(required(p, key));
            if (value < min || value > max) throw invalid(key, "phai trong [" + min + "," + max + "]");
            return value;
        } catch (NumberFormatException e) {
            throw invalid(key, "khong phai so nguyen");
        }
    }

    private static long longValue(Properties p, String key) {
        try {
            return Long.parseLong(required(p, key));
        } catch (NumberFormatException e) {
            throw invalid(key, "khong phai so nguyen");
        }
    }

    private static double decimal(Properties p, String key) {
        try {
            double value = Double.parseDouble(required(p, key));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw invalid(key, "khong phai so huu han");
        }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw invalid(key, "bi thieu/rong");
        return value.trim();
    }

    private static String optional(Properties p, String key, String fallback) {
        String value = p.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static IllegalArgumentException invalid(String key, String reason) {
        return new IllegalArgumentException("config '" + key + "' " + reason);
    }
}
