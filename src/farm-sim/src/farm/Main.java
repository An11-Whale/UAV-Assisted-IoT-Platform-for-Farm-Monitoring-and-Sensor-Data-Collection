package farm;

import farm.common.Log;
import farm.common.SimClock;
import farm.config.FarmConfig;
import farm.gateway.ConnectivityMonitor;
import farm.gateway.DataCollector;
import farm.gateway.KeyStore;
import farm.gateway.LocalStore;
import farm.gateway.UavGateway;
import farm.gateway.Uploader;
import farm.node.NodeIdentity;
import farm.node.RadioMedium;
import farm.node.SensorNode;
import farm.sensor.LightSensor;
import farm.sensor.PhSensor;
import farm.sensor.Sensor;
import farm.sensor.SoilMoistureSensor;
import farm.sensor.TempHumiditySensor;
import farm.sensor.WaterLevelSensor;
import farm.server.MockIngestionServer;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Chạy demo end-to-end các task LQ: sensor -> gateway -> offline/retry -> alert server. */
public class Main {
    public static void main(String[] args) throws Exception {
        String configOption = optionValue(args, "--config");
        Path configPath = configOption == null ? Path.of("config", "farm-sim.properties") : Path.of(configOption);
        FarmConfig config = FarmConfig.load(configPath);

        int missions = intOption(args, "--missions", 4);
        int tickMs = intOption(args, "--tick-ms", 20);
        int port = intOption(args, "--port", portOf(config.backend().ingestUri(), 8080));
        String urlOption = optionValue(args, "--url");
        URI ingestUri = urlOption == null ? config.backend().ingestUri() : URI.create(urlOption);
        if (hasOption(args, "--port") && urlOption == null)
            ingestUri = URI.create("http://localhost:" + port + "/api/ingest");
        boolean noServer = hasOption(args, "--no-server");
        boolean offlineFirst = hasOption(args, "--offline-first");
        boolean keep = hasOption(args, "--keep");
        boolean resetQueue = hasOption(args, "--reset-queue");
        validateOptions(args);
        if (missions < 1 || tickMs < 0) throw new IllegalArgumentException("missions >= 1 va tick-ms >= 0");

        SimClock clock = new SimClock(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1)
                .atTime(6, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        Log.init(clock);
        Log.i("CONFIG", "Da nap " + config.sourceFile());

        // F116: khóa sinh ngẫu nhiên ở data/node-keys.properties, không hardcode/commit.
        KeyStore keys = config.loadOrCreateKeyStore();
        FarmConfig.Radio radioConfig = config.radio();
        RadioMedium radio = new RadioMedium(radioConfig.packetLossRate(), radioConfig.randomSeed());
        for (FarmConfig.SensorNode node : config.sensorNodes())
            radio.register(newNode(node, keys.get(node.id())));

        // Hai node đối kháng để chứng minh F43: unknown và ký sai khóa.
        radio.register(newNode(new FarmConfig.SensorNode("NODE-098", "ESP32-SIM", "1.0.0",
                "FARM-01", 70, 60, 9898), randomKey()));
        keys.provision("NODE-099", randomKey());
        radio.register(newNode(new FarmConfig.SensorNode("NODE-099", "ESP32-SIM", "1.0.0",
                "FARM-01", 130, 60, 9999), randomKey()));

        FarmConfig.Gateway gateway = config.gateway();
        List<double[]> waypoints = gateway.waypoints().stream()
                .map(p -> new double[]{p.x(), p.y()}).toList();
        UavGateway uav = new UavGateway(gateway.id(), gateway.speedMps(), gateway.altitudeM(),
                gateway.radioRangeM(), waypoints);

        FarmConfig.Storage storage = config.storage();
        if (resetQueue) {
            Files.deleteIfExists(storage.queueFile());
            Files.deleteIfExists(storage.deadLetterFile());
            Log.i("QUEUE", "Da reset queue/dead-letter theo yeu cau --reset-queue");
        }
        LocalStore store = new LocalStore(storage.queueFile(), storage.capacity());
        LocalStore deadLetters = new LocalStore(storage.deadLetterFile(), Math.max(storage.capacity(), 1000));
        DataCollector collector = new DataCollector(uav.id, keys, radio, store, clock);
        ConnectivityMonitor connectivity = new ConnectivityMonitor();
        Uploader uploader = new Uploader(ingestUri, uav.id, store, deadLetters, clock, connectivity,
                config.retryPolicy(), config.backend().connectTimeout(), config.backend().requestTimeout());

        MockIngestionServer server = null;
        try {
            if (!noServer && !offlineFirst) server = startServer(port, config);

        int totalUploaded = 0;
        for (int m = 1; m <= missions; m++) {
            if (!noServer && offlineFirst && m == 2) server = startServer(port, config);
            Log.i("MISSION", "#" + m + " bat dau (UAV bay, tam thu " + (int) uav.rangeM + "m)");
            int c0 = collector.collected, r0 = collector.rejected, l0 = collector.lost;
            uav.startMission();
            collector.newMission();
            boolean finished = false;
            while (!finished) {
                finished = uav.step(1);
                clock.advance(1);
                collector.poll(uav);
                totalUploaded += uploader.syncIfDue();
                if (tickMs > 0) Thread.sleep(tickMs);
            }
            Log.i("MISSION", "#" + m + " ve base. Thu " + (collector.collected - c0) + " node, tu choi "
                    + (collector.rejected - r0) + ", mat goi " + (collector.lost - l0) + ", hang doi " + store.size());
            // Một lần force ở ranh giới mission; vòng lặp bên trên vẫn là cơ chế auto retry chính.
            totalUploaded += uploader.uploadAll();
            clock.advance(3600);
        }

        System.out.println();
        System.out.println("===== TONG KET =====");
        System.out.println("Da thu (hop le)      : " + collector.collected);
        System.out.println("Bi tu choi (xac thuc): " + collector.rejected);
        System.out.println("Mat goi radio        : " + collector.lost);
        System.out.println("Da dong bo server    : " + totalUploaded);
        System.out.println("Con trong hang doi   : " + store.size());
        System.out.println("Dead-letter          : " + deadLetters.size());
        System.out.println("Queue bi drop do day : " + store.dropped());
        System.out.println("Ket noi cuoi          : " + uploader.connectivity().state());
        if (server != null) {
            System.out.println("Server: accepted=" + server.accepted + " duplicates=" + server.duplicates
                    + " rejected=" + server.rejected + " rows=" + server.rowCount()
                    + " alerts=" + server.alerts().size());
            if (keep) {
                System.out.println("Dashboard API : http://localhost:" + port + "/api/v1/sensors/latest");
                System.out.println("Alert API     : http://localhost:" + port + "/api/v1/alerts");
                System.out.println("Nhan Ctrl+C de dung.");
                Thread.currentThread().join();
            }
            }
        } finally {
            // Bảo đảm mock server/executor không giữ port nếu collection/storage ném lỗi.
            if (server != null) server.stop();
        }
    }

    private static MockIngestionServer startServer(int port, FarmConfig config) {
        try {
            MockIngestionServer server = MockIngestionServer.start(port,
                    config.baseDir().resolve("data"), System.getenv("FARM_ADMIN_KEY"), System.getenv());
            for (FarmConfig.SensorNode node : config.sensorNodes()) server.registerNode(node.id());
            return server;
        } catch (Exception e) {
            Log.i("SERVER", "Khong mo duoc cong " + port + " (" + e.getMessage()
                    + "). Dung --port khac hoac --no-server.");
            return null;
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private static SensorNode newNode(FarmConfig.SensorNode cfg, byte[] key) {
        long seed = cfg.simulationSeed();
        int n;
        try { n = Integer.parseInt(cfg.id().substring(cfg.id().lastIndexOf('-') + 1)); }
        catch (RuntimeException e) { n = (int) (Math.abs(seed) % 100); }
        List<Sensor> sensors = List.of(
                new SoilMoistureSensor(seed, 45 + (n * 7) % 25),
                new TempHumiditySensor(seed + 1, (n % 3) - 1.0),
                new LightSensor(seed + 2),
                new PhSensor(seed + 3, 6.2 + 0.1 * (n % 4)),
                new WaterLevelSensor(seed + 4, 60 + (n * 9) % 35));
        return new SensorNode(new NodeIdentity(cfg.id(), cfg.model(), cfg.firmware(), cfg.farmId()),
                key, cfg.x(), cfg.y(), sensors);
    }

    private static int portOf(URI uri, int fallback) {
        if (uri.getPort() > 0) return uri.getPort();
        if ("https".equalsIgnoreCase(uri.getScheme())) return 443;
        return uri.getHost() == null ? fallback : 80;
    }

    private static boolean hasOption(String[] args, String option) {
        for (String arg : args) if (option.equals(arg)) return true;
        return false;
    }

    private static String optionValue(String[] args, String option) {
        for (int i = 0; i < args.length; i++) {
            if (!option.equals(args[i])) continue;
            if (i + 1 >= args.length) throw new IllegalArgumentException("thieu gia tri sau " + option);
            return args[i + 1];
        }
        return null;
    }

    private static int intOption(String[] args, String option, int fallback) {
        String value = optionValue(args, option);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(option + " phai la so nguyen"); }
    }

    private static void validateOptions(String[] args) {
        List<String> values = List.of("--missions", "--tick-ms", "--port", "--url", "--config");
        List<String> flags = List.of("--no-server", "--offline-first", "--keep", "--reset-queue");
        for (int i = 0; i < args.length; i++) {
            if (values.contains(args[i])) { i++; continue; }
            if (flags.contains(args[i])) continue;
            throw new IllegalArgumentException("tham so khong hop le: " + args[i]);
        }
    }
}
