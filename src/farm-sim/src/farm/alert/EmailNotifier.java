package farm.alert;

import farm.common.Json;
import farm.common.Log;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable SMTP notification outbox with retry scheduling.
 * Alert persistence completes before delivery, so an SMTP failure keeps mail pending without failing ingestion.
 */
public final class EmailNotifier {
    private static final class Mail {
        final String id;
        final String to;
        final String subject;
        final String body;
        int attempts;
        long nextAttemptEpoch;

        Mail(String id, String to, String subject, String body, int attempts, long nextAttemptEpoch) {
            this.id = id;
            this.to = to;
            this.subject = subject;
            this.body = body;
            this.attempts = attempts;
            this.nextAttemptEpoch = nextAttemptEpoch;
        }

        String toJson() {
            return "{\"id\":" + Json.quote(id) + ",\"to\":" + Json.quote(to)
                    + ",\"subject\":" + Json.quote(subject) + ",\"body\":" + Json.quote(body)
                    + ",\"attempts\":" + attempts + ",\"nextAttemptEpoch\":" + nextAttemptEpoch + "}";
        }
    }

    private final Path outboxFile;
    private final String host;
    private final int port;
    private final boolean ssl;
    private final String username;
    private final String password;
    private final String from;
    private final String recipients;
    private final List<Mail> pending = new ArrayList<>();

    public EmailNotifier(Path outboxFile, Map<String, String> env) {
        this.outboxFile = outboxFile;
        this.host = value(env, "SMTP_HOST", "");
        this.port = integer(value(env, "SMTP_PORT", "465"), 465);
        this.ssl = Boolean.parseBoolean(value(env, "SMTP_SSL", "true"));
        this.username = value(env, "SMTP_USERNAME", "");
        this.password = value(env, "SMTP_PASSWORD", "");
        this.from = value(env, "ALERT_EMAIL_FROM", username.isBlank() ? "alerts@localhost" : username);
        this.recipients = value(env, "ALERT_EMAIL_TO", "");
        load();
    }

    public static EmailNotifier fromEnvironment(Path outboxFile) {
        return new EmailNotifier(outboxFile, System.getenv());
    }

    public synchronized void enqueue(Alert alert) {
        if (recipients.isBlank()) {
            Log.i("EMAIL", "ALERT_EMAIL_TO chua cau hinh; alert " + alert.id() + " van duoc luu tren server");
            return;
        }
        String subject = "[" + alert.severity() + "] " + alert.type() + " - " + alert.nodeId();
        String body = "Ma canh bao: " + alert.id() + "\n"
                + "Node: " + alert.nodeId() + "\n"
                + "Loai: " + alert.type() + "\n"
                + "Chi so: " + (alert.metric() == null ? "-" : alert.metric()) + "\n"
                + "Noi dung: " + alert.message() + "\n"
                + "Thoi gian UTC: " + alert.createdAt() + "\n";
        pending.add(new Mail("MAIL-" + UUID.randomUUID().toString().substring(0, 8), recipients,
                subject, body, 0, 0));
        rewrite();
        flush();
    }

    /** Gửi những mail đến hạn; trả số mail gửi thành công. */
    public synchronized int flush() {
        if (host.isBlank() || pending.isEmpty()) return 0;
        long now = Instant.now().getEpochSecond();
        int sent = 0;
        Iterator<Mail> it = pending.iterator();
        while (it.hasNext()) {
            Mail mail = it.next();
            if (mail.nextAttemptEpoch > now) continue;
            try {
                sendSmtp(mail);
                it.remove();
                sent++;
                Log.i("EMAIL", mail.id + " gui thanh cong toi " + mail.to);
            } catch (IOException e) {
                mail.attempts++;
                long delay = Math.min(3600, 30L << Math.min(mail.attempts - 1, 7));
                mail.nextAttemptEpoch = now + delay;
                Log.i("EMAIL", mail.id + " gui that bai (" + e.getClass().getSimpleName()
                        + "), retry sau " + delay + "s");
            }
        }
        rewrite();
        return sent;
    }

    public synchronized int pendingCount() { return pending.size(); }

    private void sendSmtp(Mail mail) throws IOException {
        SocketFactory factory = ssl ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        try (Socket socket = factory.createSocket(host, port)) {
            socket.setSoTimeout(10_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
            expect(readResponse(in), 220);
            expect(command(out, in, "EHLO localhost"), 250);
            if (!username.isBlank()) {
                expect(command(out, in, "AUTH LOGIN"), 334);
                expect(command(out, in, b64(username)), 334);
                expect(command(out, in, b64(password)), 235);
            }
            expect(command(out, in, "MAIL FROM:<" + address(from) + ">"), 250);
            for (String recipient : mail.to.split(","))
                expect(command(out, in, "RCPT TO:<" + address(recipient.trim()) + ">"), 250, 251);
            expect(command(out, in, "DATA"), 354);
            String mime = "From: " + address(from) + "\r\n"
                    + "To: " + mail.to.replace("\r", "").replace("\n", "") + "\r\n"
                    + "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(mail.subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n"
                    + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atZone(ZoneOffset.UTC)) + "\r\n"
                    + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: base64\r\n\r\n"
                    + Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(mail.body.getBytes(StandardCharsets.UTF_8)) + "\r\n.\r\n";
            out.write(mime);
            out.flush();
            expect(readResponse(in), 250);
            command(out, in, "QUIT");
        }
    }

    private void load() {
        if (outboxFile == null || !Files.exists(outboxFile)) return;
        try {
            for (String line : Files.readAllLines(outboxFile)) {
                if (line.isBlank()) continue;
                try {
                    Map<String, Object> m = Json.obj(Json.parse(line));
                    pending.add(new Mail(Json.str(m.get("id")), Json.str(m.get("to")), Json.str(m.get("subject")),
                            Json.str(m.get("body")), (int) Json.num(m.get("attempts")).doubleValue(),
                            Json.num(m.get("nextAttemptEpoch")).longValue()));
                } catch (RuntimeException ignored) { }
            }
        } catch (IOException e) { throw new UncheckedIOException("Khong doc duoc email outbox", e); }
    }

    private void rewrite() {
        if (outboxFile == null) return;
        try {
            if (outboxFile.getParent() != null) Files.createDirectories(outboxFile.getParent());
            Path tmp = outboxFile.resolveSibling(outboxFile.getFileName() + ".tmp");
            List<String> lines = pending.stream().map(Mail::toJson).toList();
            Files.write(tmp, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(tmp, outboxFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, outboxFile, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new UncheckedIOException("Khong luu duoc email outbox", e); }
    }

    private static int command(BufferedWriter out, BufferedReader in, String text) throws IOException {
        out.write(text + "\r\n");
        out.flush();
        return readResponse(in);
    }

    private static int readResponse(BufferedReader in) throws IOException {
        String line = in.readLine();
        if (line == null || line.length() < 3) throw new IOException("SMTP dong ket noi");
        int code;
        try { code = Integer.parseInt(line.substring(0, 3)); }
        catch (NumberFormatException e) { throw new IOException("SMTP response khong hop le", e); }
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) throw new IOException("SMTP multiline bi ngat");
        }
        return code;
    }

    private static void expect(int actual, int... accepted) throws IOException {
        for (int code : accepted) if (actual == code) return;
        throw new IOException("SMTP tra ma " + actual);
    }

    private static String address(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+$"))
            throw new IllegalArgumentException("Dia chi email khong hop le");
        return value;
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String value(Map<String, String> env, String key, String fallback) {
        String v = env.get(key);
        return v == null ? fallback : v.trim();
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }
}
