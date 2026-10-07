package com.pki.ca.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class IdentityVerificationClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String gatewayBaseUrl;
    private final HttpClient httpClient;

    private volatile String sessionId;
    private volatile WebSocket streamSocket;

    public IdentityVerificationClient(String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1)
                : gatewayBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .sslContext(trustAllSslContext())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }


    public String createSession() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayBaseUrl + "/api/v1/sessions"))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException("createSession failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode node = MAPPER.readTree(response.body());
        this.sessionId = node.get("session_id").asText();
        return this.sessionId;
    }

    //מעלה את התעודת זהות
    public void uploadIdCard(byte[] frontImage, byte[] backImageOrNull) throws IOException, InterruptedException {
        if (sessionId == null) {
            throw new IllegalStateException("createSession() must be called first");
        }

        String boundary = "CaGateway" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, frontImage, backImageOrNull);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayBaseUrl + "/api/v1/sessions/" + sessionId + "/id-card"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("uploadIdCard failed: HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] front, byte[] back) throws IOException {
        String lineBreak = "\r\n";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        writePart(out, boundary, "front", "front.jpg", "image/jpeg", front, lineBreak);
        if (back != null) {
            writePart(out, boundary, "back", "back.jpg", "image/jpeg", back, lineBreak);
        }
        out.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writePart(java.io.ByteArrayOutputStream out, String boundary, String fieldName,
                           String fileName, String contentType, byte[] data, String lineBreak) throws IOException {
        out.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + lineBreak)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write(lineBreak.getBytes(StandardCharsets.UTF_8));
    }



    public CompletableFuture<Void> openStream(Consumer<String> onMessage, Runnable onClose, Consumer<Throwable> onError) {
        if (sessionId == null) {
            throw new IllegalStateException("createSession() must be called first");
        }

        String wsUrl = gatewayBaseUrl.replaceFirst("^http", "ws") + "/ws/sessions/" + sessionId + "/stream";
        CompletableFuture<Void> opened = new CompletableFuture<>();

        HttpClient wsHttpClient = HttpClient.newBuilder()
                .sslContext(trustAllSslContext())
                .build();

        wsHttpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    private final StringBuilder textBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        streamSocket = webSocket;
                        opened.complete(null);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        textBuffer.append(data);
                        if (last) {
                            onMessage.accept(textBuffer.toString());
                            textBuffer.setLength(0);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (!opened.isDone()) {
                            opened.completeExceptionally(error);
                        }
                        onError.accept(error);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        onClose.run();
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(ex -> {
                    opened.completeExceptionally(ex);
                    return null;
                });

        return opened;
    }

    //שולח כבינארי
    public void sendFrame(byte[] jpgOrPngBytes) {
        WebSocket ws = this.streamSocket;
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendBinary(ByteBuffer.wrap(jpgOrPngBytes), true);
        }
    }

    public void close() {
        WebSocket ws = this.streamSocket;
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    private static SSLContext trustAllSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] c, String t) { }
                        public void checkServerTrusted(X509Certificate[] c, String t) { }
                    }
            }, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}