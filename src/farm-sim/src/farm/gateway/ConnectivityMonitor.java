package farm.gateway;

import java.time.Clock;
import java.time.Instant;

/**
 * F51 - Theo dõi trạng thái kết nối nhìn từ tiến trình đồng bộ của gateway.
 *
 * <p>ONLINE nghĩa là API đã chấp nhận lần đồng bộ gần nhất. DEGRADED nghĩa là
 * gateway vẫn nhận được HTTP response nhưng API đang từ chối/lỗi. OFFLINE nghĩa
 * là không thiết lập được kết nối. Lớp này không tự tạo traffic kiểm tra mạng;
 * {@link Uploader} cập nhật trạng thái từ kết quả thật của mỗi lần upload.</p>
 */
public final class ConnectivityMonitor {
    public enum State { UNKNOWN, ONLINE, DEGRADED, OFFLINE }

    public record Snapshot(
            State state,
            Instant checkedAt,
            Instant changedAt,
            int consecutiveFailures,
            Integer lastHttpStatus,
            String detail) {

        public boolean reachable() {
            return state == State.ONLINE || state == State.DEGRADED;
        }
    }

    private final Clock clock;
    private State state = State.UNKNOWN;
    private Instant checkedAt;
    private Instant changedAt;
    private int consecutiveFailures;
    private Integer lastHttpStatus;
    private String detail = "chua kiem tra";

    public ConnectivityMonitor() {
        this(Clock.systemUTC());
    }

    public ConnectivityMonitor(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock khong duoc null");
        this.clock = clock;
        this.changedAt = clock.instant();
    }

    public synchronized void recordSuccess(int httpStatus) {
        update(State.ONLINE, 0, httpStatus, "API san sang");
    }

    /** Có HTTP response, vì vậy đường mạng hoạt động nhưng dịch vụ chưa sẵn sàng. */
    public synchronized void recordHttpFailure(int httpStatus, String reason) {
        update(State.DEGRADED, consecutiveFailures + 1, httpStatus, safe(reason));
    }

    /** Không nhận được HTTP response (DNS, timeout, refused, mất mạng...). */
    public synchronized void recordNetworkFailure(Throwable error) {
        String message = error == null ? "khong ket noi duoc" : error.getClass().getSimpleName();
        if (error != null && error.getMessage() != null && !error.getMessage().isBlank())
            message += ": " + error.getMessage();
        update(State.OFFLINE, consecutiveFailures + 1, null, message);
    }

    /** Lỗi local/response format không khẳng định mất Internet, nhưng sync chưa hoạt động. */
    public synchronized void recordLocalFailure(String reason) {
        State next = state == State.OFFLINE ? State.OFFLINE : State.DEGRADED;
        update(next, consecutiveFailures + 1, lastHttpStatus, safe(reason));
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(state, checkedAt, changedAt, consecutiveFailures, lastHttpStatus, detail);
    }

    private void update(State next, int failures, Integer status, String message) {
        Instant now = clock.instant();
        if (state != next) changedAt = now;
        state = next;
        checkedAt = now;
        consecutiveFailures = failures;
        lastHttpStatus = status;
        detail = message;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "khong ro nguyen nhan" : value;
    }
}
