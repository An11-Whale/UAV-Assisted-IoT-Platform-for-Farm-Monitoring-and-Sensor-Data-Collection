package farm.node;

import java.util.regex.Pattern;

/** F41 - Định danh node: nodeId duy nhất dạng NODE-001, kèm model phần cứng, firmware và farm. */
public record NodeIdentity(String nodeId, String model, String firmware, String farmId) {
    private static final Pattern ID = Pattern.compile("^NODE-\\d{3}$");

    public NodeIdentity {
        if (!isValidId(nodeId)) throw new IllegalArgumentException("nodeId sai dinh dang (NODE-001): " + nodeId);
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
