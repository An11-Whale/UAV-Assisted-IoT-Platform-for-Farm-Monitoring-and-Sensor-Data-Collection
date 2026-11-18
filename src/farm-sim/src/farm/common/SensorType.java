package farm.common;

/** Danh sách loại cảm biến + đơn vị + khoảng giá trị hợp lệ (dùng chung cho cảm biến và validator F47). */
public enum SensorType {
    SOIL_MOISTURE("soil_moisture", "%", 0, 100),
    TEMPERATURE("temperature", "C", -40, 80),
    HUMIDITY("humidity", "%", 0, 100),
    LIGHT("light", "lux", 0, 120000),
    PH("ph", "pH", 0, 14),
    WATER_LEVEL("water_level", "%", 0, 100);

    public final String key;
    public final String unit;
    public final double min;
    public final double max;

    SensorType(String key, String unit, double min, double max) {
        this.key = key;
        this.unit = unit;
        this.min = min;
        this.max = max;
    }

    public static SensorType fromKey(String key) {
        for (SensorType t : values()) if (t.key.equals(key)) return t;
        return null;
    }
}
