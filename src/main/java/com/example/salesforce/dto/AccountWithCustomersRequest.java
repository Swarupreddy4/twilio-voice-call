package com.example.salesforce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO for creating Account with multiple Customer Records
 */
public class AccountWithCustomersRequest {

    @NotNull(message = "Account is required")
    @Valid
    private AccountRequest account;

    @Valid
    private List<CustomerRecord> customers;

    // Getters and Setters
    public AccountRequest getAccount() {
        return account;
    }

    public void setAccount(AccountRequest account) {
        this.account = account;
    }

    public List<CustomerRecord> getCustomers() {
        return customers;
    }

    public void setCustomers(List<CustomerRecord> customers) {
        this.customers = customers;
    }
}








