package farm.gateway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * F48 - Local Data Storage: hàng đợi bền vững (JSON Lines) cho cơ chế store-and-forward.
 * Mỗi dòng = 1 bản ghi đã thu. Mất điện/khởi động lại vẫn còn dữ liệu; đầy thì bỏ bản ghi cũ nhất.
 */
public class LocalStore {
    private static final int MOVE_ATTEMPTS = 8;
    private final Path file;
    private final int capacity;
    private final List<String> lines = new ArrayList<>();
    private int dropped = 0;

    public LocalStore(Path file, int capacity) {
        if (file == null) throw new IllegalArgumentException("file khong duoc null");
        if (capacity < 1) throw new IllegalArgumentException("capacity phai >= 1");
        this.file = file.toAbsolutePath().normalize();
        this.capacity = capacity;
        try {
            Files.createDirectories(this.file.getParent());
            if (Files.exists(this.file)) {
                for (String l : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
                    if (!l.isBlank()) lines.add(l);
                }
                if (lines.size() > capacity) {
                    int excess = lines.size() - capacity;
                    lines.subList(0, excess).clear();
                    dropped += excess;
                    rewriteAtomically(lines);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void append(String line) {
        appendAll(List.of(line));
    }

    /**
     * Ghi nhiều bản ghi trong một transaction file. File cũ chỉ bị thay sau khi file tạm
     * đã được flush xuống đĩa; nếu ghi lỗi thì hàng đợi trong bộ nhớ và trên đĩa vẫn giữ nguyên.
     */
    public synchronized void appendAll(Collection<String> newLines) {
        appendAll(newLines, true);
    }

    /** Dùng cho dead-letter: đầy kho thì báo lỗi, tuyệt đối không loại bản ghi cũ. */
    public synchronized void appendAllStrict(Collection<String> newLines) {
        appendAll(newLines, false);
    }

    private void appendAll(Collection<String> newLines, boolean dropOldestOnOverflow) {
        if (newLines == null) throw new IllegalArgumentException("newLines khong duoc null");
        if (newLines.isEmpty()) return;

        List<String> next = new ArrayList<>(lines);
        for (String line : newLines) {
            validateLine(line);
            next.add(line);
        }
        int overflow = Math.max(0, next.size() - capacity);
        if (overflow > 0 && !dropOldestOnOverflow)
            throw new IllegalStateException("local store da day; can xu ly dead-letter truoc khi sync tiep");
        if (overflow > 0) next.subList(0, overflow).clear();

        rewrite(next);
        lines.clear();
        lines.addAll(next);
        dropped += overflow;
    }

    public synchronized List<String> peek(int n) {
        if (n < 0) throw new IllegalArgumentException("n phai >= 0");
        return new ArrayList<>(lines.subList(0, Math.min(n, lines.size())));
    }

    public synchronized void removeFirst(int n) {
        if (n < 0) throw new IllegalArgumentException("n phai >= 0");
        int count = Math.min(n, lines.size());
        if (count == 0) return;
        List<String> next = new ArrayList<>(lines.subList(count, lines.size()));
        rewrite(next);
        lines.clear();
        lines.addAll(next);
    }

    public synchronized int size() { return lines.size(); }

    public synchronized int dropped() { return dropped; }

    /** Snapshot an toàn để hiển thị/troubleshoot, không trả collection nội bộ. */
    public synchronized List<String> snapshot() { return List.copyOf(lines); }

    public Path path() { return file; }

    public int capacity() { return capacity; }

    private static void validateLine(String line) {
        if (line == null || line.isBlank()) throw new IllegalArgumentException("record khong duoc rong");
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0)
            throw new IllegalArgumentException("moi record JSONL phai nam tren mot dong");
    }

    private void rewrite(List<String> content) {
        try {
            rewriteAtomically(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void rewriteAtomically(List<String> content) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String line : content) {
                    validateLine(line);
                    byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                }
                channel.force(true);
            }
            replaceWithRetry(tmp, file);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(tmp);
        }
    }

    /**
     * OneDrive/antivirus trên Windows đôi khi giữ target trong vài mili-giây. Retry
     * bounded vẫn giữ nguyên file cũ cho tới khi replace thành công, thay vì xóa target.
     */
    private static void replaceWithRetry(Path source, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                if (attempt == MOVE_ATTEMPTS) break;
                try { Thread.sleep(Math.min(500L, 20L << (attempt - 1))); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Bi interrupt khi cho file runtime duoc mo khoa", interrupted);
                }
            }
        }
        throw last;
    }
}
