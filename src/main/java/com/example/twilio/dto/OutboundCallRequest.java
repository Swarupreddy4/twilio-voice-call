package com.example.twilio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for outbound call requests
 */
public class OutboundCallRequest {

    @NotBlank(message = "To number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String toNumber;

    private String fromNumber; // Optional, will use default if not provided

    private String customMessage; // Optional custom greeting message

    public OutboundCallRequest() {
    }

    public OutboundCallRequest(String toNumber, String fromNumber, String customMessage) {
        this.toNumber = toNumber;
        this.fromNumber = fromNumber;
        this.customMessage = customMessage;
    }

    public String getToNumber() {
        return toNumber;
    }

    public void setToNumber(String toNumber) {
        this.toNumber = toNumber;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }
}

