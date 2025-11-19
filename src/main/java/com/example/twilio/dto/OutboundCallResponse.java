package com.example.twilio.dto;

/**
 * DTO for outbound call responses
 */
public class OutboundCallResponse {

    private String callSid;
    private String status;
    private String message;
    private String toNumber;
    private String fromNumber;

    public OutboundCallResponse() {
    }

    public OutboundCallResponse(String callSid, String status, String message, String toNumber, String fromNumber) {
        this.callSid = callSid;
        this.status = status;
        this.message = message;
        this.toNumber = toNumber;
        this.fromNumber = fromNumber;
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
}

