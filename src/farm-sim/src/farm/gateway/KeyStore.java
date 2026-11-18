package farm.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provisions and protects the per-node HMAC secrets used for sensor authentication (F43).
 * Runtime keys can be loaded from a properties file or securely generated and persisted;
 * callers only receive defensive copies of stored key material.
 */
public class KeyStore {
    private static final Pattern NODE_ID = Pattern.compile("^NODE-\\d{3}$");
    private static final int GENERATED_KEY_BYTES = 32;

    private final Map<String, byte[]> keys = new HashMap<>();

    public synchronized void provision(String nodeId, byte[] key) {
        validateNodeId(nodeId);
        if (key == null || key.length < 16)
            throw new IllegalArgumentException("key phai co it nhat 16 byte");
        keys.put(nodeId, key.clone());
    }

    /** Trả bản sao để code gọi không thể sửa khóa đang lưu trong gateway. */
    public synchronized byte[] get(String nodeId) {
        byte[] key = keys.get(nodeId);
        return key == null ? null : key.clone();
    }

    public synchronized Set<String> nodeIds() {
        return Set.copyOf(keys.keySet());
    }

    /**
     * Nạp kho khóa runtime. Node chưa có khóa sẽ được sinh bằng SecureRandom rồi ghi bền vững.
     * File này phải nằm ngoài source control (cấu hình mẫu dùng thư mục data/ đã gitignore).
     */
    public static KeyStore loadOrCreate(Path file, Iterable<String> requiredNodeIds) {
        if (file == null) throw new IllegalArgumentException("file khong duoc null");
        if (requiredNodeIds == null) throw new IllegalArgumentException("requiredNodeIds khong duoc null");
        Path target = file.toAbsolutePath().normalize();
        Properties properties = new Properties();
        boolean changed = !Files.exists(target);

        try {
            if (Files.exists(target)) {
                try (InputStream in = Files.newInputStream(target)) {
                    properties.load(in);
                }
            }

            List<String> required = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            for (String nodeId : requiredNodeIds) {
                validateNodeId(nodeId);
                if (!unique.add(nodeId)) throw new IllegalArgumentException("node trung lap: " + nodeId);
                required.add(nodeId);
            }

            SecureRandom random = new SecureRandom();
            for (String nodeId : required) {
                if (properties.getProperty(nodeId) == null) {
                    byte[] key = new byte[GENERATED_KEY_BYTES];
                    random.nextBytes(key);
                    properties.setProperty(nodeId, Base64.getEncoder().encodeToString(key));
                    changed = true;
                }
            }

            KeyStore store = fromProperties(properties);
            for (String nodeId : required) {
                if (store.get(nodeId) == null)
                    throw new IllegalStateException("khong tao duoc key cho " + nodeId);
            }
            if (changed) writeAtomically(target, properties);
            return store;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static KeyStore load(Path file) {
        if (file == null) throw new IllegalArgumentException("file khong duoc null");
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file.toAbsolutePath().normalize())) {
            properties.load(in);
            return fromProperties(properties);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static KeyStore fromProperties(Properties properties) {
        KeyStore store = new KeyStore();
        for (String nodeId : properties.stringPropertyNames()) {
            validateNodeId(nodeId);
            String encoded = properties.getProperty(nodeId);
            try {
                store.provision(nodeId, Base64.getDecoder().decode(encoded));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("key Base64 khong hop le cho " + nodeId, e);
            }
        }
        return store;
    }

    private static void validateNodeId(String nodeId) {
        if (nodeId == null || !NODE_ID.matcher(nodeId).matches())
            throw new IllegalArgumentException("nodeId sai dinh dang NODE-001: " + nodeId);
    }

    private static void writeAtomically(Path file, Properties properties) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 OutputStream out = Channels.newOutputStream(channel)) {
                properties.store(out, "UAV farm node keys - DO NOT COMMIT");
                out.flush();
                channel.force(true);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(file);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(tmp);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows không hỗ trợ POSIX permissions; ACL của thư mục data được dùng thay thế.
        }
    }
}
