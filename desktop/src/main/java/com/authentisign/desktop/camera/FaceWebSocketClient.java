package com.authentisign.desktop.camera;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FaceWebSocketClient implements WebSocket.Listener {

    public interface Listener {
        void onReady();
        void onProgress(Map<String, String> fields);
        void onResult(Map<String, String> fields);
        void onError(String message);
    }

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("\"([a-zA-Z_]+)\"\\s*:\\s*(\"[^\"]*\"|-?[0-9]+\\.?[0-9]*|true|false|null)");

    private WebSocket ws;
    private volatile boolean isConnected = false;
    private final StringBuilder incomingBuffer = new StringBuilder();
    private Listener listener;

    private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void connect() {
        String wsUrl = "wss://localhost:8080/api/ca/ws/verify";
        System.out.println("Attempting connection to: " + wsUrl);
        try {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) {}
                    }
            }, new java.security.SecureRandom());

            ws = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), this)
                    .exceptionally(ex -> {
                        System.err.println("Connection failed: " +
                                (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                        return null;
                    })
                    .join();

            if (ws != null) {
                ws.request(1);
                System.out.println("WebSocket connected!");
            } else {
                System.err.println("WebSocket is null");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendIdFront(byte[] imageBytes) {
        sendEnvelope("id_front", imageBytes);
    }

    public void sendIdBack(byte[] imageBytes) {
        sendEnvelope("id_back", imageBytes);
    }

   //התחלת אימות
    public void sendStartVerification() {
        sendRaw("{\"type\":\"start_verification\"}");
    }

   //שליחת פריימים
    public void sendFrame(String base64) {
        sendRaw("{\"type\":\"frame\",\"data\":\"" + base64 + "\"}");
    }

    private void sendEnvelope(String type, byte[] imageBytes) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        sendRaw("{\"type\":\"" + type + "\",\"data\":\"" + base64 + "\"}");
    }

    //שליחת הודעה
    private synchronized void sendRaw(String json) {
        if (ws == null || !isConnected) {
            System.err.println("Cannot send - WebSocket not connected");
            return;
        }
        sendChain = sendChain
                .handle((w, ex) -> ws)
                .thenCompose(w -> w.sendText(json, true))
                .whenComplete((w, ex) -> {
                    if (ex != null) {
                        System.err.println("Error sending message: " + ex.getMessage());
                    }
                });
    }

    public void disconnect() {
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
            } catch (Exception e) {
                System.err.println("Error closing WebSocket: " + e.getMessage());
            }
        }
    }


    @Override
    public void onOpen(WebSocket webSocket) {
        isConnected = true;
        System.out.println("WebSocket opened");
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        isConnected = false;
        System.err.println("WebSocket error: " + error.getMessage());
        if (listener != null) listener.onError(error.getMessage());
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        isConnected = false;
        System.out.println("WebSocket closed: " + reason);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        incomingBuffer.append(data);
        webSocket.request(1);
        if (last) {
            String message = incomingBuffer.toString();
            incomingBuffer.setLength(0);
            handleMessage(message);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void handleMessage(String json) {
        System.out.println("Server response: " + json);
        Map<String, String> fields = parseFlatJson(json);
        if (listener == null) return;

        String type = fields.get("type");
        if ("ready".equals(type)) {
            listener.onReady();
        } else if ("error".equals(type)) {
            listener.onError(fields.getOrDefault("message", "Unknown error"));
        } else if ("true".equals(fields.get("concluded"))) {
            //תוצאת הפרטים המחולצים
            listener.onResult(fields);
        } else {
            listener.onProgress(fields);
        }
    }


    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = FIELD_PATTERN.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            //המרה ל-camelCase
            String mappedKey = snakeToCamel(key);
            result.put(mappedKey, value);
        }
        return result;
    }

    //המרה ל-camelCase
    private static String snakeToCamel(String snake) {
        if (!snake.contains("_")) {
            return snake;
        }

        StringBuilder camel = new StringBuilder();
        boolean nextUpper = false;

        for (char c : snake.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                camel.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                camel.append(c);
            }
        }

        return camel.toString();
    }
}