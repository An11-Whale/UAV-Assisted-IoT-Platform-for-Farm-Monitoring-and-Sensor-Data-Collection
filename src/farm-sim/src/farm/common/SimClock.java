package farm.common;

import java.time.Instant;

/** Đồng hồ mô phỏng: cho phép "tua nhanh" thời gian để thấy biến đổi theo ngày/đêm. */
public class SimClock {
    private Instant now;

    public SimClock(Instant start) { this.now = start; }

    public synchronized Instant now() { return now; }

    public synchronized void advance(long seconds) { now = now.plusSeconds(seconds); }
}
