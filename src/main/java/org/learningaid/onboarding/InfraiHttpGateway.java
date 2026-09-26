package org.learningaid.onboarding;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiHttpGateway implements InfraiGateway {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern GRANTED = Pattern.compile("\\\"granted\\\"\\s*:\\s*(true|false)");
    private static final Pattern SUPPRESSED = Pattern.compile("\\\"suppressed\\\"\\s*:\\s*(true|false)");

    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiHttpGateway(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiHttpGateway(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public boolean hasConsent(String userId, String category) {
        String body = call("GET", "/auth/consent/check/" + segment(userId) + "/" + segment(category), null, true);
        return booleanField(body, GRANTED, "granted");
    }

    @Override
    public boolean isEmailSuppressed(String email) {
        String body = call("GET", "/email/suppression/check/" + segment(email), null, true);
        return booleanField(body, SUPPRESSED, "suppressed");
    }

    @Override
    public boolean isSmsSuppressed(String phone) {
        String body = call("POST", "/sms/suppression/check", json("phone", phone), true);
        return booleanField(body, SUPPRESSED, "suppressed");
    }

    @Override
    public String sendEmail(String email, String subject, String text) {
        String payload = "{\"to\":\"" + escape(email) + "\",\"subject\":\"" + escape(subject)
                + "\",\"body\":\"" + escape(text) + "\"}";
        return stringField(call("POST", "/email/send", payload, false), MESSAGE_ID, "message_id");
    }

    @Override
    public String sendSms(String phone, String text) {
        String payload = "{\"to\":\"" + escape(phone) + "\",\"body\":\"" + escape(text) + "\"}";
        return stringField(call("POST", "/sms/send", payload, false), MESSAGE_ID, "message_id");
    }

    private String call(String method, String path, String payload, boolean retryable) {
        int attempt = 0;
        while (true) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (payload == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(payload));
            }
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && retryable && attempt < 3) {
                    pause(response.headers().firstValue("Retry-After").orElse(null), attempt++);
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiException(envelope.code(), envelope.message(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new InfraiException("HTTP_" + response.statusCode(), "Infrai request was not completed", response.statusCode());
                }
                return response.body();
            } catch (IOException exception) {
                throw new InfraiException("TRANSPORT", exception.getMessage(), 502);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new InfraiException("INTERRUPTED", "Request interrupted", 503);
            }
        }
    }

    private static Envelope decodeEnvelope(String body, int status) {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) {
            throw new InfraiException("INVALID_ENVELOPE", "Response did not contain an ok field", status);
        }
        boolean accepted = Boolean.parseBoolean(ok.group(1));
        return new Envelope(accepted, find(CODE, body, "REQUEST_REJECTED"), find(MESSAGE, body, "Request rejected"));
    }

    private static boolean booleanField(String body, Pattern pattern, String name) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) throw new InfraiException("INVALID_ENVELOPE", "Missing " + name, 502);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String stringField(String body, Pattern pattern, String name) {
        String value = find(pattern, body, null);
        if (value == null) throw new InfraiException("INVALID_ENVELOPE", "Missing " + name, 502);
        return value;
    }

    private static String find(Pattern pattern, String body, String fallback) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static void pause(String retryAfter, int attempt) throws InterruptedException {
        long millis = retryAfter == null ? 250L * (1L << attempt) : Long.parseLong(retryAfter) * 1000L;
        Thread.sleep(Math.min(millis, 4_000L));
    }

    private static String json(String key, String value) {
        return "{\"" + key + "\":\"" + escape(value) + "\"}";
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Envelope(boolean ok, String code, String message) {}

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;

        public InfraiException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }
}
