package farm.common;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Log đơn giản, in theo giờ mô phỏng (ICT). Chỉ dùng ASCII để không lỗi font trên console Windows. */
public final class Log {
    private static volatile SimClock clock;
    private static final DateTimeFormatter F =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private Log() {}

    public static void init(SimClock c) { clock = c; }

    public static synchronized void i(String tag, String msg) {
        String t = clock == null ? "--:--:--" : F.format(clock.now());
        System.out.printf("[%s] %-8s %s%n", t, tag, msg);
    }
}
