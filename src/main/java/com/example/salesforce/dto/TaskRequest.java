package com.example.salesforce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO representing a Salesforce Task creation request.
 * 
 * Fields aligned with the standard Task object fields in Salesforce.
 */
public class TaskRequest {

    // Assigned To
    @JsonProperty("OwnerId")
    private String ownerId;

    // Call Duration
    @JsonProperty("CallDurationInSeconds")
    private Integer callDurationInSeconds;

    // Call Object Identifier
    @JsonProperty("CallObject")
    private String callObject;

    // Call Result
    @JsonProperty("CallDisposition")
    private String callDisposition;

    // Call Type
    @JsonProperty("CallType")
    private String callType;

    // Comments
    @JsonProperty("Description")
    private String description;

    // Completed Date/Time (ISO-8601 string)
    @JsonProperty("CompletedDateTime")
    private String completedDateTime;

    // Create Recurring Series of Tasks
    @JsonProperty("IsRecurrence")
    private Boolean isRecurrence;

    // Created By (rarely set on create)
    @JsonProperty("CreatedById")
    private String createdById;

    // Due Date
    @JsonProperty("ActivityDate")
    private String activityDate;

    // Email
    @JsonProperty("Email")
    private String email;

    // Last Modified By (rarely set on create)
    @JsonProperty("LastModifiedById")
    private String lastModifiedById;

    // Name (WhoId - Contact/Lead)
    @JsonProperty("WhoId")
    private String whoId;

    // Phone
    @JsonProperty("Phone")
    private String phone;

    // Priority
    @JsonProperty("Priority")
    private String priority;

    // Recurrence Interval
    @JsonProperty("RecurrenceInterval")
    private Integer recurrenceInterval;

    // Related To (WhatId)
    @JsonProperty("WhatId")
    private String whatId;

    // Reminder Set
    @JsonProperty("IsReminderSet")
    private Boolean isReminderSet;

    // Repeat This Task
    @JsonProperty("RecurrenceRegeneratedType")
    private String recurrenceRegeneratedType;

    // Status
    @JsonProperty("Status")
    private String status;

    // Subject
    @NotBlank(message = "Task subject is required")
    @JsonProperty("Subject")
    private String subject;

    // Task Subtype
    @JsonProperty("TaskSubtype")
    private String taskSubtype;

    // Type
    @JsonProperty("Type")
    private String type;

    // Getters and Setters

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Integer getCallDurationInSeconds() {
        return callDurationInSeconds;
    }

    public void setCallDurationInSeconds(Integer callDurationInSeconds) {
        this.callDurationInSeconds = callDurationInSeconds;
    }

    public String getCallObject() {
        return callObject;
    }

    public void setCallObject(String callObject) {
        this.callObject = callObject;
    }

    public String getCallDisposition() {
        return callDisposition;
    }

    public void setCallDisposition(String callDisposition) {
        this.callDisposition = callDisposition;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCompletedDateTime() {
        return completedDateTime;
    }

    public void setCompletedDateTime(String completedDateTime) {
        this.completedDateTime = completedDateTime;
    }

    public Boolean getIsRecurrence() {
        return isRecurrence;
    }

    public void setIsRecurrence(Boolean isRecurrence) {
        this.isRecurrence = isRecurrence;
    }

    public String getCreatedById() {
        return createdById;
    }

    public void setCreatedById(String createdById) {
        this.createdById = createdById;
    }

    public String getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(String activityDate) {
        this.activityDate = activityDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLastModifiedById() {
        return lastModifiedById;
    }

    public void setLastModifiedById(String lastModifiedById) {
        this.lastModifiedById = lastModifiedById;
    }

    public String getWhoId() {
        return whoId;
    }

    public void setWhoId(String whoId) {
        this.whoId = whoId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Integer getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public void setRecurrenceInterval(Integer recurrenceInterval) {
        this.recurrenceInterval = recurrenceInterval;
    }

    public String getWhatId() {
        return whatId;
    }

    public void setWhatId(String whatId) {
        this.whatId = whatId;
    }

    public Boolean getIsReminderSet() {
        return isReminderSet;
    }

    public void setIsReminderSet(Boolean isReminderSet) {
        this.isReminderSet = isReminderSet;
    }

    public String getRecurrenceRegeneratedType() {
        return recurrenceRegeneratedType;
    }

    public void setRecurrenceRegeneratedType(String recurrenceRegeneratedType) {
        this.recurrenceRegeneratedType = recurrenceRegeneratedType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTaskSubtype() {
        return taskSubtype;
    }

    public void setTaskSubtype(String taskSubtype) {
        this.taskSubtype = taskSubtype;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

