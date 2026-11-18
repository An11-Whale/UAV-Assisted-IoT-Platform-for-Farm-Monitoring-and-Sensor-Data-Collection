package farm.sensor;

import farm.common.SensorType;

import java.util.Locale;

/** Immutable sensor sample containing the converted value, optional raw ADC value and read status. */
public record Reading(SensorType type, double value, int raw, String status) {

    public boolean ok() { return "OK".equals(status); }

    public String toJson() {
        StringBuilder b = new StringBuilder("{\"sensor\":\"").append(type.key).append("\",");
        if (ok()) b.append("\"value\":").append(String.format(Locale.ROOT, "%.2f", value));
        else b.append("\"value\":null");
        b.append(",\"unit\":\"").append(type.unit).append("\"");
        if (raw >= 0) b.append(",\"raw\":").append(raw);
        return b.append(",\"status\":\"").append(status).append("\"}").toString();
    }
}
