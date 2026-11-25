package farm.tests;

import farm.common.SimClock;
import farm.gateway.ConnectivityMonitor;
import farm.gateway.LocalStore;
import farm.gateway.RetryPolicy;
import farm.gateway.Uploader;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/**
 * Acceptance test for F52: the scheduled retry must happen through syncIfDue(),
 * without sleeping and without calling uploadAll().
 */
public final class AutomaticRetryTest {
    private AutomaticRetryTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("farm-auto-retry-").toAbsolutePath().normalize();
        try {
            Instant t0 = Instant.parse("2026-11-23T01:00:00Z");
            MutableClock clock = new MutableClock(t0);
            LocalStore queue = new LocalStore(root.resolve("pending.jsonl"), 10);
            LocalStore deadLetters = new LocalStore(root.resolve("dead-letter.jsonl"), 10);
            queue.append("{\"nodeId\":\"NODE-001\",\"seq\":1}");

            RetryPolicy retry = new RetryPolicy(
                    Duration.ofSeconds(2), Duration.ofSeconds(30), 2.0, 0.0);
            ConnectivityMonitor connectivity = new ConnectivityMonitor(clock);
            RetryHttpClient http = new RetryHttpClient();
            Uploader uploader = new Uploader(URI.create("http://backend.test/api/ingest"),
                    "GW-TEST", queue, deadLetters, new SimClock(t0), connectivity, retry,
                    clock, Duration.ofSeconds(2), http);

            Uploader.SyncResult first = uploader.syncIfDueDetailed();
            equal(Uploader.SyncStatus.FAILED, first.status(), "first HTTP 503 must fail");
            equal(1, queue.size(), "failed record must stay queued");
            equal(1, http.requests(), "first attempt must call backend once");
            equal(t0.plusSeconds(2), first.nextAttemptAt(), "first retry must use initial delay");

            Uploader.SyncResult early = uploader.syncIfDueDetailed();
            equal(Uploader.SyncStatus.BACKING_OFF, early.status(), "retry before due time must wait");
            equal(1, http.requests(), "backoff must not call backend");

            clock.advance(Duration.ofMillis(1999));
            Uploader.SyncResult stillEarly = uploader.syncIfDueDetailed();
            equal(Uploader.SyncStatus.BACKING_OFF, stillEarly.status(),
                    "retry one millisecond early must still wait");
            equal(1, http.requests(), "early retry must not create extra request");

            clock.advance(Duration.ofMillis(1));
            Uploader.SyncResult due = uploader.syncIfDueDetailed();
            equal(Uploader.SyncStatus.SUCCESS, due.status(), "due retry must synchronize");
            equal(1, due.synchronizedRecords(), "due retry must acknowledge queued record");
            equal(0, queue.size(), "successful retry must clear queue");
            equal(2, http.requests(), "exactly one automatic retry must reach backend");
            equal(ConnectivityMonitor.State.ONLINE, connectivity.snapshot().state(),
                    "connectivity must recover to ONLINE");

            System.out.println("[PASS] F52 automatic retry fires at the scheduled backoff deadline");
            System.out.println("RESULT: 1 PASS, 0 FAIL");
        } finally {
            deleteTree(root);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }

        private void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    /** First request fails temporarily; the next one succeeds. No socket is opened. */
    private static final class RetryHttpClient extends HttpClient {
        private final AtomicInteger requests = new AtomicInteger();
        private final SSLContext sslContext;

        private RetryHttpClient() throws NoSuchAlgorithmException {
            sslContext = SSLContext.getDefault();
        }

        private int requests() { return requests.get(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler) {
            int attempt = requests.incrementAndGet();
            int status = attempt == 1 ? 503 : 202;
            String body = attempt == 1 ? "temporary unavailable" : "";
            return (HttpResponse<T>) new StringResponse(request, status, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return sslContext; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }

    private record StringResponse(HttpRequest request, int statusCode, String body)
            implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
