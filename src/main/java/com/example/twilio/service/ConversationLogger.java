package com.example.twilio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for logging conversation history
 * Tracks user speech and AI responses per session
 */
@Service
public class ConversationLogger {

    private static final Logger logger = LoggerFactory.getLogger(ConversationLogger.class);
    
    // Store conversation history per session
    private final ConcurrentMap<String, List<ConversationEntry>> conversationHistory = new ConcurrentHashMap<>();
    
    /**
     * Logs a conversation entry (user speech or AI response)
     */
    public void logConversation(String sessionId, String callSid, String type, String text) {
        ConversationEntry entry = new ConversationEntry(
            LocalDateTime.now(),
            type,
            text,
            callSid
        );
        
        conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(entry);
        
        // Log to console with timestamp
        String timestamp = entry.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logger.info("[{}] [{}] [{}] {}", timestamp, callSid, type, text);
    }
    
    /**
     * Gets conversation history for a session
     */
    public List<ConversationEntry> getConversationHistory(String sessionId) {
        return conversationHistory.getOrDefault(sessionId, new ArrayList<>());
    }
    
    /**
     * Clears conversation history for a session
     */
    public void clearConversationHistory(String sessionId) {
        conversationHistory.remove(sessionId);
    }
    
    /**
     * Gets formatted conversation log for a session
     */
    public String getFormattedConversationLog(String sessionId) {
        List<ConversationEntry> history = getConversationHistory(sessionId);
        if (history.isEmpty()) {
            return "No conversation history for session: " + sessionId;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("CONVERSATION LOG - Session: ").append(sessionId).append("\n");
        sb.append("=".repeat(80)).append("\n");
        
        for (ConversationEntry entry : history) {
            String timestamp = entry.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            sb.append(String.format("[%s] %s: %s\n", 
                timestamp, 
                entry.getType(), 
                entry.getText()));
        }
        
        sb.append("=".repeat(80)).append("\n");
        return sb.toString();
    }
    
    /**
     * Conversation entry class
     */
    public static class ConversationEntry {
        private final LocalDateTime timestamp;
        private final String type; // "USER" or "AI"
        private final String text;
        private final String callSid;
        
        public ConversationEntry(LocalDateTime timestamp, String type, String text, String callSid) {
            this.timestamp = timestamp;
            this.type = type;
            this.text = text;
            this.callSid = callSid;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public String getType() {
            return type;
        }
        
        public String getText() {
            return text;
        }
        
        public String getCallSid() {
            return callSid;
        }
    }
}

