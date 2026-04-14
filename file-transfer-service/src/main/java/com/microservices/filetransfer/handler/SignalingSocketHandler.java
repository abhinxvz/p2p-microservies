package com.microservices.filetransfer.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SignalingSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingSocketHandler.class);
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery(); // e.g. "userId=alice"
        String userId = extractParam(query, "userId");
        if (userId != null && !userId.isBlank()) {
            sessions.put(userId, session);
            log.info("[WS] Connected: userId={} sessionId={}", userId, session.getId());
        } else {
            log.warn("[WS] Connection rejected — missing userId query param (sessionId={})", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try {
            JsonNode json = objectMapper.readTree(payload);
            JsonNode targetNode = json.get("targetId");
            if (targetNode == null || targetNode.isNull()) {
                log.warn("[WS] Message missing 'targetId' field, ignoring.");
                return;
            }
            String targetId = targetNode.asText();
            WebSocketSession targetSession = sessions.get(targetId);
            if (targetSession != null && targetSession.isOpen()) {
                targetSession.sendMessage(new TextMessage(payload));
                log.debug("[WS] Forwarded message to targetId={}", targetId);
            } else {
                log.warn("[WS] Target peer '{}' not found or session closed.", targetId);
            }
        } catch (Exception e) {
            log.error("[WS] Failed to process message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.values().remove(session);
        log.info("[WS] Disconnected: sessionId={} status={}", session.getId(), status);
    }

    // ---------- helpers ----------

    public void sendToUser(String userId, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                log.error("[WS] Failed to send message to userId={}: {}", userId, e.getMessage());
            }
        } else {
            log.warn("[WS] Cannot send to userId={} — not connected", userId);
        }
    }

    private String extractParam(String query, String key) {
        if (query == null || query.isBlank()) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
}
