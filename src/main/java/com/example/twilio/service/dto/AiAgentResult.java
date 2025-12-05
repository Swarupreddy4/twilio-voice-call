package com.example.twilio.service.dto;

/**
 * Result of processing audio by the AI agent.
 * Contains the AI response text and flags for call control (e.g., end call).
 */
public class AiAgentResult {

    private final String aiResponse;
    private final boolean endCall;

    public AiAgentResult(String aiResponse, boolean endCall) {
        this.aiResponse = aiResponse;
        this.endCall = endCall;
    }

    public String getAiResponse() {
        return aiResponse;
    }

    public boolean isEndCall() {
        return endCall;
    }
}



