package com.textellent.mcp.sse;

import com.textellent.mcp.models.McpRpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SSE sessions for MCP clients.
 * Handles session creation, message routing, and cleanup.
 */
@Component
public class SseSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SseSessionManager.class);

    // Session ID -> SSE Sink for sending responses
    private final Map<String, Sinks.Many<McpRpcResponse>> sessions = new ConcurrentHashMap<>();

    // Session ID -> Authentication info for security
    private final Map<String, SessionInfo> sessionMetadata = new ConcurrentHashMap<>();

    /**
     * Create a new SSE session.
     * @param authentication Authentication principal (email, user ID, etc.)
     * @return Session ID
     */
    public String createSession(String authentication) {
        String sessionId = UUID.randomUUID().toString();

        // Create multicast sink for this session (allows multiple subscribers)
        Sinks.Many<McpRpcResponse> sink = Sinks.many().multicast().onBackpressureBuffer();

        sessions.put(sessionId, sink);
        sessionMetadata.put(sessionId, new SessionInfo(authentication, System.currentTimeMillis()));

        logger.info("Created SSE session: {} for user: {}", sessionId, authentication);
        return sessionId;
    }

    /**
     * Get the sink for a session to send responses.
     */
    public Sinks.Many<McpRpcResponse> getSessionSink(String sessionId) {
        Sinks.Many<McpRpcResponse> sink = sessions.get(sessionId);
        if (sink == null) {
            logger.warn("Session not found: {}", sessionId);
        }
        return sink;
    }

    /**
     * Send a JSON-RPC response to a specific session.
     */
    public boolean sendToSession(String sessionId, McpRpcResponse response) {
        Sinks.Many<McpRpcResponse> sink = sessions.get(sessionId);
        if (sink == null) {
            logger.warn("Cannot send to session {}: session not found", sessionId);
            return false;
        }

        try {
            Sinks.EmitResult result = sink.tryEmitNext(response);
            if (result.isFailure()) {
                logger.error("Failed to emit response to session {}: {}", sessionId, result);
                return false;
            }
            logger.debug("Sent response to session {}: method={}, id={}",
                sessionId, response.getResult() != null ? "result" : "error", response.getId());
            return true;
        } catch (Exception e) {
            logger.error("Error sending to session " + sessionId, e);
            return false;
        }
    }

    /**
     * Close a session and clean up resources.
     */
    public void closeSession(String sessionId) {
        Sinks.Many<McpRpcResponse> sink = sessions.remove(sessionId);
        sessionMetadata.remove(sessionId);

        if (sink != null) {
            sink.tryEmitComplete();
            logger.info("Closed SSE session: {}", sessionId);
        }
    }

    /**
     * Get session metadata.
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return sessionMetadata.get(sessionId);
    }

    /**
     * Check if session exists.
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * Get total active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Clean up stale sessions (older than timeout).
     */
    public void cleanupStaleSessions(long timeoutMillis) {
        long now = System.currentTimeMillis();
        sessionMetadata.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getCreatedAt() > timeoutMillis) {
                logger.info("Cleaning up stale session: {}", entry.getKey());
                closeSession(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Session metadata holder.
     */
    public static class SessionInfo {
        private final String authentication;
        private final long createdAt;
        private long lastActivity;

        public SessionInfo(String authentication, long createdAt) {
            this.authentication = authentication;
            this.createdAt = createdAt;
            this.lastActivity = createdAt;
        }

        public String getAuthentication() {
            return authentication;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getLastActivity() {
            return lastActivity;
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
        }
    }
}
