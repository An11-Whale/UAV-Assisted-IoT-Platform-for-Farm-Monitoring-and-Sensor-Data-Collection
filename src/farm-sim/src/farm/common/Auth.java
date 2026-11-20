package farm.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Packet authentication helpers using a per-node pre-shared key and HMAC-SHA256.
 * Verification uses a constant-time comparison and signed JSON can be attached or split without dependencies.
 */
public final class Auth {
    private static final String ALG = "HmacSHA256";
    private static final String SIG_MARK = ",\"sig\":\"";

    private Auth() {}

    public static String sign(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(key, ALG));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** So sánh hằng thời gian để tránh timing attack. */
    public static boolean verify(byte[] key, String data, String sigB64) {
        byte[] a = sign(key, data).getBytes(StandardCharsets.UTF_8);
        byte[] b = sigB64.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Gắn chữ ký vào cuối JSON: {...body...} -> {...body...,"sig":"..."} */
    public static String attachSignature(String body, String sig) {
        return body.substring(0, body.length() - 1) + SIG_MARK + sig + "\"}";
    }

    /** Tách gói đã ký thành {body, sig}; trả null nếu sai định dạng. */
    public static String[] split(String full) {
        int idx = full.lastIndexOf(SIG_MARK);
        if (idx < 0 || !full.endsWith("\"}")) return null;
        String body = full.substring(0, idx) + "}";
        String sig = full.substring(idx + SIG_MARK.length(), full.length() - 2);
        return new String[]{body, sig};
    }
}
