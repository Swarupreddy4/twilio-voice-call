package com.example.salesforce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Generic response DTO for Salesforce API operations
 */
public class SalesforceResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("errors")
    private List<String> errors;

    private String message;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}








