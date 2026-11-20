package farm.node;

import java.util.regex.Pattern;

/**
 * Immutable provisioned identity for a sensor node (F41), including its validated
 * {@code NODE-001} identifier, hardware model, firmware version and farm assignment.
 */
public record NodeIdentity(String nodeId, String model, String firmware, String farmId) {
    private static final Pattern ID = Pattern.compile("^NODE-\\d{3}$");

    public NodeIdentity {
        if (!isValidId(nodeId)) throw new IllegalArgumentException("nodeId sai dinh dang (NODE-001): " + nodeId);
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
