package farm.gateway;

import farm.common.Json;
import farm.common.Log;
import farm.common.SimClock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronizes queued gateway records to the backend in bounded batches (F50-F52).
 *
 * <p>The uploader combines connectivity tracking, timeout handling and exponential retry.
 * Accepted or duplicate records are acknowledged, backend-rejected records are moved to a
 * durable dead-letter store, and ambiguous failures remain queued for safe redelivery.</p>
 */
public class Uploader {
    private static final int BATCH = 20;

    public enum SyncStatus { IDLE, BACKING_OFF, SUCCESS, PARTIAL, FAILED }

    public record SyncResult(
            SyncStatus status,
            int synchronizedRecords,
            int deadLetteredRecords,
            int remainingRecords,
            Instant nextAttemptAt,
            String detail) { }

    private record BatchOutcome(Map<Integer, List<String>> rejected) { }

    private final URI uri;
    private final String gatewayId;
    private final LocalStore store;
    private final LocalStore deadLetters;
    private final SimClock simulationClock;
    private final ConnectivityMonitor connectivity;
    private final RetryPolicy retryPolicy;
    private final Clock wallClock;
    private final Duration requestTimeout;
    private final HttpClient http;

    private int batchNo;
    private int consecutiveFailures;
    private Instant nextAttemptAt = Instant.EPOCH;
    private SyncResult lastResult = new SyncResult(SyncStatus.IDLE, 0, 0, 0,
            Instant.EPOCH, "chua dong bo");

    /** Constructor cũ vẫn hoạt động; dead-letter được tạo cạnh file queue. */
    public Uploader(String url, String gatewayId, LocalStore store, SimClock clock) {
        this(parseUri(url), gatewayId, store,
                new LocalStore(defaultDeadLetterPath(store), Integer.MAX_VALUE), clock,
                new ConnectivityMonitor(), RetryPolicy.defaults(), Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    /** Constructor dùng với FarmConfig, cho phép cấu hình đầy đủ queue/retry/timeout. */
    public Uploader(URI uri, String gatewayId, LocalStore store, LocalStore deadLetters,
                    SimClock simulationClock, ConnectivityMonitor connectivity,
                    RetryPolicy retryPolicy, Duration connectTimeout, Duration requestTimeout) {
        this(uri, gatewayId, store, deadLetters, simulationClock, connectivity, retryPolicy,
                Clock.systemUTC(), requestTimeout,
                HttpClient.newBuilder().connectTimeout(requirePositive(connectTimeout, "connectTimeout")).build());
    }

    Uploader(URI uri, String gatewayId, LocalStore store, LocalStore deadLetters,
             SimClock simulationClock, ConnectivityMonitor connectivity, RetryPolicy retryPolicy,
             Clock wallClock, Duration requestTimeout, HttpClient http) {
        this.uri = validateUri(uri);
        if (gatewayId == null || gatewayId.isBlank())
            throw new IllegalArgumentException("gatewayId khong duoc rong");
        if (store == null || deadLetters == null || simulationClock == null || connectivity == null
                || retryPolicy == null || wallClock == null || http == null)
            throw new IllegalArgumentException("Uploader dependency khong duoc null");
        if (store.path().equals(deadLetters.path()))
            throw new IllegalArgumentException("queue va dead-letter phai la hai file khac nhau");
        this.gatewayId = gatewayId;
        this.store = store;
        this.deadLetters = deadLetters;
        this.simulationClock = simulationClock;
        this.connectivity = connectivity;
        this.retryPolicy = retryPolicy;
        this.wallClock = wallClock;
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
        this.http = http;
        this.lastResult = new SyncResult(SyncStatus.IDLE, 0, 0, store.size(),
                Instant.EPOCH, "san sang");
    }

    /**
     * API tương thích bản cũ: thử ngay lập tức và trả số record đã có ở server
     * (accepted + duplicate). Record bị reject không được tính là upload thành công.
     */
    public synchronized int uploadAll() {
        return synchronize(true).synchronizedRecords();
    }

    /**
     * Gọi định kỳ từ vòng lặp gateway. Hàm tự bỏ qua khi chưa đến hạn backoff,
     * không sleep và không chặn luồng thu thập ngoài thời gian của một HTTP request.
     */
    public synchronized int syncIfDue() {
        return syncIfDueDetailed().synchronizedRecords();
    }

    public synchronized SyncResult syncIfDueDetailed() {
        return synchronize(false);
    }

    public synchronized SyncResult lastResult() { return lastResult; }

    public synchronized Instant nextAttemptAt() { return nextAttemptAt; }

    public ConnectivityMonitor.Snapshot connectivity() { return connectivity.snapshot(); }

    public Path deadLetterPath() { return deadLetters.path(); }

    private SyncResult synchronize(boolean force) {
        Instant now = wallClock.instant();
        if (store.size() == 0) {
            consecutiveFailures = 0;
            nextAttemptAt = Instant.EPOCH;
            return save(new SyncResult(SyncStatus.IDLE, 0, 0, 0,
                    nextAttemptAt, "hang doi rong"));
        }
        if (!force && now.isBefore(nextAttemptAt)) {
            return save(new SyncResult(SyncStatus.BACKING_OFF, 0, 0, store.size(),
                    nextAttemptAt, "cho den lan retry tiep theo"));
        }

        int synchronizedRecords = 0;
        int deadLetteredRecords = 0;
        while (true) {
            List<String> batch = store.peek(BATCH);
            if (batch.isEmpty()) break;
            String batchId = gatewayId + "-" + (++batchNo);
            String body = buildBody(batchId, batch);
            HttpResponse<String> response;
            try {
                response = http.send(HttpRequest.newBuilder(uri).timeout(requestTimeout)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                connectivity.recordNetworkFailure(e);
                return failed(synchronizedRecords, deadLetteredRecords, "upload bi interrupt");
            } catch (IOException e) {
                connectivity.recordNetworkFailure(e);
                return failed(synchronizedRecords, deadLetteredRecords,
                        "khong ket noi duoc API: " + e.getClass().getSimpleName());
            } catch (RuntimeException e) {
                connectivity.recordLocalFailure("khong tao/gui duoc request: " + e.getMessage());
                return failed(synchronizedRecords, deadLetteredRecords, "loi request: " + e.getMessage());
            }

            int status = response.statusCode();
            if (status / 100 != 2) {
                String detail = "HTTP " + status;
                connectivity.recordHttpFailure(status, detail);
                Log.i("UPLOAD", batchId + " -> " + detail + ", giu lai " + store.size() + " ban ghi");
                return failed(synchronizedRecords, deadLetteredRecords, detail);
            }

            try {
                BatchOutcome outcome = parseOutcome(response.body(), batchId, batch.size());
                List<String> rejectedEntries = new ArrayList<>();
                for (Map.Entry<Integer, List<String>> rejected : outcome.rejected().entrySet()) {
                    rejectedEntries.add(deadLetter(batchId, status, batch.get(rejected.getKey()), rejected.getValue()));
                }
                // Dead-letter phải ghi xong trước khi xóa queue. Nếu đầy/lỗi, pending vẫn còn để retry an toàn.
                deadLetters.appendAllStrict(rejectedEntries);
                store.removeFirst(batch.size());

                int rejectedCount = rejectedEntries.size();
                synchronizedRecords += batch.size() - rejectedCount;
                deadLetteredRecords += rejectedCount;
                consecutiveFailures = 0;
                nextAttemptAt = Instant.EPOCH;
                connectivity.recordSuccess(status);
                Log.i("UPLOAD", batchId + " -> HTTP " + status + ", synced="
                        + (batch.size() - rejectedCount) + ", dead-letter=" + rejectedCount);
            } catch (RuntimeException e) {
                // Backend có thể đã nhận batch; giữ pending để lần sau server deduplicate.
                connectivity.recordLocalFailure("response/dead-letter khong hop le: " + e.getMessage());
                Log.i("UPLOAD", batchId + " -> khong commit queue: " + e.getMessage());
                return failed(synchronizedRecords, deadLetteredRecords,
                        "khong xu ly duoc response/dead-letter: " + e.getMessage());
            }
        }

        SyncStatus status = deadLetteredRecords == 0 ? SyncStatus.SUCCESS : SyncStatus.PARTIAL;
        String detail = deadLetteredRecords == 0 ? "dong bo thanh cong"
                : "dong bo xong; record bi tu choi da vao dead-letter";
        return save(new SyncResult(status, synchronizedRecords, deadLetteredRecords,
                store.size(), nextAttemptAt, detail));
    }

    private SyncResult failed(int synchronizedRecords, int deadLetteredRecords, String detail) {
        consecutiveFailures++;
        nextAttemptAt = retryPolicy.nextAttempt(wallClock.instant(), consecutiveFailures);
        Log.i("RETRY", "lan loi " + consecutiveFailures + ", thu lai luc " + nextAttemptAt);
        return save(new SyncResult(SyncStatus.FAILED, synchronizedRecords, deadLetteredRecords,
                store.size(), nextAttemptAt, detail));
    }

    private SyncResult save(SyncResult result) {
        lastResult = result;
        return result;
    }

    private String buildBody(String batchId, List<String> batch) {
        return "{\"gatewayId\":" + Json.quote(gatewayId)
                + ",\"batchId\":" + Json.quote(batchId)
                + ",\"sentAt\":" + Json.quote(simulationClock.now().toString())
                + ",\"records\":[" + String.join(",", batch) + "]}";
    }

    private BatchOutcome parseOutcome(String text, String expectedBatchId, int batchSize) {
        if (text == null || text.isBlank()) return new BatchOutcome(Map.of());
        Map<String, Object> response;
        try {
            response = Json.obj(Json.parse(text));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("response khong phai JSON", e);
        }
        if (response == null) throw new IllegalArgumentException("response phai la JSON object");

        String responseBatchId = Json.str(response.get("batchId"));
        if (responseBatchId != null && !expectedBatchId.equals(responseBatchId))
            throw new IllegalArgumentException("batchId response khong khop");

        Map<Integer, List<String>> rejected = new LinkedHashMap<>();
        Object rejectedValue = response.get("rejected");
        if (rejectedValue != null) {
            List<Object> list = Json.arr(rejectedValue);
            if (list == null) throw new IllegalArgumentException("rejected phai la array");
            for (Object item : list) {
                Map<String, Object> rejection = Json.obj(item);
                if (rejection == null) throw new IllegalArgumentException("rejected item phai la object");
                int index = requiredIndex(rejection.get("index"), batchSize);
                if (rejected.containsKey(index))
                    throw new IllegalArgumentException("rejected index bi trung: " + index);
                rejected.put(index, errorMessages(rejection.get("errors")));
            }
        }

        Integer accepted = optionalCount(response.get("accepted"), "accepted");
        Integer duplicates = optionalCount(response.get("duplicates"), "duplicates");
        if (accepted != null || duplicates != null) {
            int accounted = (accepted == null ? 0 : accepted)
                    + (duplicates == null ? 0 : duplicates) + rejected.size();
            if (accounted != batchSize)
                throw new IllegalArgumentException("response count khong khop batch: " + accounted + "/" + batchSize);
        }
        return new BatchOutcome(rejected);
    }

    private String deadLetter(String batchId, int httpStatus, String record, List<String> errors) {
        StringBuilder b = new StringBuilder("{\"failedAt\":")
                .append(Json.quote(wallClock.instant().toString()))
                .append(",\"gatewayId\":").append(Json.quote(gatewayId))
                .append(",\"batchId\":").append(Json.quote(batchId))
                .append(",\"httpStatus\":").append(httpStatus)
                .append(",\"errors\":[");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) b.append(',');
            b.append(Json.quote(errors.get(i)));
        }
        return b.append("],\"record\":").append(record).append('}').toString();
    }

    private static int requiredIndex(Object value, int batchSize) {
        Double number = Json.num(value);
        if (number == null || number != Math.floor(number) || number < 0 || number >= batchSize)
            throw new IllegalArgumentException("rejected.index ngoai batch");
        return number.intValue();
    }

    private static Integer optionalCount(Object value, String field) {
        if (value == null) return null;
        Double number = Json.num(value);
        if (number == null || number != Math.floor(number) || number < 0)
            throw new IllegalArgumentException(field + " phai la so nguyen >= 0");
        return number.intValue();
    }

    private static List<String> errorMessages(Object value) {
        List<Object> raw = Json.arr(value);
        if (raw == null || raw.isEmpty()) return List.of("backend tu choi khong kem ly do");
        List<String> messages = new ArrayList<>();
        for (Object item : raw) {
            String text = Json.str(item);
            messages.add(text == null ? String.valueOf(item) : text);
        }
        return List.copyOf(messages);
    }

    private static URI parseUri(String value) {
        if (value == null) throw new IllegalArgumentException("url khong duoc null");
        return validateUri(URI.create(value));
    }

    private static URI validateUri(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null
                || !("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme())))
            throw new IllegalArgumentException("ingest URI phai la URL http/https day du");
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative())
            throw new IllegalArgumentException(name + " phai > 0");
        return value;
    }

    private static Path defaultDeadLetterPath(LocalStore store) {
        if (store == null) throw new IllegalArgumentException("store khong duoc null");
        Path queue = store.path();
        String name = queue.getFileName().toString();
        String deadName = name.endsWith(".jsonl")
                ? name.substring(0, name.length() - ".jsonl".length()) + ".dead-letter.jsonl"
                : name + ".dead-letter.jsonl";
        return queue.resolveSibling(deadName);
    }
}
