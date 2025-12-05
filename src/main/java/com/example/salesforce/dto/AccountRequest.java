package com.example.salesforce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * DTO for creating/updating Salesforce Account.
 * 
 * Fields are aligned with standard and custom Account fields in Salesforce.
 */
public class AccountRequest {

    // Account Name
    @NotBlank(message = "Account name is required")
    @JsonProperty("Name")
    private String name;

    // Account Number
    @JsonProperty("AccountNumber")
    private String accountNumber;

    // Account Owner
    @JsonProperty("OwnerId")
    private String ownerId;

    // Account Site
    @JsonProperty("Site")
    private String site;

    // Account Source
    @JsonProperty("AccountSource")
    private String accountSource;

    // Active (custom)
    @JsonProperty("Active__c")
    private String active;

    // Annual Revenue
    @JsonProperty("AnnualRevenue")
    private Long annualRevenue;

    // Billing Address (split fields)
    @JsonProperty("BillingStreet")
    private String billingStreet;

    @JsonProperty("BillingCity")
    private String billingCity;

    @JsonProperty("BillingState")
    private String billingState;

    @JsonProperty("BillingPostalCode")
    private String billingPostalCode;

    @JsonProperty("BillingCountry")
    private String billingCountry;

    // Clean Status
    @JsonProperty("CleanStatus")
    private String cleanStatus;

    // Created By (rarely used on create, but included for completeness)
    @JsonProperty("CreatedById")
    private String createdById;

    // Customer Priority (custom)
    @JsonProperty("CustomerPriority__c")
    private String customerPriority;

    // D&B Company
    @JsonProperty("DandbCompanyId")
    private String dandbCompanyId;

    // D-U-N-S Number
    @JsonProperty("DunsNumber")
    private String dunsNumber;

    // Data.com Key
    @JsonProperty("Jigsaw")
    private String jigsaw;

    // Description
    @JsonProperty("Description")
    private String description;

    // Einstein Account Tier / Tier
    @JsonProperty("Tier")
    private String tier;

    // Employees
    @JsonProperty("NumberOfEmployees")
    private Integer numberOfEmployees;

    // Fax
    @JsonProperty("Fax")
    private String fax;

    // First Name (custom)
    @JsonProperty("First_Name__c")
    private String firstName;

    // Founding Date (custom)
    @JsonProperty("Founding_Date__c")
    private String foundingDate;

    // Industry
    @JsonProperty("Industry")
    private String industry;

    // Last Modified By (rarely set on create)
    @JsonProperty("LastModifiedById")
    private String lastModifiedById;

    // Last Name (custom)
    @JsonProperty("Last_Name__c")
    private String lastName;

    // Match Billing Address (custom)
    @JsonProperty("Match_Billing_Address__c")
    private Boolean matchBillingAddress;

    // NAICS Code
    @JsonProperty("NaicsCode")
    private String naicsCode;

    // NAICS Description
    @JsonProperty("NaicsDesc")
    private String naicsDescription;

    // Number Of Contacts (custom)
    @JsonProperty("Number_Of_Contacts__c")
    private Long numberOfContacts;

    // Number of Locations
    @JsonProperty("NumberofLocations__c")
    private Integer numberOfLocations;

    // Ownership
    @JsonProperty("Ownership")
    private String ownership;

    // Parent Account
    @JsonProperty("ParentId")
    private String parentId;

    // Phone
    @JsonProperty("Phone")
    private String phone;

    // Potential Value (custom roll-up)
    @JsonProperty("Potential_Value__c")
    private Double potentialValue;

    // Rating
    @JsonProperty("Rating")
    private String rating;

    // Shipping Address (split fields)
    @JsonProperty("ShippingStreet")
    private String shippingStreet;

    @JsonProperty("ShippingCity")
    private String shippingCity;

    @JsonProperty("ShippingState")
    private String shippingState;

    @JsonProperty("ShippingPostalCode")
    private String shippingPostalCode;

    @JsonProperty("ShippingCountry")
    private String shippingCountry;

    // SIC Code
    @JsonProperty("Sic")
    private String sicCode;

    // SIC Description
    @JsonProperty("SicDesc")
    private String sicDescription;

    // SLA (custom)
    @JsonProperty("SLA__c")
    private String sla;

    // SLA Expiration Date (custom)
    @JsonProperty("SLAExpirationDate__c")
    private String slaExpirationDate;

    // SLA Serial Number (custom)
    @JsonProperty("SLASerialNumber__c")
    private String slaSerialNumber;

    // Sum of Opportunities (custom roll-up)
    @JsonProperty("Sum_of_Opportunities__c")
    private Double sumOfOpportunities;

    // Ticker Symbol
    @JsonProperty("TickerSymbol")
    private String tickerSymbol;

    // Top Account (custom)
    @JsonProperty("Top_Account__c")
    private Boolean topAccount;

    // Tradestyle
    @JsonProperty("Tradestyle")
    private String tradestyle;

    // Type
    @JsonProperty("Type")
    private String type;

    // Upsell Opportunity (custom)
    @JsonProperty("UpsellOpportunity__c")
    private String upsellOpportunity;

    // Website
    @JsonProperty("Website")
    private String website;

    // Year Started
    @JsonProperty("YearStarted")
    private String yearStarted;

    // Custom dynamic fields (for any additional fields not modeled above)
    @JsonProperty("CustomFields")
    private Map<String, Object> customFields;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getAccountSource() {
        return accountSource;
    }

    public void setAccountSource(String accountSource) {
        this.accountSource = accountSource;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public Long getAnnualRevenue() {
        return annualRevenue;
    }

    public void setAnnualRevenue(Long annualRevenue) {
        this.annualRevenue = annualRevenue;
    }

    public String getBillingStreet() {
        return billingStreet;
    }

    public void setBillingStreet(String billingStreet) {
        this.billingStreet = billingStreet;
    }

    public String getBillingCity() {
        return billingCity;
    }

    public void setBillingCity(String billingCity) {
        this.billingCity = billingCity;
    }

    public String getBillingState() {
        return billingState;
    }

    public void setBillingState(String billingState) {
        this.billingState = billingState;
    }

    public String getBillingPostalCode() {
        return billingPostalCode;
    }

    public void setBillingPostalCode(String billingPostalCode) {
        this.billingPostalCode = billingPostalCode;
    }

    public String getBillingCountry() {
        return billingCountry;
    }

    public void setBillingCountry(String billingCountry) {
        this.billingCountry = billingCountry;
    }

    public String getCleanStatus() {
        return cleanStatus;
    }

    public void setCleanStatus(String cleanStatus) {
        this.cleanStatus = cleanStatus;
    }

    public String getCreatedById() {
        return createdById;
    }

    public void setCreatedById(String createdById) {
        this.createdById = createdById;
    }

    public String getCustomerPriority() {
        return customerPriority;
    }

    public void setCustomerPriority(String customerPriority) {
        this.customerPriority = customerPriority;
    }

    public String getDandbCompanyId() {
        return dandbCompanyId;
    }

    public void setDandbCompanyId(String dandbCompanyId) {
        this.dandbCompanyId = dandbCompanyId;
    }

    public String getDunsNumber() {
        return dunsNumber;
    }

    public void setDunsNumber(String dunsNumber) {
        this.dunsNumber = dunsNumber;
    }

    public String getJigsaw() {
        return jigsaw;
    }

    public void setJigsaw(String jigsaw) {
        this.jigsaw = jigsaw;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public Integer getNumberOfEmployees() {
        return numberOfEmployees;
    }

    public void setNumberOfEmployees(Integer numberOfEmployees) {
        this.numberOfEmployees = numberOfEmployees;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getFoundingDate() {
        return foundingDate;
    }

    public void setFoundingDate(String foundingDate) {
        this.foundingDate = foundingDate;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getLastModifiedById() {
        return lastModifiedById;
    }

    public void setLastModifiedById(String lastModifiedById) {
        this.lastModifiedById = lastModifiedById;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getMatchBillingAddress() {
        return matchBillingAddress;
    }

    public void setMatchBillingAddress(Boolean matchBillingAddress) {
        this.matchBillingAddress = matchBillingAddress;
    }

    public String getNaicsCode() {
        return naicsCode;
    }

    public void setNaicsCode(String naicsCode) {
        this.naicsCode = naicsCode;
    }

    public String getNaicsDescription() {
        return naicsDescription;
    }

    public void setNaicsDescription(String naicsDescription) {
        this.naicsDescription = naicsDescription;
    }

    public Long getNumberOfContacts() {
        return numberOfContacts;
    }

    public void setNumberOfContacts(Long numberOfContacts) {
        this.numberOfContacts = numberOfContacts;
    }

    public Integer getNumberOfLocations() {
        return numberOfLocations;
    }

    public void setNumberOfLocations(Integer numberOfLocations) {
        this.numberOfLocations = numberOfLocations;
    }

    public String getOwnership() {
        return ownership;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Double getPotentialValue() {
        return potentialValue;
    }

    public void setPotentialValue(Double potentialValue) {
        this.potentialValue = potentialValue;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getShippingStreet() {
        return shippingStreet;
    }

    public void setShippingStreet(String shippingStreet) {
        this.shippingStreet = shippingStreet;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingState() {
        return shippingState;
    }

    public void setShippingState(String shippingState) {
        this.shippingState = shippingState;
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public void setShippingPostalCode(String shippingPostalCode) {
        this.shippingPostalCode = shippingPostalCode;
    }

    public String getShippingCountry() {
        return shippingCountry;
    }

    public void setShippingCountry(String shippingCountry) {
        this.shippingCountry = shippingCountry;
    }

    public String getSicCode() {
        return sicCode;
    }

    public void setSicCode(String sicCode) {
        this.sicCode = sicCode;
    }

    public String getSicDescription() {
        return sicDescription;
    }

    public void setSicDescription(String sicDescription) {
        this.sicDescription = sicDescription;
    }

    public String getSla() {
        return sla;
    }

    public void setSla(String sla) {
        this.sla = sla;
    }

    public String getSlaExpirationDate() {
        return slaExpirationDate;
    }

    public void setSlaExpirationDate(String slaExpirationDate) {
        this.slaExpirationDate = slaExpirationDate;
    }

    public String getSlaSerialNumber() {
        return slaSerialNumber;
    }

    public void setSlaSerialNumber(String slaSerialNumber) {
        this.slaSerialNumber = slaSerialNumber;
    }

    public Double getSumOfOpportunities() {
        return sumOfOpportunities;
    }

    public void setSumOfOpportunities(Double sumOfOpportunities) {
        this.sumOfOpportunities = sumOfOpportunities;
    }

    public String getTickerSymbol() {
        return tickerSymbol;
    }

    public void setTickerSymbol(String tickerSymbol) {
        this.tickerSymbol = tickerSymbol;
    }

    public Boolean getTopAccount() {
        return topAccount;
    }

    public void setTopAccount(Boolean topAccount) {
        this.topAccount = topAccount;
    }

    public String getTradestyle() {
        return tradestyle;
    }

    public void setTradestyle(String tradestyle) {
        this.tradestyle = tradestyle;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUpsellOpportunity() {
        return upsellOpportunity;
    }

    public void setUpsellOpportunity(String upsellOpportunity) {
        this.upsellOpportunity = upsellOpportunity;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getYearStarted() {
        return yearStarted;
    }

    public void setYearStarted(String yearStarted) {
        this.yearStarted = yearStarted;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}







