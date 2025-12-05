package com.example.salesforce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * DTO for updating comments dynamically on customer records
 */
public class CommentUpdateRequest {

    @NotBlank(message = "Record ID is required")
    private String recordId;

    @NotBlank(message = "Comment is required")
    @JsonProperty("Comments")
    private String comments;

    @JsonProperty("Description")
    private String description;

    private LocalDateTime timestamp;

    public CommentUpdateRequest() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}








