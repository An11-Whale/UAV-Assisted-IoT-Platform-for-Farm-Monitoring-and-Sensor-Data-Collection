package farm.sensor;

import java.time.Instant;
import java.util.List;

/** Common sensor contract shared by simulated devices and future hardware drivers. */
public interface Sensor {
    String name();

    List<Reading> read(Instant now);
}
