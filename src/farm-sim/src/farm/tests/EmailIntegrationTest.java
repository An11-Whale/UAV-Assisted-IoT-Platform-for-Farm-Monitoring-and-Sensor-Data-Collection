package farm.tests;

import farm.alert.Alert;
import farm.alert.AlertRepository;
import farm.alert.EmailNotifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * F93 integration test: sends one alert through a real SMTP socket and verifies what the
 * receiving server got. The server is local and disposable, so the test needs no credentials
 * and never sends mail to the Internet.
 *
 * Run after the normal build:
 *   java -cp out farm.tests.EmailIntegrationTest
 */
public final class EmailIntegrationTest {
    private EmailIntegrationTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("farm-email-test-").toAbsolutePath().normalize();
        try (FakeSmtpServer smtp = new FakeSmtpServer()) {
            smtp.start();
            Path outbox = root.resolve("email-outbox.jsonl");
            Map<String, String> settings = Map.of(
                    "SMTP_HOST", "127.0.0.1",
                    "SMTP_PORT", Integer.toString(smtp.port()),
                    "SMTP_SSL", "false",
                    "ALERT_EMAIL_FROM", "alerts@example.test",
                    "ALERT_EMAIL_TO", "farmer@example.test");

            AlertRepository alerts = new AlertRepository(null);
            Alert alert = alerts.createIfAbsent(
                    "DATA_TIMEOUT", "WARNING", "NODE-002", null,
                    120.0, null, 60.0, "node stale",
                    Instant.parse("2026-11-23T08:00:00Z")).alert();

            EmailNotifier notifier = new EmailNotifier(outbox, settings);
            notifier.enqueue(alert);
            ReceivedMail mail = smtp.awaitMail(Duration.ofSeconds(5));

            check(notifier.pendingCount() == 0,
                    "successful SMTP delivery must remove the message from the outbox");
            check(!Files.exists(outbox) || Files.readString(outbox).isBlank(),
                    "durable outbox must be empty after SMTP acknowledgement");
            check(mail.from().equals("alerts@example.test"),
                    "SMTP envelope must use ALERT_EMAIL_FROM");
            check(mail.recipient().equals("farmer@example.test"),
                    "SMTP envelope must use ALERT_EMAIL_TO");
            check(mail.message().contains("To: farmer@example.test"),
                    "MIME message must contain the configured recipient");
            check(mail.message().contains("Subject: =?UTF-8?B?"),
                    "subject must be UTF-8 encoded");

            int separator = mail.message().indexOf("\r\n\r\n");
            check(separator >= 0, "MIME headers and body must be separated");
            String encodedBody = mail.message().substring(separator + 4);
            String decodedBody = new String(
                    Base64.getMimeDecoder().decode(encodedBody), StandardCharsets.UTF_8);
            check(decodedBody.contains(alert.id()), "email body must contain the alert id");
            check(decodedBody.contains("NODE-002"), "email body must contain the node id");
            check(decodedBody.contains("DATA_TIMEOUT"), "email body must contain the alert type");

            System.out.println("[PASS] F93 EmailNotifier SMTP delivery");
            System.out.println("       accepted=1, pending=0, recipient=" + mail.recipient());
        } finally {
            deleteTree(root);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record ReceivedMail(String from, String recipient, String message) { }

    /** Minimal one-message SMTP receiver used only by this integration test. */
    private static final class FakeSmtpServer implements AutoCloseable {
        private final ServerSocket server;
        private final CountDownLatch received = new CountDownLatch(1);
        private Thread worker;
        private volatile ReceivedMail mail;
        private volatile Throwable failure;
        private final StringBuilder transcript = new StringBuilder();

        FakeSmtpServer() throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        }

        int port() {
            return server.getLocalPort();
        }

        void start() {
            worker = new Thread(this::serve, "fake-smtp-server");
            worker.setDaemon(true);
            worker.start();
        }

        ReceivedMail awaitMail(Duration timeout) throws Exception {
            if (!received.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("SMTP server did not receive a message within " + timeout);
            }
            if (failure != null) throw new AssertionError("fake SMTP server failed", failure);
            if (mail == null) throw new AssertionError("SMTP transaction ended without DATA");
            return mail;
        }

        private void serve() {
            try (Socket socket = server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.US_ASCII));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                socket.setSoTimeout(5_000);
                reply(out, "220 localhost F93 test SMTP ready");
                String from = "";
                String recipient = "";
                String line;
                while ((line = in.readLine()) != null) {
                    transcript.append(line).append(" | ");
                    String upper = line.toUpperCase(java.util.Locale.ROOT);
                    if (upper.startsWith("EHLO ") || upper.startsWith("HELO ")) {
                        reply(out, "250 localhost");
                    } else if (upper.startsWith("MAIL FROM:<") && line.endsWith(">")) {
                        from = line.substring(line.indexOf('<') + 1, line.length() - 1);
                        reply(out, "250 sender accepted");
                    } else if (upper.startsWith("RCPT TO:<") && line.endsWith(">")) {
                        recipient = line.substring(line.indexOf('<') + 1, line.length() - 1);
                        reply(out, "250 recipient accepted");
                    } else if (upper.equals("DATA")) {
                        reply(out, "354 end with <CRLF>.<CRLF>");
                        StringBuilder data = new StringBuilder();
                        while ((line = in.readLine()) != null && !line.equals(".")) {
                            if (line.startsWith("..")) line = line.substring(1);
                            data.append(line).append("\r\n");
                        }
                        if (data.length() >= 2) data.setLength(data.length() - 2);
                        mail = new ReceivedMail(from, recipient, data.toString());
                        reply(out, "250 queued for local test");
                        received.countDown();
                    } else if (upper.equals("QUIT")) {
                        reply(out, "221 bye");
                        return;
                    } else {
                        reply(out, "500 unsupported test command");
                    }
                }
                if (mail == null) {
                    failure = new IOException("SMTP client closed before DATA; commands=" + transcript);
                    received.countDown();
                }
            } catch (Throwable error) {
                failure = error;
                received.countDown();
            }
        }

        private static void reply(BufferedWriter out, String value) throws IOException {
            out.write(value);
            out.write("\r\n");
            out.flush();
        }

        @Override
        public void close() throws IOException {
            server.close();
            if (worker != null) {
                try {
                    worker.join(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while stopping fake SMTP server", e);
                }
            }
        }
    }
}
