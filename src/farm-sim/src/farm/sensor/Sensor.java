package farm.sensor;

import java.time.Instant;
import java.util.List;

/** Giao diện chung cho mọi cảm biến. Sau này thay bằng driver phần cứng thật chỉ cần implement lại interface này. */
public interface Sensor {
    String name();

    List<Reading> read(Instant now);
}
