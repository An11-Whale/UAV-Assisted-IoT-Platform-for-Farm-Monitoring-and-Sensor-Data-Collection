package farm.common;

import java.time.Instant;

/** Adjustable, thread-safe clock used to accelerate missions and day/night sensor behavior in the simulator. */
public class SimClock {
    private Instant now;

    public SimClock(Instant start) { this.now = start; }

    public synchronized Instant now() { return now; }

    public synchronized void advance(long seconds) { now = now.plusSeconds(seconds); }
}
