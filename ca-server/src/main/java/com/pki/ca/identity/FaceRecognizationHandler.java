package com.pki.ca.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FaceRecognizationHandler extends TextWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String identityGatewayBaseUrl;

    private final ExecutorService setupExecutor = Executors.newCachedThreadPool();

    private final Map<String, IdentityVerificationClient> pythonClients = new ConcurrentHashMap<>();
    private final Map<String, byte[]> pendingFrontImage = new ConcurrentHashMap<>();
    private final Map<String, byte[]> pendingBackImage = new ConcurrentHashMap<>();

    public FaceRecognizationHandler(String identityGatewayBaseUrl) {
        this.identityGatewayBaseUrl = identityGatewayBaseUrl;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sid = session.getId();
        JsonNode json;
        try {
            json = MAPPER.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "Invalid JSON message");
            return;
        }

        String type = json.hasNonNull("type") ? json.get("type").asText() : "";

        switch (type) {
            case "id_front" -> pendingFrontImage.put(sid, decode(json));
            case "id_back" -> pendingBackImage.put(sid, decode(json));
            case "start_verification" -> startVerification(session);
            case "frame" -> {
                forwardFrame(session, decode(json));
                System.out.println("I Got frames");
            }
            default -> sendError(session, "Unknown message type: " + type);
        }
    }

    private byte[] decode(JsonNode json) {
        return Base64.getDecoder().decode(json.get("data").asText());
    }

    private void startVerification(WebSocketSession session) {
        String sid = session.getId();
        byte[] front = pendingFrontImage.get(sid);
        if (front == null) {
            sendError(session, "id_front must be sent before start_verification");
            return;
        }
        byte[] back = pendingBackImage.get(sid);

        setupExecutor.submit(() -> {
            try {
                IdentityVerificationClient client = new IdentityVerificationClient(identityGatewayBaseUrl);
                client.createSession();
                client.uploadIdCard(front, back);

                client.openStream(
                        jsonFromPython -> relay(session, jsonFromPython),
                        () -> pythonClients.remove(sid),
                        error -> sendError(session, "Identity service stream error: " + error.getMessage())
                ).get();

                pythonClients.put(sid, client);
                relay(session, "{\"type\":\"ready\"}");
            } catch (Exception e) {
                sendError(session, "Failed to start verification: " + e.getMessage());
            }
        });
    }

    private void forwardFrame(WebSocketSession session, byte[] frameBytes) {
        IdentityVerificationClient client = pythonClients.get(session.getId());
        if (client == null) {
            sendError(session, "Verification not started yet (send start_verification first)");
            return;
        }
        System.out.println("I send frames");
        client.sendFrame(frameBytes);
    }

    private void relay(WebSocketSession session, String rawJson) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(rawJson));
            }
        } catch (Exception ignored) {
        }
    }

    private void sendError(WebSocketSession session, String message) {
        relay(session, "{\"type\":\"error\",\"message\":\"" + message.replace("\"", "'") + "\"}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sid = session.getId();
        pendingFrontImage.remove(sid);
        pendingBackImage.remove(sid);
        IdentityVerificationClient client = pythonClients.remove(sid);
        if (client != null) {
            client.close();
        }
    }
}